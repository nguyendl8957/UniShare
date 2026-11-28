package vn.unishare.feature.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.collectLatest
import vn.unishare.R
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val LibraryBg = Color(0xFFF7F9FB)
private val LibraryPrimaryGreen = Color(0xFF2E7D32)
private val LibrarySoftGreenBg = Color(0xFFE7F3E9)
private val LibraryNavyText = Color(0xFF1F2937)
private val LibraryMutedText = Color(0xFF667085)
private val LibraryCardBorder = Color(0xFFE2E6ED)

// Màn hình Thư viện tài liệu đã mua của Buyer hiện tại
@Composable
fun PurchasedLibraryScreen(
    viewModel: PurchasedLibraryViewModel,
    onOpenDocumentReader: (String) -> Unit = {},
    onExploreClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onBottomNavSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Tự tải lại danh sách quyền đọc khi quay lại màn hình thư viện
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onLibraryResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is PurchasedLibraryEvent.NavigateToReader -> onOpenDocumentReader(event.docId)
                is PurchasedLibraryEvent.ShowMessage -> Unit
            }
        }
    }

    UniShareTheme(mode = UniAppMode.BUYER) {
        PurchasedLibraryContent(
            uiState = uiState,
            onRefreshClick = viewModel::refreshLibrary,
            onDocumentClick = viewModel::requestOpenDocument,
            onExploreClick = onExploreClick,
            onLoginClick = onLoginClick,
            onBottomNavSelected = onBottomNavSelected,
            modifier = modifier
        )
    }
}

@Composable
fun PurchasedLibraryContent(
    uiState: PurchasedLibraryUiState,
    onRefreshClick: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onExploreClick: () -> Unit,
    onLoginClick: () -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("purchased_library_screen"),
        containerColor = LibraryBg,
        topBar = {
            PurchasedLibraryTopBar(
                showRefresh = !uiState.isGuest && !uiState.isRestoringSession,
                isRefreshing = uiState.isRefreshing,
                onRefreshClick = onRefreshClick
            )
        },
        bottomBar = {
            UniBottomBar(
                selectedRoute = "purchased",
                onItemSelected = onBottomNavSelected,
                mode = UniAppMode.BUYER
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isRestoringSession -> {
                    UiFeedback(state = UiFeedbackState.Loading)
                }

                uiState.isGuest -> {
                    GuestLibraryPrompt(onLoginClick = onLoginClick)
                }

                else -> {
                    when (val listState = uiState.listState) {
                        is PurchasedLibraryListState.Idle,
                        is PurchasedLibraryListState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("library_loading_view")
                            ) {
                                UiFeedback(state = UiFeedbackState.Skeleton)
                            }
                        }

                        is PurchasedLibraryListState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("library_error_view")
                            ) {
                                UiFeedback(
                                    state = UiFeedbackState.Error(listState.message),
                                    onRetry = onRefreshClick
                                )
                            }
                        }

                        is PurchasedLibraryListState.Empty -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp)
                                    .testTag("library_empty_view")
                            ) {
                                UiFeedback(
                                    state = UiFeedbackState.Empty(
                                        message = "Bạn chưa có tài liệu đã mua",
                                        subtitle = "Khám phá kho ghi chú, đề thi và đồ án đã kiểm duyệt để mở khóa học ngay."
                                    ),
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                UniButton(
                                    text = "Khám phá tài liệu",
                                    onClick = onExploreClick,
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .testTag("btn_library_explore")
                                )
                            }
                        }

                        is PurchasedLibraryListState.Content -> {
                            PurchasedDocumentList(
                                items = listState.items,
                                isRefreshing = uiState.isRefreshing,
                                refreshErrorMessage = uiState.refreshErrorMessage,
                                onRefreshClick = onRefreshClick,
                                onDocumentClick = onDocumentClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchasedLibraryTopBar(
    showRefresh: Boolean,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.unishare_wordmark),
                        contentDescription = "Biểu trưng UniShare",
                        modifier = Modifier
                            .width(106.dp)
                            .height(34.dp)
                    )
                    Text(
                        text = "• Tài liệu đã mua",
                        color = LibraryNavyText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showRefresh) {
                    IconButton(
                        onClick = onRefreshClick,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_library_refresh")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = "Làm mới thư viện",
                            tint = LibraryPrimaryGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(
                    color = LibraryPrimaryGreen,
                    trackColor = LibrarySoftGreenBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }
        }
    }
}

