package vn.unishare.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import java.io.IOException

/**
 * Khóa lưu đường dẫn quay lại (ví dụ: "checkout:doc_123" hoặc "doc_123") trong SavedStateHandle.
 * Không lưu mật khẩu hay token trong SavedStateHandle theo quy định bảo mật C02.
 */
const val RETURN_TARGET_KEY = "return_target"

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    // Trạng thái phiên đăng nhập lấy trực tiếp từ AuthRepository của A02
    val authSession: StateFlow<AuthSession> = authRepository.session

    private val _uiState = MutableStateFlow(
        AuthUiState(
            session = authRepository.session.value,
            returnTarget = savedStateHandle.get<String>(RETURN_TARGET_KEY)
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // Kênh phát sự kiện dùng một lần (One-shot events)
    private val _eventChannel = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _eventChannel.receiveAsFlow()

    init {
        // Quan sát phiên đăng nhập từ A02: khi chuyển sang SignedIn thì tiêu thụ returnTarget đúng 1 lần
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { currentSession ->
                _uiState.update { it.copy(session = currentSession) }
                if (currentSession is AuthSession.SignedIn && !_uiState.value.isReturnTargetConsumed) {
                    val target = _uiState.value.returnTarget
                    _uiState.update { it.copy(isReturnTargetConsumed = true) }
                    savedStateHandle[RETURN_TARGET_KEY] = null
                    _eventChannel.send(AuthEvent.NavigateToTarget(target))
                }
            }
        }
    }

    fun onEmailChange(newEmail: String) {
        _uiState.update {
            it.copy(
                email = newEmail,
                emailError = null,
                generalError = null
            )
        }
    }

    fun switchFormMode(mode: AuthFormMode) {
        _uiState.update {
            it.copy(
                formMode = mode,
                emailError = null,
                passwordError = null,
                confirmPasswordError = null,
                generalError = null,
                resetEmailSentMessage = null
            )
        }
    }

    fun submit(
        emailInput: String = _uiState.value.email,
        passwordInput: String = "",
        confirmPasswordInput: String? = null
    ) {
        // Chặn gửi trùng khi request trước đang xử lý
        if (_uiState.value.isLoading) return

        val trimmedEmail = emailInput.trim()
        _uiState.update {
            it.copy(
                email = trimmedEmail,
                confirmPasswordError = null,
                resetEmailSentMessage = null
            )
        }

        val mode = _uiState.value.formMode
        val isEmailValid = validateEmail(trimmedEmail)
        val isPasswordValid = if (mode == AuthFormMode.FORGOT_PASSWORD) {
            true
        } else {
            validatePassword(passwordInput)
        }
        val isConfirmValid = if (mode == AuthFormMode.REGISTER && confirmPasswordInput != null) {
            validateConfirmPassword(passwordInput, confirmPasswordInput)
        } else {
            true
        }

        // Nếu dữ liệu không hợp lệ thì dừng ngay, không gọi xuống Repository
        if (!isEmailValid || !isPasswordValid || !isConfirmValid) return

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch(mainDispatcher) {
            val result = when (mode) {
                AuthFormMode.LOGIN -> authRepository.signIn(trimmedEmail, passwordInput)
                AuthFormMode.REGISTER -> authRepository.signUp(trimmedEmail, passwordInput)
                AuthFormMode.FORGOT_PASSWORD -> authRepository.sendPasswordReset(trimmedEmail)
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, generalError = null) }
                    if (mode == AuthFormMode.FORGOT_PASSWORD) {
                        val msg = "Email đặt lại mật khẩu đã được gửi."
                        _uiState.update {
                            it.copy(
                                formMode = AuthFormMode.LOGIN,
                                resetEmailSentMessage = msg
                            )
                        }
                        _eventChannel.send(AuthEvent.ShowMessage(msg))
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = mapErrorMessage(error)
                        )
                    }
                }
            )
        }
    }

    /**
     * Xử lý kết quả hoặc hủy khi bấm Đăng nhập với Google (adapter thật do A02 cung cấp)
     */
    fun onGoogleSignInResult(result: Result<Unit>?) {
        if (_uiState.value.isLoading) return
        if (result == null) {
            // Người dùng hủy hộp thoại chọn tài khoản Google -> giữ nguyên trạng thái, không crash
            _uiState.update { it.copy(isLoading = false, generalError = null) }
            return
        }
        result.fold(
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, generalError = null) }
            },
            onFailure = { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generalError = mapErrorMessage(error)
                    )
                }
            }
        )
    }

    fun cancelAuth() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch(mainDispatcher) {
            _eventChannel.send(AuthEvent.AuthCanceled)
        }
    }

    private fun validateEmail(email: String): Boolean {
        return if (email.isBlank() || !emailRegex.matches(email)) {
            _uiState.update { it.copy(emailError = "Email không hợp lệ") }
            false
        } else {
            _uiState.update { it.copy(emailError = null) }
            true
        }
    }

    private fun validatePassword(password: String): Boolean {
        return if (password.length < 6) {
            _uiState.update { it.copy(passwordError = "Mật khẩu phải từ 6 ký tự trở lên") }
            false
        } else {
            _uiState.update { it.copy(passwordError = null) }
            true
        }
    }

    private fun validateConfirmPassword(password: String, confirmPassword: String): Boolean {
        return if (confirmPassword.isBlank() || confirmPassword != password) {
            _uiState.update { it.copy(confirmPasswordError = "Mật khẩu xác nhận không khớp") }
            false
        } else {
            _uiState.update { it.copy(confirmPasswordError = null) }
            true
        }
    }

    private fun mapErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is IOException || raw.contains("network", ignoreCase = true) || raw.contains("offline", ignoreCase = true) ->
                "Mất kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            raw.contains("401") || raw.contains("session", ignoreCase = true) ->
                "Phiên đăng nhập đã hết hạn (401). Vui lòng đăng nhập lại."
            raw.contains("password", ignoreCase = true) || raw.contains("credential", ignoreCase = true) ->
                "Email hoặc mật khẩu không chính xác."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi. Vui lòng thử lại."
        }
    }
}

