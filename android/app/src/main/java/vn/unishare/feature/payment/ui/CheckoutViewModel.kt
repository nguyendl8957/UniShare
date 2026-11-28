package vn.unishare.feature.payment.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.data.remote.OrderApiException
import java.io.IOException
import java.time.Instant
import java.util.UUID

/**
 * ============================================================================
 * LOGIC ĐIỀU PHỐI THANH TOÁN VÀ THEO DÕI ĐƠN HÀNG (B04)
 * Issue: [B04] Dựng giao diện Checkout và theo dõi trạng thái đơn (#10)
 * ============================================================================
 *
 * ViewModel chịu trách nhiệm:
 * 1. Nhận [docId] và khởi tạo hoặc khôi phục đơn hàng thông qua [OrderRepository].
 * 2. Lưu giữ [idempotencyKey] và [orderId] trong [SavedStateHandle] để chống tạo trùng
 *    đơn khi người dùng bấm nhiều lần hoặc khi xoay màn hình/app restart.
 * 3. Quản lý vòng đời Polling có Backoff: tự động thăm dò trạng thái đơn khi [OrderStatus.PENDING],
 *    và tự động ngắt polling khi đạt trạng thái cuối ([OrderStatus.PAID], [OrderStatus.EXPIRED],
 *    [OrderStatus.REVIEW_REQUIRED], [OrderStatus.FAILED]) hoặc khi rời màn hình.
 * 4. Quản lý đồng hồ đếm ngược 15 phút (TTL) dựa trên mốc [OrderUiModel.expiresAtUtc].
 * 5. Tách biệt lỗi mạng và lỗi nghiệp vụ: Giữ nguyên PENDING khi mất mạng.
 * 6. Tuân thủ quy tắc bảo mật: Không ghi thông tin tài khoản ngân hàng ra log.
 */

// Các khóa định danh lưu trong SavedStateHandle
const val KEY_CHECKOUT_IDEMPOTENCY = "checkout_idempotency_key"
const val KEY_CHECKOUT_ORDER_ID = "checkout_order_id"

