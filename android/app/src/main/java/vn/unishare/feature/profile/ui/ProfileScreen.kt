package vn.unishare.feature.profile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val BuyerPrimaryGreen = Color(0xFF2E7D32)
private val ProfileSetupTitleNavy = Color(0xFF102348)
private val ProfileSetupMuted = Color(0xFF8192B0)
private val ProfileInputBorder = Color(0xFFCAC4D0)
private val ProfileRequiredRed = Color(0xFFF43F5E)
private val ProfileDisabledBg = Color(0xFFE6E9ED)
private val ProfileDisabledText = Color(0xFF99A2AF)
private val ProfileAvatarCircleBg = Color(0xFFD5E0F2)

private val BuyerProfileBg = Color(0xFFF8F9FA)
private val BuyerProfileNavy = Color(0xFF102345)
private val BuyerProfileMuted = Color(0xFF7B8CAA)
private val BuyerSwitchBlue = Color(0xFF1565C0)
private val BuyerDividerColor = Color(0xFFEAF0F5)

private val SellerDashboardBg = Color(0xFFF5F8FB)
private val SellerHeaderBlue = Color(0xFF1966D2)
private val SellerNavyText = Color(0xFF10244E)
private val SellerMutedText = Color(0xFF7789AE)
private val SellerMetricCardBg = Color(0xFFE4F2FF)
private val SellerMetricIconBg = Color(0xFF2F86FC)
private val SellerGrowthGreen = Color(0xFF168A39)

// Màn hình Hồ sơ cá nhân, Thiết lập hồ sơ và Chuyển chế độ Buyer <-> Seller
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onLoginClick: () -> Unit = {},
    onOpenPurchasedLibrary: () -> Unit = {},
    onOpenTransactionHistory: () -> Unit = {},
    onOpenSellerManagement: () -> Unit = {},
    onOpenSellerUpload: () -> Unit = {},
    onOpenPublicSellerProfile: (String) -> Unit = {},
    onBottomNavSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSellerTermsDialog by remember { mutableStateOf(false) }

    val canCancelEdit = uiState.isEditingProfile &&
        uiState.displayName.isNotBlank() &&
        uiState.school.isNotBlank() &&
        uiState.faculty.isNotBlank()

    BackHandler(enabled = canCancelEdit || uiState.currentMode == UniAppMode.SELLER) {
        if (canCancelEdit) {
            viewModel.cancelEditingProfile()
        } else if (uiState.currentMode == UniAppMode.SELLER) {
            viewModel.switchMode(UniAppMode.BUYER)
        }
    }

    UniShareTheme(mode = uiState.currentMode) {
        if (showSellerTermsDialog) {
            AlertDialog(
                onDismissRequest = { showSellerTermsDialog = false },
                title = { Text("Đăng ký Người bán") },
                text = {
                    Text(
                        "Bạn xác nhận muốn đăng bán tài liệu do mình " +
                        "có quyền chia sẻ và tuân thủ quy định UniShare. " +
                        "Việc đăng ký không tự kích hoạt nhận thanh toán."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSellerTermsDialog = false
                            viewModel.requestSellerEnrollment(
                                confirmTerms = true
                            )
                        }
                    ) {
                        Text("Tôi đồng ý")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showSellerTermsDialog = false }
                    ) {
                        Text("Hủy")
                    }
                }
            )
        }

        ProfileScreenContent(
            uiState = uiState,
            onLoginClick = onLoginClick,
            onRetryLoad = viewModel::reloadMyProfile,
            onRetrySellerDashboard = viewModel::loadSellerDashboard,
            onStartEdit = viewModel::startEditingProfile,
            onCancelEdit = viewModel::cancelEditingProfile,
            onEditNameChange = viewModel::onEditDisplayNameChange,
            onEditSchoolChange = viewModel::onEditSchoolChange,
            onEditFacultyChange = viewModel::onEditFacultyChange,
            onPickAvatar = {
                viewModel.onEditAvatarChange("avatar://updated_${System.currentTimeMillis()}")
            },
            onSaveProfile = viewModel::saveProfile,
            onSwitchMode = viewModel::switchMode,
            onRequestSellerEnrollment = { showSellerTermsDialog = true },
            onDismissEnrollmentNotice = viewModel::dismissEnrollmentNotice,
            onOpenPurchasedLibrary = onOpenPurchasedLibrary,
            onOpenTransactionHistory = onOpenTransactionHistory,
            onOpenSellerManagement = {
                viewModel.tryOpenProtectedSellerAction(onOpenSellerManagement)
            },
            onOpenSellerUpload = {
                viewModel.tryOpenProtectedSellerAction(onOpenSellerUpload)
            },
            onOpenPublicSellerProfile = {
                uiState.activeUid?.let(onOpenPublicSellerProfile)
            },
            onSignOut = viewModel::signOut,
            onBottomNavSelected = { route ->
                if (uiState.currentMode == UniAppMode.SELLER && route == "manage") {
                    viewModel.tryOpenProtectedSellerAction(onOpenSellerManagement)
                } else {
                    onBottomNavSelected(route)
                }
            },
            modifier = modifier
        )
    }
}

