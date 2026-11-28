package vn.unishare.feature.payment.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import vn.unishare.R
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Màn hình Checkout (Stateful): Lắng nghe State và Event từ ViewModel
 */
@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel,
    onNavigateToReader: (docId: String) -> Unit,
    onNavigateToAuth: (returnTarget: String) -> Unit,
    onNavigateBack: () -> Unit,
    onShowSnackbar: (message: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val orderId = uiState.order?.orderId
    var openedOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(orderId, uiState.status, lifecycleState) {
        if (orderId != null && openedOrderId != orderId && uiState.status == OrderStatus.PAID &&
            lifecycleState == Lifecycle.State.RESUMED
        ) {
            delay(1400)
            openedOrderId = orderId
            onNavigateToReader(viewModel.docId)
        }
    }

    // Quản lý polling theo vòng đời màn hình: Tự động tải lại khi Resume và dừng khi Pause
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    // Lắng nghe sự kiện một lần (One-shot Events)
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is CheckoutEvent.NavigateToReader -> {
                    if (openedOrderId != viewModel.uiState.value.order?.orderId) {
                        openedOrderId = viewModel.uiState.value.order?.orderId
                        onNavigateToReader(event.docId)
                    }
                }
                is CheckoutEvent.NavigateToAuth -> onNavigateToAuth(event.returnTarget)
                is CheckoutEvent.NavigateBack -> onNavigateBack()
                is CheckoutEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                    onShowSnackbar(event.message)
                }
            }
        }
    }

    CheckoutScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onCopyField = { label, text ->
            clipboardManager.setText(AnnotatedString(text))
            viewModel.copyField(label, text)
        },
        onManualRefresh = viewModel::manualRefresh,
        onOpenReader = viewModel::onOpenReaderClicked,
        onBack = viewModel::onBackClicked
    )
}

/**
 * Giao diện Checkout (Stateless): Hiển thị theo trạng thái đơn hàng
 */
@Composable
fun CheckoutScreenContent(
    uiState: CheckoutUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onCopyField: (label: String, text: String) -> Unit,
    onManualRefresh: () -> Unit,
    onOpenReader: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandGreen = Color(0xFF2E7D32)
    val dividerColor = Color(0xFFE2E6ED)
    val textPrimary = Color(0xFF1F2937)
    val textSecondary = Color(0xFF667085)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            // Thanh tiêu đề TopBar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("btn_checkout_back")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Quay lại",
                        tint = textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thanh toán tài liệu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }
        }
    ) { innerPadding ->
        // Trạng thái đang tải dữ liệu lần đầu
        if (uiState.isLoading && uiState.order == null) {
            UiFeedback(state = UiFeedbackState.Loading, modifier = Modifier.padding(innerPadding))
            return@Scaffold
        }

        // Lỗi mất mạng lần đầu khi chưa có đơn hàng
        if (uiState.networkError != null && uiState.order == null) {
            UiFeedback(
                state = UiFeedbackState.Error(
                    message = uiState.networkError
                ),
                onRetry = onManualRefresh,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        // Lỗi nghiệp vụ không có đơn hàng
        if (uiState.businessError != null && uiState.order == null) {
            UiFeedback(
                state = UiFeedbackState.Error(
                    message = uiState.businessError
                ),
                onRetry = onManualRefresh,
                modifier = Modifier.padding(innerPadding)
            )
            return@Scaffold
        }

        val order = uiState.order ?: return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Banner cảnh báo lỗi mạng (Tách biệt khỏi trạng thái đơn)
            if (uiState.networkError != null) {
                Surface(
                    color = Color(0xFFFFFBEB),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_network_error")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.networkError,
                            color = Color(0xFF92400E),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onManualRefresh) {
                            Text("Thử lại", color = brandGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. Thẻ tóm tắt đơn hàng
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                border = BorderStroke(1.dp, dividerColor),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_text),
                            contentDescription = null,
                            tint = brandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = order.documentTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    }
                    Text(
                        text = "Mã đơn: ${order.orderId}",
                        fontSize = 12.sp,
                        color = textSecondary
                    )
                    HorizontalDivider(color = dividerColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Số tiền cần thanh toán:", fontSize = 13.sp, color = textSecondary)
                        Text(
                            text = "${formatVnd(order.amountVnd)} đ",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandGreen
                        )
                    }
                }
            }

            // 3. Hiển thị nội dung theo trạng thái đơn hàng
            when (uiState.status) {
                OrderStatus.PENDING -> {
                    // CHỈ hiển thị thông tin ngân hàng khi PENDING và còn hạn
                    val bankInfo = order.transferInfo
                    if (bankInfo != null && uiState.remainingSeconds > 0L) {
                        BankTransferSection(
                            transferInfo = bankInfo,
                            remainingSeconds = uiState.remainingSeconds,
                            isPolling = uiState.isPolling,
                            onCopyField = onCopyField,
                            onManualRefresh = onManualRefresh
                        )
                    } else {
                        Text(
                            text = "Chưa có thông tin chuyển khoản hợp lệ. Vui lòng không chuyển tiền và thử lại sau.",
                            color = Color(0xFFB45309),
                            fontSize = 13.sp,
                            modifier = Modifier.testTag("text_bank_unavailable")
                        )
                    }
                }
                OrderStatus.PAID -> {
                    // Thành công: Ẩn tài khoản ngân hàng, hiện nút mở đọc PDF
                    PaidSuccessSection(onOpenReader = onOpenReader)
                }
                OrderStatus.EXPIRED -> {
                    // Hết hạn: Ẩn tài khoản ngân hàng, hướng dẫn không chuyển tiền thêm
                    ExpiredSection(onRetry = onManualRefresh)
                }
                OrderStatus.REVIEW_REQUIRED -> {
                    // Đối soát: Hướng dẫn chờ kiểm tra, không chuyển tiền lại
                    ReviewRequiredSection()
                }
                OrderStatus.FAILED -> {
                    // Thất bại / Lỗi nghiệp vụ
                    FailedSection(errorMessage = uiState.businessError, onBack = onBack)
                }
            }
        }
    }
}

