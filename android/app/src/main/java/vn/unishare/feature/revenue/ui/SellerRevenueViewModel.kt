package vn.unishare.feature.revenue.ui

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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ============================================================================
 * ĐẶC TẢ HỢP ĐỒNG DỮ LIỆU & BỘ GIẢ LẬP PHỤC VỤ PHÁT TRIỂN ĐỘC LẬP (B06)
 * Issue: [B06] Dựng bảng doanh thu và giao dịch của Seller (#22)
 * ============================================================================
 */

/**
 * DTO dữ liệu tổng hợp doanh thu từ server
 */
data class SellerRevenueSummaryDto(
    val totalRevenueVnd: Long,
    val paidOrderCount: Int,
    val totalDocumentsSold: Int
)

/**
 * DTO một trang danh sách giao dịch bán hàng theo phân trang Cursor
 */
data class SellerSalesPageDto(
    val items: List<SellerTransactionItemUiModel>,
    val nextCursor: String?
)

/**
 * Hợp đồng dữ liệu Doanh thu & Giao dịch của Seller
 * Khi Leader A bàn giao API adapter A07, triển khai chính thức sẽ cài đặt interface này.
 */
interface SellerRevenueRepository {
    suspend fun getRevenueSummary(from: String? = null, to: String? = null): Result<SellerRevenueSummaryDto>
    suspend fun getSales(cursor: String? = null, limit: Int = 20): Result<SellerSalesPageDto>
    suspend fun checkSellerRole(): Result<Boolean>
}

/**
 * ============================================================================
 * BỘ GIẢ LẬP DỮ LIỆU ĐỘC LẬP (FAKE REPOSITORY) PHỤC VỤ TEST & DEV THEO MỤC 2
 * ============================================================================
 *
 * Tính năng hỗ trợ:
 * 1. Phân quyền và bảo mật: Dữ liệu cô lập giữa 2 Seller ([UID_SELLER_ONE], [UID_SELLER_TWO]).
 * 2. Phân loại trạng thái: Hỗ trợ các đơn PAID, PENDING, EXPIRED (chỉ đơn PAID được tính doanh thu).
 * 3. Phân trang: Phân trang con trỏ (Cursor), kiểm tra hết trang và khử trùng lặp.
 * 4. Mô phỏng ngoại lệ: Mất mạng (simulateOffline), không có quyền Seller (isSellerRole).
 */
class FakeSellerRevenueRepository(
    var activeUid: String? = UID_SELLER_ONE,
    var simulateOffline: Boolean = false,
    var isSellerRole: Boolean = true
) : SellerRevenueRepository {

    companion object {
        const val UID_SELLER_ONE = "uid_seller_test_01"
        const val UID_SELLER_TWO = "uid_seller_test_02"
        const val UID_EMPTY_SELLER = "uid_seller_empty"

        const val CURSOR_PAGE_2 = "cursor_seller_p2"
    }

    private data class InternalOrder(
        val orderId: String,
        val sellerId: String,
        val docId: String,
        val documentTitle: String,
        val amountVnd: Long,
        val status: OrderStatus,
        val paymentCode: String,
        val paidAtUtc: String
    )

    private val allOrders = mutableListOf<InternalOrder>()

    init {
        resetFixture()
    }

    fun resetFixture() {
        allOrders.clear()
        val now = Instant.now()
        val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")

        // 1. Dữ liệu cho Seller 1:
        // Đơn 1: PAID (hôm nay)
        allOrders.add(
            InternalOrder(
                orderId = "ord_s1_001",
                sellerId = UID_SELLER_ONE,
                docId = "doc_dsa_01",
                documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật nâng cao",
                amountVnd = 50_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNIS1A01",
                paidAtUtc = now.toString()
            )
        )
        // Đơn 2: PAID (3 ngày trước)
        allOrders.add(
            InternalOrder(
                orderId = "ord_s1_002",
                sellerId = UID_SELLER_ONE,
                docId = "doc_web_02",
                documentTitle = "Tổng hợp đề thi Lập trình Web Fullstack",
                amountVnd = 100_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNIS1A02",
                paidAtUtc = now.minusSeconds(86400 * 3).toString()
            )
        )
        // Đơn 3: PENDING (đang chờ - KHÔNG được tính doanh thu và KHÔNG xuất hiện trên list sales)
        allOrders.add(
            InternalOrder(
                orderId = "ord_s1_003",
                sellerId = UID_SELLER_ONE,
                docId = "doc_os_03",
                documentTitle = "Tóm tắt Hệ điều hành - Chương Tiến trình",
                amountVnd = 30_000L,
                status = OrderStatus.PENDING,
                paymentCode = "UNIS1A03",
                paidAtUtc = now.toString()
            )
        )
        // Đơn 4: PAID (20 ngày trước - trang 2 của Cursor)
        allOrders.add(
            InternalOrder(
                orderId = "ord_s1_004",
                sellerId = UID_SELLER_ONE,
                docId = "doc_db_04",
                documentTitle = "Thiết kế Cơ sở dữ liệu chuẩn hóa 3NF",
                amountVnd = 75_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNIS1A04",
                paidAtUtc = now.minusSeconds(86400 * 20).toString()
            )
        )
        // Đơn 5: EXPIRED (hết hạn - KHÔNG được tính doanh thu)
        allOrders.add(
            InternalOrder(
                orderId = "ord_s1_005",
                sellerId = UID_SELLER_ONE,
                docId = "doc_ai_05",
                documentTitle = "Nhập môn Trí tuệ nhân tạo",
                amountVnd = 45_000L,
                status = OrderStatus.EXPIRED,
                paymentCode = "UNIS1A05",
                paidAtUtc = now.minusSeconds(86400 * 40).toString()
            )
        )

        // 2. Dữ liệu cho Seller 2: Cô lập hoàn toàn với Seller 1
        allOrders.add(
            InternalOrder(
                orderId = "ord_s2_001",
                sellerId = UID_SELLER_TWO,
                docId = "doc_eng_06",
                documentTitle = "Tiếng Anh chuyên ngành Công nghệ thông tin",
                amountVnd = 120_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNIS2B01",
                paidAtUtc = now.toString()
            )
        )
    }

    override suspend fun checkSellerRole(): Result<Boolean> {
        if (simulateOffline) {
            return Result.failure(IOException("Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng."))
        }
        return Result.success(isSellerRole)
    }

    override suspend fun getRevenueSummary(from: String?, to: String?): Result<SellerRevenueSummaryDto> {
        if (simulateOffline) {
            return Result.failure(IOException("Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng."))
        }
        val currentSellerId = activeUid ?: return Result.success(SellerRevenueSummaryDto(0L, 0, 0))

        // Lọc danh sách chỉ thuộc Seller hiện tại, có trạng thái PAID và nằm trong khoảng thời gian [from, to]
        val paidOrders = allOrders.filter { order ->
            order.sellerId == currentSellerId &&
                order.status == OrderStatus.PAID &&
                (from == null || order.paidAtUtc >= from) &&
                (to == null || order.paidAtUtc <= to)
        }

        val totalAmount = paidOrders.sumOf { it.amountVnd }
        val orderCount = paidOrders.size
        val uniqueDocs = paidOrders.map { it.docId }.distinct().size

        return Result.success(
            SellerRevenueSummaryDto(
                totalRevenueVnd = totalAmount,
                paidOrderCount = orderCount,
                totalDocumentsSold = uniqueDocs
            )
        )
    }

    override suspend fun getSales(cursor: String?, limit: Int): Result<SellerSalesPageDto> {
        if (simulateOffline) {
            return Result.failure(IOException("Không thể kết nối đến máy chủ. Vui lòng kiểm tra mạng."))
        }
        val currentSellerId = activeUid ?: return Result.success(SellerSalesPageDto(emptyList(), null))

        // Theo quy định Issue B06: GET /v1/me/sales chỉ trả về các đơn PAID của Seller xác thực
        val paidOrders = allOrders.filter { it.sellerId == currentSellerId && it.status == OrderStatus.PAID }

        val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val uiItems = paidOrders.map { order ->
            val instant = runCatching { Instant.parse(order.paidAtUtc) }.getOrDefault(Instant.now())
            val zonedDateTime = instant.atZone(zoneVn)

            SellerTransactionItemUiModel(
                orderId = order.orderId,
                docId = order.docId,
                documentTitle = order.documentTitle,
                amountVnd = order.amountVnd,
                status = order.status,
                paymentCode = order.paymentCode,
                paidAtUtc = order.paidAtUtc,
                formattedDate = zonedDateTime.format(dateFormatter),
                formattedTime = zonedDateTime.format(timeFormatter),
                formattedAmountVnd = "+${formatVnd(order.amountVnd)}"
            )
        }

        // Mô phỏng phân trang 2 trang cho Seller 1
        return if (currentSellerId == UID_SELLER_ONE) {
            if (cursor == null) {
                // Trang 1: 2 đơn đầu tiên, có cursor trang 2
                Result.success(SellerSalesPageDto(items = uiItems.take(2), nextCursor = CURSOR_PAGE_2))
            } else if (cursor == CURSOR_PAGE_2) {
                // Trang 2: Đơn còn lại, hết cursor
                Result.success(SellerSalesPageDto(items = uiItems.drop(2), nextCursor = null))
            } else {
                Result.success(SellerSalesPageDto(items = emptyList(), null))
            }
        } else {
            Result.success(SellerSalesPageDto(items = uiItems, nextCursor = null))
        }
    }

    private fun formatVnd(amount: Long): String {
        val localeVn = Locale.forLanguageTag("vi-VN")
        return NumberFormat.getNumberInstance(localeVn).format(amount) + " đ"
    }
}

/**
 * ============================================================================
 * VIEWMODEL ĐIỀU PHỐI BẢNG DOANH THU & GIAO DỊCH SELLER (B06)
 * ============================================================================
 */
class SellerRevenueViewModel(
    private val authRepository: AuthRepository,
    private val revenueRepository: SellerRevenueRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(SellerRevenueUiState())
    val uiState: StateFlow<SellerRevenueUiState> = _uiState.asStateFlow()

    private var activeLoadJob: Job? = null
    private var loadSequence: Long = 0L

    init {
        // 1. Lắng nghe phiên đăng nhập từ AuthRepository
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { session ->
                handleSessionChange(session)
            }
        }
    }

    private fun handleSessionChange(session: AuthSession) {
        when (session) {
            is AuthSession.Restoring -> {
                _uiState.update { it.copy(isRestoringSession = true, isGuest = false) }
            }

            is AuthSession.Guest -> {
                activeLoadJob?.cancel()
                _uiState.update {
                    it.copy(
                        isRestoringSession = false,
                        isGuest = true,
                        activeUid = null,
                        listState = SellerRevenueListState.Idle
                    )
                }
            }

            is AuthSession.SignedIn -> {
                val previousUid = _uiState.value.activeUid
                if (previousUid != session.uid) {
                    // Xóa sạch toàn bộ số liệu của Seller cũ lập tức để không bị rò rỉ dữ liệu chéo
                    _uiState.update {
                        SellerRevenueUiState(
                            isRestoringSession = false,
                            isGuest = false,
                            activeUid = session.uid
                        )
                    }
                }
                loadDashboardData()
            }
        }
    }

    /**
     * Tải toàn bộ dữ liệu Dashboard (KPI + Trang đầu danh sách đơn bán)
     */
    fun loadDashboardData(isRefresh: Boolean = false) {
        activeLoadJob?.cancel()
        val currentSeq = ++loadSequence

        activeLoadJob = viewModelScope.launch(mainDispatcher) {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true, generalError = null) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingKpi = true,
                        listState = SellerRevenueListState.InitialLoading,
                        generalError = null
                    )
                }
            }

            // 1. Kiểm tra quyền Seller
            val roleCheck = revenueRepository.checkSellerRole()
            if (currentSeq != loadSequence) return@launch

            val isSeller = roleCheck.getOrDefault(true)
            if (!isSeller) {
                _uiState.update {
                    it.copy(
                        isSellerRole = false,
                        isLoadingKpi = false,
                        isRefreshing = false,
                        listState = SellerRevenueListState.Error("Tài khoản chưa được kích hoạt quyền Seller.")
                    )
                }
                return@launch
            }

            // 2. Tải tổng hợp doanh thu (KPI) theo khoảng ngày được chọn
            val (fromUtc, toUtc) = calculateDateRangeUtc(_uiState.value.selectedFilter)
            val revenueResult = revenueRepository.getRevenueSummary(from = fromUtc, to = toUtc)
            if (currentSeq != loadSequence) return@launch

            revenueResult.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isLoadingKpi = false,
                        kpiError = null,
                        kpiState = SellerRevenueKpiUiModel(
                            totalRevenueVnd = summary.totalRevenueVnd,
                            formattedTotalRevenue = formatVnd(summary.totalRevenueVnd),
                            paidOrderCount = summary.paidOrderCount,
                            totalDocumentsSold = summary.totalDocumentsSold,
                            periodLabel = _uiState.value.selectedFilter.label
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingKpi = false,
                        kpiError = err.message ?: "Không thể tải số liệu doanh thu."
                    )
                }
            }

            // 3. Tải danh sách đơn bán (Trang 1)
            val salesResult = revenueRepository.getSales(cursor = null)
            if (currentSeq != loadSequence) return@launch

            _uiState.update { it.copy(isRefreshing = false) }

            salesResult.onSuccess { page ->
                if (page.items.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            listState = SellerRevenueListState.Empty,
                            nextCursor = null,
                            hasMore = false
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            listState = SellerRevenueListState.Content(page.items),
                            nextCursor = page.nextCursor,
                            hasMore = page.nextCursor != null
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        listState = SellerRevenueListState.Error(
                            err.message ?: "Không thể tải danh sách giao dịch."
                        )
                    )
                }
            }
        }
    }

    /**
     * Chuyển đổi bộ lọc thời gian theo quy ước ngày Việt Nam
     */
    fun selectFilter(filter: RevenueDateRangeFilter) {
        if (_uiState.value.selectedFilter == filter) return
        _uiState.update { it.copy(selectedFilter = filter) }
        loadDashboardData()
    }

    /**
     * Tải thêm trang giao dịch kế tiếp (Cursor pagination)
     */
    fun loadMore() {
        val currentState = _uiState.value
        val cursor = currentState.nextCursor
        if (!currentState.hasMore || cursor.isNullOrBlank() || currentState.isLoadingMore) {
            return
        }

        val currentItems = (currentState.listState as? SellerRevenueListState.Content)?.items ?: return

        viewModelScope.launch(mainDispatcher) {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }

            revenueRepository.getSales(cursor = cursor).onSuccess { page ->
                // Khử trùng lặp theo orderId để đảm bảo tính đúng đắn khi mạng chập chờn
                val combined = (currentItems + page.items).distinctBy { it.orderId }

                _uiState.update {
                    it.copy(
                        listState = SellerRevenueListState.Content(combined),
                        nextCursor = page.nextCursor,
                        hasMore = page.nextCursor != null,
                        isLoadingMore = false,
                        loadMoreError = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        loadMoreError = err.message ?: "Lỗi tải thêm giao dịch. Vui lòng thử lại."
                    )
                }
            }
        }
    }

    fun retryLoadMore() {
        loadMore()
    }

    fun refresh() {
        loadDashboardData(isRefresh = true)
    }

    private fun calculateDateRangeUtc(filter: RevenueDateRangeFilter): Pair<String?, String?> {
        val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")
        val now = Instant.now()
        val todayVn = now.atZone(zoneVn).toLocalDate()

        return when (filter) {
            RevenueDateRangeFilter.ALL_TIME -> null to null
            RevenueDateRangeFilter.TODAY -> {
                val startOfDay = todayVn.atStartOfDay(zoneVn).toInstant().toString()
                startOfDay to now.toString()
            }
            RevenueDateRangeFilter.LAST_7_DAYS -> {
                val sevenDaysAgo = now.minusSeconds(86400 * 7).toString()
                sevenDaysAgo to now.toString()
            }
            RevenueDateRangeFilter.LAST_30_DAYS -> {
                val thirtyDaysAgo = now.minusSeconds(86400 * 30).toString()
                thirtyDaysAgo to now.toString()
            }
            RevenueDateRangeFilter.THIS_MONTH -> {
                val firstDayOfMonth = todayVn.withDayOfMonth(1).atStartOfDay(zoneVn).toInstant().toString()
                firstDayOfMonth to now.toString()
            }
        }
    }

    private fun formatVnd(amount: Long): String {
        val localeVn = Locale.forLanguageTag("vi-VN")
        return NumberFormat.getNumberInstance(localeVn).format(amount) + " đ"
    }
}

