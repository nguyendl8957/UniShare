package vn.unishare.feature.revenue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

// Hệ màu chuyên biệt cho chế độ Seller theo Figma Design System
private val SellerNavyText = Color(0xFF10244E)
private val SellerMutedText = Color(0xFF7789AE)
private val SellerBorderColor = Color(0xFFE2E8F0)
private val SellerCardBg = Color(0xFFE4F2FF)
private val SellerCardBorder = Color(0xFFBACCE5)
private val SellerPrimaryBlue = Color(0xFF0863CE)
private val SellerPaidGreen = Color(0xFF168A39)
private val SellerPaidBadgeBg = Color(0xFFDCFCE7)
private val SellerPaidBadgeText = Color(0xFF15803D)
private val DisclaimerBg = Color(0xFFF1F5F9)

/**
 * ============================================================================
 * MÀN HÌNH BẢNG DOANH THU & GIAO DỊCH SELLER (B06)
 * Issue: [B06] Dựng bảng doanh thu và giao dịch của Seller (#22)
 * ============================================================================
 */
@Composable
fun SellerRevenueScreen(
    viewModel: SellerRevenueViewModel,
    onNavigateBack: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    UniShareTheme(mode = UniAppMode.SELLER) {
        SellerRevenueScreenContent(
            uiState = uiState,
            onNavigateBack = onNavigateBack,
            onLoginClick = onLoginClick,
            onFilterSelected = viewModel::selectFilter,
            onRefresh = viewModel::refresh,
            onLoadMore = viewModel::loadMore,
            onRetryLoadMore = viewModel::retryLoadMore,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerRevenueScreenContent(
    uiState: SellerRevenueUiState,
    onNavigateBack: () -> Unit,
    onLoginClick: () -> Unit,
    onFilterSelected: (RevenueDateRangeFilter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("seller_revenue_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Doanh thu Seller",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SellerNavyText
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_left),
                            contentDescription = "Quay lại",
                            tint = SellerNavyText
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("btn_refresh")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = "Làm mới dữ liệu",
                            tint = SellerPrimaryBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 1. Đang khôi phục phiên xác thực
                uiState.isRestoringSession -> {
                    UiFeedback(state = UiFeedbackState.Skeleton)
                }

                // 2. Khách vãng lai (Guest) chưa đăng nhập
                uiState.isGuest -> {
                    GuestNoticeView(
                        onLoginClick = onLoginClick,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                // 3. Tài khoản chưa được cấp quyền Seller
                !uiState.isSellerRole -> {
                    DeniedNoticeView(
                        onNavigateBack = onNavigateBack,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    )
                }

                // 4. Lỗi tải ban đầu (Initial Loading Error)
                uiState.listState is SellerRevenueListState.Error &&
                    uiState.kpiState.totalRevenueVnd == 0L -> {
                    UiFeedback(
                        state = UiFeedbackState.Error((uiState.listState as SellerRevenueListState.Error).message),
                        onRetry = onRefresh
                    )
                }

                // 5. Đang tải trang đầu tiên (Skeleton Loading)
                uiState.listState is SellerRevenueListState.InitialLoading && !uiState.isRefreshing -> {
                    UiFeedback(state = UiFeedbackState.Skeleton)
                }

                // 6. Hiển thị nội dung chính hoặc danh sách trống / lỗi cục bộ
                else -> {
                    RevenueContentList(
                        uiState = uiState,
                        onFilterSelected = onFilterSelected,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onRetryLoadMore = onRetryLoadMore
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueContentList(
    uiState: SellerRevenueUiState,
    onFilterSelected: (RevenueDateRangeFilter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("seller_revenue_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Thẻ chỉ số tổng quan (KPI Card)
        item(key = "kpi_card") {
            SellerRevenueKpiCard(kpi = uiState.kpiState)
        }

        // Cảnh báo nếu tải KPI lỗi nhưng danh sách vẫn hiển thị
        if (uiState.kpiError != null) {
            item(key = "kpi_error_banner") {
                InlineErrorCard(
                    message = uiState.kpiError,
                    onRetry = onRefresh,
                    tag = "kpi_error_view"
                )
            }
        }

        // 2. Bộ lọc khoảng thời gian (Date Range Filter Chips)
        item(key = "filter_chips") {
            RevenueDateRangeChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = onFilterSelected
            )
        }

        // 3. Tiêu đề danh sách giao dịch
        item(key = "section_title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lịch sử bán hàng",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SellerNavyText
                )

                if (uiState.listState is SellerRevenueListState.Content) {
                    val count = (uiState.listState as SellerRevenueListState.Content).items.size
                    Text(
                        text = "$count đơn đã tải",
                        fontSize = 12.sp,
                        color = SellerMutedText
                    )
                }
            }
        }

        // 4. Danh sách giao dịch hoặc trạng thái rỗng
        when (val listState = uiState.listState) {
            is SellerRevenueListState.Empty -> {
                item(key = "empty_view") {
                    EmptySalesView(modifier = Modifier.padding(vertical = 24.dp))
                }
            }

            is SellerRevenueListState.Error -> {
                item(key = "list_error_view") {
                    InlineErrorCard(
                        message = listState.message,
                        onRetry = onRefresh,
                        tag = "list_error_view"
                    )
                }
            }

            is SellerRevenueListState.Content -> {
                items(
                    items = listState.items,
                    key = { it.orderId }
                ) { item ->
                    SellerTransactionRow(item = item)
                }

                // Chân trang: Đang tải thêm
                if (uiState.isLoadingMore) {
                    item(key = "loading_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = SellerPrimaryBlue,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                // Chân trang: Lỗi tải thêm
                if (uiState.loadMoreError != null) {
                    item(key = "load_more_error") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = uiState.loadMoreError,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TextButton(onClick = onRetryLoadMore) {
                                Text("Thử lại", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Nút tải thêm hoặc thông báo hết dữ liệu
                if (uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
                    item(key = "btn_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SellerBorderColor)
                            ) {
                                Text("Xem thêm giao dịch", fontSize = 13.sp, color = SellerPrimaryBlue)
                            }
                        }
                    }
                } else if (!uiState.hasMore && listState.items.isNotEmpty()) {
                    item(key = "end_of_list") {
                        Text(
                            text = "— Đã hiển thị toàn bộ giao dịch —",
                            fontSize = 12.sp,
                            color = SellerMutedText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }

            else -> Unit
        }
    }
}

/**
 * Thẻ chỉ số tổng hợp Doanh thu ghi nhận & Số lượng đơn bán
 */
@Composable
private fun SellerRevenueKpiCard(
    kpi: SellerRevenueKpiUiModel,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SellerCardBg,
        border = BorderStroke(1.dp, SellerCardBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Nhãn tiêu đề cố định: "DOANH THU GHI NHẬN"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SellerPrimaryBlue)
                )
                Text(
                    text = "DOANH THU GHI NHẬN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SellerPrimaryBlue,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Số tiền VND hiển thị to rõ
            Text(
                text = kpi.formattedTotalRevenue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = SellerNavyText,
                modifier = Modifier.testTag("kpi_total_revenue")
            )

            Text(
                text = "Khoảng thời gian: ${kpi.periodLabel}",
                fontSize = 12.sp,
                color = SellerMutedText
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Hai thẻ phụ: Số đơn bán thành công & Số tài liệu bán
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricSubBox(
                    label = "Đơn thành công",
                    value = "${kpi.paidOrderCount} đơn",
                    tag = "kpi_order_count",
                    modifier = Modifier.weight(1f)
                )

                MetricSubBox(
                    label = "Tài liệu đã bán",
                    value = "${kpi.totalDocumentsSold} tài liệu",
                    tag = "kpi_doc_count",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Ghi chú minh bạch: Không phải số dư rút tiền (Payout)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = DisclaimerBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_warning),
                        contentDescription = null,
                        tint = SellerMutedText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = kpi.revenueNotice,
                        fontSize = 11.sp,
                        color = SellerMutedText,
                        lineHeight = 15.sp,
                        modifier = Modifier.testTag("txt_revenue_disclaimer")
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricSubBox(
    label: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SellerCardBorder.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = SellerMutedText
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SellerNavyText,
                modifier = Modifier.testTag(tag)
            )
        }
    }
}

/**
 * Thanh chọn khoảng thời gian dạng Chip có thể cuộn ngang
 */
@Composable
private fun RevenueDateRangeChips(
    selectedFilter: RevenueDateRangeFilter,
    onFilterSelected: (RevenueDateRangeFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RevenueDateRangeFilter.entries.forEach { filter ->
            val isSelected = filter == selectedFilter

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) SellerPrimaryBlue else Color.White,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) SellerPrimaryBlue else SellerBorderColor
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onFilterSelected(filter) }
                    .testTag("chip_filter_${filter.name}")
            ) {
                Text(
                    text = filter.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else SellerNavyText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Dòng hiển thị một giao dịch đơn hàng bán thành công
 */
@Composable
private fun SellerTransactionRow(
    item: SellerTransactionItemUiModel,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, SellerBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("item_order_${item.orderId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Tên tài liệu đã bán
                Text(
                    text = item.documentTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SellerNavyText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Mã đơn và ngày giờ bán
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "#${item.paymentCode}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = SellerMutedText
                    )

                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = SellerMutedText
                    )

                    Text(
                        text = "${item.formattedDate} ${item.formattedTime}",
                        fontSize = 11.sp,
                        color = SellerMutedText
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Nhãn trạng thái PAID
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SellerPaidBadgeBg
                ) {
                    Text(
                        text = "ĐÃ NHẬN TIỀN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SellerPaidBadgeText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Số tiền ghi nhận (+XX.XXX đ)
            Text(
                text = item.formattedAmountVnd,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SellerPaidGreen,
                modifier = Modifier.testTag("item_amount_${item.orderId}")
            )
        }
    }
}

/**
 * Giao diện khi chưa có giao dịch bán nào trong khoảng thời gian lọc
 */
@Composable
private fun EmptySalesView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_sales_view"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_file_text),
            contentDescription = null,
            tint = SellerMutedText.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Chưa có giao dịch bán nào",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = SellerNavyText
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Các đơn hàng được thanh toán thành công (PAID) sẽ tự động xuất hiện tại đây.",
            fontSize = 12.sp,
            color = SellerMutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

/**
 * Giao diện thông báo khi người dùng chưa đăng nhập
 */
@Composable
private fun GuestNoticeView(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("guest_notice_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_lock_keyhole),
            contentDescription = null,
            tint = SellerPrimaryBlue,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Yêu cầu đăng nhập",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SellerNavyText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Vui lòng đăng nhập tài khoản Seller để xem doanh thu và danh sách đơn bán của bạn.",
            fontSize = 13.sp,
            color = SellerMutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        UniButton(
            text = "Đăng nhập UniShare",
            onClick = onLoginClick,
            modifier = Modifier.widthIn(max = 240.dp)
        )
    }
}

/**
 * Giao diện thông báo khi tài khoản chưa được kích hoạt quyền Seller
 */
@Composable
private fun DeniedNoticeView(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("denied_notice_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Chưa có quyền Seller",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SellerNavyText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tài khoản của bạn chưa được cấp quyền Seller để bán tài liệu. Vui lòng vào trang Cá nhân để đăng ký chế độ Seller.",
            fontSize = 13.sp,
            color = SellerMutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        UniButton(
            text = "Quay lại",
            onClick = onNavigateBack,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

@Composable
private fun InlineErrorCard(
    message: String,
    onRetry: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text("Thử lại", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

