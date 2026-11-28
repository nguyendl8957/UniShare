package vn.unishare.feature.auth.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import vn.unishare.R
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
    onGoogleSignInClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authSession by viewModel.authSession.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    BackHandler { viewModel.cancelAuth() }

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
        onSubmit = { email, password, confirmPassword ->
            viewModel.submit(email, password, confirmPassword)
        },
        onSwitchMode = viewModel::switchFormMode,
        onGoogleSignInClick = onGoogleSignInClick ?: {
            viewModel.signInWithGoogle {
                @SuppressLint("DiscouragedApi")
                val clientId = resources.getIdentifier("default_web_client_id", "string", context.packageName)
                check(clientId != 0) { "UniShare chưa được cấu hình đăng nhập Google. Vui lòng dùng email." }
                val option = GetSignInWithGoogleOption.Builder(resources.getString(clientId)).build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val credential = CredentialManager.create(context).getCredential(context, request).credential
                check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            }
        },
        onCancelClick = viewModel::cancelAuth
    )
}

/**
 * Giao diện màn hình Đăng nhập / Đăng ký / Khôi phục mật khẩu bám sát 100% thiết kế Figma Frame 82:86 ("6-Đăng nhập").
 * Lưu ý: Trên Figma chỉ vẽ sẵn tab "Đăng nhập" (node 82:86), chưa có frame riêng cho tab "Đăng ký".
 * Phần "Đăng ký" được thiết kế đơn giản (Email, Mật khẩu, Xác nhận mật khẩu) nhưng giữ nguyên 100%
 * màu sắc (#2E7D32, #738096, #CAC4D0), kiểu chữ, bo góc (12dp) và bộ Vector Asset từ Figma 82:86.
 */
