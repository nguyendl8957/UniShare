package vn.unishare.feature.seller.ui

import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.core.view.WindowCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vn.unishare.R
import androidx.lifecycle.compose.LifecycleResumeEffect
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme

private val ManageBg = Color(0xFFF8FBFF)
private val ManageHeaderTop = Color(0xFF0756B8)
private val ManageHeaderBottom = Color(0xFF004CAE)
private val ManageAccentBlue = Color(0xFF005BDA)
private val ManageTabIdleBg = Color(0xFFF3F7FF)
private val ManageBorderBlue = Color(0xFFE6EEFC)
private val ManageActionBg = Color(0xFFF7FAFF)
private val ManageActionBorder = Color(0xFFC7D9FF)
private val ManageDeleteBorder = Color(0xFFE2D9E8)
private val ManageDeleteRed = Color(0xFFFF1424)
private val ManageNavy = Color(0xFF10194C)
private val ManageMutedBlue = Color(0xFF5369A3)
private val ManageStatusActiveBg = Color(0xFFD9F7EF)
private val ManageStatusActiveText = Color(0xFF008D6D)
private val ManageStatusArchivedBg = Color(0xFFF1F5F9)
private val ManageStatusArchivedText = Color(0xFF64748B)

// Màn hình quản lý danh sách tài liệu đang bán của Seller
@Composable
fun SellerDocumentsScreen(
    viewModel: UploadWizardViewModel,
    onBackClick: () -> Unit,
    onCreateDocumentClick: () -> Unit,
    onRevenueTabClick: () -> Unit = {},
    onViewDetailClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    onBottomNavSelected: ((String) -> Unit)? = null
) {
    val state by viewModel.documentsState.collectAsState()
    LifecycleResumeEffect(viewModel) {
        viewModel.loadSellerDocuments()
        onPauseOrDispose {}
    }

    SellerDocumentsContent(
        state = state,
        onBackClick = onBackClick,
        onCreateDocumentClick = onCreateDocumentClick,
        onRevenueTabClick = onRevenueTabClick,
        onViewDetailClick = { doc ->
            viewModel.dismissActionSheet()
            onViewDetailClick(doc.docId)
        },
        onSelectSort = viewModel::selectDocumentsSort,
        onOpenActionSheet = viewModel::openActionSheet,
        onDismissActionSheet = viewModel::dismissActionSheet,
        onEditClick = viewModel::openEditDialog,
        onUpdateEditDraft = viewModel::updateEditDraft,
        onDismissEditDialog = viewModel::dismissEditDialog,
        onSaveEditMetadata = viewModel::saveEditMetadata,
        onRequestArchive = viewModel::requestArchiveConfirmation,
        onUnarchiveClick = viewModel::unarchiveDocument,
        onDismissArchiveConfirm = viewModel::dismissArchiveConfirmation,
        onConfirmArchive = viewModel::confirmArchiveDocument,
        onRetryLoad = viewModel::loadSellerDocuments,
        onRefreshConflict = viewModel::refreshAfterConflict,
        onDismissFeedback = viewModel::dismissManagementFeedback,
        modifier = modifier,
        onBottomNavSelected = onBottomNavSelected
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerDocumentsContent(
    state: SellerDocumentsUiState,
    onBackClick: () -> Unit,
    onCreateDocumentClick: () -> Unit,
    onRevenueTabClick: () -> Unit,
    onViewDetailClick: (SellerDocumentItem) -> Unit,
    onSelectSort: (SellerDocumentSort) -> Unit,
    onOpenActionSheet: (SellerDocumentItem) -> Unit,
    onDismissActionSheet: () -> Unit,
    onEditClick: (SellerDocumentItem) -> Unit,
    onUpdateEditDraft: (
        title: String?,
        category: String?,
        subject: String?,
        school: String?,
        priceText: String?,
        description: String?
    ) -> Unit,
    onDismissEditDialog: () -> Unit,
    onSaveEditMetadata: () -> Unit,
    onRequestArchive: (SellerDocumentItem, SellerArchiveTrigger) -> Unit,
    onUnarchiveClick: (SellerDocumentItem) -> Unit = {},
    onDismissArchiveConfirm: () -> Unit,
    onConfirmArchive: () -> Unit,
    onRetryLoad: () -> Unit,
    onRefreshConflict: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    onBottomNavSelected: ((String) -> Unit)? = null
) {
    val window = LocalActivity.current?.window
    val view = LocalView.current
    if (window != null && !view.isInEditMode) {
        LifecycleResumeEffect(window, view) {
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onPauseOrDispose { controller.isAppearanceLightStatusBars = previous }
        }
    }

    val totalCount = (state.listState as? SellerDocumentsListState.Content)?.items?.size ?: 0

    val listHasDocuments = state.listState is SellerDocumentsListState.Content
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ManageBg,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SellerManageAppBar(onBackClick = onBackClick)
                SellerSectionTabs(onRevenueTabClick = onRevenueTabClick)
            }
        },
        bottomBar = {
            if (onBottomNavSelected != null) {
                UniBottomBar(
                    selectedRoute = "manage",
                    onItemSelected = onBottomNavSelected,
                    mode = UniAppMode.SELLER
                )
            }
        },
        floatingActionButton = {
            if (listHasDocuments) {
                Surface(
                    color = ManageAccentBlue,
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onCreateDocumentClick)
                        .testTag("fab_create_document")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_plus),
                            contentDescription = "Đăng bán tài liệu mới",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Đăng bán",
                            color = Color.White,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Thông báo thao tác (đang lưu / thành công / xung đột phiên bản / lỗi)
            SellerManagementFeedbackBanner(
                feedback = state.feedback,
                onRefreshConflict = onRefreshConflict,
                onDismiss = onDismissFeedback
            )

            // Dòng tổng số tài liệu và sắp xếp
            SellerListControlsRow(
                totalCount = totalCount,
                sortOption = state.sortOption,
                onSelectSort = onSelectSort
            )

            when (val listState = state.listState) {
                is SellerDocumentsListState.Loading -> {
                    UiFeedback(state = UiFeedbackState.Skeleton)
                }

                is SellerDocumentsListState.Error -> {
                    UiFeedback(
                        state = UiFeedbackState.Error(listState.message),
                        onRetry = onRetryLoad
                    )
                }

                is SellerDocumentsListState.Empty -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .testTag("seller_docs_empty")
                    ) {
                        UiFeedback(
                            state = UiFeedbackState.Empty(
                                message = "Bạn chưa có tài liệu nào đang bán",
                                subtitle = "Tải lên tài liệu đầu tiên của bạn.",
                                illustrationRes = R.drawable.ic_seller_empty_illustration
                            ),
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onCreateDocumentClick,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ManageAccentBlue)
                        ) {
                            Text("Đăng tài liệu ngay", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                is SellerDocumentsListState.Content -> {
                    LazyColumn(
                        contentPadding = PaddingValues(top = 6.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("seller_documents_list")
                    ) {
                        items(
                            items = listState.items,
                            key = { it.docId }
                        ) { doc ->
                            SellerDocumentCard(
                                document = doc,
                                onMoreClick = { onOpenActionSheet(doc) },
                                onEditClick = { onEditClick(doc) },
                                onHideClick = { onRequestArchive(doc, SellerArchiveTrigger.HIDE) },
                                onUnhideClick = { onUnarchiveClick(doc) },
                                onRemoveClick = { onRequestArchive(doc, SellerArchiveTrigger.REMOVE) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Bottom Sheet thao tác nhanh trên tài liệu
    val activeSheetDoc = state.actionSheetDocument
    if (activeSheetDoc != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissActionSheet,
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            SellerDocumentActionSheet(
                document = activeSheetDoc,
                onViewDetail = { onViewDetailClick(activeSheetDoc) },
                onEdit = { onEditClick(activeSheetDoc) },
                onHide = { onRequestArchive(activeSheetDoc, SellerArchiveTrigger.HIDE) },
                onUnhide = { onUnarchiveClick(activeSheetDoc) },
                onRemove = { onRequestArchive(activeSheetDoc, SellerArchiveTrigger.REMOVE) },
                onCancel = onDismissActionSheet
            )
        }
    }

    // Hộp thoại xác nhận ẩn/lưu trữ tài liệu (bảo toàn quyền đọc cho người đã mua)
    val confirmArchive = state.confirmArchive
    if (confirmArchive != null) {
        val isRemove = confirmArchive.trigger == SellerArchiveTrigger.REMOVE
        AlertDialog(
            onDismissRequest = onDismissArchiveConfirm,
            modifier = Modifier.testTag("dialog_confirm_archive"),
            title = {
                Text(
                    text = if (isRemove) "Xác nhận gỡ khỏi danh mục bán?" else "Xác nhận ẩn tài liệu?",
                    color = ManageNavy,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Tài liệu \"${confirmArchive.document.title}\" sẽ chuyển sang trạng thái Đã ẩn (lưu trữ) và ngừng nhận đơn mua mới. Người mua đã thanh toán trước đó vẫn giữ quyền truy cập phiên bản họ đã mua.",
                    color = ManageMutedBlue,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmArchive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRemove) ManageDeleteRed else ManageAccentBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("btn_confirm_archive")
                ) {
                    Text(if (isRemove) "Xác nhận lưu trữ" else "Xác nhận ẩn")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissArchiveConfirm,
                    modifier = Modifier.testTag("btn_cancel_archive")
                ) {
                    Text("Hủy", color = ManageMutedBlue)
                }
            }
        )
    }

    // Hộp thoại chỉnh sửa thông tin tài liệu
    val editDraft = state.editDraft
    if (editDraft != null) {
        SellerEditMetadataDialog(
            draft = editDraft,
            onUpdate = onUpdateEditDraft,
            onDismiss = onDismissEditDialog,
            onSave = onSaveEditMetadata
        )
    }
}

@Composable
private fun SellerManageAppBar(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(ManageHeaderTop, ManageHeaderBottom)))
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("btn_seller_manage_back")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_arrow_left),
                    contentDescription = "Quay lại",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "UniShare",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quản lý tài liệu của bạn",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.5.sp
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.nav_seller_documents),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SellerSectionTabs(onRevenueTabClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(ManageBg)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Surface(
            color = ManageAccentBlue,
            shape = RoundedCornerShape(11.dp),
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Tài liệu đang bán",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            color = ManageTabIdleBg,
            shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, ManageBorderBlue),
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .clickable(onClick = onRevenueTabClick)
                .testTag("tab_seller_revenue")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Doanh thu",
                    color = ManageNavy,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SellerListControlsRow(
    totalCount: Int,
    sortOption: SellerDocumentSort,
    onSelectSort: (SellerDocumentSort) -> Unit
) {
    var sortExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append("Tổng: ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("$totalCount tài liệu")
                }
            },
            color = ManageNavy,
            fontSize = 13.sp
        )

        Box {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(7.dp),
                border = BorderStroke(1.dp, ManageBorderBlue),
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable { sortExpanded = true }
                    .testTag("btn_seller_sort")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_list_filter),
                        contentDescription = null,
                        tint = ManageNavy,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = sortOption.label,
                        color = ManageNavy,
                        fontSize = 12.5.sp
                    )
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_down),
                        contentDescription = null,
                        tint = ManageNavy,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                SellerDocumentSort.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label, fontSize = 13.sp) },
                        onClick = {
                            sortExpanded = false
                            onSelectSort(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SellerDocumentCard(
    document: SellerDocumentItem,
    onMoreClick: () -> Unit,
    onEditClick: () -> Unit,
    onHideClick: () -> Unit,
    onUnhideClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val isArchived = document.status == SellerDocumentStatus.ARCHIVED

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, ManageBorderBlue),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("seller_doc_card_${document.docId}")
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Ảnh bìa tài liệu kèm nhãn PDF
                Box(
                    modifier = Modifier
                        .width(84.dp)
                        .height(98.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFEFF6FF), Color(0xFFDCEAFE)))
                        )
                ) {
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF93C5FD))
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFBFDBFE))
                        )
                    }
                    Surface(
                        color = ManageAccentBlue,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(5.dp)
                    ) {
                        Text(
                            text = "PDF",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = document.title,
                            color = ManageNavy,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onMoreClick,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("btn_more_${document.docId}")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_more_vert),
                                contentDescription = "Mở thao tác tài liệu",
                                tint = ManageMutedBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "${document.subject} • ${document.school}",
                        color = ManageMutedBlue,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatSellerPriceVnd(document.priceVnd),
                            color = ManageAccentBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        SellerStatusChip(status = document.status)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = null,
                            tint = ManageMutedBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = document.publishedDateLabel,
                            color = ManageMutedBlue,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hàng 3 nút hành động: Sửa - Ẩn - Xóa
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CardActionButton(
                    label = "Sửa",
                    iconRes = R.drawable.ic_edit,
                    textColor = ManageAccentBlue,
                    iconTint = ManageAccentBlue,
                    containerColor = ManageActionBg,
                    borderColor = ManageActionBorder,
                    enabled = true,
                    onClick = onEditClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_edit_${document.docId}")
                )

                CardActionButton(
                    label = if (isArchived) "Bỏ ẩn" else "Ẩn",
                    iconRes = if (isArchived) R.drawable.ic_eye else R.drawable.ic_eye_off,
                    textColor = if (isArchived) ManageAccentBlue else ManageNavy,
                    iconTint = if (isArchived) ManageAccentBlue else ManageNavy,
                    containerColor = ManageActionBg,
                    borderColor = ManageActionBorder,
                    enabled = true,
                    onClick = if (isArchived) onUnhideClick else onHideClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_hide_${document.docId}")
                )

                CardActionButton(
                    label = "Lưu trữ",
                    iconRes = R.drawable.ic_delete,
                    textColor = if (isArchived) ManageStatusArchivedText else ManageDeleteRed,
                    iconTint = if (isArchived) ManageStatusArchivedText else ManageDeleteRed,
                    containerColor = Color.White,
                    borderColor = ManageDeleteBorder,
                    enabled = !isArchived,
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_delete_${document.docId}")
                )
            }
        }
    }
}