/**
 * Khối chỉ dẫn chuyển khoản ngân hàng (Chỉ hiển thị khi đơn PENDING)
 */
@Composable
private fun BankTransferSection(
    transferInfo: BankTransferInfo,
    remainingSeconds: Long,
    isPolling: Boolean,
    onCopyField: (label: String, text: String) -> Unit,
    onManualRefresh: () -> Unit
) {
    val brandGreen = Color(0xFF2E7D32)
    val textPrimary = Color(0xFF1F2937)
    val textSecondary = Color(0xFF667085)
    val dividerColor = Color(0xFFE2E6ED)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, brandGreen.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_bank_instructions")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Đồng hồ đếm ngược
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Thông tin chuyển khoản",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
                Text(
                    text = formatCountdown(remainingSeconds),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (remainingSeconds < 180) Color(0xFFD32F2F) else brandGreen,
                    modifier = Modifier.testTag("text_countdown_timer")
                )
            }

            Text(
                text = "Quét QR hoặc chuyển tiền chính xác theo thông tin dưới đây:",
                fontSize = 12.sp,
                color = textSecondary
            )

            HorizontalDivider(color = dividerColor)

            VietQrSection(info = transferInfo)


            // Dòng 1: Ngân hàng nhận
            TransferInfoRow(
                label = "Ngân hàng",
                value = "${transferInfo.bankName} (${transferInfo.bankCode})"
            )

            // Dòng 2: Số tài khoản + Nút sao chép
            TransferInfoRowWithCopy(
                label = "Số tài khoản",
                value = transferInfo.accountNumber,
                copyTag = "btn_copy_account",
                onCopy = { onCopyField("Số tài khoản", transferInfo.accountNumber) }
            )

            // Dòng 3: Tên chủ tài khoản
            TransferInfoRow(
                label = "Chủ tài khoản",
                value = transferInfo.accountHolder
            )

            // Dòng 4: Số tiền + Nút sao chép
            TransferInfoRowWithCopy(
                label = "Số tiền",
                value = "${formatVnd(transferInfo.amountVnd)} đ",
                copyTag = "btn_copy_amount",
                onCopy = { onCopyField("Số tiền", transferInfo.amountVnd.toString()) }
            )

            // Dòng 5: Nội dung chuyển khoản (bắt buộc đúng paymentCode)
            TransferInfoRowWithCopy(
                label = "Nội dung chuyển khoản",
                value = VietQr.transferContent(transferInfo),
                valueColor = Color(0xFFD32F2F),
                copyTag = "btn_copy_code",
                onCopy = { onCopyField("Nội dung chuyển khoản", VietQr.transferContent(transferInfo)) }
            )

            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Lưu ý: Bắt buộc ghi đúng mã nội dung '${VietQr.transferContent(transferInfo)}' để hệ thống tự động duyệt thanh toán.",
                    color = Color(0xFF991B1B),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Nút bấm kiểm tra trạng thái
            UniButton(
                text = if (isPolling) "Đang kiểm tra giao dịch..." else "Tôi đã chuyển tiền / Kiểm tra ngay",
                onClick = onManualRefresh,
                isLoading = isPolling,
                modifier = Modifier.testTag("btn_refresh_status")
            )
        }
    }
}

/**
 * Hàng hiển thị thông tin kèm nút sao chép
 */
@Composable
private fun TransferInfoRowWithCopy(
    label: String,
    value: String,
    copyTag: String,
    valueColor: Color = Color(0xFF1F2937),
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF667085))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
        OutlinedButton(
            onClick = onCopy,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier
                .height(32.dp)
                .testTag(copyTag)
        ) {
            Text("Sao chép", fontSize = 11.sp)
        }
    }
}

/**
 * Hàng hiển thị thông tin đơn giản
 */