class CheckoutViewModel(
    val docId: String,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val resumeOrderId: String? = null
) : ViewModel() {

    // 1. Quản lý khóa Idempotency bất biến: lấy từ SavedStateHandle nếu đã có,
    // hoặc sinh mới một chuỗi UUID duy nhất và lưu lại ngay vào SavedStateHandle.
    val idempotencyKey: String = savedStateHandle.get<String>(KEY_CHECKOUT_IDEMPOTENCY)
        ?: UUID.randomUUID().toString().also {
            savedStateHandle[KEY_CHECKOUT_IDEMPOTENCY] = it
        }

    // 2. Trạng thái giao diện tổng thể (StateFlow)
    private val _uiState = MutableStateFlow(CheckoutUiState(isLoading = true))
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    // 3. Kênh phát sự kiện dùng một lần (One-shot Events)
    private val _eventChannel = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    // Quản lý các Coroutine Jobs bất đồng bộ
    private var pollingJob: Job? = null
    private var countdownJob: Job? = null
    private var loadOrderJob: Job? = null
    private var manualRefreshJob: Job? = null

    init {
        // Tự động kiểm tra phiên và nạp hoặc khôi phục đơn hàng khi ViewModel được tạo
        loadOrCreateOrder()
    }

    /**
     * Khởi tạo đơn mới hoặc nạp lại đơn đang có hiệu lực.
     * Hỗ trợ khôi phục orderId từ SavedStateHandle nếu có.
     */
    fun loadOrCreateOrder() {
        if (loadOrderJob?.isActive == true) return
        // Kiểm tra phiên đăng nhập (nếu có AuthRepository)
        val currentSession = authRepository?.session?.value
        if (currentSession is AuthSession.Restoring) return
        if (currentSession is AuthSession.Guest) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    businessError = "Bạn cần đăng nhập để thanh toán tài liệu này."
                )
            }
            viewModelScope.launch(mainDispatcher) {
                _eventChannel.send(CheckoutEvent.NavigateToAuth("checkout:$docId"))
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                networkError = null,
                businessError = null
            )
        }

        loadOrderJob = viewModelScope.launch(mainDispatcher) {
            val savedOrderId = savedStateHandle.get<String>(KEY_CHECKOUT_ORDER_ID) ?: resumeOrderId

            // Nếu đã có orderId lưu trước đó (sau khi xoay màn hình hoặc resume)
            val result = if (!savedOrderId.isNullOrBlank()) {
                val statusResult = orderRepository.getOrderStatus(savedOrderId)
                if (statusResult.isSuccess) {
                    statusResult
                } else {
                    val error = statusResult.exceptionOrNull()
                    if (resumeOrderId == null && error is OrderApiException && error.httpStatus == 404) {
                        orderRepository.createOrGetOrder(docId, idempotencyKey)
                    } else {
                        statusResult
                    }
                }
            } else {
                orderRepository.createOrGetOrder(docId, idempotencyKey)
            }

            ensureActive()
            result.fold(
                onSuccess = { order ->
                    handleOrderSuccess(order)
                },
                onFailure = { error ->
                    handleOrderFailure(error)
                }
            )
        }
    }

    /**
     * Xử lý khi lấy/tạo đơn hàng thành công từ Repository
     */
    private fun handleOrderSuccess(order: OrderUiModel) {
        // Lưu orderId vào SavedStateHandle để phục hồi khi cần
        savedStateHandle[KEY_CHECKOUT_ORDER_ID] = order.orderId

        val initialSeconds = calculateRemainingSeconds(order.expiresAtUtc)
        val actualStatus = if (initialSeconds <= 0 && order.status == OrderStatus.PENDING) {
            OrderStatus.EXPIRED
        } else {
            order.status
        }

        _uiState.update {
            it.copy(
                isLoading = false,
                order = order.copy(status = actualStatus),
                status = actualStatus,
                remainingSeconds = initialSeconds.coerceAtLeast(0L),
                networkError = null,
                businessError = null
            )
        }

        // Bắt đầu đếm ngược thời gian nếu đơn còn PENDING
        if (actualStatus == OrderStatus.PENDING) {
            startCountdownTimer(initialSeconds)
            startPolling(order.orderId)
        } else if (actualStatus == OrderStatus.PAID) {
            stopPolling()
        }
    }

    /**
     * Xử lý phân loại lỗi: Phân tách lỗi kết nối mạng khỏi lỗi nghiệp vụ
     */
    private fun handleOrderFailure(error: Throwable) {
        val message = error.message.orEmpty()

        val unauthorized =
            (error is OrderApiException && error.httpStatus == 401) ||
            message.contains("401 Unauthorized", ignoreCase = true)

        when {
            unauthorized -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        remainingSeconds = 0L,
                        businessError = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại."
                    )
                }
                viewModelScope.launch(mainDispatcher) {
                    _eventChannel.send(
                        CheckoutEvent.NavigateToAuth("checkout:$docId")
                    )
                }
            }

            error is OrderApiException && error.httpStatus >= 500 -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        networkError = "Máy chủ tạm thời không phản hồi. Vui lòng thử lại."
                    )
                }
            }

            error is IOException && error !is OrderApiException -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        networkError = "Mất kết nối mạng. Vui lòng thử lại."
                    )
                }
            }

            else -> {
                val errorCode = if (error is OrderApiException) {
                    error.errorCode
                } else {
                    message
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = OrderStatus.FAILED,
                        businessError = mapBusinessErrorMessage(errorCode)
                    )
                }
            }
        }
    }

    /**
     * Vòng lặp Polling kiểm tra trạng thái đơn hàng (Lifecycle-aware Polling with Backoff).
     *
     * Chu kỳ thăm dò an toàn:
     * - Bắt đầu với khoảng chờ 3 giây (3000ms).
     * - Mỗi chu kỳ tăng thêm 1 giây cho đến tối đa 8 giây (8000ms) để tiết kiệm tài nguyên.
     * - Tự động dừng ngay khi đơn chuyển sang PAID, EXPIRED, REVIEW_REQUIRED, FAILED.
     */
    fun startPolling(orderId: String) {
        // Hủy polling job cũ nếu đang chạy
        pollingJob?.cancel()

        pollingJob = viewModelScope.launch(mainDispatcher) {
            var currentDelayMs = 3000L

            while (_uiState.value.status == OrderStatus.PENDING) {
                delay(currentDelayMs)

                // Nếu trong lúc chờ trạng thái đã thay đổi hoặc hết hạn thì thoát ngay
                if (_uiState.value.status != OrderStatus.PENDING) break
                if (manualRefreshJob?.isActive == true) continue

                _uiState.update { it.copy(isPolling = true) }

                val statusResult = orderRepository.getOrderStatus(orderId)
                ensureActive()
                statusResult.fold(
                    onSuccess = { updatedOrder ->
                        val seconds = calculateRemainingSeconds(updatedOrder.expiresAtUtc)
                        val newStatus = if (seconds == 0L && updatedOrder.status == OrderStatus.PENDING) {
                            OrderStatus.EXPIRED
                        } else updatedOrder.status
                        _uiState.update {
                            it.copy(
                                order = updatedOrder.copy(status = newStatus),
                                status = newStatus,
                                remainingSeconds = seconds,
                                isPolling = false,
                                networkError = null
                            )
                        }

                        // Nếu đã thanh toán thành công (PAID) hoặc trạng thái kết thúc -> Dừng poll
                        if (newStatus != OrderStatus.PENDING) {
                            if (newStatus == OrderStatus.PAID) {
                                _eventChannel.send(CheckoutEvent.ShowSnackbar("Thanh toán thành công!"))
                            }
                            stopPolling()
                            return@launch
                        }
                    },
                    onFailure = { pollError ->
                        if (pollError is OrderApiException && pollError.httpStatus == 401) {
                            handleOrderFailure(pollError)
                            stopPolling()
                            return@launch
                        }
                        _uiState.update { it.copy(isPolling = false, networkError = "Chưa cập nhật được trạng thái giao dịch.") }
                    }
                )

                // Áp dụng Backoff tăng dần khoảng cách thăm dò: 3s -> 4s -> 5s -> ... -> max 8s
                if (currentDelayMs < 8000L) {
                    currentDelayMs = (currentDelayMs + 1000L).coerceAtMost(8000L)
                }
            }
        }
    }

    /**
     * Dừng vòng lặp Polling (gọi khi rời màn hình hoặc khi đơn đạt trạng thái kết thúc)
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    /**
     * Đồng hồ đếm ngược 15 phút (TTL Countdown Timer).
     * Mỗi giây trừ remainingSeconds đi 1. Khi về 0, chuyển sang EXPIRED.
     */
    private fun startCountdownTimer(initialSeconds: Long) {
        countdownJob?.cancel()

        countdownJob = viewModelScope.launch(mainDispatcher) {
            var seconds = initialSeconds
            while (seconds > 0) {
                delay(1000L)
                // Nếu trạng thái đã chuyển sang PAID hoặc FAILED thì dừng đếm ngược
                if (_uiState.value.status != OrderStatus.PENDING) break

                val expiresAt = _uiState.value.order?.expiresAtUtc.orEmpty()
                seconds = calculateRemainingSeconds(expiresAt)
                _uiState.update { it.copy(remainingSeconds = seconds) }
            }

            // Khi hết thời gian mà đơn vẫn PENDING -> Đơn chính thức EXPIRED
            if (_uiState.value.status == OrderStatus.PENDING) {
                _uiState.update {
                    it.copy(
                        remainingSeconds = 0L,
                        status = OrderStatus.EXPIRED
                    )
                }
                stopPolling()
            }
        }
    }

    /**
     * Tính số giây còn lại từ chuỗi thời gian UTC ISO 8601 của expiresAt
     */
    private fun calculateRemainingSeconds(expiresAtIso: String): Long {
        return runCatching {
            val expiryMs = Instant.parse(expiresAtIso).toEpochMilli()
            val nowMs = System.currentTimeMillis()
            val diffSec = (expiryMs - nowMs) / 1000L
            diffSec.coerceAtLeast(0L)
        }.getOrDefault(0L)
    }

    /**
     * Xử lý thao tác Sao chép thông tin (Copy to Clipboard)
     */
    fun copyField(fieldLabel: String, textToCopy: String) {
        val message = "Đã sao chép $fieldLabel"
        _uiState.update { it.copy(copiedFieldMessage = message) }
        viewModelScope.launch(mainDispatcher) {
            _eventChannel.send(CheckoutEvent.ShowSnackbar(message))
        }
    }

    /**
     * Người dùng chủ động bấm nút "Kiểm tra trạng thái" / "Làm mới"
     */
    fun manualRefresh() {
        if (_uiState.value.isPolling || manualRefreshJob?.isActive == true) return
        val currentOrder = _uiState.value.order
        if (currentOrder != null && _uiState.value.status in setOf(OrderStatus.PENDING, OrderStatus.EXPIRED)) {
            manualRefreshJob = viewModelScope.launch(mainDispatcher) {
                _uiState.update { it.copy(isPolling = true, networkError = null) }
                val result = orderRepository.getOrderStatus(currentOrder.orderId)
                ensureActive()
                result.fold(
                    onSuccess = { updated ->
                        val seconds = calculateRemainingSeconds(updated.expiresAtUtc)
                        val status = if (seconds == 0L && updated.status == OrderStatus.PENDING) {
                            OrderStatus.EXPIRED
                        } else updated.status
                        _uiState.update {
                            it.copy(
                                order = updated.copy(status = status),
                                status = status,
                                remainingSeconds = seconds,
                                isPolling = false,
                                networkError = null
                            )
                        }
                        if (status != OrderStatus.PENDING) {
                            if (status == OrderStatus.PAID) {
                                _eventChannel.send(CheckoutEvent.ShowSnackbar("Thanh toán thành công!"))
                            }
                            stopPolling()
                        }
                    },
                    onFailure = { error ->
                        handleOrderFailure(error)
                        _uiState.update { it.copy(isPolling = false) }
                    }
                )
            }
        } else if (currentOrder == null) {
            loadOrCreateOrder()
        }
    }

    /**
     * Tự động khôi phục polling hoặc làm mới trạng thái khi màn hình Resume
     */
    fun onScreenResumed() {
        val currentOrder = _uiState.value.order
        if (currentOrder != null && _uiState.value.status == OrderStatus.EXPIRED) {
            manualRefresh()
        }
        if (currentOrder != null && _uiState.value.status == OrderStatus.PENDING) {
            manualRefresh()
            if (pollingJob == null || pollingJob?.isActive != true) {
                startPolling(currentOrder.orderId)
            }
        }
    }

    /**
     * Người dùng bấm nút "Mở tài liệu đọc ngay" khi đơn đã PAID
     */
    fun onOpenReaderClicked() {
        val current = _uiState.value

        if (
            current.status != OrderStatus.PAID ||
            current.order?.status != OrderStatus.PAID
        ) {
            return
        }

        viewModelScope.launch(mainDispatcher) {
            _eventChannel.send(CheckoutEvent.NavigateToReader(docId))
        }
    }

    /**
     * Người dùng bấm nút "Quay lại"
     */
    fun onBackClicked() {
        stopPolling()
        viewModelScope.launch(mainDispatcher) {
            _eventChannel.send(CheckoutEvent.NavigateBack)
        }
    }

    /**
     * Ánh xạ các mã lỗi nghiệp vụ chuẩn từ Backend B02 thành câu thông báo thân thiện
     */
    private fun mapBusinessErrorMessage(rawError: String): String {
        return when {
            rawError.contains("ALREADY_OWNED", ignoreCase = true) ->
                "Bạn đã mua và sở hữu tài liệu này từ trước."
            rawError.contains("SELF_PURCHASE", ignoreCase = true) ->
                "Bạn là tác giả của tài liệu này nên không thể tự mua."
            rawError.contains("ORDER_UNDER_REVIEW", ignoreCase = true) ->
                "Đơn hàng trước đó đang trong quá trình đối soát. Vui lòng chờ nhân viên kiểm tra."
            rawError.contains("DOCUMENT_NOT_FOUND", ignoreCase = true) ->
                "Không tìm thấy tài liệu trên hệ thống."
            rawError.contains("INVALID_DOCUMENT_STATUS", ignoreCase = true) ->
                "Tài liệu hiện chưa được xuất bản hoặc đã bị ẩn."
            rawError.isNotBlank() -> rawError
            else -> "Đã xảy ra lỗi khi tạo đơn hàng. Vui lòng thử lại."
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Đảm bảo dọn dẹp các coroutines và hủy polling khi ViewModel bị hủy
        stopPolling()
        countdownJob?.cancel()
    }
}

