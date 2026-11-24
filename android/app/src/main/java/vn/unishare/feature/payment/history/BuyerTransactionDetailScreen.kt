package vn.unishare.feature.payment.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.payment.ui.OrderStatus

private val BrandGreen = Color(0xFF2E7D32)
private val BackgroundColor = Color(0xFFF7F9FB)
private val TextPrimary = Color(0xFF1F2937)
private val TextSecondary = Color(0xFF667085)
private val CardBorderColor = Color(0xFFE2E6ED)
private val ReceiptBgColor = Color(0xFFFFFFFF)

/**
 * ============================================================================
 * MÀN HÌNH CHI TIẾT BIÊN NHẬN GIAO DỊCH TRONG APP (B05)
 * ============================================================================
 *
 * Nguyên tắc thiết kế (Senior Developer & Architecture Principles):
 * 1. Phản ánh đúng Snapshot Server: Chỉ hiển thị dữ liệu server đã chốt (tên tài liệu,
 *    số tiền VND, thời điểm tạo, mã thanh toán và trạng thái).
 * 2. Không gây hiểu lầm pháp lý: Hiển thị rõ đây là biên nhận điện tử nội bộ hệ thống,
 *    không thay thế hóa đơn tài chính / thuế GTGT hay payout của Seller.
 * 3. Chống gian lận: Tuyệt đối không báo "Đã trả tiền" đối với trạng thái PENDING.
 *    Hiển thị cảnh báo trực quan tương ứng với từng trạng thái đơn hàng.
 */
@Composable
fun BuyerTransactionDetailScreen(
    viewModel: BuyerTransactionsViewModel,
    orderId: String? = null,
    onBackClick: () -> Unit = {},
    onNavigateToReader: (String) -> Unit = {},
    onNavigateToCheckout: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Tự động tải nếu màn hình được mở qua Deep Link hoặc Route argument mà chưa có detail
    LaunchedEffect(orderId) {
        if (!orderId.isNullOrBlank() && uiState.selectedOrderDetail?.orderId != orderId) {
            viewModel.loadOrderDetail(orderId)
        }
    }

    BuyerTransactionDetailContent(
        detail = uiState.selectedOrderDetail,
        isLoading = uiState.isLoadingDetail,
        errorMessage = uiState.detailError,
        onRetry = {
            val targetId = orderId ?: uiState.selectedOrderDetail?.orderId
            if (!targetId.isNullOrBlank()) {
                viewModel.loadOrderDetail(targetId)
            } else {
                onBackClick()
            }
        },
        onBackClick = onBackClick,
        onNavigateToReader = onNavigateToReader,
        onNavigateToCheckout = onNavigateToCheckout,
        modifier = modifier
    )
}

/**
 * Stateless Content phục vụ Preview và Kiểm thử UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerTransactionDetailContent(
    detail: BuyerTransactionDetailUiModel?,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onBackClick: () -> Unit = {},
    onNavigateToReader: (String) -> Unit = {},
    onNavigateToCheckout: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chi tiết biên nhận",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_back_detail")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_left),
                            contentDescription = "Quay lại",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                ),
                modifier = Modifier.statusBarsPadding()
            )
        },
        containerColor = BackgroundColor,
        modifier = modifier
            .fillMaxSize()
            .testTag("screen_transaction_detail")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    UiFeedback(
                        state = UiFeedbackState.Loading,
                        modifier = Modifier.testTag("detail_loading")
                    )
                }

                errorMessage != null || detail == null -> {
                    UiFeedback(
                        state = UiFeedbackState.Error(
                            message = errorMessage ?: "Không tìm thấy thông tin biên nhận giao dịch."
                        ),
                        onRetry = onRetry,
                        modifier = Modifier.testTag("detail_error")
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Thẻ Biên nhận chính (Receipt Card)
                        ReceiptCard(detail = detail)

                        // 2. Thẻ cảnh báo và hành động tương ứng với trạng thái
                        StatusGuidanceCard(
                            detail = detail,
                            onNavigateToReader = onNavigateToReader,
                            onNavigateToCheckout = onNavigateToCheckout
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Nút quay lại danh sách
                        UniButton(
                            text = "Đóng",
                            onClick = onBackClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_close_detail")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị Biên nhận giao dịch chuẩn cấu trúc snapshot
 */
