package vn.unishare.feature.seller.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import java.util.UUID

private val SellerBarBlue = Color(0xFF0955AE)
private val SellerPrimaryBlue = Color(0xFF0863CE)
private val SellerLineDone = Color(0xFF8EAFFF)
private val SellerLineIdle = Color(0xFFD5DDEE)
private val SellerStepIdleBg = Color(0xFFF3F5F9)
private val SellerBorderColor = Color(0xFFDCE2ED)
private val SellerSoftSurface = Color(0xFFE9EDF7)
private val SellerInfoBoxBg = Color(0xFFE8F2FF)
private val SellerInfoBoxText = Color(0xFF4D86D3)
private val SellerDashBlue = Color(0xFF73C1FA)
private val SellerNavyText = Color(0xFF18243B)
private val SellerMutedText = Color(0xFF6E7C96)
private val SellerErrorRed = Color(0xFFF1454D)
private val SellerSuccessGreen = Color(0xFF008D6D)
private val SellerSuccessBg = Color(0xFFD9F7EF)

// Màn hình đăng bán tài liệu 3 bước: Ảnh preview -> Upload PDF -> Thông tin
@Composable
fun UploadWizardScreen(
    viewModel: UploadWizardViewModel,
    onClose: () -> Unit,
    onPublishSuccessNavigate: (SellerDocumentItem) -> Unit = {},
    onPickPreviewImageOverride: (() -> Unit)? = null,
    onCapturePreviewImageOverride: (() -> Unit)? = null,
    onPickPdfOverride: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.wizardState.collectAsState()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val resolved = resolveSelectedPreviewImage(context, uri)
            viewModel.addPreviewImage(resolved)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val resolved = resolveSelectedPdfFile(context, uri)
            viewModel.selectPdfFile(resolved)
        }
    }

    val launchImagePicker: () -> Unit = onPickPreviewImageOverride ?: {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
    val launchCameraCapture: () -> Unit = onCapturePreviewImageOverride ?: launchImagePicker
    val launchPdfPicker: () -> Unit = onPickPdfOverride ?: {
        pdfPickerLauncher.launch(arrayOf(ALLOWED_PDF_MIME))
    }

    val handleBack: () -> Unit = {
        if (!state.isBusy) {
            if (state.currentStep == UploadWizardStep.PREVIEW_IMAGES) {
                onClose()
            } else {
                viewModel.goToPreviousStep()
            }
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    UploadWizardContent(
        state = state,
        onBackClick = handleBack,
        onCloseClick = {
            if (!state.isBusy) onClose()
        },
        onToggleFlash = viewModel::toggleFlash,
        onToggleCameraFacing = viewModel::toggleCameraFacing,
        onSelectPreviewIndex = viewModel::selectPreviewImage,
        onRemovePreviewImage = viewModel::removePreviewImage,
        onPickPreviewImage = launchImagePicker,
        onCapturePreviewImage = launchCameraCapture,
        onStep1Next = { viewModel.validateStep1AndNext() },
        onPickPdf = launchPdfPicker,
        onRemovePdf = viewModel::clearPdfFile,
        onStep2Back = viewModel::goToPreviousStep,
        onStep2Next = { viewModel.validateStep2AndNext() },
        onTitleChange = viewModel::onTitleChange,
        onCategoryChange = viewModel::onCategoryChange,
        onSubjectChange = viewModel::onSubjectChange,
        onSchoolChange = viewModel::onSchoolChange,
        onPriceChange = viewModel::onPriceChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onOwnershipConsentChange = viewModel::onOwnershipConsentChange,
        onStep3Back = viewModel::goToPreviousStep,
        onPublishClick = viewModel::submitPublish,
        onCancelUploadClick = viewModel::cancelUpload,
        onRetryPublishClick = viewModel::retryPublish,
        onPublishedDoneClick = { doc ->
            onPublishSuccessNavigate(doc)
        },
        modifier = modifier
    )
}

@Composable
fun UploadWizardContent(
    state: UploadWizardUiState,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onSelectPreviewIndex: (Int) -> Unit,
    onRemovePreviewImage: (String) -> Unit,
    onPickPreviewImage: () -> Unit,
    onCapturePreviewImage: () -> Unit,
    onStep1Next: () -> Unit,
    onPickPdf: () -> Unit,
    onRemovePdf: () -> Unit,
    onStep2Back: () -> Unit,
    onStep2Next: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onSchoolChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOwnershipConsentChange: (Boolean) -> Unit,
    onStep3Back: () -> Unit,
    onPublishClick: () -> Unit,
    onCancelUploadClick: () -> Unit,
    onRetryPublishClick: () -> Unit,
    onPublishedDoneClick: (SellerDocumentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                WizardTopAppBar(
                    onBackClick = onBackClick,
                    onCloseClick = onCloseClick,
                    actionsEnabled = !state.isBusy
                )
                WizardStepperBar(currentStep = state.currentStep)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when (state.currentStep) {
                UploadWizardStep.PREVIEW_IMAGES -> {
                    Step1PreviewImagesBody(
                        state = state,
                        onToggleFlash = onToggleFlash,
                        onToggleCameraFacing = onToggleCameraFacing,
                        onSelectPreviewIndex = onSelectPreviewIndex,
                        onRemovePreviewImage = onRemovePreviewImage,
                        onPickPreviewImage = onPickPreviewImage,
                        onCapturePreviewImage = onCapturePreviewImage,
                        onNextClick = onStep1Next
                    )
                }

                UploadWizardStep.UPLOAD_PDF -> {
                    Step2UploadPdfBody(
                        state = state,
                        onPickPdf = onPickPdf,
                        onRemovePdf = onRemovePdf,
                        onBackClick = onStep2Back,
                        onNextClick = onStep2Next
                    )
                }

                UploadWizardStep.METADATA -> {
                    Step3MetadataBody(
                        state = state,
                        onTitleChange = onTitleChange,
                        onCategoryChange = onCategoryChange,
                        onSubjectChange = onSubjectChange,
                        onSchoolChange = onSchoolChange,
                        onPriceChange = onPriceChange,
                        onDescriptionChange = onDescriptionChange,
                        onOwnershipConsentChange = onOwnershipConsentChange,
                        onBackClick = onStep3Back,
                        onPublishClick = onPublishClick,
                        onCancelUploadClick = onCancelUploadClick,
                        onRetryPublishClick = onRetryPublishClick,
                        onPublishedDoneClick = onPublishedDoneClick
                    )
                }
            }
        }
    }
}

@Composable
private fun WizardTopAppBar(
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    actionsEnabled: Boolean
) {
    Surface(
        color = SellerBarBlue,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                enabled = actionsEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_wizard_top_back")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left),
                    contentDescription = "Quay lại",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "Đăng bán tài liệu",
                color = Color.White,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = onCloseClick,
                enabled = actionsEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_wizard_close")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Đóng",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// Thanh tiến trình 3 bước của trình hướng dẫn đăng bán
@Composable
private fun WizardStepperBar(currentStep: UploadWizardStep) {
    val steps = UploadWizardStep.entries
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("wizard_stepper")
    ) {
        // Đường nối ngang phía sau 3 nút tròn
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 44.dp, vertical = 13.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        if (currentStep.stepNumber >= 2) SellerLineDone else SellerLineIdle
                    )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        if (currentStep.stepNumber >= 3) SellerLineDone else SellerLineIdle
                    )
            )
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            steps.forEach { step ->
                val isCompleted = step.stepNumber < currentStep.stepNumber
                val isActive = step == currentStep
                val circleBg = if (isCompleted || isActive) SellerPrimaryBlue else SellerStepIdleBg
                val circleBorder = if (isCompleted || isActive) SellerPrimaryBlue else SellerBorderColor
                val labelColor = if (isCompleted || isActive) SellerPrimaryBlue else SellerMutedText

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(circleBg)
                            .border(1.5.dp, circleBorder, CircleShape)
                    ) {
                        Text(
                            text = if (isCompleted) "✓" else step.stepNumber.toString(),
                            color = if (isCompleted || isActive) Color.White else SellerMutedText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = step.label,
                        color = labelColor,
                        fontSize = 12.sp,
                        fontWeight = if (isCompleted || isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// --- Bước 1: Chụp / chọn ảnh preview ---

@Composable
private fun Step1PreviewImagesBody(
    state: UploadWizardUiState,
    onToggleFlash: () -> Unit,
    onToggleCameraFacing: () -> Unit,
    onSelectPreviewIndex: (Int) -> Unit,
    onRemovePreviewImage: (String) -> Unit,
    onPickPreviewImage: () -> Unit,
    onCapturePreviewImage: () -> Unit,
    onNextClick: () -> Unit
) {
    val activePreview = state.previewImages.getOrNull(state.selectedPreviewIndex)

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Khung xem trước ảnh đã chọn / chụp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2B3445), Color(0xFF1A2230))
                    )
                )
                .testTag("step1_preview_frame")
        ) {
            if (activePreview != null) {
                PreviewPlaceholderSheet(
                    image = activePreview,
                    index = state.selectedPreviewIndex + 1,
                    total = state.previewImages.size,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_camera),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Chưa có ảnh xem trước",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chọn từ Thư viện hoặc nhấn nút chụp bên dưới (tối đa $MAX_PREVIEW_IMAGES ảnh)",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Nút bật/tắt flash góc trên trái
            Surface(
                color = if (state.isFlashEnabled) SellerPrimaryBlue else Color(0xB3282720),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFlash)
                    .testTag("btn_toggle_flash")
            ) {
                Text(
                    text = if (state.isFlashEnabled) "Flash: Bật" else "Flash",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            // Nút đổi camera góc trên phải
            Surface(
                color = Color(0xA6282720),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleCameraFacing)
                    .testTag("btn_toggle_camera")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_switch_role),
                        contentDescription = "Đổi camera",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Nhãn hướng dẫn phía dưới khung
            Surface(
                color = Color(0xCC4B5367),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = "Chụp ảnh rõ nét để có preview tốt nhất",
                    color = Color.White,
                    fontSize = 10.5.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Dải ảnh đã chọn + ô dấu cộng thêm ảnh
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = state.previewImages,
                key = { _, item -> item.id }
            ) { index, img ->
                val isSelected = index == state.selectedPreviewIndex
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) SellerPrimaryBlue else SellerBorderColor,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .background(SellerSoftSurface)
                        .clickable { onSelectPreviewIndex(index) }
                        .testTag("preview_thumb_$index")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "Trang ${index + 1}",
                            color = SellerNavyText,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = img.displayName,
                            color = SellerMutedText,
                            fontSize = 8.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Nút xóa ảnh góc trên phải
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC18243B))
                            .clickable { onRemovePreviewImage(img.id) }
                            .testTag("btn_remove_preview_$index")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Xóa ảnh ${index + 1}",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (state.previewImages.size < MAX_PREVIEW_IMAGES) {
                item(key = "add_preview_slot") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SellerSoftSurface)
                            .clickable(onClick = onPickPreviewImage)
                            .testTag("btn_add_preview_tile")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = "Thêm ảnh preview",
                            tint = SellerPrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (state.fieldErrors.previewError != null) {
            FieldErrorNotice(
                message = state.fieldErrors.previewError,
                modifier = Modifier.testTag("error_step1_preview")
            )
        }

        // Hàng điều khiển: Thư viện - Chụp ảnh - Tiếp theo
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onPickPreviewImage)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("btn_pick_gallery_image")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(SellerSoftSurface)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder_open),
                        contentDescription = "Chọn ảnh từ thư viện",
                        tint = SellerPrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Thư viện",
                    color = SellerMutedText,
                    fontSize = 12.sp
                )
            }

            // Nút tròn chụp ảnh ở giữa
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color(0xFFC5D9F5), CircleShape)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(SellerPrimaryBlue)
                    .clickable(onClick = onCapturePreviewImage)
                    .testTag("btn_capture_preview")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_camera),
                    contentDescription = "Chụp ảnh preview",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Button(
                onClick = onNextClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SellerPrimaryBlue,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_step1_next")
            ) {
                Text(
                    text = "Tiếp theo",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PreviewPlaceholderSheet(
    image: SelectedPreviewImage,
    index: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDBEAFE)))
            )
            .padding(24.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(220.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ảnh preview $index/$total",
                        color = SellerPrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatFileSize(image.sizeBytes),
                        color = SellerMutedText,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = image.displayName,
                    color = SellerNavyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SellerSoftSurface)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SellerSoftSurface)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(SellerSoftSurface)
                )
            }
        }
    }
}

