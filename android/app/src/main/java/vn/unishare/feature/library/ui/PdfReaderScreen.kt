package vn.unishare.feature.library.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import vn.unishare.R
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val ReaderCanvasBg = Color(0xFFF5F8FB)
private val ReaderPrimaryGreen = Color(0xFF2E7D32)
private val ReaderSoftGreen = Color(0xFFE7F3E9)
private val ReaderHeadingBlue = Color(0xFF1D4E89)
private val ReaderNavyText = Color(0xFF253047)
private val ReaderBodyText = Color(0xFF334155)
private val ReaderMutedText = Color(0xFF70798A)
private val ReaderCodeBg = Color(0xFFF6F8FB)
private val ReaderBorder = Color(0xFFE2E6ED)

// Màn hình Trình đọc PDF theo docId và quyền sở hữu từ server
@Composable
fun PdfReaderScreen(
    docId: String,
    viewModel: PurchasedLibraryViewModel,
    onBackClick: () -> Unit = {},
    onAskAiClick: (String) -> Unit = {},
    onRequireLoginClick: () -> Unit = {},
    onBottomNavSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(docId, uiState.activeUid) {
        viewModel.openDocumentReader(docId)
    }

    val handleBack: () -> Unit = {
        viewModel.clearReaderState()
        onBackClick()
    }

    BackHandler(onBack = handleBack)

    UniShareTheme(mode = UniAppMode.BUYER) {
        PdfReaderContent(
            readerState = uiState.readerState,
            snackbarHostState = snackbarHostState,
            onBackClick = handleBack,
            onRetryClick = viewModel::retryOpenDocument,
            onPreviousPage = viewModel::goToPreviousPage,
            onNextPage = viewModel::goToNextPage,
            onJumpToFirstPage = { viewModel.jumpToPage(1) },
            onJumpToLastPage = {
                val ready = uiState.readerState as? PdfReaderLoadState.Ready
                if (ready != null) viewModel.jumpToPage(ready.totalPages)
            },
            onShareClick = {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Không hỗ trợ xuất tệp PDF gốc để bảo vệ bản quyền tài liệu."
                    )
                }
            },
            onAskAiClick = {
                if (uiState.readerState is PdfReaderLoadState.Ready) {
                    onAskAiClick(docId)
                }
            },
            onRequireLoginClick = onRequireLoginClick,
            onBottomNavSelected = onBottomNavSelected,
            modifier = modifier
        )
    }
}

