package vn.unishare.feature.payment.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.payment.ui.OrderStatus
import java.io.IOException
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ============================================================================
 * ĐẶC TẢ HỢP ĐỒNG DỮ LIỆU VÀ TẦNG ĐIỀU PHỐI LỊCH SỬ GIAO DỊCH (B05)
 * Issue: [B05] Dựng lịch sử mua và chi tiết biên nhận Buyer (#20)
 * ============================================================================
 */

/**
 * Dữ liệu phản hồi của một trang lịch sử giao dịch phân trang theo Cursor
 */
data class BuyerTransactionsPage(
    val items: List<BuyerTransactionItemUiModel>,
    val nextCursor: String?
)

/**
 * Hợp đồng giao tiếp dữ liệu lịch sử giao dịch của Buyer
 * Khi A07 bàn giao API adapter, triển khai chính thức sẽ cài đặt interface này.
 */
interface BuyerTransactionsRepository {
    suspend fun getMyTransactions(
        cursor: String? = null,
        limit: Int = 20
    ): Result<BuyerTransactionsPage>

    suspend fun getTransactionDetail(
        orderId: String
    ): Result<BuyerTransactionDetailUiModel>
}

/**
 * ============================================================================
 * BỘ GIẢ LẬP DỮ LIỆU ĐỘC LẬP (FAKE REPOSITORY) PHỤC VỤ TEST & DEV THEO MỤC 2
 * ============================================================================
 *
 * Hỗ trợ:
 * 1. Hai tài khoản Buyer riêng biệt ([UID_BUYER_ONE], [UID_BUYER_TWO]) để kiểm tra tính bảo mật.
 * 2. Đầy đủ các trạng thái đơn hàng: PENDING, PAID, EXPIRED, REVIEW_REQUIRED.
 * 3. Phân trang cursor nhiều trang và kiểm tra khử trùng lặp (Dedup).
 * 4. Mô phỏng mất mạng (Offline simulation).
 */
class FakeBuyerTransactionsRepository(
    var activeUid: String? = UID_BUYER_ONE,
    var simulateOffline: Boolean = false,
    var simulateOrderNotFound: Boolean = false
) : BuyerTransactionsRepository {

    companion object {
        const val UID_BUYER_ONE = "uid_buyer_test_01"
        const val UID_BUYER_TWO = "uid_buyer_test_02"
        const val UID_EMPTY_BUYER = "uid_buyer_empty"

        const val CURSOR_PAGE_2 = "cursor_page_02"
    }

    // Kho dữ liệu mẫu gán theo từng UID
    private val ordersByUser = mutableMapOf<String, MutableList<BuyerTransactionDetailUiModel>>()

    init {
        resetFixture()
    }

    fun resetFixture() {
        val now = Instant.now()
        val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")

        // 1. Dữ liệu cho Buyer 1: Có 4 đơn với 4 trạng thái khác nhau
        val buyerOneOrders = mutableListOf(
            BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_001",
                docId = "doc_dsa_01",
                documentTitle = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
                amountVnd = 50_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNI8A3F12",
                createdAtUtc = now.minusSeconds(7200).toString(),
                expiresAtUtc = now.minusSeconds(6300).toString(),
                formattedCreatedAt = formatIsoToVn(now.minusSeconds(7200).toString(), zoneVn),
                formattedExpiresAt = formatIsoToVn(now.minusSeconds(6300).toString(), zoneVn),
                formattedAmountVnd = formatVnd(50_000L),
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = "MB",
                accountNumber = "0345678999",
                accountHolder = "UNISHARE SYSTEM"
            ),
            BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_002",
                docId = "doc_macro_econ",
                documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
                amountVnd = 35_000L,
                status = OrderStatus.PENDING,
                paymentCode = "UNIMACRO01",
                createdAtUtc = now.minusSeconds(300).toString(),
                expiresAtUtc = now.plusSeconds(600).toString(),
                formattedCreatedAt = formatIsoToVn(now.minusSeconds(300).toString(), zoneVn),
                formattedExpiresAt = formatIsoToVn(now.plusSeconds(600).toString(), zoneVn),
                formattedAmountVnd = formatVnd(35_000L),
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = "MB",
                accountNumber = "0345678999",
                accountHolder = "UNISHARE SYSTEM"
            ),
            BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_003",
                docId = "doc_os_deadlock",
                documentTitle = "Hệ điều hành - Đồng bộ tiến trình & Deadlock",
                amountVnd = 40_000L,
                status = OrderStatus.EXPIRED,
                paymentCode = "UNIOS009",
                createdAtUtc = now.minusSeconds(86400).toString(),
                expiresAtUtc = now.minusSeconds(85500).toString(),
                formattedCreatedAt = formatIsoToVn(now.minusSeconds(86400).toString(), zoneVn),
                formattedExpiresAt = formatIsoToVn(now.minusSeconds(85500).toString(), zoneVn),
                formattedAmountVnd = formatVnd(40_000L),
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = "MB",
                accountNumber = "0345678999",
                accountHolder = "UNISHARE SYSTEM"
            ),
            BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_004",
                docId = "doc_network_review",
                documentTitle = "Đề cương Ôn thi Mạng máy tính",
                amountVnd = 45_000L,
                status = OrderStatus.REVIEW_REQUIRED,
                paymentCode = "UNINET888",
                createdAtUtc = now.minusSeconds(172800).toString(),
                expiresAtUtc = now.minusSeconds(171900).toString(),
                formattedCreatedAt = formatIsoToVn(now.minusSeconds(172800).toString(), zoneVn),
                formattedExpiresAt = formatIsoToVn(now.minusSeconds(171900).toString(), zoneVn),
                formattedAmountVnd = formatVnd(45_000L),
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = "MB",
                accountNumber = "0345678999",
                accountHolder = "UNISHARE SYSTEM"
            )
        )

        // 2. Dữ liệu cho Buyer 2: Chỉ có 1 đơn PAID duy nhất
        val buyerTwoOrders = mutableListOf(
            BuyerTransactionDetailUiModel(
                orderId = "ord_buyer2_999",
                docId = "doc_algebra",
                documentTitle = "Đại số tuyến tính nâng cao",
                amountVnd = 60_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNIALG999",
                createdAtUtc = now.minusSeconds(3600).toString(),
                expiresAtUtc = now.minusSeconds(2700).toString(),
                formattedCreatedAt = formatIsoToVn(now.minusSeconds(3600).toString(), zoneVn),
                formattedExpiresAt = formatIsoToVn(now.minusSeconds(2700).toString(), zoneVn),
                formattedAmountVnd = formatVnd(60_000L),
                bankName = "Ngân hàng Quân đội (MB)",
                bankCode = "MB",
                accountNumber = "0345678999",
                accountHolder = "UNISHARE SYSTEM"
            )
        )

        ordersByUser[UID_BUYER_ONE] = buyerOneOrders
        ordersByUser[UID_BUYER_TWO] = buyerTwoOrders
        ordersByUser[UID_EMPTY_BUYER] = mutableListOf()
    }

    override suspend fun getMyTransactions(
        cursor: String?,
        limit: Int
    ): Result<BuyerTransactionsPage> {
        if (simulateOffline) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }

        val uid = activeUid ?: return Result.failure(
            IllegalStateException("Chưa xác thực người dùng")
        )

        val all = ordersByUser[uid] ?: emptyList()

        // Mô phỏng phân trang: Nếu cursor là null trả về 2 phần tử đầu + cursor; nếu là CURSOR_PAGE_2 trả về phần còn lại
        val (pageItems, nextCursor) = when (cursor) {
            null -> {
                if (all.size > 2) {
                    all.take(2) to CURSOR_PAGE_2
                } else {
                    all to null
                }
            }
            CURSOR_PAGE_2 -> {
                all.drop(2) to null
            }
            else -> {
                emptyList<BuyerTransactionDetailUiModel>() to null
            }
        }

        val uiItems = pageItems.map { detail ->
            val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")
            BuyerTransactionItemUiModel(
                orderId = detail.orderId,
                docId = detail.docId,
                documentTitle = detail.documentTitle,
                amountVnd = detail.amountVnd,
                status = detail.status,
                paymentCode = detail.paymentCode,
                createdAtUtc = detail.createdAtUtc,
                formattedDate = formatDateOnly(detail.createdAtUtc, zoneVn),
                formattedTime = formatTimeOnly(detail.createdAtUtc, zoneVn),
                formattedAmountVnd = detail.formattedAmountVnd
            )
        }

        return Result.success(BuyerTransactionsPage(uiItems, nextCursor))
    }

    override suspend fun getTransactionDetail(
        orderId: String
    ): Result<BuyerTransactionDetailUiModel> {
        if (simulateOffline) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }
        if (simulateOrderNotFound) {
            return Result.failure(NoSuchElementException("Không tìm thấy đơn hàng"))
        }

        val uid = activeUid ?: return Result.failure(
            IllegalStateException("Chưa xác thực người dùng")
        )

        val order = ordersByUser[uid]?.firstOrNull { it.orderId == orderId }
            ?: return Result.failure(NoSuchElementException("Không tìm thấy đơn hàng của bạn"))

        return Result.success(order)
    }
}