@Composable
private fun CardActionButton(
    label: String,
    iconRes: Int,
    textColor: Color,
    iconTint: Color,
    containerColor: Color,
    borderColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(7.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SellerStatusChip(status: SellerDocumentStatus) {
    val (bg, fg) = when (status) {
        SellerDocumentStatus.PUBLISHED -> ManageStatusActiveBg to ManageStatusActiveText
        SellerDocumentStatus.ARCHIVED -> ManageStatusArchivedBg to ManageStatusArchivedText
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = status.label,
            color = fg,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun SellerDocumentActionSheet(
    document: SellerDocumentItem,
    onViewDetail: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit,
    onUnhide: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit
) {
    val isArchived = document.status == SellerDocumentStatus.ARCHIVED

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("sheet_seller_actions")
    ) {
        // Tóm tắt tài liệu đang chọn
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(62.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFEFF6FF))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_file_text),
                    contentDescription = null,
                    tint = ManageAccentBlue,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = document.title,
                    color = ManageNavy,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = formatSellerPriceVnd(document.priceVnd),
                        color = ManageAccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    SellerStatusChip(status = document.status)
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            if (!isArchived) ActionSheetMenuRow(
                label = "Xem chi tiết",
                iconRes = R.drawable.ic_eye,
                iconTint = ManageNavy,
                textColor = ManageNavy,
                enabled = true,
                onClick = onViewDetail,
                modifier = Modifier.testTag("sheet_action_view")
            )
            ActionSheetMenuRow(
                label = "Chỉnh sửa",
                iconRes = R.drawable.ic_edit,
                iconTint = ManageNavy,
                textColor = ManageNavy,
                enabled = true,
                onClick = onEdit,
                modifier = Modifier.testTag("sheet_action_edit")
            )
            ActionSheetMenuRow(
                label = if (isArchived) "Bỏ ẩn tài liệu" else "Ẩn tài liệu",
                iconRes = if (isArchived) R.drawable.ic_eye else R.drawable.ic_eye_off,
                iconTint = if (isArchived) ManageAccentBlue else ManageNavy,
                textColor = if (isArchived) ManageAccentBlue else ManageNavy,
                enabled = true,
                onClick = if (isArchived) onUnhide else onHide,
                modifier = Modifier.testTag("sheet_action_hide")
            )
            ActionSheetMenuRow(
                label = "Lưu trữ tài liệu",
                iconRes = R.drawable.ic_delete,
                iconTint = if (isArchived) ManageStatusArchivedText else ManageDeleteRed,
                textColor = if (isArchived) ManageStatusArchivedText else ManageNavy,
                enabled = !isArchived,
                onClick = onRemove,
                modifier = Modifier.testTag("sheet_action_delete")
            )
        }

        OutlinedButton(
            onClick = onCancel,
            shape = RoundedCornerShape(7.dp),
            border = BorderStroke(1.dp, ManageAccentBlue),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("sheet_action_cancel")
        ) {
            Text(
                text = "Hủy",
                color = ManageAccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ActionSheetMenuRow(
    label: String,
    iconRes: Int,
    iconTint: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    HorizontalDivider(color = ManageBorderBlue)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = 13.5.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = ManageMutedBlue,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SellerManagementFeedbackBanner(
    feedback: SellerManagementFeedback,
    onRefreshConflict: () -> Unit,
    onDismiss: () -> Unit
) {
    when (feedback) {
        is SellerManagementFeedback.None -> Unit

        is SellerManagementFeedback.Saving -> {
            LinearProgressIndicator(
                color = ManageAccentBlue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        is SellerManagementFeedback.Success -> {
            Surface(
                color = ManageStatusActiveBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("banner_manage_success")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = feedback.message,
                        color = ManageStatusActiveText,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Đóng", color = ManageStatusActiveText, fontSize = 12.sp)
                    }
                }
            }
        }

        is SellerManagementFeedback.VersionConflict -> {
            Surface(
                color = Color(0xFFFFF7ED),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("banner_version_conflict")
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_warning),
                            contentDescription = null,
                            tint = Color(0xFFEA580C),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = feedback.message,
                            color = Color(0xFF9A3412),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = onRefreshConflict,
                        shape = RoundedCornerShape(7.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ManageAccentBlue),
                        modifier = Modifier.testTag("btn_refresh_conflict")
                    ) {
                        Text("Làm mới danh sách", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        is SellerManagementFeedback.Error -> {
            Surface(
                color = Color(0xFFFEF2F2),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .testTag("banner_manage_error")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = feedback.message,
                        color = Color(0xFF991B1B),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Đóng", color = Color(0xFF991B1B), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerEditMetadataDialog(
    draft: SellerEditDraft,
    onUpdate: (
        title: String?,
        category: String?,
        subject: String?,
        school: String?,
        priceText: String?,
        description: String?
    ) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dialog_edit_document"),
        title = {
            Text(
                text = "Chỉnh sửa thông tin tài liệu",
                color = ManageNavy,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onUpdate(it, null, null, null, null, null) },
                    label = { Text("Tiêu đề") },
                    isError = draft.titleError != null,
                    supportingText = draft.titleError?.let { err -> { Text(err) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_title")
                )
                OutlinedTextField(
                    value = draft.priceText,
                    onValueChange = { onUpdate(null, null, null, null, it, null) },
                    label = { Text("Giá bán (VNĐ)") },
                    isError = draft.priceError != null,
                    supportingText = draft.priceError?.let { err -> { Text(err) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_price")
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { onUpdate(null, null, null, null, null, it) },
                    label = { Text("Mô tả") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_description")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = ManageAccentBlue),
                modifier = Modifier.testTag("btn_save_edit")
            ) {
                Text("Lưu thay đổi", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = ManageMutedBlue)
            }
        }
    )
}

@Preview(widthDp = 360, heightDp = 800, name = "Seller Documents - 360dp")
@Preview(widthDp = 412, heightDp = 860, name = "Seller Documents - 412dp")
@Preview(widthDp = 412, heightDp = 860, fontScale = 1.5f, name = "Seller Documents - Large Font")
@Composable
private fun PreviewSellerDocuments() {
    val sampleState = SellerDocumentsUiState(
        listState = SellerDocumentsListState.Content(
            FakeDocumentUploadRepository.defaultSellerDocuments()
        )
    )
    UniShareTheme(mode = UniAppMode.SELLER) {
        SellerDocumentsContent(
            state = sampleState,
            onBackClick = {},
            onCreateDocumentClick = {},
            onRevenueTabClick = {},
            onViewDetailClick = {},
            onSelectSort = {},
            onOpenActionSheet = {},
            onDismissActionSheet = {},
            onEditClick = {},
            onUpdateEditDraft = { _, _, _, _, _, _ -> },
            onDismissEditDialog = {},
            onSaveEditMetadata = {},
            onRequestArchive = { _, _ -> },
            onDismissArchiveConfirm = {},
            onConfirmArchive = {},
            onRetryLoad = {},
            onRefreshConflict = {},
            onDismissFeedback = {}
        )
    }
}