@Composable
fun PdfReaderContent(
    readerState: PdfReaderLoadState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetryClick: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onJumpToFirstPage: () -> Unit,
    onJumpToLastPage: () -> Unit,
    onShareClick: () -> Unit,
    onAskAiClick: () -> Unit,
    onRequireLoginClick: () -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = readerState is PdfReaderLoadState.Ready

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("pdf_reader_screen"),
        containerColor = ReaderCanvasBg,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            PdfReaderTopBar(
                readerState = readerState,
                onBackClick = onBackClick,
                onShareClick = onShareClick,
                onRefreshAccessClick = onRetryClick,
                onJumpToFirstPage = onJumpToFirstPage,
                onJumpToLastPage = onJumpToLastPage
            )
        },
        floatingActionButton = {
            if (isReady) {
                AskAiReaderFab(onClick = onAskAiClick)
            }
        },
        bottomBar = {
            UniBottomBar(
                selectedRoute = "purchased",
                onItemSelected = onBottomNavSelected,
                mode = UniAppMode.BUYER,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (readerState) {
                is PdfReaderLoadState.Idle,
                is PdfReaderLoadState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader_loading_view")
                    ) {
                        UiFeedback(state = UiFeedbackState.Loading)
                    }
                }

                is PdfReaderLoadState.Unauthorized -> {
                    ReaderBlockedPanel(
                        tag = "reader_unauthorized_view",
                        title = "Cần đăng nhập lại",
                        message = readerState.message,
                        primaryButtonText = "Đăng nhập lại",
                        primaryButtonTag = "btn_reader_reauth",
                        onPrimaryClick = onRequireLoginClick,
                        onBackClick = onBackClick
                    )
                }

                is PdfReaderLoadState.Denied -> {
                    ReaderBlockedPanel(
                        tag = "reader_denied_view",
                        title = "Chưa có quyền đọc tài liệu",
                        message = readerState.message,
                        primaryButtonText = "Quay lại thư viện",
                        primaryButtonTag = "btn_reader_back_to_library",
                        onPrimaryClick = onBackClick
                    )
                }

                is PdfReaderLoadState.Revoked -> {
                    ReaderBlockedPanel(
                        tag = "reader_revoked_view",
                        title = "Quyền truy cập đã bị thu hồi",
                        message = readerState.message,
                        primaryButtonText = "Quay lại thư viện",
                        primaryButtonTag = "btn_reader_back_to_library",
                        onPrimaryClick = onBackClick
                    )
                }

                is PdfReaderLoadState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reader_error_view")
                    ) {
                        UiFeedback(
                            state = UiFeedbackState.Error(readerState.message),
                            onRetry = onRetryClick
                        )
                    }
                }

                is PdfReaderLoadState.Ready -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        PdfPageControlBar(
                            title = readerState.title,
                            currentPage = readerState.currentPage,
                            totalPages = readerState.totalPages,
                            onPreviousPage = onPreviousPage,
                            onNextPage = onNextPage
                        )

                        Box(
                            contentAlignment = Alignment.TopCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 14.dp)
                        ) {
                            val page = readerState.currentPageContent
                            if (page != null) {
                                PdfRenderedPageCard(
                                    page = page,
                                    modifier = Modifier.widthIn(max = 680.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfReaderTopBar(
    readerState: PdfReaderLoadState,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onRefreshAccessClick: () -> Unit,
    onJumpToFirstPage: () -> Unit,
    onJumpToLastPage: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_reader_back")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Quay lại thư viện",
                        tint = ReaderNavyText,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.unishare_wordmark),
                    contentDescription = "Biểu trưng UniShare",
                    modifier = Modifier
                        .width(114.dp)
                        .height(38.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onShareClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_reader_share")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_share),
                        contentDescription = "Thông tin chia sẻ",
                        tint = ReaderNavyText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_reader_more")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "Tùy chọn đọc tài liệu",
                            tint = ReaderNavyText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Kiểm tra lại quyền đọc") },
                            onClick = {
                                menuExpanded = false
                                onRefreshAccessClick()
                            }
                        )
                        if (readerState is PdfReaderLoadState.Ready) {
                            DropdownMenuItem(
                                text = { Text("Về trang đầu") },
                                onClick = {
                                    menuExpanded = false
                                    onJumpToFirstPage()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Đến trang cuối (${readerState.totalPages})") },
                                onClick = {
                                    menuExpanded = false
                                    onJumpToLastPage()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageControlBar(
    title: String,
    currentPage: Int,
    totalPages: Int,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit
) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, ReaderBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = ReaderNavyText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onPreviousPage,
                    enabled = currentPage > 1,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ReaderBorder),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("btn_reader_prev_page")
                ) {
                    Text("Trước", fontSize = 11.5.sp, color = ReaderNavyText)
                }

                Surface(
                    color = ReaderSoftGreen,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Trang $currentPage/$totalPages",
                        color = ReaderPrimaryGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("txt_reader_page_indicator")
                    )
                }

                OutlinedButton(
                    onClick = onNextPage,
                    enabled = currentPage < totalPages,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, ReaderBorder),
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .testTag("btn_reader_next_page")
                ) {
                    Text("Sau", fontSize = 11.5.sp, color = ReaderNavyText)
                }
            }
        }
    }
}

