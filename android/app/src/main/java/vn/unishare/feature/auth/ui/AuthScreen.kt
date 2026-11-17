package vn.unishare.feature.auth.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.components.UniTextField
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.domain.repository.AuthSession

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onNavigateToTarget: (String?) -> Unit,
    onAuthCanceled: () -> Unit,
    onShowSnackbar: (String) -> Unit = {},
    onGoogleSignInClick: () -> Unit = { viewModel.onGoogleSignInResult(null) }
) {
    val uiState by viewModel.uiState.collectAsState()
    val authSession by viewModel.authSession.collectAsState()

    // Lắng nghe sự kiện điều hướng / thông báo dùng một lần (One-shot events)
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AuthEvent.NavigateToTarget -> onNavigateToTarget(event.targetRoute)
                is AuthEvent.AuthCanceled -> onAuthCanceled()
                is AuthEvent.ShowMessage -> onShowSnackbar(event.message)
            }
        }
    }

    AuthScreenContent(
        uiState = uiState.copy(session = authSession),
        onEmailChange = viewModel::onEmailChange,
        onSubmit = { email, password -> viewModel.submit(email, password) },
        onSwitchMode = viewModel::switchFormMode,
        onGoogleSignInClick = onGoogleSignInClick,
        onCancelClick = viewModel::cancelAuth
    )
}