// --- Bước 2: Chọn tệp PDF ---

@Composable
private fun Step2UploadPdfBody(
    state: UploadWizardUiState,
    onPickPdf: () -> Unit,
    onRemovePdf: () -> Unit,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Vùng viền nét đứt chọn file PDF
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .drawBehind {
                    drawRoundRect(
                        color = SellerDashBlue,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        ),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }
                .clickable(onClick = onPickPdf)
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(SellerInfoBoxBg)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder_open),
                    contentDescription = null,
                    tint = SellerPrimaryBlue,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = "Chọn file PDF",
                color = SellerNavyText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Kéo thả hoặc nhấn để chọn file",
                color = SellerMutedText,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "(PDF, tối đa 50MB)",
                color = SellerMutedText,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onPickPdf,
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SellerPrimaryBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .height(44.dp)
                    .testTag("btn_pick_pdf")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_folder_open),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Chọn file từ thiết bị",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (state.fieldErrors.pdfError != null) {
            FieldErrorNotice(
                message = state.fieldErrors.pdfError,
                modifier = Modifier.testTag("error_step2_pdf")
            )
        }

        // Thẻ hiển thị tệp PDF đã chọn
        val pdf = state.selectedPdf
        if (pdf != null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "File đã chọn",
                    color = SellerNavyText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(9.dp),
                    border = BorderStroke(1.dp, SellerBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("selected_pdf_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .width(34.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SellerErrorRed)
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "PDF",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = pdf.displayName,
                                color = SellerNavyText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatFileSize(pdf.sizeBytes),
                                color = SellerMutedText,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = onRemovePdf,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("btn_remove_pdf")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Xóa file PDF đã chọn",
                                tint = SellerMutedText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Hộp thông báo bảo mật tài liệu
        Surface(
            color = SellerInfoBoxBg,
            shape = RoundedCornerShape(9.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(14.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lock_keyhole),
                    contentDescription = null,
                    tint = SellerPrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "File PDF sẽ được xử lý và bảo mật. Người mua chỉ có thể xem trực tiếp trên ứng dụng, không thể tải về hoặc chụp màn hình.",
                    color = SellerInfoBoxText,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hai nút điều hướng Quay lại / Tiếp theo
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            OutlinedButton(
                onClick = onBackClick,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, SellerPrimaryBlue),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_step2_back")
            ) {
                Text(
                    text = "Quay lại",
                    color = SellerPrimaryBlue,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onNextClick,
                shape = RoundedCornerShape(7.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SellerPrimaryBlue,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_step2_next")
            ) {
                Text(
                    text = "Tiếp theo",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// --- Bước 3: Nhập thông tin tài liệu & đăng bán ---

@Composable
private fun Step3MetadataBody(
    state: UploadWizardUiState,
    onTitleChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onSchoolChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onOwnershipConsentChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onPublishClick: () -> Unit,
    onCancelUploadClick: () -> Unit,
    onRetryPublishClick: () -> Unit,
    onPublishedDoneClick: (SellerDocumentItem) -> Unit
) {
    val errors = state.fieldErrors
    val fieldsEnabled = !state.isBusy && state.uploadPublishState !is UploadPublishState.Published

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Thông tin tài liệu",
            color = SellerNavyText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        // Tiêu đề
        LabeledFieldBlock(label = "Tiêu đề", required = true, errorText = errors.titleError) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                enabled = fieldsEnabled,
                isError = errors.titleError != null,
                placeholder = {
                    Text("Ví dụ: Tóm tắt Kinh tế vi mô - Chương 1 đến 5", color = SellerMutedText, fontSize = 13.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(7.dp),
                colors = sellerFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_seller_title")
            )
        }

        // Danh mục
        LabeledFieldBlock(label = "Danh mục", required = true, errorText = errors.categoryError) {
            SellerDropdownField(
                selectedValue = state.category,
                placeholder = "Chọn danh mục...",
                options = UploadWizardUiState.CATEGORY_OPTIONS,
                enabled = fieldsEnabled,
                leadingIconRes = R.drawable.ic_file_text,
                onOptionSelected = onCategoryChange,
                modifier = Modifier.testTag("dropdown_seller_category")
            )
        }

        // Môn học
        LabeledFieldBlock(label = "Môn học", required = true, errorText = errors.subjectError) {
            SellerDropdownField(
                selectedValue = state.subject,
                placeholder = "Chọn môn học...",
                options = UploadWizardUiState.SUBJECT_OPTIONS,
                enabled = fieldsEnabled,
                leadingIconRes = null,
                onOptionSelected = onSubjectChange,
                modifier = Modifier.testTag("dropdown_seller_subject")
            )
        }

        // Trường áp dụng
        LabeledFieldBlock(label = "Trường áp dụng", required = true, errorText = errors.schoolError) {
            SellerDropdownField(
                selectedValue = state.school,
                placeholder = "Chọn trường áp dụng...",
                options = UploadWizardUiState.SCHOOL_OPTIONS,
                enabled = fieldsEnabled,
                leadingIconRes = null,
                onOptionSelected = onSchoolChange,
                modifier = Modifier.testTag("dropdown_seller_school")
            )
        }

        // Giá bán
        LabeledFieldBlock(label = "Giá bán (VNĐ)", required = true, errorText = errors.priceError) {
            OutlinedTextField(
                value = state.priceText,
                onValueChange = onPriceChange,
                enabled = fieldsEnabled,
                isError = errors.priceError != null,
                placeholder = { Text("15000", color = SellerMutedText, fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(7.dp),
                colors = sellerFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_seller_price")
            )
            Text(
                text = "Từ ${formatSellerPriceVnd(MIN_SELLER_PRICE_VND)} - ${formatSellerPriceVnd(MAX_SELLER_PRICE_VND)}",
                color = SellerMutedText,
                fontSize = 11.5.sp
            )
        }

        // Mô tả
        LabeledFieldBlock(label = "Mô tả (tùy chọn)", required = false, errorText = errors.descriptionError) {
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                enabled = fieldsEnabled,
                placeholder = {
                    Text(
                        "Tóm tắt nội dung chính, phù hợp cho ôn thi giữa kỳ hoặc cuối kỳ...",
                        color = SellerMutedText,
                        fontSize = 13.sp
                    )
                },
                minLines = 3,
                maxLines = 4,
                shape = RoundedCornerShape(7.dp),
                colors = sellerFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_seller_description")
            )
            Text(
                text = "${state.description.length}/$MAX_DESCRIPTION_CHARS",
                color = SellerMutedText,
                fontSize = 10.5.sp,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Xác nhận quyền đăng bán
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = fieldsEnabled) {
                        onOwnershipConsentChange(!state.hasOwnershipConsent)
                    }
                    .padding(vertical = 4.dp)
                    .testTag("checkbox_ownership_consent")
            ) {
                Checkbox(
                    checked = state.hasOwnershipConsent,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = SellerPrimaryBlue)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tôi xác nhận có quyền hợp lệ để đăng bán tài liệu này trên UniShare.",
                    color = SellerNavyText,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            if (errors.consentError != null) {
                FieldErrorNotice(
                    message = errors.consentError,
                    modifier = Modifier.testTag("error_ownership_consent")
                )
            }
        }

        // Thẻ tiến trình tải lên / lỗi / hủy / thành công
        UploadPublishStatusCard(
            status = state.uploadPublishState,
            onCancelUploadClick = onCancelUploadClick,
            onRetryPublishClick = onRetryPublishClick,
            onPublishedDoneClick = onPublishedDoneClick
        )

        // Thanh hành động cuối bước 3
        if (state.uploadPublishState !is UploadPublishState.Published) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 14.dp)
            ) {
                OutlinedButton(
                    onClick = onBackClick,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(7.dp),
                    border = BorderStroke(1.dp, SellerPrimaryBlue),
                    modifier = Modifier
                        .weight(0.4f)
                        .height(48.dp)
                        .testTag("btn_step3_back")
                ) {
                    Text(
                        text = "Quay lại",
                        color = SellerPrimaryBlue,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = onPublishClick,
                    enabled = !state.isBusy,
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SellerPrimaryBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .weight(0.6f)
                        .height(48.dp)
                        .testTag("btn_publish_document")
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang xử lý...", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_send),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đăng bán", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadPublishStatusCard(
    status: UploadPublishState,
    onCancelUploadClick: () -> Unit,
    onRetryPublishClick: () -> Unit,
    onPublishedDoneClick: (SellerDocumentItem) -> Unit
) {
    when (status) {
        is UploadPublishState.Idle -> Unit

        is UploadPublishState.CreatingDraft -> {
            Surface(
                color = SellerInfoBoxBg,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_creating_draft")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = SellerPrimaryBlue,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Đang khởi tạo bản nháp tải lên...",
                        color = SellerNavyText,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is UploadPublishState.Uploading -> {
            Surface(
                color = SellerInfoBoxBg,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_uploading")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = status.stageLabel,
                            color = SellerNavyText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${status.progressPercent}%",
                            color = SellerPrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { status.progressPercent / 100f },
                        color = SellerPrimaryBlue,
                        trackColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    OutlinedButton(
                        onClick = onCancelUploadClick,
                        shape = RoundedCornerShape(7.dp),
                        border = BorderStroke(1.dp, SellerErrorRed),
                        modifier = Modifier.testTag("btn_cancel_upload")
                    ) {
                        Text(
                            text = "Hủy tải lên",
                            color = SellerErrorRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        is UploadPublishState.Publishing -> {
            Surface(
                color = SellerInfoBoxBg,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_publishing")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = SellerPrimaryBlue,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Đã tải xong tệp (100%). Đang xác nhận xuất bản tài liệu...",
                        color = SellerNavyText,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is UploadPublishState.Canceled -> {
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_upload_canceled")
            ) {
                Text(
                    text = status.message,
                    color = Color(0xFF9A3412),
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        is UploadPublishState.Failed -> {
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_upload_failed")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = SellerErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = status.message,
                            color = Color(0xFF991B1B),
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (status.canRetry) {
                        Button(
                            onClick = onRetryPublishClick,
                            shape = RoundedCornerShape(7.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SellerPrimaryBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("btn_retry_publish")
                        ) {
                            Text("Thử lại", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        is UploadPublishState.Published -> {
            Surface(
                color = SellerSuccessBg,
                shape = RoundedCornerShape(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_published_success")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(14.dp)
                ) {
                    Text(
                        text = "Đăng bán tài liệu thành công!",
                        color = SellerSuccessGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = status.document.title,
                        color = SellerNavyText,
                        fontSize = 13.sp
                    )
                    Button(
                        onClick = { onPublishedDoneClick(status.document) },
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SellerPrimaryBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("btn_view_seller_documents")
                    ) {
                        Text("Về danh sách đang bán", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledFieldBlock(
    label: String,
    required: Boolean,
    errorText: String?,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = buildAnnotatedString {
                append(label)
                if (required) {
                    withStyle(SpanStyle(color = SellerErrorRed)) {
                        append(" *")
                    }
                }
            },
            color = SellerNavyText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        content()
        if (errorText != null) {
            Text(
                text = errorText,
                color = SellerErrorRed,
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun SellerDropdownField(
    selectedValue: String,
    placeholder: String,
    options: List<String>,
    enabled: Boolean,
    leadingIconRes: Int?,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, SellerBorderColor),
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(7.dp))
                .clickable(enabled = enabled) { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (leadingIconRes != null) {
                    Icon(
                        painter = painterResource(id = leadingIconRes),
                        contentDescription = null,
                        tint = SellerNavyText,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = selectedValue.ifBlank { placeholder },
                    color = if (selectedValue.isBlank()) SellerMutedText else SellerNavyText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_down),
                    contentDescription = null,
                    tint = SellerMutedText,
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

@Composable
private fun FieldErrorNotice(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFEF2F2),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFFECACA)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                tint = SellerErrorRed,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = message,
                color = Color(0xFFB91C1C),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun sellerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SellerPrimaryBlue,
    unfocusedBorderColor = SellerBorderColor,
    errorBorderColor = SellerErrorRed,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    focusedTextColor = SellerNavyText,
    unfocusedTextColor = SellerNavyText
)

private fun resolveSelectedPreviewImage(context: Context, uri: Uri): SelectedPreviewImage {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: "image/jpeg"
    var name = "preview_${System.currentTimeMillis()}.jpg"
    var size = 512_000L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
    return SelectedPreviewImage(
        id = "img_${UUID.randomUUID().toString().take(8)}",
        uriString = uri.toString(),
        displayName = name,
        mimeType = mime,
        sizeBytes = size
    )
}

private fun resolveSelectedPdfFile(context: Context, uri: Uri): SelectedPdfFile {
    val resolver = context.contentResolver
    val mime = resolver.getType(uri) ?: ALLOWED_PDF_MIME
    var name = "tai_lieu.pdf"
    var size = 1_024_000L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
    return SelectedPdfFile(
        uriString = uri.toString(),
        displayName = name,
        mimeType = mime,
        sizeBytes = size
    )
}

@Preview(widthDp = 360, heightDp = 800, name = "Wizard Step 1 - 360dp")
@Composable
private fun PreviewUploadStep1() {
    val sample = UploadWizardUiState(
        currentStep = UploadWizardStep.PREVIEW_IMAGES,
        previewImages = listOf(
            SelectedPreviewImage("1", "content://preview/1", "trang_1.jpg", "image/jpeg", 420_000L),
            SelectedPreviewImage("2", "content://preview/2", "trang_2.jpg", "image/jpeg", 380_000L)
        )
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        UploadWizardContent(
            state = sample,
            onBackClick = {},
            onCloseClick = {},
            onToggleFlash = {},
            onToggleCameraFacing = {},
            onSelectPreviewIndex = {},
            onRemovePreviewImage = {},
            onPickPreviewImage = {},
            onCapturePreviewImage = {},
            onStep1Next = {},
            onPickPdf = {},
            onRemovePdf = {},
            onStep2Back = {},
            onStep2Next = {},
            onTitleChange = {},
            onCategoryChange = {},
            onSubjectChange = {},
            onSchoolChange = {},
            onPriceChange = {},
            onDescriptionChange = {},
            onOwnershipConsentChange = {},
            onStep3Back = {},
            onPublishClick = {},
            onCancelUploadClick = {},
            onRetryPublishClick = {},
            onPublishedDoneClick = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Wizard Step 2 - PDF")
@Composable
private fun PreviewUploadStep2() {
    val sample = UploadWizardUiState(
        currentStep = UploadWizardStep.UPLOAD_PDF,
        selectedPdf = SelectedPdfFile(
            uriString = "content://docs/kinh_te_vi_mo.pdf",
            displayName = "Kinh_te_vi_mo.pdf",
            mimeType = ALLOWED_PDF_MIME,
            sizeBytes = 3_565_158L
        )
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        UploadWizardContent(
            state = sample,
            onBackClick = {},
            onCloseClick = {},
            onToggleFlash = {},
            onToggleCameraFacing = {},
            onSelectPreviewIndex = {},
            onRemovePreviewImage = {},
            onPickPreviewImage = {},
            onCapturePreviewImage = {},
            onStep1Next = {},
            onPickPdf = {},
            onRemovePdf = {},
            onStep2Back = {},
            onStep2Next = {},
            onTitleChange = {},
            onCategoryChange = {},
            onSubjectChange = {},
            onSchoolChange = {},
            onPriceChange = {},
            onDescriptionChange = {},
            onOwnershipConsentChange = {},
            onStep3Back = {},
            onPublishClick = {},
            onCancelUploadClick = {},
            onRetryPublishClick = {},
            onPublishedDoneClick = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Wizard Step 3 - 360dp")
@Preview(widthDp = 412, heightDp = 860, fontScale = 1.5f, name = "Wizard Step 3 - Large Font")
@Composable
private fun PreviewUploadStep3() {
    val sample = UploadWizardUiState(
        currentStep = UploadWizardStep.METADATA,
        title = "Tóm tắt Kinh tế vi mô - Chương 1 đến 5",
        category = "Note tóm tắt",
        subject = "Kinh tế vi mô",
        school = "Đại học Kinh tế Quốc dân",
        priceText = "15000",
        description = "Tóm tắt đầy đủ lý thuyết và ví dụ minh họa. Phù hợp cho ôn thi giữa kỳ và cuối kỳ.",
        hasOwnershipConsent = true
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        UploadWizardContent(
            state = sample,
            onBackClick = {},
            onCloseClick = {},
            onToggleFlash = {},
            onToggleCameraFacing = {},
            onSelectPreviewIndex = {},
            onRemovePreviewImage = {},
            onPickPreviewImage = {},
            onCapturePreviewImage = {},
            onStep1Next = {},
            onPickPdf = {},
            onRemovePdf = {},
            onStep2Back = {},
            onStep2Next = {},
            onTitleChange = {},
            onCategoryChange = {},
            onSubjectChange = {},
            onSchoolChange = {},
            onPriceChange = {},
            onDescriptionChange = {},
            onOwnershipConsentChange = {},
            onStep3Back = {},
            onPublishClick = {},
            onCancelUploadClick = {},
            onRetryPublishClick = {},
            onPublishedDoneClick = {}
        )
    }
}