@Composable
private fun PdfRenderedPageCard(
    page: PdfPageContent,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(12.dp),
                ambientColor = Color(0x2225344A),
                spotColor = Color(0x2225344A)
            )
            .testTag("reader_pdf_page_card")
    ) {
        Box(modifier = Modifier.padding(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = page.chapterTitle,
                    color = ReaderPrimaryGreen,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("txt_reader_chapter_title")
                )
                HorizontalDivider(color = ReaderBorder)

                page.sections.forEachIndexed { index, section ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    PdfSectionBlock(
                        section = section,
                        isFirstSection = index == 0
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Hình mờ chéo thể hiện bản quyền tài liệu của người bán
            Text(
                text = "Tài liệu bởi ${page.watermarkText}",
                color = ReaderMutedText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .rotate(-34f)
                    .alpha(0.22f)
            )
        }
    }
}

@Composable
private fun PdfSectionBlock(
    section: PdfSectionContent,
    isFirstSection: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = section.sectionTitle,
            color = ReaderHeadingBlue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = if (isFirstSection) {
                Modifier.testTag("txt_reader_section_title")
            } else {
                Modifier
            }
        )

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            section.bulletPoints.forEach { bullet ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "•",
                        color = ReaderBodyText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = bullet,
                        color = ReaderBodyText,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                }
            }
        }

        if (!section.codeSnippet.isNullOrBlank()) {
            if (!section.codeTitle.isNullOrBlank()) {
                Text(
                    text = section.codeTitle,
                    color = ReaderHeadingBlue,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Surface(
                color = ReaderCodeBg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, ReaderBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = section.codeSnippet,
                    color = ReaderNavyText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (section.tableRows.isNotEmpty() && section.tableHeaderLeft != null && section.tableHeaderRight != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ReaderBorder, RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ReaderCodeBg)
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = section.tableHeaderLeft,
                        color = ReaderNavyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = section.tableHeaderRight,
                        color = ReaderNavyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                section.tableRows.forEach { (left, right) ->
                    HorizontalDivider(color = ReaderBorder)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = left,
                            color = ReaderBodyText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = right,
                            color = ReaderBodyText,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AskAiReaderFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = ReaderPrimaryGreen,
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier = Modifier
            .size(58.dp)
            .testTag("btn_reader_ask_ai")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_bot),
                contentDescription = "Hỏi AI",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Hỏi AI",
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ReaderBlockedPanel(
    tag: String,
    title: String,
    message: String,
    primaryButtonText: String,
    primaryButtonTag: String,
    onPrimaryClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .background(Color(0xFFFEF2F2), CircleShape)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lock_keyhole),
                contentDescription = null,
                tint = Color(0xFFDC2626),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = ReaderNavyText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = ReaderMutedText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(22.dp))
        UniButton(
            text = primaryButtonText,
            onClick = onPrimaryClick,
            modifier = Modifier
                .widthIn(max = 260.dp)
                .testTag(primaryButtonTag)
        )
        if (onBackClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onBackClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ReaderNavyText)
            ) {
                Text("Quay lại")
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 913, name = "PDF Reader - 412dp")
@Composable
private fun PreviewPdfReader412() {
    val sampleContent = PdfReaderLoadState.Ready(
        docId = FakeLibraryRepository.DOC_DSA_BINARY_SEARCH,
        title = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
        versionNumber = 2,
        totalPages = 3,
        currentPage = 1,
        pages = listOf(
            PdfPageContent(
                pageNumber = 1,
                chapterTitle = "Chương 3: Thuật toán Tìm kiếm nhị phân",
                sections = listOf(
                    PdfSectionContent(
                        sectionTitle = "3.2. Điều kiện áp dụng",
                        bulletPoints = listOf(
                            "Mảng phải được sắp xếp (tăng dần hoặc giảm dần).",
                            "Các phần tử có thể so sánh được."
                        )
                    ),
                    PdfSectionContent(
                        sectionTitle = "3.4. Pseudocode & Độ phức tạp",
                        bulletPoints = listOf("Chia đôi đoạn tìm kiếm sau mỗi bước."),
                        codeSnippet = "int binarySearch(int arr[], int n, int x) {\n    int left = 0, right = n - 1;\n    return -1;\n}",
                        tableHeaderLeft = "Trường hợp",
                        tableHeaderRight = "Độ phức tạp",
                        tableRows = listOf("Tốt nhất" to "O(1)", "Trung bình" to "O(log n)")
                    )
                ),
                watermarkText = "Nguyễn Văn A - nguyenvana@gmail.com"
            )
        )
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        PdfReaderContent(
            readerState = sampleContent,
            snackbarHostState = remember { SnackbarHostState() },
            onBackClick = {},
            onRetryClick = {},
            onPreviousPage = {},
            onNextPage = {},
            onJumpToFirstPage = {},
            onJumpToLastPage = {},
            onShareClick = {},
            onAskAiClick = {},
            onRequireLoginClick = {},
            onBottomNavSelected = {}
        )
    }
}
