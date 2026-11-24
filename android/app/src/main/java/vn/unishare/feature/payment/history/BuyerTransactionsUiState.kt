package vn.unishare.feature.payment.history

import vn.unishare.feature.payment.ui.OrderStatus

/**
 * ============================================================================
 * ĐẶC TẢ TRẠNG THÁI GIAO DIỆN LỊCH SỬ GIAO DỊCH & BIÊN NHẬN BUYER (B05)
 * Issue: [B05] Dựng lịch sử mua và chi tiết biên nhận Buyer (#20)
 * ============================================================================
 *
 * Nguyên tắc thiết kế (Senior Developer & Architecture Principles):
 * 1. Unidirectional Data Flow (UDF): UI chỉ đọc [BuyerTransactionsUiState] và kích hoạt
 *    sự kiện, không tự ý thay đổi dữ liệu bên trong state.
 * 2. Phân tách rõ ràng các trạng thái tải: Tải lần đầu (InitialLoading - Skeleton),
 *    Tải thêm (LoadingMore - Footer Indicator), Rỗng (Empty) và Lỗi (Error).
 * 3. Bảo mật dữ liệu người dùng (Privacy & Security):
 *    - Tuyệt đối không lưu giữ Token, Private Key hay thông tin tài khoản không cần thiết trong state.
 *    - Dữ liệu lịch sử gắn chặt với `activeUid`, tự động dọn sạch khi đổi tài khoản.
 * 4. Biên nhận hiển thị snapshot: Phản ánh dữ liệu server tại thời điểm tạo đơn,
 *    không tự ý diễn giải thành hóa đơn thuế pháp lý hay payout.
 */

/**
 * 1. Mô hình hiển thị của từng mục giao dịch trong danh sách lịch sử (Order Item UI Model)
 */
data class BuyerTransactionItemUiModel(
    val orderId: String,
    val docId: String,
    val documentTitle: String,
    val amountVnd: Long,
    val status: OrderStatus,
    val paymentCode: String,
    val createdAtUtc: String,
    val formattedDate: String = "",
    val formattedTime: String = "",
    val formattedAmountVnd: String = ""
)

/**
 * 2. Mô hình hiển thị chi tiết biên nhận trong ứng dụng (In-app Receipt Detail UI Model)
 */
data class BuyerTransactionDetailUiModel(
    val orderId: String,
    val docId: String,
    val documentTitle: String,
    val amountVnd: Long,
    val status: OrderStatus,
    val paymentCode: String,
    val createdAtUtc: String,
    val expiresAtUtc: String,
    val formattedCreatedAt: String = "",
    val formattedExpiresAt: String = "",
    val formattedAmountVnd: String = "",
    val bankName: String? = null,
    val bankCode: String? = null,
    val accountNumber: String? = null,
    val accountHolder: String? = null
)

/**
 * 3. Trạng thái danh sách giao dịch phân tách rõ ràng (Sealed Interface)
 */
sealed interface BuyerTransactionsListState {
    data object Idle : BuyerTransactionsListState
    data object InitialLoading : BuyerTransactionsListState
    data object Empty : BuyerTransactionsListState
    data class Content(val items: List<BuyerTransactionItemUiModel>) : BuyerTransactionsListState
    data class Error(val message: String) : BuyerTransactionsListState
}

/**
 * 4. Trạng thái giao diện tổng thể bất biến của màn hình Lịch sử giao dịch (Immutable UI State)
 */
data class BuyerTransactionsUiState(
    /** Cờ báo hiệu đang khôi phục phiên đăng nhập */
    val isRestoringSession: Boolean = false,

    /** Cờ báo hiệu người dùng là khách vãng lai (Guest) chưa đăng nhập */
    val isGuest: Boolean = false,

    /** UID của Buyer đang sở hữu danh sách giao dịch hiển thị */
    val activeUid: String? = null,

    /** Trạng thái chính của danh sách (Loading / Content / Empty / Error) */
    val listState: BuyerTransactionsListState = BuyerTransactionsListState.Idle,

    /** Con trỏ phân trang dạng Opaque Cursor nhận từ Server để tải trang kế tiếp */
    val nextCursor: String? = null,

    /** Cờ báo hiệu còn trang tiếp theo để tải hay không */
    val hasMore: Boolean = false,

    /** Cờ báo hiệu đang thực hiện tải thêm trang tiếp theo (Load-more) */
    val isLoadingMore: Boolean = false,

    /** Lỗi tải thêm trang tiếp theo (nếu có, hiển thị nút thử lại ở chân trang mà không mất dữ liệu cũ) */
    val loadMoreError: String? = null,

    /** Cờ báo hiệu đang thực hiện làm mới dữ liệu (Pull-to-refresh) */
    val isRefreshing: Boolean = false,

    /** Thông báo lỗi làm mới dữ liệu */
    val refreshErrorMessage: String? = null,

    /** Chi tiết biên nhận của đơn hàng được chọn để xem chi tiết */
    val selectedOrderDetail: BuyerTransactionDetailUiModel? = null,

    /** Cờ báo hiệu đang tải thông tin chi tiết đơn hàng */
    val isLoadingDetail: Boolean = false,

    /** Thông báo lỗi khi tải chi tiết đơn hàng */
    val detailError: String? = null
)

/**
 * 5. Các sự kiện dùng một lần (One-shot Navigation & Action Events)
 */
sealed interface BuyerTransactionsEvent {
    data class NavigateToDetail(val orderId: String) : BuyerTransactionsEvent
    data object NavigateToAuth : BuyerTransactionsEvent
    data class ShowSnackbar(val message: String) : BuyerTransactionsEvent
}
