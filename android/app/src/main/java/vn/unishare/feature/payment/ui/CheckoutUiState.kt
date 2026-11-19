package vn.unishare.feature.payment.ui

/**
 * ============================================================================
 * ĐẶC TẢ TRẠNG THÁI GIAO DIỆN VÀ MÔ HÌNH DỮ LIỆU CHECKOUT (B04)
 * Issue: [B04] Dựng giao diện Checkout và theo dõi trạng thái đơn (#10)
 * ============================================================================
 *
 * Tệp này định nghĩa các mô hình dữ liệu bất biến (Immutable Data Models) phục vụ
 * cho màn hình thanh toán tài liệu (Checkout).
 *
 * Nguyên tắc thiết kế (Senior Developer & Architecture Principles):
 * 1. Unidirectional Data Flow (UDF): UI chỉ đọc [CheckoutUiState] và kích hoạt
 *    sự kiện, không tự ý thay đổi dữ liệu bên trong state.
 * 2. Phân tách lỗi mạng và trạng thái đơn: [networkError] độc lập hoàn toàn với
 *    [status]. Khi mất mạng, [status] vẫn giữ nguyên [OrderStatus.PENDING] an toàn.
 * 3. Bảo vệ bất biến thanh toán (Payment Invariants): Trạng thái [OrderStatus.PAID]
 *    chỉ được cập nhật từ Server, client không có quyền tự kích hoạt PAID.
 */

/**
 * 1. Vòng đời trạng thái của đơn hàng thanh toán
 * Khớp 100% với đặc tả Backend B02 (createOrder.ts) và B03 (matchPayment.ts).
 */
enum class OrderStatus {
    /** Đang chờ thanh toán và còn trong thời hạn 15 phút để nhận tiền. */
    PENDING,

    /** Đã thanh toán thành công (được Server xác nhận qua Webhook SePay). */
    PAID,

    /** Đã hết hạn nhận tiền (quá 15 phút tính từ lúc tạo đơn). */
    EXPIRED,

    /** Đang trong quá trình đối soát thủ công (chuyển sai số tiền, sai nội dung hoặc sau khi hết hạn). */
    REVIEW_REQUIRED,

    /** Lỗi nghiệp vụ không thể hoàn tất (ví dụ: tự mua tài liệu của mình, đã sở hữu từ trước). */
    FAILED
}

/**
 * 2. Thông tin chỉ dẫn chuyển khoản ngân hàng (Transfer Instructions)
 * Dùng để render thông tin cho Buyer thực hiện quét mã QR hoặc chuyển khoản.
 */
data class BankTransferInfo(
    val bankName: String = "",
    val bankCode: String = "",
    val accountNumber: String = "",
    val accountHolder: String = "",
    val amountVnd: Long = 0L,
    val paymentCode: String = "" // Ví dụ: "UNI8A3F12"
)

/**
 * 3. Mô hình dữ liệu đơn hàng hiển thị trên UI (Order UI Model)
 * Chụp dữ liệu từ OrderDTO của server gửi về.
 */
data class OrderUiModel(
    val orderId: String,
    val docId: String,
    val documentTitle: String,
    val amountVnd: Long,
    val status: OrderStatus,
    val paymentCode: String,
    val createdAtUtc: String,
    val expiresAtUtc: String,
    val transferInfo: BankTransferInfo? = null
)

/**
 * 4. Trạng thái giao diện tổng thể bất biến của màn hình Checkout (Immutable UI State)
 */
data class CheckoutUiState(
    /** Cờ báo hiệu đang tải dữ liệu đơn hàng lần đầu (hiển thị Shimmer / CircularProgress) */
    val isLoading: Boolean = true,

    /** Thông tin chi tiết của đơn hàng sau khi tạo mới hoặc khôi phục thành công */
    val order: OrderUiModel? = null,

    /** Trạng thái hiện tại của đơn hàng (Mặc định là PENDING khi mới tạo) */
    val status: OrderStatus = OrderStatus.PENDING,

    /** Số giây còn lại của hạn 15 phút (đếm ngược từ expiresAtUtc) */
    val remainingSeconds: Long = 0L,

    /** Cờ báo hiệu ViewModel đang trong chu kỳ polling gửi request kiểm tra trạng thái đơn */
    val isPolling: Boolean = false,

    /**
     * Thông báo lỗi kết nối mạng (Offline / Timeout).
     * LƯU Ý BẢO MẬT & KIẾN TRÚC:
     * Lỗi mạng được tách biệt khỏi [status]. Khi mất mạng, cờ này được gán thông báo
     * nhưng [status] vẫn là [OrderStatus.PENDING], cho phép người dùng bấm "Thử lại".
     */
    val networkError: String? = null,

    /** Lỗi nghiệp vụ không thể xử lý tiếp (ví dụ: 404 không tìm thấy tài liệu, 409 đã sở hữu) */
    val businessError: String? = null,

    /** Thông báo ngắn khi sao chép thành công một trường (STK, Số tiền, Mã nội dung) */
    val copiedFieldMessage: String? = null
)

/**
 * 5. Cổng giao tiếp dữ liệu đơn hàng (Order Repository Port)
 * Tuân thủ kiến trúc Ports & Adapters: ViewModel chỉ phụ thuộc vào Interface này,
 * không gọi trực tiếp Firebase Firestore hay HTTP Client.
 */
interface OrderRepository {
    /**
     * Tạo đơn hàng mới hoặc tái sử dụng đơn PENDING còn hạn cho tài liệu docId
     * Tương ứng endpoint: POST /v1/orders
     */
    suspend fun createOrGetOrder(
        docId: String,
        idempotencyKey: String
    ): Result<OrderUiModel>

    /**
     * Lấy thông tin và trạng thái cập nhật mới nhất của đơn hàng
     * Tương ứng endpoint: GET /v1/orders/{orderId}
     */
    suspend fun getOrderStatus(
        orderId: String
    ): Result<OrderUiModel>
}

/**
 * 6. Sự kiện dùng một lần (One-shot Events) phát từ ViewModel ra UI
 * Sử dụng Sealed Interface để đảm bảo tính đầy đủ (Exhaustiveness) khi xử lý bằng when.
 */
sealed interface CheckoutEvent {
    /**
     * Thanh toán thành công (PAID) -> Điều hướng sang màn hình đọc PDF (A06)
     */
    data class NavigateToReader(val docId: String) : CheckoutEvent

    /**
     * Phiên đăng nhập hết hạn (401) hoặc chưa đăng nhập -> Điều hướng sang AuthScreen kèm returnTarget
     */
    data class NavigateToAuth(val returnTarget: String) : CheckoutEvent

    /**
     * Người dùng bấm nút Back quay lại màn hình chi tiết tài liệu
     */
    data object NavigateBack : CheckoutEvent

    /**
     * Hiển thị thông báo Snackbar trên màn hình (ví dụ: "Đã sao chép mã thanh toán")
     */
    data class ShowSnackbar(val message: String) : CheckoutEvent
}