@Composable
private fun TransferInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 11.sp, color = Color(0xFF667085))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1F2937))
    }
}

/**
 * Trạng thái PAID: Thành công
 */
@Composable
private fun PaidSuccessSection(onOpenReader: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = BorderStroke(1.dp, Color(0xFF86EFAC)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_paid_success")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Thanh toán thành công!",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF166534)
            )
            Text(
                text = "Hệ thống đã nhận được tiền và cấp quyền đọc tài liệu cho tài khoản của bạn.",
                fontSize = 13.sp,
                color = Color(0xFF15803D),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            UniButton(
                text = "Đọc tài liệu ngay",
                onClick = onOpenReader,
                modifier = Modifier.testTag("btn_open_reader")
            )
        }
    }
}

/**
 * Trạng thái EXPIRED: Hết hạn
 */
@Composable
private fun ExpiredSection(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        border = BorderStroke(1.dp, Color(0xFFFDBA74)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_expired")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                tint = Color(0xFFC2410C),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Đơn hàng đã hết hạn",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9A3412)
            )
            Text(
                text = "Đơn hàng đã quá hạn 15 phút. Nếu đã chuyển tiền, hãy kiểm tra trạng thái hoặc liên hệ hỗ trợ trước khi chuyển thêm.",
                fontSize = 12.sp,
                color = Color(0xFFC2410C),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            UniButton(
                text = "Kiểm tra trạng thái giao dịch",
                onClick = onRetry,
                modifier = Modifier.testTag("btn_retry_expired")
            )
        }
    }
}

/**
 * Trạng thái REVIEW_REQUIRED: Đang đối soát
 */
@Composable
private fun ReviewRequiredSection() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
        border = BorderStroke(1.dp, Color(0xFF93C5FD)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("section_review_required")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_warning),
                contentDescription = null,
                tint = Color(0xFF1D4ED8),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Đơn hàng đang chờ đối soát",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E40AF)
            )
            Text(
                text = "Giao dịch đã được ghi nhận nhưng cần nhân viên kiểm tra xác minh. Vui lòng không chuyển thêm tiền.",
                fontSize = 12.sp,
                color = Color(0xFF1D4ED8),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Trạng thái FAILED: Lỗi nghiệp vụ
 */
@Composable
private fun FailedSection(errorMessage: String?, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = errorMessage ?: "Không thể thực hiện thanh toán.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        UniButton(text = "Quay lại", onClick = onBack)
    }
}

/**
 * Hàm phụ trợ: Định dạng số tiền VND
 */
private fun formatVnd(amount: Long): String {
    return NumberFormat.getNumberInstance(Locale("vi", "VN")).format(amount)
}

/**
 * Hàm phụ trợ: Định dạng số giây thành chuỗi mm:ss
 */
private fun formatCountdown(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

// ============================================================================
// PREVIEWS CHO JETPACK COMPOSE
// ============================================================================

@Preview(name = "1. Trạng thái PENDING (Chờ chuyển khoản)", showBackground = true)
@Composable
private fun PreviewCheckoutPending() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        CheckoutScreenContent(
            uiState = CheckoutUiState(
                isLoading = false,
                status = OrderStatus.PENDING,
                remainingSeconds = 854,
                order = OrderUiModel(
                    orderId = "ord_demo_101",
                    docId = "doc_ctdl",
                    documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật",
                    amountVnd = 50_000L,
                    status = OrderStatus.PENDING,
                    paymentCode = "UNI8A3F12",
                    createdAtUtc = "2026-11-19T10:00:00Z",
                    expiresAtUtc = "2026-11-19T10:15:00Z",
                    transferInfo = BankTransferInfo(
                        bankName = "Ngân hàng Quân đội",
                        bankCode = "MB",
                        accountNumber = "0345678999",
                        accountHolder = "UNISHARE SYSTEM",
                        amountVnd = 50_000L,
                        paymentCode = "UNI8A3F12"
                    )
                )
            ),
            onCopyField = { _, _ -> },
            onManualRefresh = {},
            onOpenReader = {},
            onBack = {}
        )
    }
}

@Preview(name = "2. Trạng thái PAID (Thành công)", showBackground = true)
@Composable
private fun PreviewCheckoutPaid() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        CheckoutScreenContent(
            uiState = CheckoutUiState(
                isLoading = false,
                status = OrderStatus.PAID,
                order = OrderUiModel(
                    orderId = "ord_demo_101",
                    docId = "doc_ctdl",
                    documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật",
                    amountVnd = 50_000L,
                    status = OrderStatus.PAID,
                    paymentCode = "UNI8A3F12",
                    createdAtUtc = "2026-11-19T10:00:00Z",
                    expiresAtUtc = "2026-11-19T10:15:00Z",
                    transferInfo = BankTransferInfo()
                )
            ),
            onCopyField = { _, _ -> },
            onManualRefresh = {},
            onOpenReader = {},
            onBack = {}
        )
    }
}