@Composable
private fun GuestLibraryPrompt(onLoginClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .testTag("library_guest_view")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(LibrarySoftGreenBg)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lock_keyhole),
                contentDescription = null,
                tint = LibraryPrimaryGreen,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Đăng nhập để mở thư viện của bạn",
            color = LibraryNavyText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Các tài liệu PDF bạn đã thanh toán hợp lệ sẽ được lưu tại đây và chỉ tài khoản của bạn mới đọc được.",
            color = LibraryMutedText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        UniButton(
            text = "Đăng nhập / Đăng ký",
            onClick = onLoginClick,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .testTag("btn_library_guest_login")
        )
    }
}

@Composable
private fun PurchasedDocumentList(
    items: List<PurchasedDocumentItem>,
    isRefreshing: Boolean,
    refreshErrorMessage: String?,
    onRefreshClick: () -> Unit,
    onDocumentClick: (String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("library_content_list")
    ) {
        if (!refreshErrorMessage.isNullOrBlank()) {
            item(key = "inline_refresh_error") {
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("banner_library_refresh_error")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = refreshErrorMessage,
                            color = Color(0xFF991B1B),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onRefreshClick,
                            enabled = !isRefreshing
                        ) {
                            Text(
                                text = "Thử lại",
                                color = LibraryPrimaryGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item(key = "library_count_header") {
            Text(
                text = "Đang sở hữu ${items.size} tài liệu hợp lệ",
                color = LibraryMutedText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        items(
            items = items,
            key = { it.docId }
        ) { doc ->
            PurchasedDocumentCard(
                doc = doc,
                onClick = { onDocumentClick(doc.docId) }
            )
        }
    }
}

@Composable
private fun PurchasedDocumentCard(
    doc: PurchasedDocumentItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, LibraryCardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("library_doc_${doc.docId}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(LibrarySoftGreenBg)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_file_text),
                        contentDescription = null,
                        tint = LibraryPrimaryGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = doc.title,
                        color = LibraryNavyText,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = LibrarySoftGreenBg,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = doc.categoryLabel,
                                color = LibraryPrimaryGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "v${doc.versionNumber} • ${doc.pageCount} trang",
                            color = LibraryMutedText,
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "${doc.authorName} • ${doc.schoolName}",
                        color = LibraryMutedText,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (doc.isSellerArchived) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Người bán đã ẩn bản mới • Phiên bản bạn đã mua vẫn mở đọc bình thường.",
                        color = Color(0xFF475569),
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF0F4F8))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = doc.purchasedAtLabel,
                    color = LibraryMutedText,
                    fontSize = 11.5.sp
                )

                Button(
                    onClick = onClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LibraryPrimaryGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_open_doc_${doc.docId}")
                ) {
                    Text(
                        text = "Mở đọc PDF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Purchased Library - 360dp")
@Composable
private fun PreviewPurchasedLibrary360() {
    val sampleItems = listOf(
        PurchasedDocumentItem(
            docId = FakeLibraryRepository.DOC_DSA_BINARY_SEARCH,
            ownerUid = FakeLibraryRepository.UID_BUYER_ONE,
            title = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
            categoryLabel = "Note tóm tắt",
            authorName = "Nguyễn Văn A",
            schoolName = "ĐH Bách Khoa TP.HCM",
            pageCount = 4,
            versionNumber = 2,
            purchasedAtLabel = "Đã mua 18/11/2026",
            entitlementStatus = EntitlementStatus.ACTIVE
        ),
        PurchasedDocumentItem(
            docId = FakeLibraryRepository.DOC_MACRO_ECON,
            ownerUid = FakeLibraryRepository.UID_BUYER_ONE,
            title = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
            categoryLabel = "Giáo trình",
            authorName = "Đặng Lâm Nguyên",
            schoolName = "ĐH Kinh tế TP.HCM",
            pageCount = 3,
            versionNumber = 1,
            purchasedAtLabel = "Đã mua 15/11/2026",
            entitlementStatus = EntitlementStatus.ACTIVE,
            isSellerArchived = true
        )
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        PurchasedLibraryContent(
            uiState = PurchasedLibraryUiState(
                isGuest = false,
                activeUid = FakeLibraryRepository.UID_BUYER_ONE,
                listState = PurchasedLibraryListState.Content(sampleItems)
            ),
            onRefreshClick = {},
            onDocumentClick = {},
            onExploreClick = {},
            onLoginClick = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 412, heightDp = 860, fontScale = 1.4f, name = "Purchased Library Empty - 412dp")
@Composable
private fun PreviewPurchasedLibraryEmpty() {
    UniShareTheme(mode = UniAppMode.BUYER) {
        PurchasedLibraryContent(
            uiState = PurchasedLibraryUiState(
                isGuest = false,
                activeUid = FakeLibraryRepository.UID_EMPTY_BUYER,
                listState = PurchasedLibraryListState.Empty
            ),
            onRefreshClick = {},
            onDocumentClick = {},
            onExploreClick = {},
            onLoginClick = {},
            onBottomNavSelected = {}
        )
    }
}