/**
 * Composable thuần (Stateless) nhận state bất biến và callback theo thiết kế Figma Frame 82:86 ("6-Đăng nhập").
 * Tái sử dụng UniButton, UniTextField và UiFeedback từ C01.
 */
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: (email: String, password: String) -> Unit,
    onSwitchMode: (AuthFormMode) -> Unit,
    onGoogleSignInClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Khi phiên đang khôi phục (Restoring), hiển thị màn chờ từ C01
    if (uiState.session is AuthSession.Restoring) {
        UiFeedback(state = UiFeedbackState.Loading)
        return
    }

    // Mật khẩu chỉ giữ tạm trên UI trong lúc nhập, KHÔNG lưu bằng rememberSaveable để tránh lưu vào Bundle khi process death
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Thương hiệu & Slogan theo Figma node 101:2429 & 82:104
        Text(
            text = "UniShare",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Chia sẻ tri thức - Cùng nhau tiến bộ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Thanh chuyển tab Đăng nhập / Đăng ký theo Figma node 82:105
        if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD) {
            val selectedTabIndex = if (uiState.formMode == AuthFormMode.LOGIN) 0 else 1
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = uiState.formMode == AuthFormMode.LOGIN,
                    onClick = { onSwitchMode(AuthFormMode.LOGIN) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag("tab_login"),
                    text = {
                        Text(
                            text = "Đăng nhập",
                            fontWeight = if (uiState.formMode == AuthFormMode.LOGIN) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                )
                Tab(
                    selected = uiState.formMode == AuthFormMode.REGISTER,
                    onClick = { onSwitchMode(AuthFormMode.REGISTER) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag("tab_register"),
                    text = {
                        Text(
                            text = "Đăng ký",
                            fontWeight = if (uiState.formMode == AuthFormMode.REGISTER) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                )
            }
        } else {
            Text(
                text = "Khôi phục mật khẩu",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Ô nhập Email (tái sử dụng UniTextField của C01)
        UniTextField(
            value = uiState.email,
            onValueChange = onEmailChange,
            label = "Email hoặc số điện thoại",
            enabled = !uiState.isLoading,
            errorMessage = uiState.emailError,
            isLoading = uiState.isLoading,
            modifier = Modifier.testTag("input_email")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Ô nhập Mật khẩu (ẩn khi ở chế độ Quên mật khẩu)
        if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("input_password")
                        .semantics {
                            contentDescription = "Ô nhập liệu cho Mật khẩu"
                        },
                    enabled = !uiState.isLoading,
                    isError = uiState.passwordError != null,
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            enabled = !uiState.isLoading
                        ) {
                            Text(
                                text = if (isPasswordVisible) "Ẩn" else "Hiện",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                )
                if (uiState.passwordError != null) {
                    Text(
                        text = uiState.passwordError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            // Link Quên mật khẩu? căn phải theo Figma node 82:123
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = { onSwitchMode(AuthFormMode.FORGOT_PASSWORD) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.testTag("btn_forgot_password")
                ) {
                    Text(
                        text = "Quên mật khẩu?",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Thông báo lỗi chung hoặc thông báo đã gửi email khôi phục
        if (uiState.generalError != null) {
            Text(
                text = uiState.generalError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("text_general_error")
            )
        }

        if (uiState.resetEmailSentMessage != null) {
            Text(
                text = uiState.resetEmailSentMessage,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("text_reset_sent")
            )
        }

        // 5. Nút hành động chính (tái sử dụng UniButton từ C01)
        val submitLabel = when (uiState.formMode) {
            AuthFormMode.LOGIN -> "Đăng nhập"
            AuthFormMode.REGISTER -> "Tạo tài khoản"
            AuthFormMode.FORGOT_PASSWORD -> "Gửi link khôi phục"
        }
        UniButton(
            text = submitLabel,
            onClick = { onSubmit(uiState.email, password) },
            enabled = !uiState.isLoading,
            isLoading = uiState.isLoading,
            modifier = Modifier.testTag("btn_submit_auth")
        )

        // 6. Vạch "Hoặc" và nút "Đăng nhập với Google" theo Figma node 82:129 & 82:133
        if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD && uiState.isGoogleAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "Hoặc",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            OutlinedButton(
                onClick = onGoogleSignInClick,
                enabled = !uiState.isLoading,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("btn_google_sign_in")
            ) {
                Text(
                    text = "Đăng nhập với Google",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 7. Chuyển đổi nhanh chế độ hoặc Hủy về màn Guest
        if (uiState.formMode == AuthFormMode.FORGOT_PASSWORD) {
            TextButton(
                onClick = { onSwitchMode(AuthFormMode.LOGIN) },
                enabled = !uiState.isLoading,
                modifier = Modifier.testTag("btn_back_to_login")
            ) {
                Text("Quay lại Đăng nhập")
            }
        } else {
            TextButton(
                onClick = {
                    val targetMode = if (uiState.formMode == AuthFormMode.LOGIN) {
                        AuthFormMode.REGISTER
                    } else {
                        AuthFormMode.LOGIN
                    }
                    onSwitchMode(targetMode)
                },
                enabled = !uiState.isLoading
            ) {
                Text(
                    text = if (uiState.formMode == AuthFormMode.LOGIN) {
                        "Chưa có tài khoản? Đăng ký ngay"
                    } else {
                        "Đã có tài khoản? Đăng nhập"
                    }
                )
            }
        }

        TextButton(
            onClick = onCancelClick,
            enabled = !uiState.isLoading,
            modifier = Modifier.testTag("btn_cancel_auth")
        ) {
            Text("Bỏ qua / Quay lại")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 8. Điều khoản sử dụng theo Figma node 82:142
        Text(
            text = "Bằng việc đăng nhập, bạn đồng ý với\nĐiều khoản sử dụng & Chính sách bảo mật",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview(widthDp = 360, name = "Auth Screen - 360dp (Figma 82:86)")
@Preview(widthDp = 412, name = "Auth Screen - 412dp")
@Preview(fontScale = 1.5f, name = "Auth Screen - Large Font")
@Composable
private fun AuthScreenPreview() {
    UniShareTheme {
        Surface {
            AuthScreenContent(
                uiState = AuthUiState(
                    formMode = AuthFormMode.LOGIN,
                    email = "sinhvien@ut.edu.vn",
                    session = AuthSession.Guest
                ),
                onEmailChange = {},
                onSubmit = { _, _ -> },
                onSwitchMode = {}
            )
        }
    }
}