/**
 * ============================================================================
 * VIEWMODEL QUẢN LÝ LỊCH SỬ GIAO DỊCH BUYER (B05)
 * ============================================================================
 */
class BuyerTransactionsViewModel(
    private val authRepository: AuthRepository,
    private val transactionsRepository: BuyerTransactionsRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(BuyerTransactionsUiState(isRestoringSession = true))
    val uiState: StateFlow<BuyerTransactionsUiState> = _uiState.asStateFlow()

    private val _events = Channel<BuyerTransactionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var initialLoadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var detailJob: Job? = null

    init {
        // Quan sát phiên đăng nhập: tự động dọn sạch dữ liệu khi đổi tài khoản
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { session ->
                handleSessionChanged(session)
            }
        }
    }

    private fun handleSessionChanged(session: AuthSession) {
        when (session) {
            is AuthSession.Restoring -> {
                cancelAllJobs()
                _uiState.update {
                    it.copy(
                        isRestoringSession = true,
                        isGuest = false,
                        activeUid = null,
                        listState = BuyerTransactionsListState.Idle,
                        selectedOrderDetail = null
                    )
                }
            }

            is AuthSession.Guest -> {
                cancelAllJobs()
                _uiState.update {
                    it.copy(
                        isRestoringSession = false,
                        isGuest = true,
                        activeUid = null,
                        listState = BuyerTransactionsListState.Empty,
                        nextCursor = null,
                        hasMore = false,
                        selectedOrderDetail = null
                    )
                }
            }

            is AuthSession.SignedIn -> {
                val previousUid = _uiState.value.activeUid
                if (previousUid != session.uid) {
                    // Đổi sang UID khác: dọn dẹp sạch sẽ dữ liệu cũ để chống rò rỉ thông tin
                    cancelAllJobs()
                    _uiState.update {
                        BuyerTransactionsUiState(
                            isRestoringSession = false,
                            isGuest = false,
                            activeUid = session.uid,
                            listState = BuyerTransactionsListState.InitialLoading,
                            selectedOrderDetail = null
                        )
                    }
                    loadInitialTransactions()
                }
            }
        }
    }

    /**
     * Nạp trang đầu tiên của danh sách giao dịch
     */
    fun loadInitialTransactions() {
        if (_uiState.value.isGuest || _uiState.value.activeUid == null) return

        initialLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    listState = BuyerTransactionsListState.InitialLoading,
                    nextCursor = null,
                    hasMore = false,
                    loadMoreError = null,
                    refreshErrorMessage = null
                )
            }

            val result = transactionsRepository.getMyTransactions(cursor = null, limit = 20)

            result.fold(
                onSuccess = { page ->
                    val hasMore = page.nextCursor != null
                    _uiState.update {
                        it.copy(
                            listState = if (page.items.isEmpty()) {
                                BuyerTransactionsListState.Empty
                            } else {
                                BuyerTransactionsListState.Content(page.items)
                            },
                            nextCursor = page.nextCursor,
                            hasMore = hasMore
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            listState = BuyerTransactionsListState.Error(
                                error.message ?: "Không thể nạp lịch sử giao dịch. Vui lòng thử lại!"
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Tải trang tiếp theo (Load-more với Cursor)
     */
    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.activeUid == null) return

        val cursor = currentState.nextCursor ?: return

        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    isLoadingMore = true,
                    loadMoreError = null
                )
            }

            val result = transactionsRepository.getMyTransactions(cursor = cursor, limit = 20)

            result.fold(
                onSuccess = { page ->
                    val currentItems = (currentState.listState as? BuyerTransactionsListState.Content)?.items ?: emptyList()
                    // Khử trùng lặp ID (Dedup by orderId)
                    val combinedItems = (currentItems + page.items).distinctBy { it.orderId }
                    val hasMore = page.nextCursor != null

                    _uiState.update {
                        it.copy(
                            listState = BuyerTransactionsListState.Content(combinedItems),
                            nextCursor = page.nextCursor,
                            hasMore = hasMore,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            loadMoreError = error.message ?: "Lỗi tải thêm giao dịch. Chạm để thử lại."
                        )
                    }
                }
            )
        }
    }

    /**
     * Làm mới dữ liệu (Pull-to-refresh)
     */
    fun refresh() {
        if (_uiState.value.isGuest || _uiState.value.activeUid == null) return

        viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    refreshErrorMessage = null
                )
            }

            val result = transactionsRepository.getMyTransactions(cursor = null, limit = 20)

            result.fold(
                onSuccess = { page ->
                    val hasMore = page.nextCursor != null
                    _uiState.update {
                        it.copy(
                            listState = if (page.items.isEmpty()) {
                                BuyerTransactionsListState.Empty
                            } else {
                                BuyerTransactionsListState.Content(page.items)
                            },
                            nextCursor = page.nextCursor,
                            hasMore = hasMore,
                            isRefreshing = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            refreshErrorMessage = error.message ?: "Làm mới thất bại"
                        )
                    }
                    _events.send(BuyerTransactionsEvent.ShowSnackbar("Không thể làm mới danh sách"))
                }
            )
        }
    }

    /**
     * Xem chi tiết biên nhận của một đơn hàng
     */
    fun selectOrder(orderId: String) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch(mainDispatcher) {
            _uiState.update {
                it.copy(
                    isLoadingDetail = true,
                    detailError = null
                )
            }

            val result = transactionsRepository.getTransactionDetail(orderId)

            result.fold(
                onSuccess = { detail ->
                    _uiState.update {
                        it.copy(
                            selectedOrderDetail = detail,
                            isLoadingDetail = false
                        )
                    }
                    _events.send(BuyerTransactionsEvent.NavigateToDetail(orderId))
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingDetail = false,
                            detailError = error.message ?: "Không thể tải chi tiết đơn hàng"
                        )
                    }
                    _events.send(BuyerTransactionsEvent.ShowSnackbar(error.message ?: "Lỗi tải chi tiết đơn hàng"))
                }
            )
        }
    }

    fun clearSelectedOrderDetail() {
        _uiState.update { it.copy(selectedOrderDetail = null, detailError = null) }
    }

    private fun cancelAllJobs() {
        initialLoadJob?.cancel()
        loadMoreJob?.cancel()
        detailJob?.cancel()
    }
}

/**
 * ============================================================================
 * CÁC HÀM TIỆN ÍCH ĐỊNH DẠNG (FORMATTERS)
 * ============================================================================
 */
private fun formatVnd(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount) + " đ"
}

private fun formatIsoToVn(isoString: String, zoneId: ZoneId): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(zoneId)
        formatter.format(instant)
    } catch (_: Exception) {
        isoString
    }
}

private fun formatDateOnly(isoString: String, zoneId: ZoneId): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(zoneId)
        formatter.format(instant)
    } catch (_: Exception) {
        isoString
    }
}

private fun formatTimeOnly(isoString: String, zoneId: ZoneId): String {
    return try {
        val instant = Instant.parse(isoString)
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zoneId)
        formatter.format(instant)
    } catch (_: Exception) {
        isoString
    }
}