@Composable
private fun ReceiptCard(detail: BuyerTransactionDetailUiModel) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ReceiptBgColor),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_receipt")
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Biên nhận
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BIÊN NHẬN GIAO DỊCH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandGreen,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "UniShare Academic Marketplace",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
                OrderStatusChip(status = detail.status, orderId = detail.orderId)
            }

            HorizontalDivider(color = CardBorderColor)

            // Dòng Số tiền thanh toán nổi bật
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Tổng số tiền",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Text(
                    text = detail.formattedAmountVnd,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen,
                    modifier = Modifier.testTag("text_detail_amount")
                )
            }

            HorizontalDivider(color = CardBorderColor)

            // Danh sách các trường dữ liệu Snapshot
            ReceiptRow(label = "Tài liệu", value = detail.documentTitle, testTag = "text_detail_document_title")
            ReceiptRow(label = "Mã đơn hàng", value = detail.orderId, testTag = "text_detail_order_id")
            ReceiptRow(label = "Mã thanh toán", value = detail.paymentCode, testTag = "text_detail_payment_code")
            ReceiptRow(label = "Thời điểm tạo", value = detail.formattedCreatedAt, testTag = "text_detail_created_at")

            if (detail.status == OrderStatus.PENDING && detail.formattedExpiresAt.isNotBlank()) {
                ReceiptRow(label = "Hạn thanh toán", value = detail.formattedExpiresAt, testTag = "text_detail_expires_at")
            }

            // Thông tin ngân hàng / phương thức chuyển khoản
            if (!detail.bankName.isNullOrBlank()) {
                ReceiptRow(label = "Ngân hàng thụ hưởng", value = detail.bankName, testTag = "text_detail_bank_name")
            }
            if (!detail.accountNumber.isNullOrBlank()) {
                ReceiptRow(label = "Số tài khoản nhận", value = detail.accountNumber, testTag = "text_detail_account_number")
            }
            if (!detail.accountHolder.isNullOrBlank()) {
                ReceiptRow(label = "Chủ tài khoản nhận", value = detail.accountHolder, testTag = "text_detail_account_holder")
            }

            HorizontalDivider(color = CardBorderColor)

            // Chú thích từ chối trách nhiệm hóa đơn tài chính theo yêu cầu Issue B05
            Text(
                text = "Lưu ý: Biên nhận điện tử snapshot này được phát hành tự động cho Buyer để theo dõi trạng thái mua tài liệu nội bộ trên ứng dụng UniShare. Biên nhận không mang giá trị hóa đơn tài chính hoặc hóa đơn thuế GTGT theo quy định pháp luật.",
                fontSize = 11.sp,
                color = Color(0xFF9CA3AF),
                lineHeight = 15.sp,
                textAlign = TextAlign.Justify
            )
        }
    }
}

/**
 * Một dòng thông tin nhãn - giá trị trên biên nhận
 */
@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    testTag: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1.8f)
                .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
        )
    }
}

/**
 * Thẻ cảnh báo và hướng dẫn dựa theo trạng thái đơn hàng
 */