/**
 * ============================================================================
 * FAKE ORDER REPOSITORY (KIỂM THỬ ĐỘC LẬP & DEMO THEO PORTS & ADAPTERS)
 * ============================================================================
 *
 * Cung cấp nguồn dữ liệu giả lập có thể kiểm soát hoàn toàn (Controllable Fake).
 * Phục vụ Unit Test [CheckoutViewModelTest] và Compose Preview [CheckoutScreenTest]
 * mà không cần phụ thuộc vào Firebase Backend thật của A07.
 */
class FakeOrderRepository(
    var initialStatus: OrderStatus = OrderStatus.PENDING,
    var mockAmountVnd: Long = 50_000L,
    var mockDocumentTitle: String = "Giáo trình Cấu trúc dữ liệu & Giải thuật",
    var mockBankCode: String = "MB",
    var mockAccountNumber: String = "0345678999",
    var mockAccountHolder: String = "UNISHARE SYSTEM"
) : OrderRepository {

    var createCallCount = 0
    var getStatusCallCount = 0
    var lastIdempotencyKey: String? = null

    var nextResult: Result<OrderUiModel>? = null
    var pollsUntilPaid: Int? = null

    private var inMemoryOrder: OrderUiModel? = null

    override suspend fun createOrGetOrder(
        docId: String,
        idempotencyKey: String
    ): Result<OrderUiModel> {
        createCallCount++
        lastIdempotencyKey = idempotencyKey

        nextResult?.let { return it }

        val order = OrderUiModel(
            orderId = "ord_fake_${docId.takeLast(6)}",
            docId = docId,
            documentTitle = mockDocumentTitle,
            amountVnd = mockAmountVnd,
            status = initialStatus,
            paymentCode = "UNI${docId.takeLast(3).uppercase()}99",
            createdAtUtc = Instant.now().toString(),
            expiresAtUtc = Instant.now().plusSeconds(900).toString(), // 15 phút
            transferInfo = BankTransferInfo(
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = mockBankCode,
                accountNumber = mockAccountNumber,
                accountHolder = mockAccountHolder,
                amountVnd = mockAmountVnd,
                paymentCode = "UNI${docId.takeLast(3).uppercase()}99"
            )
        )
        inMemoryOrder = order
        return Result.success(order)
    }

    override suspend fun getOrderStatus(orderId: String): Result<OrderUiModel> {
        getStatusCallCount++

        nextResult?.let { return it }

        val current = inMemoryOrder ?: OrderUiModel(
            orderId = orderId,
            docId = "doc_default",
            documentTitle = mockDocumentTitle,
            amountVnd = mockAmountVnd,
            status = initialStatus,
            paymentCode = "UNI8A3F12",
            createdAtUtc = Instant.now().toString(),
            expiresAtUtc = Instant.now().plusSeconds(900).toString(),
            transferInfo = BankTransferInfo(
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = mockBankCode,
                accountNumber = mockAccountNumber,
                accountHolder = mockAccountHolder,
                amountVnd = mockAmountVnd,
                paymentCode = "UNI8A3F12"
            )
        )

        // Mô phỏng chuyển trạng thái sang PAID sau N lần polling
        val targetStatus = pollsUntilPaid?.let { threshold ->
            if (getStatusCallCount >= threshold) OrderStatus.PAID else current.status
        } ?: current.status

        val updated = current.copy(status = targetStatus)
        inMemoryOrder = updated
        return Result.success(updated)
    }

    /**
     * Hàm phụ trợ cho Unit Test: Cập nhật trạng thái đơn hàng thủ công
     */
    fun setOrderStatus(newStatus: OrderStatus) {
        inMemoryOrder = inMemoryOrder?.copy(status = newStatus)
    }
}

