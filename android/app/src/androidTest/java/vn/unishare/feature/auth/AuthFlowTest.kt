package vn.unishare.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.auth.ui.AuthScreen
import vn.unishare.feature.auth.ui.AuthViewModel
import vn.unishare.feature.auth.ui.RETURN_TARGET_KEY

private class FakeFlowAuthRepository : AuthRepository {
    private val _session = MutableStateFlow<AuthSession>(AuthSession.Guest)
    override val session: StateFlow<AuthSession> = _session

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        _session.value = AuthSession.SignedIn("buyer-uid-01")
        return Result.success(Unit)
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        _session.value = AuthSession.SignedIn("buyer-uid-02")
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        _session.value = AuthSession.Guest
        return Result.success(Unit)
    }
}

class AuthFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun guestClicksBuy_opensAuth_signsIn_returnsToTargetDocId() {
        val fakeRepo = FakeFlowAuthRepository()
        var currentRoute by mutableStateOf("detail:doc_CTDL_01")
        var returnedTarget: String? = null

        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(mapOf(RETURN_TARGET_KEY to "checkout:doc_CTDL_01")),
            mainDispatcher = Dispatchers.Main.immediate
        )

        composeTestRule.setContent {
            UniShareTheme {
                when (currentRoute) {
                    "detail:doc_CTDL_01" -> {
                        Column {
                            Text("Chi tiết tài liệu: Cấu trúc dữ liệu & Giải thuật")
                            Button(
                                onClick = { currentRoute = "auth" },
                                modifier = Modifier.testTag("btn_buy_now")
                            ) {
                                Text("Mua ngay")
                            }
                        }
                    }
                    "auth" -> {
                        AuthScreen(
                            viewModel = viewModel,
                            onNavigateToTarget = { target ->
                                returnedTarget = target
                                currentRoute = target ?: "detail:doc_CTDL_01"
                            },
                            onAuthCanceled = {
                                currentRoute = "detail:doc_CTDL_01"
                            }
                        )
                    }
                    "checkout:doc_CTDL_01" -> {
                        Text("Màn hình thanh toán cho doc_CTDL_01")
                    }
                }
            }
        }

        // 1. Guest xem nội dung tài liệu ngay khi mở
        composeTestRule.onNodeWithText("Chi tiết tài liệu: Cấu trúc dữ liệu & Giải thuật").assertIsDisplayed()

        // 2. Bấm "Mua ngay" -> chuyển sang màn hình Auth
        composeTestRule.onNodeWithTag("btn_buy_now").performClick()
        composeTestRule.waitForIdle()

        // 3. Nhập email và mật khẩu hợp lệ rồi bấm Đăng nhập
        composeTestRule
            .onNodeWithContentDescription("Ô nhập liệu cho Email hoặc số điện thoại")
            .performTextReplacement("khang@ut.edu.vn")
        composeTestRule.onNodeWithTag("input_password").performTextReplacement("123456")
        composeTestRule.onNodeWithTag("btn_submit_auth").performClick()
        composeTestRule.waitForIdle()

        // 4. Xác minh tự động quay lại đúng docId / checkout một lần duy nhất
        assertEquals("checkout:doc_CTDL_01", returnedTarget)
        composeTestRule.onNodeWithText("Màn hình thanh toán cho doc_CTDL_01").assertIsDisplayed()
    }

    @Test
    fun guestCancelsAuth_returnsToDetailWithoutOpeningCheckout() {
        val fakeRepo = FakeFlowAuthRepository()
        var currentRoute by mutableStateOf("auth")
        var checkoutOpened = false

        val viewModel = AuthViewModel(
            authRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(mapOf(RETURN_TARGET_KEY to "checkout:doc_CTDL_01")),
            mainDispatcher = Dispatchers.Main.immediate
        )

        composeTestRule.setContent {
            UniShareTheme {
                when (currentRoute) {
                    "auth" -> {
                        AuthScreen(
                            viewModel = viewModel,
                            onNavigateToTarget = {
                                checkoutOpened = true
                                currentRoute = "checkout:doc_CTDL_01"
                            },
                            onAuthCanceled = {
                                currentRoute = "detail:doc_CTDL_01"
                            }
                        )
                    }
                    "detail:doc_CTDL_01" -> {
                        Text("Quay lại chi tiết tài liệu ở chế độ Guest")
                    }
                }
            }
        }

        // Bấm nút Bỏ qua / Quay lại
        composeTestRule.onNodeWithTag("btn_cancel_auth").performClick()
        composeTestRule.waitForIdle()

        // Không được mở checkout khi hủy đăng nhập
        assertEquals(false, checkoutOpened)
        composeTestRule.onNodeWithText("Quay lại chi tiết tài liệu ở chế độ Guest").assertIsDisplayed()
    }
}
