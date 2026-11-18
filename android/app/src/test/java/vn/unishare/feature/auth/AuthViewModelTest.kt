package vn.unishare.feature.auth

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.auth.ui.AuthEvent
import vn.unishare.feature.auth.ui.AuthFormMode
import vn.unishare.feature.auth.ui.AuthViewModel
import vn.unishare.feature.auth.ui.RETURN_TARGET_KEY
import java.io.IOException

/**
 * Fake AuthRepository có kiểm soát phục vụ Unit Test cho C02 (U02/U04).
 */
private class ControllableFakeAuthRepository(
    initialSession: AuthSession = AuthSession.Guest
) : AuthRepository {

    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = _session

    var signInCallCount = 0
    var signUpCallCount = 0
    var resetCallCount = 0

    var nextResult: Result<Unit> = Result.success(Unit)
    var suspendGate: CompletableDeferred<Unit>? = null

    fun emitSession(newSession: AuthSession) {
        _session.value = newSession
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        signInCallCount++
        suspendGate?.await()
        if (nextResult.isSuccess) {
            _session.value = AuthSession.SignedIn("uid-test-01")
        }
        return nextResult
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        signUpCallCount++
        suspendGate?.await()
        if (nextResult.isSuccess) {
            _session.value = AuthSession.SignedIn("uid-new-02")
        }
        return nextResult
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        resetCallCount++
        suspendGate?.await()
        return nextResult
    }

    override suspend fun signOut(): Result<Unit> {
        _session.value = AuthSession.Guest
        return Result.success(Unit)
    }
}

class AuthViewModelTest {

    @Test
    fun invalidEmailOrShortPassword_doesNotCallRepository() = runBlocking {
        val fakeRepo = ControllableFakeAuthRepository()
        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // 1. Email sai định dạng
        viewModel.submit(emailInput = "email-khong-hop-le", passwordInput = "123456")
        assertEquals(0, fakeRepo.signInCallCount)
        assertNotNull(viewModel.uiState.value.emailError)

        // 2. Mật khẩu ngắn hơn 6 ký tự
        viewModel.submit(emailInput = "khang@ut.edu.vn", passwordInput = "123")
        assertEquals(0, fakeRepo.signInCallCount)
        assertNull(viewModel.uiState.value.emailError)
        assertNotNull(viewModel.uiState.value.passwordError)

        // 3. Đăng ký với mật khẩu xác nhận không khớp -> không gọi signUp
        viewModel.switchFormMode(AuthFormMode.REGISTER)
        viewModel.submit(
            emailInput = "khang@ut.edu.vn",
            passwordInput = "123456",
            confirmPasswordInput = "654321"
        )
        assertEquals(0, fakeRepo.signUpCallCount)
        assertNotNull(viewModel.uiState.value.confirmPasswordError)
    }

    @Test
    fun loginSuccess_updatesSessionAndConsumesReturnTargetOnce() = runBlocking {
        val fakeRepo = ControllableFakeAuthRepository()
        val handle = SavedStateHandle(mapOf(RETURN_TARGET_KEY to "checkout:doc_101"))
        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = handle,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.submit(emailInput = "khang@ut.edu.vn", passwordInput = "matkhau123")

        assertEquals(1, fakeRepo.signInCallCount)
        assertEquals(AuthSession.SignedIn("uid-test-01"), viewModel.uiState.value.session)
        assertTrue(viewModel.uiState.value.isReturnTargetConsumed)
        assertNull(handle.get<String>(RETURN_TARGET_KEY))

        val firstEvent = viewModel.events.first()
        assertEquals(AuthEvent.NavigateToTarget("checkout:doc_101"), firstEvent)

        // Mô phỏng phát lại SignedIn (như khi xoay màn hình) -> không phát trùng NavigateToTarget
        fakeRepo.emitSession(AuthSession.SignedIn("uid-test-01"))
        assertTrue(viewModel.uiState.value.isReturnTargetConsumed)
    }

    @Test
    fun passwordReset_sendsEmailAndSwitchesBackToLogin() = runBlocking {
        val fakeRepo = ControllableFakeAuthRepository()
        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.switchFormMode(AuthFormMode.FORGOT_PASSWORD)
        viewModel.submit(emailInput = "khang@ut.edu.vn")

        assertEquals(1, fakeRepo.resetCallCount)
        assertEquals(AuthFormMode.LOGIN, viewModel.uiState.value.formMode)
        assertNotNull(viewModel.uiState.value.resetEmailSentMessage)
    }

    @Test
    fun credential401AndNetworkErrors_showFeedbackAndAllowRetry() = runBlocking {
        val fakeRepo = ControllableFakeAuthRepository()
        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(mapOf(RETURN_TARGET_KEY to "doc_202")),
            mainDispatcher = Dispatchers.Unconfined
        )

        // 1. Lỗi sai mật khẩu
        fakeRepo.nextResult = Result.failure(IllegalArgumentException("Invalid password credential"))
        viewModel.submit("khang@ut.edu.vn", "sai_mat_khau")
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Email hoặc mật khẩu không chính xác.", viewModel.uiState.value.generalError)

        // 2. Lỗi mất mạng (IOException)
        fakeRepo.nextResult = Result.failure(IOException("Network unreachable"))
        viewModel.submit("khang@ut.edu.vn", "matkhau123")
        assertTrue(viewModel.uiState.value.generalError.orEmpty().contains("Mất kết nối mạng"))

        // 3. Thử lại (Retry) khi mạng phục hồi -> thành công
        fakeRepo.nextResult = Result.success(Unit)
        viewModel.submit("khang@ut.edu.vn", "matkhau123")
        assertNull(viewModel.uiState.value.generalError)
        assertEquals(AuthSession.SignedIn("uid-test-01"), viewModel.uiState.value.session)
    }

    @Test
    fun duplicateSubmitWhileLoading_sendsOnlyOneRequest() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fakeRepo = ControllableFakeAuthRepository().apply {
            suspendGate = gate
        }
        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val job = launch(Dispatchers.Unconfined) {
            viewModel.submit("khang@ut.edu.vn", "matkhau123")
        }

        // Đang loading -> bấm lần 2 phải bị bỏ qua
        assertTrue(viewModel.uiState.value.isLoading)
        viewModel.submit("khang@ut.edu.vn", "matkhau123")
        assertEquals(1, fakeRepo.signInCallCount)

        gate.complete(Unit)
        job.join()
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