@Composable
fun AuthScreenContent(
    uiState: AuthUiState,
    onEmailChange: (String) -> Unit,
    onSubmit: (email: String, password: String, confirmPassword: String?) -> Unit,
    onSwitchMode: (AuthFormMode) -> Unit,
    onGoogleSignInClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (uiState.session is AuthSession.Restoring) {
        UiFeedback(state = UiFeedbackState.Loading)
        return
    }

    // Mật khẩu chỉ giữ tạm trên UI trong lúc nhập, KHÔNG lưu bằng rememberSaveable để tránh rò rỉ vào Bundle
    var password by remember(uiState.formMode) { mutableStateOf("") }
    var confirmPassword by remember(uiState.formMode) { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isConfirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    val brandGreen = Color(0xFF2E7D32)
    val mutedGray = Color(0xFF738096)
    val dividerGray = Color(0xFFE2E6ED)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            // 1. Header Thương hiệu + Chuông + Menu theo Figma node 82:89 (cao 64dp, padding ngang 24dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Image(
                    painter = painterResource(id = R.drawable.unishare_wordmark),
                    contentDescription = "Thương hiệu UniShare",
                    modifier = Modifier
                        .width(134.dp)
                        .height(48.55.dp)
                )

                IconButton(onClick = onCancelClick, modifier = Modifier.testTag("btn_cancel_auth")) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Đóng đăng nhập")
                }
            }

            // 2. Thân màn hình theo Figma node 82:102 (padding ngang 24dp, trên 8dp, dưới 24dp, SpaceBetween)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Khối biểu mẫu phía trên (Figma node 82:103)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Slogan (Figma node 82:104)
                    Text(
                        text = "Chia sẻ tri thức - Cùng nhau tiến bộ",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = mutedGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Thanh Tab Đăng nhập / Đăng ký (Figma node 82:105: pt 16dp, cao 44dp mỗi tab)
                    if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(44.dp)
                        ) {
                            val isLogin = uiState.formMode == AuthFormMode.LOGIN
                            // Tab Đăng nhập (Figma node 82:106)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = !uiState.isLoading) {
                                        onSwitchMode(AuthFormMode.LOGIN)
                                    }
                                    .drawBehind {
                                        val strokePx = if (isLogin) 2.dp.toPx() else 1.dp.toPx()
                                        val color = if (isLogin) brandGreen else dividerGray
                                        drawLine(
                                            color = color,
                                            start = Offset(0f, size.height - strokePx / 2),
                                            end = Offset(size.width, size.height - strokePx / 2),
                                            strokeWidth = strokePx
                                        )
                                    }
                                    .testTag("tab_login"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Đăng nhập",
                                    fontSize = 14.sp,
                                    fontWeight = if (isLogin) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isLogin) brandGreen else mutedGray
                                )
                            }

                            // Tab Đăng ký (Figma node 82:110)
                            val isRegister = uiState.formMode == AuthFormMode.REGISTER
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(enabled = !uiState.isLoading) {
                                        onSwitchMode(AuthFormMode.REGISTER)
                                    }
                                    .drawBehind {
                                        val strokePx = if (isRegister) 2.dp.toPx() else 1.dp.toPx()
                                        val color = if (isRegister) brandGreen else dividerGray
                                        drawLine(
                                            color = color,
                                            start = Offset(0f, size.height - strokePx / 2),
                                            end = Offset(size.width, size.height - strokePx / 2),
                                            strokeWidth = strokePx
                                        )
                                    }
                                    .testTag("tab_register"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Đăng ký",
                                    fontSize = 14.sp,
                                    fontWeight = if (isRegister) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isRegister) brandGreen else mutedGray
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Khôi phục mật khẩu",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandGreen,
                            modifier = Modifier.padding(top = 20.dp)
                        )
                    }

                    // Khối ô nhập liệu (Figma node 82:112: pt 20dp, gap 12dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Ô nhập Email (Chỉ ghi "Email" theo yêu cầu của nhóm, dẫn icon ic_mail từ Figma)
                        UniTextField(
                            value = uiState.email,
                            onValueChange = onEmailChange,
                            label = "Email",
                            enabled = !uiState.isLoading,
                            errorMessage = uiState.emailError,
                            isLoading = uiState.isLoading,
                            leadingIconRes = R.drawable.ic_mail,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = if (uiState.formMode == AuthFormMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                            ),
                            modifier = Modifier.testTag("input_email")
                        )

                        // Ô nhập Mật khẩu (Figma node 82:117: icon khóa + mắt ẩn/hiện)
                        if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD) {
                            UniTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = "Mật khẩu",
                                enabled = !uiState.isLoading,
                                errorMessage = uiState.passwordError,
                                isLoading = uiState.isLoading,
                                leadingIconRes = R.drawable.ic_lock_keyhole,
                                visualTransformation = if (isPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isPasswordVisible = !isPasswordVisible },
                                        enabled = !uiState.isLoading
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off
                                            ),
                                            contentDescription = if (isPasswordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = if (uiState.formMode == AuthFormMode.REGISTER) ImeAction.Next else ImeAction.Done
                                ),
                                modifier = Modifier.testTag("input_password")
                            )
                        }

                        // Ô Xác nhận mật khẩu (chỉ hiển thị ở tab Đăng ký, dùng chung kiểu dáng và icon của Figma 82:117)
                        if (uiState.formMode == AuthFormMode.REGISTER) {
                            UniTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = "Xác nhận mật khẩu",
                                enabled = !uiState.isLoading,
                                errorMessage = uiState.confirmPasswordError,
                                isLoading = uiState.isLoading,
                                leadingIconRes = R.drawable.ic_lock_keyhole,
                                visualTransformation = if (isConfirmPasswordVisible) {
                                    VisualTransformation.None
                                } else {
                                    PasswordVisualTransformation()
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                                        enabled = !uiState.isLoading
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isConfirmPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_off
                                            ),
                                            contentDescription = if (isConfirmPasswordVisible) "Ẩn mật khẩu xác nhận" else "Hiện mật khẩu xác nhận",
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier.testTag("input_confirm_password")
                            )
                        }
                    }

                    // Quên mật khẩu? (Figma node 82:123: chỉ hiển thị ở tab Đăng nhập, pt 8dp, pb 16dp, căn phải)
                    if (uiState.formMode == AuthFormMode.LOGIN) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "Quên mật khẩu?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = brandGreen,
                                modifier = Modifier
                                    .clickable(enabled = !uiState.isLoading) {
                                        onSwitchMode(AuthFormMode.FORGOT_PASSWORD)
                                    }
                                    .testTag("btn_forgot_password")
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Thông báo lỗi chung hoặc thông báo đã gửi email khôi phục
                    if (uiState.generalError != null) {
                        Text(
                            text = uiState.generalError,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
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
                            color = brandGreen,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .testTag("text_reset_sent")
                        )
                    }

                    // Nút hành động chính (Figma node 82:125: cao 48dp, bo góc 12dp)
                    val submitLabel = when (uiState.formMode) {
                        AuthFormMode.LOGIN -> "Đăng nhập"
                        AuthFormMode.REGISTER -> "Đăng ký"
                        AuthFormMode.FORGOT_PASSWORD -> "Gửi link khôi phục"
                    }
                    UniButton(
                        text = submitLabel,
                        onClick = {
                            val confirmArg = if (uiState.formMode == AuthFormMode.REGISTER) confirmPassword else null
                            onSubmit(uiState.email, password, confirmArg)
                        },
                        enabled = !uiState.isLoading,
                        isLoading = uiState.isLoading,
                        modifier = Modifier.testTag("btn_submit_auth")
                    )

                    // Vạch "Hoặc" & Nút "Đăng nhập / Đăng ký với Google" (Figma node 82:129 & 82:133)
                    if (uiState.formMode != AuthFormMode.FORGOT_PASSWORD && uiState.isGoogleAvailable) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = dividerGray
                            )
                            Text(
                                text = "Hoặc",
                                fontSize = 11.sp,
                                color = mutedGray
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = dividerGray
                            )
                        }

                        OutlinedButton(
                            onClick = onGoogleSignInClick,
                            enabled = !uiState.isLoading,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF28352F)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_google_sign_in")
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (uiState.formMode == AuthFormMode.REGISTER) {
                                    "Đăng ký với Google"
                                } else {
                                    "Đăng nhập với Google"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF28352F)
                            )
                        }
                    }

                    // Dòng chuyển đổi dưới nút Google (Figma node 82:141)
                    Spacer(modifier = Modifier.height(20.dp))
                    if (uiState.formMode == AuthFormMode.FORGOT_PASSWORD) {
                        Text(
                            text = "Quay lại Đăng nhập",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = brandGreen,
                            modifier = Modifier
                                .clickable(enabled = !uiState.isLoading) {
                                    onSwitchMode(AuthFormMode.LOGIN)
                                }
                                .testTag("btn_back_to_login")
                        )
                    } else {
                        val isLogin = uiState.formMode == AuthFormMode.LOGIN
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = mutedGray, fontSize = 12.sp)) {
                                    append(if (isLogin) "Chưa có tài khoản? " else "Đã có tài khoản? ")
                                }
                                withStyle(
                                    SpanStyle(
                                        color = brandGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                ) {
                                    append(if (isLogin) "Đăng ký ngay" else "Đăng nhập ngay")
                                }
                            },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.clickable(enabled = !uiState.isLoading) {
                                onSwitchMode(if (isLogin) AuthFormMode.REGISTER else AuthFormMode.LOGIN)
                            }
                        )
                    }
                }

                // Khối Điều khoản cuối màn hình (Figma node 82:142)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (uiState.formMode == AuthFormMode.REGISTER) {
                            "Bằng việc đăng ký, bạn đồng ý với"
                        } else {
                            "Bằng việc đăng nhập, bạn đồng ý với"
                        },
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = mutedGray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Điều khoản sử dụng & Chính sách bảo mật",
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = brandGreen,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "1. Đăng nhập - 360x800 (Figma 82:86)")
@Composable
private fun AuthScreenLoginPreview() {
    UniShareTheme {
        AuthScreenContent(
            uiState = AuthUiState(
                formMode = AuthFormMode.LOGIN,
                email = "",
                session = AuthSession.Guest
            ),
            onEmailChange = {},
            onSubmit = { _, _, _ -> },
            onSwitchMode = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "2. Đăng ký - 360x800 (Theo chuẩn Figma)")
@Composable
private fun AuthScreenRegisterPreview() {
    UniShareTheme {
        AuthScreenContent(
            uiState = AuthUiState(
                formMode = AuthFormMode.REGISTER,
                email = "",
                session = AuthSession.Guest
            ),
            onEmailChange = {},
            onSubmit = { _, _, _ -> },
            onSwitchMode = {}
        )
    }
}



