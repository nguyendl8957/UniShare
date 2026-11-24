package vn.unishare.feature.payment.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import vn.unishare.R
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.payment.ui.OrderStatus

// Bảng màu chuẩn thương hiệu UniShare
private val BrandGreen = Color(0xFF2E7D32)
private val BackgroundColor = Color(0xFFF7F9FB)
private val TextPrimary = Color(0xFF1F2937)
private val TextSecondary = Color(0xFF667085)
private val CardBorderColor = Color(0xFFE2E6ED)

/**
 * ============================================================================
 * MÀN HÌNH DANH SÁCH LỊCH SỬ GIAO DỊCH BUYER (B05)
 * ============================================================================
 */
@Composable
fun BuyerTransactionsScreen(
    viewModel: BuyerTransactionsViewModel,
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Lắng nghe sự kiện dùng một lần (One-shot Navigation & Message Events)
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is BuyerTransactionsEvent.NavigateToDetail -> onNavigateToDetail(event.orderId)
                is BuyerTransactionsEvent.NavigateToAuth -> onNavigateToAuth()
                is BuyerTransactionsEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    BuyerTransactionsContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onSelectOrder = viewModel::selectOrder,
        onLoadMore = viewModel::loadMore,
        onRefresh = viewModel::refresh,
        onRetryInitial = viewModel::loadInitialTransactions,
        onLoginClick = onNavigateToAuth,
        onBackClick = onBackClick,
        modifier = modifier
    )
}

/**
 * Stateless Content phục vụ Preview và Kiểm thử UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuyerTransactionsContent(
    uiState: BuyerTransactionsUiState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSelectOrder: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetryInitial: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Lịch sử mua",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("btn_back")
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = BackgroundColor,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. Đang khôi phục phiên hoặc tải dữ liệu lần đầu -> Hiển thị Skeleton theo C01
                uiState.isRestoringSession || uiState.listState is BuyerTransactionsListState.InitialLoading -> {
                    UiFeedback(
                        state = UiFeedbackState.Skeleton,
                        modifier = Modifier.testTag("feedback_skeleton")
                    )
                }

                // 2. Chưa đăng nhập (Guest) -> Yêu cầu đăng nhập
                uiState.isGuest -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("guest_warning_container"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = null,
                            tint = BrandGreen,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Yêu cầu đăng nhập",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Vui lòng đăng nhập tài khoản sinh viên UniShare để xem lịch sử mua và biên nhận đơn hàng.",
                            fontSize = 14.sp,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        UniButton(
                            text = "Đăng nhập ngay",
                            onClick = onLoginClick,
                            modifier = Modifier.testTag("btn_login_guest")
                        )
                    }
                }

                // 3. Mất kết nối mạng / Lỗi tải trang đầu -> Hiển thị Error theo C01 kèm nút Thử lại
                uiState.listState is BuyerTransactionsListState.Error -> {
                    UiFeedback(
                        state = UiFeedbackState.Error(
                            message = (uiState.listState as BuyerTransactionsListState.Error).message
                        ),
                        onRetry = onRetryInitial,
                        modifier = Modifier.testTag("feedback_error")
                    )
                }

                // 4. Danh sách rỗng -> Hiển thị Empty state theo C01
                uiState.listState is BuyerTransactionsListState.Empty -> {
                    UiFeedback(
                        state = UiFeedbackState.Empty(
                            message = "Chưa có giao dịch nào",
                            subtitle = "Các tài liệu bạn đã thanh toán hoặc đang giao dịch sẽ xuất hiện tại đây."
                        ),
                        modifier = Modifier.testTag("feedback_empty")
                    )
                }

                // 5. Có dữ liệu giao dịch -> Hiển thị danh sách cuộn LazyColumn
                uiState.listState is BuyerTransactionsListState.Content -> {
                    val items = (uiState.listState as BuyerTransactionsListState.Content).items
                    val listState = rememberLazyListState()
                    val pullRefreshState = rememberPullToRefreshState()

                    // Kiểm tra cuộn gần cuối danh sách để kích hoạt tải thêm (Load-more)
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisibleIndex >= listState.layoutInfo.totalItemsCount - 2
                        }
                    }

                    LaunchedEffect(shouldLoadMore, uiState.hasMore, uiState.isLoadingMore, uiState.loadMoreError) {
                        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && uiState.loadMoreError == null) {
                            onLoadMore()
                        }
                    }

                    PullToRefreshBox(
                        state = pullRefreshState,
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Nhóm các giao dịch theo ngày (Date Grouping)
                        val groupedByDate = remember(items) {
                            items.groupBy { it.formattedDate.ifBlank { "Gần đây" } }
                        }

                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("list_transactions")
                        ) {
                            groupedByDate.forEach { (dateHeader, dateItems) ->
                                item(key = "header_$dateHeader") {
                                    Text(
                                        text = dateHeader,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                    )
                                }

                                items(
                                    items = dateItems,
                                    key = { it.orderId }
                                ) { transaction ->
                                    TransactionItemCard(
                                        item = transaction,
                                        onClick = { onSelectOrder(transaction.orderId) },
                                        modifier = Modifier.testTag("item_transaction_${transaction.orderId}")
                                    )
                                }
                            }

                            // Chân trang hiển thị khi tải thêm (Load-more Footer)
                            if (uiState.isLoadingMore) {
                                item(key = "footer_loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = BrandGreen,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            } else if (uiState.loadMoreError != null) {
                                item(key = "footer_error") {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = uiState.loadMoreError,
                                            fontSize = 12.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(
                                            onClick = onLoadMore,
                                            modifier = Modifier.testTag("btn_retry_load_more")
                                        ) {
                                            Text(
                                                text = "Thử lại",
                                                color = BrandGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị tóm tắt từng mục giao dịch trong danh sách
 */
