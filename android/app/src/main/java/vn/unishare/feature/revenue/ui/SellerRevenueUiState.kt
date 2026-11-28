package vn.unishare.feature.revenue.ui

import vn.unishare.feature.payment.ui.OrderStatus

/** Route điều hướng chuẩn đã thống nhất với Leader cho màn hình Doanh thu Seller. */
const val SELLER_REVENUE_ROUTE = "seller/revenue"


/**
 * Bộ lọc khoảng thời gian theo quy ước ngày Việt Nam (UTC+7)
 */
enum class RevenueDateRangeFilter(val label: String) {
    ALL_TIME("Toàn thời gian"),
    THIS_MONTH("Tháng này"),
    LAST_30_DAYS("30 ngày qua"),
    LAST_7_DAYS("7 ngày qua"),
    TODAY("Hôm nay")
}

/**
 * Mô hình dữ liệu hiển thị thẻ chỉ số hiệu suất doanh thu (KPI Card Model)
 */
data class SellerRevenueKpiUiModel(
    val totalRevenueVnd: Long = 0L,
    val formattedTotalRevenue: String = "0 đ",
    val paidOrderCount: Int = 0,
    val totalDocumentsSold: Int = 0,
    val periodLabel: String = "Toàn thời gian",
    val revenueNotice: String = "Số liệu dựa trên các đơn đã thanh toán thành công (PAID). Đây là doanh thu ghi nhận của tài khoản, không phải số dư khả dụng để rút."
)

/**
 * Mô hình dữ liệu hiển thị từng giao dịch bán trong danh sách (Seller Transaction Row Model)
 * Lưu ý: Hoàn toàn không chứa thông tin định danh hay thông tin ngân hàng của Buyer.
 */
data class SellerTransactionItemUiModel(
    val orderId: String,
    val docId: String,
    val documentTitle: String,
    val amountVnd: Long,
    val status: OrderStatus = OrderStatus.PAID,
    val paymentCode: String,
    val paidAtUtc: String,
    val formattedDate: String = "",
    val formattedTime: String = "",
    val formattedAmountVnd: String = ""
)

/**
 * Phân nhánh trạng thái của danh sách giao dịch bán hàng (Sealed Interface)
 */
sealed interface SellerRevenueListState {
    data object Idle : SellerRevenueListState
    data object InitialLoading : SellerRevenueListState
    data object Empty : SellerRevenueListState
    data class Content(val items: List<SellerTransactionItemUiModel>) : SellerRevenueListState
    data class Error(val message: String) : SellerRevenueListState
}

/**
 * Trạng thái giao diện tổng thể bất biến của màn hình Doanh thu Seller (Immutable UI State)
 */
data class SellerRevenueUiState(
    /** Cờ báo hiệu đang khôi phục phiên xác thực */
    val isRestoringSession: Boolean = false,

    /** Cờ báo hiệu người dùng là khách vãng lai (Guest) chưa đăng nhập */
    val isGuest: Boolean = false,

    /** Cờ báo hiệu tài khoản hiện tại có quyền Seller hay không */
    val isSellerRole: Boolean = true,

    /** UID của Seller sở hữu số liệu doanh thu */
    val activeUid: String? = null,

    /** Khoảng thời gian đang được chọn để lọc */
    val selectedFilter: RevenueDateRangeFilter = RevenueDateRangeFilter.ALL_TIME,

    /** Trạng thái tải thẻ chỉ số KPI */
    val isLoadingKpi: Boolean = false,

    /** Dữ liệu hiển thị của thẻ KPI */
    val kpiState: SellerRevenueKpiUiModel = SellerRevenueKpiUiModel(),

    /** Thông báo lỗi khi tải thẻ KPI (nếu có) */
    val kpiError: String? = null,

    /** Trạng thái hiển thị của danh sách giao dịch */
    val listState: SellerRevenueListState = SellerRevenueListState.Idle,

    /** Con trỏ nhận từ server để tải trang giao dịch kế tiếp */
    val nextCursor: String? = null,

    /** Cờ báo hiệu còn trang tiếp theo để tải hay không */
    val hasMore: Boolean = false,

    /** Cờ báo hiệu đang tải thêm trang kế tiếp (Load-more) */
    val isLoadingMore: Boolean = false,

    /** Lỗi khi tải thêm trang tiếp theo */
    val loadMoreError: String? = null,

    /** Cờ báo hiệu đang làm mới dữ liệu (Pull-to-refresh) */
    val isRefreshing: Boolean = false,

    /** Thông báo lỗi chung của màn hình */
    val generalError: String? = null
)