@Composable
fun ProfileScreenContent(
    uiState: ProfileUiState,
    onLoginClick: () -> Unit,
    onRetryLoad: () -> Unit,
    onRetrySellerDashboard: () -> Unit = {},
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onEditSchoolChange: (String) -> Unit,
    onEditFacultyChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onSaveProfile: () -> Unit,
    onSwitchMode: (UniAppMode) -> Unit,
    onRequestSellerEnrollment: () -> Unit,
    onDismissEnrollmentNotice: () -> Unit,
    onOpenPurchasedLibrary: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onOpenSellerManagement: () -> Unit,
    onOpenSellerUpload: () -> Unit,
    onOpenPublicSellerProfile: () -> Unit,
    onSignOut: () -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBottomTab = if (uiState.currentMode == UniAppMode.SELLER) "home" else "profile"
    val screenBg = when {
        uiState.isEditingProfile || uiState.isGuest -> Color.White
        uiState.currentMode == UniAppMode.SELLER -> SellerDashboardBg
        else -> BuyerProfileBg
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = screenBg,
        topBar = {
            if (!uiState.isGuest && !uiState.isEditingProfile && uiState.currentMode == UniAppMode.SELLER) {
                SellerModeTopAppBar(
                    onSwitchToBuyer = { onSwitchMode(UniAppMode.BUYER) }
                )
            }
        },
        bottomBar = {
            UniBottomBar(
                selectedRoute = selectedBottomTab,
                onItemSelected = onBottomNavSelected,
                mode = uiState.currentMode,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isRestoringSession || uiState.isLoadingProfile -> {
                    UiFeedback(state = UiFeedbackState.Loading)
                }

                uiState.loadError != null -> {
                    UiFeedback(
                        state = UiFeedbackState.Error(uiState.loadError),
                        onRetry = onRetryLoad
                    )
                }

                uiState.isGuest -> {
                    GuestProfilePrompt(onLoginClick = onLoginClick)
                }

                uiState.isEditingProfile -> {
                    ProfileSetupEditForm(
                        uiState = uiState,
                        canCancel = uiState.displayName.isNotBlank() &&
                            uiState.school.isNotBlank() &&
                            uiState.faculty.isNotBlank(),
                        onCancelEdit = onCancelEdit,
                        onEditNameChange = onEditNameChange,
                        onEditSchoolChange = onEditSchoolChange,
                        onEditFacultyChange = onEditFacultyChange,
                        onPickAvatar = onPickAvatar,
                        onSaveProfile = onSaveProfile
                    )
                }

                uiState.currentMode == UniAppMode.SELLER && uiState.sellerEnabled -> {
                    SellerModeDashboardBody(
                        uiState = uiState,
                        onRetrySellerDashboard = onRetrySellerDashboard,
                        onOpenSellerManagement = onOpenSellerManagement,
                        onOpenSellerUpload = onOpenSellerUpload,
                        onOpenPublicSellerProfile = onOpenPublicSellerProfile
                    )
                }

                else -> {
                    BuyerProfileBody(
                        uiState = uiState,
                        onStartEdit = onStartEdit,
                        onSwitchToSeller = { onSwitchMode(UniAppMode.SELLER) },
                        onRequestSellerEnrollment = onRequestSellerEnrollment,
                        onDismissEnrollmentNotice = onDismissEnrollmentNotice,
                        onOpenPurchasedLibrary = onOpenPurchasedLibrary,
                        onOpenTransactionHistory = onOpenTransactionHistory,
                        onOpenPublicSellerProfile = onOpenPublicSellerProfile,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }
}

// --- Trạng thái Khách (Guest) ---

@Composable
private fun GuestProfilePrompt(onLoginClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 28.dp)
            .testTag("profile_guest_view")
    ) {
        Image(
            painter = painterResource(id = R.drawable.unishare_wordmark),
            contentDescription = "Biểu trưng UniShare",
            modifier = Modifier
                .width(134.dp)
                .height(46.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ProfileAvatarCircleBg)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                contentDescription = null,
                tint = BuyerProfileNavy,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Bạn đang ở chế độ Khách",
            color = BuyerProfileNavy,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Đăng nhập để thiết lập hồ sơ trường/khoa, quản lý tài liệu đã mua và chuyển sang chế độ Người bán.",
            color = BuyerProfileMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        UniButton(
            text = "Đăng nhập / Đăng ký",
            onClick = onLoginClick,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .testTag("btn_profile_guest_login")
        )
    }
}

// --- Màn hình Thiết lập / Chỉnh sửa hồ sơ ---

@Composable
private fun ProfileSetupEditForm(
    uiState: ProfileUiState,
    canCancel: Boolean,
    onCancelEdit: () -> Unit,
    onEditNameChange: (String) -> Unit,
    onEditSchoolChange: (String) -> Unit,
    onEditFacultyChange: (String) -> Unit,
    onPickAvatar: () -> Unit,
    onSaveProfile: () -> Unit
) {
    val isSaving = uiState.saveStatus is ProfileSaveStatus.Saving
    val errors = uiState.fieldErrors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("profile_setup_form")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.unishare_wordmark),
                contentDescription = "Biểu trưng UniShare",
                modifier = Modifier
                    .width(134.dp)
                    .height(44.dp)
            )
            if (canCancel) {
                TextButton(
                    onClick = onCancelEdit,
                    enabled = !isSaving,
                    modifier = Modifier.testTag("btn_cancel_edit_profile")
                ) {
                    Text(
                        text = "Hủy",
                        color = ProfileSetupMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Thiết lập hồ sơ",
            color = ProfileSetupTitleNavy,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Ảnh đại diện kèm nút máy ảnh
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clickable(enabled = !isSaving, onClick = onPickAvatar)
                    .testTag("btn_pick_avatar")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ProfileAvatarCircleBg)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BuyerPrimaryGreen)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = "Chọn ảnh đại diện",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (uiState.editAvatarPath != null) "Đã chọn ảnh đại diện" else "Chọn ảnh đại diện",
                color = ProfileSetupTitleNavy,
                fontSize = 12.sp
            )
            Text(
                text = "JPG, PNG (tối đa 5MB)",
                color = ProfileSetupMuted,
                fontSize = 10.5.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Họ tên
        ProfileFormLabel(label = "Họ tên")
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = uiState.editDisplayName,
            onValueChange = onEditNameChange,
            enabled = !isSaving,
            isError = errors.displayNameError != null,
            placeholder = {
                Text("Nhập họ tên của bạn", color = ProfileSetupMuted, fontSize = 12.5.sp)
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                    contentDescription = null,
                    tint = ProfileSetupMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BuyerPrimaryGreen,
                unfocusedBorderColor = ProfileInputBorder,
                errorBorderColor = ProfileRequiredRed,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("input_profile_name")
        )
        if (errors.displayNameError != null) {
            ProfileFieldErrorText(
                message = errors.displayNameError,
                modifier = Modifier.testTag("error_profile_name")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trường
        ProfileFormLabel(label = "Trường")
        Spacer(modifier = Modifier.height(6.dp))
        ProfileDropdownSelector(
            selectedValue = uiState.editSchool,
            placeholder = "Chọn trường",
            options = ProfileUiState.ALLOWED_SCHOOLS,
            enabled = !isSaving,
            hasError = errors.schoolError != null,
            onOptionSelected = onEditSchoolChange,
            modifier = Modifier.testTag("dropdown_profile_school")
        )
        if (errors.schoolError != null) {
            ProfileFieldErrorText(
                message = errors.schoolError,
                modifier = Modifier.testTag("error_profile_school")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Khoa
        ProfileFormLabel(label = "Khoa")
        Spacer(modifier = Modifier.height(6.dp))
        ProfileDropdownSelector(
            selectedValue = uiState.editFaculty,
            placeholder = "Chọn khoa",
            options = ProfileUiState.ALLOWED_FACULTIES,
            enabled = !isSaving,
            hasError = errors.facultyError != null,
            onOptionSelected = onEditFacultyChange,
            modifier = Modifier.testTag("dropdown_profile_faculty")
        )
        if (errors.facultyError != null) {
            ProfileFieldErrorText(
                message = errors.facultyError,
                modifier = Modifier.testTag("error_profile_faculty")
            )
        }

        val saveError = (uiState.saveStatus as? ProfileSaveStatus.Error)?.message
        if (saveError != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("banner_profile_save_error")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = null,
                        tint = ProfileRequiredRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = saveError,
                        color = Color(0xFF991B1B),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = onSaveProfile,
                        modifier = Modifier.testTag("btn_retry_save_profile")
                    ) {
                        Text("Thử lại", color = BuyerPrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        val isReady = uiState.isFormComplete
        Button(
            onClick = onSaveProfile,
            enabled = !isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isReady) BuyerPrimaryGreen else ProfileDisabledBg,
                contentColor = if (isReady) Color.White else ProfileDisabledText
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_profile_save")
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = "Hoàn tất",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = ProfileRequiredRed)) { append("*") }
                append(" Thông tin bắt buộc")
            },
            color = ProfileSetupMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileFormLabel(label: String) {
    Text(
        text = buildAnnotatedString {
            append(label)
            withStyle(SpanStyle(color = ProfileRequiredRed)) {
                append(" *")
            }
        },
        color = ProfileSetupTitleNavy,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun ProfileFieldErrorText(
    message: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = message,
        color = ProfileRequiredRed,
        fontSize = 11.5.sp,
        modifier = modifier.padding(top = 4.dp, start = 4.dp)
    )
}

@Composable
private fun ProfileDropdownSelector(
    selectedValue: String,
    placeholder: String,
    options: List<String>,
    enabled: Boolean,
    hasError: Boolean,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (hasError) ProfileRequiredRed else ProfileInputBorder
            ),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = enabled) { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_school),
                    contentDescription = null,
                    tint = ProfileSetupMuted,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = selectedValue.ifBlank { placeholder },
                    color = if (selectedValue.isBlank()) ProfileSetupMuted else ProfileSetupTitleNavy,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = ProfileSetupMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 13.sp) },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}

// --- Màn hình Hồ sơ chế độ Buyer (12-Hồ sơ, chuyển Role) ---

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuyerProfileBody(
    uiState: ProfileUiState,
    onStartEdit: () -> Unit,
    onSwitchToSeller: () -> Unit,
    onRequestSellerEnrollment: () -> Unit,
    onDismissEnrollmentNotice: () -> Unit,
    onOpenPurchasedLibrary: () -> Unit,
    onOpenTransactionHistory: () -> Unit,
    onOpenPublicSellerProfile: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("profile_buyer_view")
    ) {
        // Ảnh đại diện & thông tin trường/khoa
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clickable(onClick = onStartEdit)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ProfileAvatarCircleBg)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(BuyerSwitchBlue)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = "Chỉnh sửa hồ sơ",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.displayName,
                color = BuyerProfileNavy,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("txt_profile_display_name")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${uiState.school} / ${uiState.faculty}",
                color = BuyerProfileMuted,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("txt_profile_school_faculty")
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, BuyerDividerColor),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onStartEdit)
                        .testTag("btn_edit_profile")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = null,
                            tint = BuyerSwitchBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Chỉnh sửa hồ sơ",
                            color = BuyerSwitchBlue,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (uiState.sellerEnabled) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, BuyerDividerColor),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onOpenPublicSellerProfile)
                            .testTag("btn_view_my_public_profile")
                    ) {
                        Text(
                            text = "Hồ sơ công khai",
                            color = BuyerProfileNavy,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Dãy huy hiệu thành tích
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            uiState.achievements.forEach { badge ->
                AchievementBadgePill(badge = badge)
            }
            Surface(
                color = Color(0xFFE5F0FF),
                shape = RoundedCornerShape(100.dp)
            ) {
                Text(
                    text = "+3",
                    color = Color(0xFF294A7F),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Nút chuyển sang chế độ Người bán
        Surface(
            color = BuyerSwitchBlue,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSwitchToSeller)
                .testTag("btn_switch_to_seller")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_switch_role),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Chuyển sang chế độ Người bán",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Đăng bán tài liệu, kiếm thêm thu nhập",
                        color = Color(0xFFD1E6FF),
                        fontSize = 10.5.sp
                    )
                }
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Hộp thông báo khi chưa có quyền Seller hoặc máy chủ từ chối
        when (val enrollment = uiState.enrollmentState) {
            is SellerEnrollmentState.Idle -> Unit

            is SellerEnrollmentState.Enrolling -> {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFE8F2FF),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = BuyerSwitchBlue,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Đang xác minh quyền Người bán từ hệ thống...",
                            color = BuyerProfileNavy,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            is SellerEnrollmentState.Denied -> {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFFFF7ED),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_seller_denied")
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_warning),
                                contentDescription = null,
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = enrollment.message,
                                color = Color(0xFF9A3412),
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onRequestSellerEnrollment,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BuyerSwitchBlue),
                                modifier = Modifier.testTag("btn_enroll_seller")
                            ) {
                                Text("Đăng ký quyền Người bán", color = Color.White, fontSize = 12.sp)
                            }
                            TextButton(onClick = onDismissEnrollmentNotice) {
                                Text("Đóng", color = Color(0xFF9A3412), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Thẻ menu danh mục hồ sơ
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileMenuRow(
                    iconRes = R.drawable.ic_file_text,
                    title = "Tài liệu đã mua",
                    subtitle = "Xem lại tất cả tài liệu bạn đã mua",
                    onClick = onOpenPurchasedLibrary,
                    modifier = Modifier.testTag("menu_purchased_docs")
                )
                HorizontalDivider(
                    color = BuyerDividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                ProfileMenuRow(
                    iconRes = R.drawable.ic_history,
                    title = "Lịch sử giao dịch",
                    subtitle = "Theo dõi các giao dịch gần đây",
                    onClick = onOpenTransactionHistory,
                    modifier = Modifier.testTag("menu_transaction_history")
                )
                HorizontalDivider(
                    color = BuyerDividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                ProfileMenuRow(
                    iconRes = R.drawable.ic_settings,
                    title = "Cài đặt",
                    subtitle = "Tùy chỉnh ứng dụng & hồ sơ cá nhân",
                    onClick = onStartEdit,
                    modifier = Modifier.testTag("menu_settings")
                )
                HorizontalDivider(
                    color = BuyerDividerColor,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                ProfileMenuRow(
                    iconRes = R.drawable.ic_logout,
                    title = "Đăng xuất",
                    subtitle = null,
                    onClick = onSignOut,
                    modifier = Modifier.testTag("btn_profile_logout")
                )
            }
        }
    }
}

@Composable
internal fun AchievementBadgePill(badge: ProfileAchievementBadge) {
    Surface(
        color = Color(badge.containerColorHex),
        shape = RoundedCornerShape(100.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        ) {
            if (badge.code != null && badge.badgeCircleColorHex != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(badge.badgeCircleColorHex))
                ) {
                    Text(
                        text = badge.code,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_star),
                    contentDescription = null,
                    tint = Color(badge.contentColorHex),
                    modifier = Modifier.size(15.dp)
                )
            }
            Text(
                text = badge.label,
                color = Color(badge.contentColorHex),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    iconRes: Int,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = BuyerSwitchBlue,
            modifier = Modifier.size(24.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = BuyerProfileNavy,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = BuyerProfileMuted,
                    fontSize = 11.5.sp
                )
            }
        }
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = BuyerProfileMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

// --- Màn hình Tổng quan Chế độ Người bán (13-Chế độ người bán) ---

@Composable
private fun SellerModeTopAppBar(
    onSwitchToBuyer: () -> Unit
) {
    Surface(
        color = SellerHeaderBlue,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp)
        ) {
            IconButton(
                onClick = onSwitchToBuyer,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left),
                    contentDescription = "Quay lại chế độ Người mua",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }

            Text(
                text = "Chế độ Người bán",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Surface(
                color = Color.Transparent,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF6BA0E6)),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSwitchToBuyer)
                    .testTag("btn_switch_to_buyer")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_switch_role),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Quay lại Người mua",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerModeDashboardBody(
    uiState: ProfileUiState,
    onRetrySellerDashboard: () -> Unit,
    onOpenSellerManagement: () -> Unit,
    onOpenSellerUpload: () -> Unit,
    onOpenPublicSellerProfile: () -> Unit
) {
    val dashboard = uiState.sellerDashboard

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("profile_seller_dashboard")
    ) {
        // Lời chào Người bán
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenPublicSellerProfile)
                .padding(vertical = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(ProfileAvatarCircleBg)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                    contentDescription = null,
                    tint = SellerNavyText,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Xin chào, ${uiState.displayName}!",
                    color = SellerNavyText,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("txt_seller_greeting")
                )
                Text(
                    text = "Cùng chia sẻ tri thức, tạo giá trị cho cộng đồng sinh viên",
                    color = SellerMutedText,
                    fontSize = 11.5.sp
                )
            }
        }

        if (uiState.sellerDashboardLoading) {
            Text(
                text = "Đang tải thống kê thực tế...",
                color = SellerMutedText,
                fontSize = 12.sp
            )
        }

        uiState.sellerDashboardError?.let { error ->
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Không tải được thống kê: $error",
                        color = Color(0xFF9A3412),
                        fontSize = 12.sp
                    )
                    TextButton(onClick = onRetrySellerDashboard) {
                        Text("Thử lại")
                    }
                }
            }
        }

        // 3 thẻ chỉ số tổng quan
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SellerMetricCard(
                iconRes = R.drawable.ic_history,
                label = "Doanh thu",
                valueText = if (uiState.sellerDashboardLoaded) formatProfileVnd(dashboard.monthlyRevenueVnd) else "—",
                footerLeft = "Tháng này",
                footerRight = dashboard.revenueGrowthLabel,
                modifier = Modifier.weight(1f)
            )
            SellerMetricCard(
                iconRes = R.drawable.ic_file_text,
                label = "Số tài liệu đã đăng",
                valueText = if (uiState.sellerDashboardLoaded) dashboard.publishedDocCount.toString() else "—",
                footerLeft = "Tổng cộng",
                footerRight = null,
                showChevron = true,
                onClick = onOpenSellerManagement,
                modifier = Modifier
                    .weight(1f)
                    .testTag("card_seller_doc_count")
            )
            SellerMetricCard(
                iconRes = R.drawable.nav_seller_documents,
                label = "Đơn hôm nay",
                valueText = if (uiState.sellerDashboardLoaded) dashboard.todayOrderCount.toString() else "—",
                footerLeft = "Tổng đơn",
                footerRight = dashboard.orderGrowthLabel,
                modifier = Modifier.weight(1f)
            )
        }

        // Nút Đăng tài liệu mới
        Button(
            onClick = onOpenSellerUpload,
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SellerHeaderBlue,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("btn_seller_new_upload")
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_plus),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Đăng tài liệu mới",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Mục Tài liệu của tôi
        SellerDashboardSectionHeader(
            title = "Tài liệu của tôi",
            onViewAllClick = onOpenSellerManagement,
            viewAllTag = "btn_seller_manage_all"
        )

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                dashboard.myDocuments.forEachIndexed { idx, doc ->
                    if (idx > 0) {
                        HorizontalDivider(color = Color(0xFFEEF2F7))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenSellerManagement)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(SellerMetricCardBg)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_file_text),
                                contentDescription = null,
                                tint = SellerHeaderBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = doc.title,
                                color = SellerNavyText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    color = Color(doc.categoryBgHex),
                                    shape = RoundedCornerShape(30.dp)
                                ) {
                                    Text(
                                        text = doc.categoryLabel,
                                        color = Color(doc.categoryTextHex),
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }

                            }
                            Text(
                                text = formatProfileVnd(doc.priceVnd),
                                color = SellerNavyText,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = null,
                            tint = SellerMutedText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        // Mục Đơn hàng gần đây
        SellerDashboardSectionHeader(
            title = "Đơn hàng gần đây",
            onViewAllClick = onOpenSellerManagement,
            viewAllTag = "btn_seller_recent_orders_all"
        )

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                dashboard.recentOrders.forEachIndexed { idx, order ->
                    if (idx > 0) {
                        HorizontalDivider(color = Color(0xFFEEF2F7))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ProfileAvatarCircleBg)
                        ) {
                            Text(
                                text = order.buyerDisplayName.take(1),
                                color = SellerNavyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = order.buyerDisplayName,
                                color = SellerNavyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = order.documentTitle,
                                color = SellerMutedText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = order.timeAgoLabel,
                            color = SellerMutedText,
                            fontSize = 9.5.sp
                        )
                        Text(
                            text = formatProfileVnd(order.amountVnd),
                            color = SellerNavyText,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            color = if (order.isPaid) Color(0xFFE4F5EE) else Color(0xFFE5F0FF),
                            shape = RoundedCornerShape(30.dp)
                        ) {
                            Text(
                                text = if (order.isPaid) "Đã thanh toán" else "Đang xử lý",
                                color = if (order.isPaid) Color(0xFF26806F) else Color(0xFF0875FF),
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerMetricCard(
    iconRes: Int,
    label: String,
    valueText: String,
    footerLeft: String,
    footerRight: String?,
    modifier: Modifier = Modifier,
    showChevron: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        color = SellerMetricCardBg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(92.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SellerMetricIconBg)
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    color = SellerMutedText,
                    fontSize = 9.sp,
                    maxLines = 2,
                    lineHeight = 11.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = valueText,
                color = SellerNavyText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = footerLeft,
                    color = SellerMutedText,
                    fontSize = 9.sp
                )
                if (footerRight != null) {
                    Text(
                        text = footerRight,
                        color = SellerGrowthGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (showChevron) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = SellerMutedText,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerDashboardSectionHeader(
    title: String,
    onViewAllClick: () -> Unit,
    viewAllTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = SellerNavyText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onViewAllClick)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag(viewAllTag)
        ) {
            Text(
                text = "Xem tất cả",
                color = Color(0xFF0875FF),
                fontSize = 11.sp
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color(0xFF0875FF),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Profile Setup - 360dp")
@Composable
private fun PreviewProfileSetup() {
    val state = ProfileUiState(
        isGuest = false,
        activeUid = "uid_01",
        isEditingProfile = true
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        ProfileScreenContent(
            uiState = state,
            onLoginClick = {},
            onRetryLoad = {},
            onStartEdit = {},
            onCancelEdit = {},
            onEditNameChange = {},
            onEditSchoolChange = {},
            onEditFacultyChange = {},
            onPickAvatar = {},
            onSaveProfile = {},
            onSwitchMode = {},
            onRequestSellerEnrollment = {},
            onDismissEnrollmentNotice = {},
            onOpenPurchasedLibrary = {},
            onOpenTransactionHistory = {},
            onOpenSellerManagement = {},
            onOpenSellerUpload = {},
            onOpenPublicSellerProfile = {},
            onSignOut = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 860, name = "Buyer Profile - 412dp")
@Composable
private fun PreviewBuyerProfile() {
    val state = ProfileUiState(
        isGuest = false,
        activeUid = "uid_01",
        displayName = "Đặng Lâm Nguyên",
        school = "Trường đại học Giao thông vận tải TPHCM",
        faculty = "Khoa CNTT",
        currentMode = UniAppMode.BUYER,
        sellerEnabled = true
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        ProfileScreenContent(
            uiState = state,
            onLoginClick = {},
            onRetryLoad = {},
            onStartEdit = {},
            onCancelEdit = {},
            onEditNameChange = {},
            onEditSchoolChange = {},
            onEditFacultyChange = {},
            onPickAvatar = {},
            onSaveProfile = {},
            onSwitchMode = {},
            onRequestSellerEnrollment = {},
            onDismissEnrollmentNotice = {},
            onOpenPurchasedLibrary = {},
            onOpenTransactionHistory = {},
            onOpenSellerManagement = {},
            onOpenSellerUpload = {},
            onOpenPublicSellerProfile = {},
            onSignOut = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 890, name = "Seller Dashboard - 412dp")
@Composable
private fun PreviewSellerDashboard() {
    val state = ProfileUiState(
        isGuest = false,
        activeUid = "uid_01",
        displayName = "Đặng Lâm Nguyên",
        school = "Trường đại học Giao thông vận tải TPHCM",
        faculty = "Khoa CNTT",
        currentMode = UniAppMode.SELLER,
        sellerEnabled = true
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        ProfileScreenContent(
            uiState = state,
            onLoginClick = {},
            onRetryLoad = {},
            onStartEdit = {},
            onCancelEdit = {},
            onEditNameChange = {},
            onEditSchoolChange = {},
            onEditFacultyChange = {},
            onPickAvatar = {},
            onSaveProfile = {},
            onSwitchMode = {},
            onRequestSellerEnrollment = {},
            onDismissEnrollmentNotice = {},
            onOpenPurchasedLibrary = {},
            onOpenTransactionHistory = {},
            onOpenSellerManagement = {},
            onOpenSellerUpload = {},
            onOpenPublicSellerProfile = {},
            onSignOut = {},
            onBottomNavSelected = {}
        )
    }
}