@Composable
private fun StatusGuidanceCard(
    detail: BuyerTransactionDetailUiModel,
    onNavigateToReader: (String) -> Unit,
    onNavigateToCheckout: (String) -> Unit
) {
    when (detail.status) {
        OrderStatus.PAID -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_text),
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Đã thanh toán thành công",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandGreen
                        )
                    }
                    Text(
                        text = "Quyền đọc tài liệu đã được cấp vào Thư viện của bạn. Bạn có thể mở đọc trực tiếp ngay bây giờ.",
                        fontSize = 12.sp,
                        color = Color(0xFF1B5E20)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    UniButton(
                        text = "Đọc tài liệu ngay",
                        onClick = { onNavigateToReader(detail.docId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_read_document")
                    )
                }
            }
        }

        OrderStatus.PENDING -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Chưa thanh toán (Pending)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                    Text(
                        text = "Đơn hàng đang chờ nhận tiền chuyển khoản. Vui lòng thanh toán trước hạn ${detail.formattedExpiresAt} để hoàn tất nhận tài liệu.",
                        fontSize = 12.sp,
                        color = Color(0xFF92400E)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    UniButton(
                        text = "Tiếp tục thanh toán",
                        onClick = { onNavigateToCheckout(detail.docId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_continue_checkout")
                    )
                }
            }
        }

        OrderStatus.EXPIRED -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Đơn hàng đã hết hạn",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B5563)
                    )
                    Text(
                        text = "Đơn hàng đã quá thời hạn 15 phút. Tuyệt đối KHÔNG chuyển tiền vào mã thanh toán này. Nếu vẫn muốn mua tài liệu, vui lòng tạo đơn hàng mới từ trang chi tiết tài liệu.",
                        fontSize = 12.sp,
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }

        OrderStatus.REVIEW_REQUIRED -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Đang kiểm tra đối soát",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D4ED8)
                    )
                    Text(
                        text = "Hệ thống phát hiện khoản chuyển có thể bị sai số tiền, sai nội dung hoặc chuyển sau khi hết hạn. Ban quản trị đang đối soát thủ công và sẽ cập nhật quyền đọc khi xác nhận xong.",
                        fontSize = 12.sp,
                        color = Color(0xFF1E40AF)
                    )
                }
            }
        }

        OrderStatus.FAILED -> {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Giao dịch không thành công",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C)
                    )
                    Text(
                        text = "Không thể hoàn tất đơn hàng này do lỗi nghiệp vụ.",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626)
                    )
                }
            }
        }
    }
}

// ============================================================================
// COMPOSE PREVIEWS CHO MÀN HÌNH CHI TIẾT BIÊN NHẬN
// ============================================================================

@Preview(name = "1. Biên nhận đơn PAID (Thành công)", showBackground = true)
@Composable
private fun PreviewBuyerTransactionDetailPaid() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        BuyerTransactionDetailContent(
            detail = BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_001",
                docId = "doc_dsa_01",
                documentTitle = "Cấu trúc dữ liệu & Giải thuật nâng cao",
                amountVnd = 50_000L,
                status = OrderStatus.PAID,
                paymentCode = "UNI8A3F12",
                createdAtUtc = "2026-11-24T07:30:00Z",
                expiresAtUtc = "2026-11-24T07:45:00Z",
                formattedCreatedAt = "24/11/2026 14:30",
                formattedExpiresAt = "24/11/2026 14:45",
                formattedAmountVnd = "50.000 đ"
            )
        )
    }
}

@Preview(name = "2. Biên nhận đơn PENDING (Chờ thanh toán)", showBackground = true)
@Composable
private fun PreviewBuyerTransactionDetailPending() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        BuyerTransactionDetailContent(
            detail = BuyerTransactionDetailUiModel(
                orderId = "ord_buyer1_002",
                docId = "doc_macro_econ",
                documentTitle = "Tóm tắt Kinh tế vĩ mô ĐH Kinh tế",
                amountVnd = 35_000L,
                status = OrderStatus.PENDING,
                paymentCode = "UNIMACRO01",
                createdAtUtc = "2026-11-24T07:30:00Z",
                expiresAtUtc = "2026-11-24T07:45:00Z",
                formattedCreatedAt = "24/11/2026 14:30",
                formattedExpiresAt = "24/11/2026 14:45",
                formattedAmountVnd = "35.000 đ"
            )
        )
    }
}