@Composable
private fun TransactionItemCard(
    item: BuyerTransactionItemUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, CardBorderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Mã đơn: ${item.orderId}",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.Medium
                )
                OrderStatusChip(status = item.status, orderId = item.orderId)
            }

            Text(
                text = item.documentTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.formattedTime.ifBlank { "Gần đây" },
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Text(
                    text = item.formattedAmountVnd,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandGreen
                )
            }
        }
    }
}

/**
 * Chip hiển thị trạng thái giao dịch chuẩn màu và nhãn
 */
@Composable
fun OrderStatusChip(
    status: OrderStatus,
    orderId: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        OrderStatus.PAID -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Thành công")
        OrderStatus.PENDING -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Chờ thanh toán")
        OrderStatus.EXPIRED -> Triple(Color(0xFFF3F4F6), Color(0xFF6B7280), "Đã hết hạn")
        OrderStatus.REVIEW_REQUIRED -> Triple(Color(0xFFEFF6FF), Color(0xFF2563EB), "Đang kiểm tra")
        OrderStatus.FAILED -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "Thất bại")
    }

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = modifier.testTag("text_status_$orderId")
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

// ============================================================================
// COMPOSE PREVIEWS CHO MÀN HÌNH DANH SÁCH LỊCH SỬ GIAO DỊCH
// ============================================================================

@Preview(name = "1. Danh sách có dữ liệu", showBackground = true)
@Composable
private fun PreviewBuyerTransactionsContent() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        BuyerTransactionsContent(
            uiState = BuyerTransactionsUiState(
                activeUid = "uid_preview",
                listState = BuyerTransactionsListState.Content(
                    items = listOf(
                        BuyerTransactionItemUiModel(
                            orderId = "ord_001",
                            docId = "doc_1",
                            documentTitle = "Cấu trúc dữ liệu & Giải thuật nâng cao",
                            amountVnd = 50_000L,
                            status = OrderStatus.PAID,
                            paymentCode = "UNI8A3F12",
                            createdAtUtc = "",
                            formattedDate = "24/11/2026",
                            formattedTime = "14:30",
                            formattedAmountVnd = "50.000 đ"
                        ),
                        BuyerTransactionItemUiModel(
                            orderId = "ord_002",
                            docId = "doc_2",
                            documentTitle = "Giáo trình Kinh tế vĩ mô ĐH Kinh tế",
                            amountVnd = 35_000L,
                            status = OrderStatus.PENDING,
                            paymentCode = "UNIMACRO01",
                            createdAtUtc = "",
                            formattedDate = "24/11/2026",
                            formattedTime = "12:15",
                            formattedAmountVnd = "35.000 đ"
                        )
                    )
                )
            )
        )
    }
}

@Preview(name = "2. Danh sách rỗng", showBackground = true)
@Composable
private fun PreviewBuyerTransactionsEmpty() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        BuyerTransactionsContent(
            uiState = BuyerTransactionsUiState(
                activeUid = "uid_preview",
                listState = BuyerTransactionsListState.Empty
            )
        )
    }
}
