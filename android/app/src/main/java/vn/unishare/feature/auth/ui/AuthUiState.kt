package vn.unishare.feature.auth.ui

import vn.unishare.domain.repository.AuthSession

/**
 * Trạng thái bất biến (Immutable UI State) của màn hình Auth (C02).
 * Bao gồm chế độ hiển thị form, dữ liệu email, lỗi từng trường, trạng thái tải,
 * phiên hiện tại và mục tiêu quay lại (returnTarget).
 *
 * Lưu ý bảo mật (C02 Mục 4): KHÔNG lưu mật khẩu vào SavedStateHandle hay xuất thông tin
 * nhạy cảm/credential ra log.
 */
data class AuthUiState(
    val formMode: AuthFormMode = AuthFormMode.LOGIN,
    val email: String = "",
    val isLoading: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val generalError: String? = null,
    val resetEmailSentMessage: String? = null,
    val isGoogleAvailable: Boolean = true,
    val session: AuthSession = AuthSession.Restoring,
    val returnTarget: String? = null, // Ví dụ: "checkout:doc_123" hoặc "detail:doc_123"
    val isReturnTargetConsumed: Boolean = false
)

/**
 * Ba chế độ của biểu mẫu xác thực
 */
enum class AuthFormMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

/**
 * Sự kiện dùng một lần (One-shot events) phát từ AuthViewModel ra tầng điều hướng / UI
 */
sealed interface AuthEvent {
    /**
     * Đăng nhập/đăng ký thành công -> điều hướng về đúng returnTarget (docId / checkout) đúng 1 lần
     */
    data class NavigateToTarget(val targetRoute: String?) : AuthEvent

    /**
     * Người dùng bấm Hủy / Back khi đang ở luồng Guest -> quay lại màn trước, không mở checkout
     */
    data object AuthCanceled : AuthEvent

    /**
     * Thông báo trạng thái ngắn (ví dụ: đã gửi email đặt lại mật khẩu)
     */
    data class ShowMessage(val message: String) : AuthEvent
}

