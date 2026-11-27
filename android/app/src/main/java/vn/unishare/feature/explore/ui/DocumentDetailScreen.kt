package vn.unishare.feature.explore.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.unishare.R
import vn.unishare.core.ui.components.UniBottomBar
import vn.unishare.core.ui.components.UniButton
import vn.unishare.core.ui.feedback.UiFeedback
import vn.unishare.core.ui.feedback.UiFeedbackState
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

private val DetailPrimaryGreen = Color(0xFF2E7D32)
private val DetailCtaGreen = Color(0xFF17823B)
private val DetailChipBg = Color(0xFFEEF6EE)
private val DetailTitleInk = Color(0xFF20223A)
private val DetailMutedInk = Color(0xFF727781)
private val DetailBadgeBlue = Color(0xFF1688F8)
private val DetailSellerGradientTop = Color(0xFFF0F6EE)
private val DetailSellerGradientBottom = Color(0xFFF3F8F5)
private val DetailDividerColor = Color(0xFFD7DDDA)
private val GalleryDarkBg = Color(0xFF171717)
private val GalleryInactiveBorder = Color(0xFF4C4C4C)

@Composable
fun DocumentDetailScreen(
    docId: String,
    viewModel: ExploreViewModel,
    onBackClick: () -> Unit,
    onBuyClick: (String) -> Unit,
    onSellerClick: (String) -> Unit,
    onBottomNavSelected: (String) -> Unit = {},
    onShareClick: (DocumentPublicDetail) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(docId) {
        if (uiState.selectedDocId != docId || uiState.detailState is DocumentDetailState.Idle) {
            viewModel.openDocumentDetail(docId)
        }
    }

    val handleBack = {
        if (uiState.isPreviewGalleryOpen) {
            viewModel.closePreviewGallery()
        } else {
            viewModel.closeDocumentDetail()
            onBackClick()
        }
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    val detailState = uiState.detailState
    if (uiState.isPreviewGalleryOpen && detailState is DocumentDetailState.Content) {
        DocumentPreviewGallery(
            previewRefs = detailState.detail.previewRefs,
            activeIndex = detailState.activePreviewIndex,
            brokenPreviewIds = detailState.brokenPreviewIds,
            onPageChanged = viewModel::selectPreviewPage,
            onPreviewLoadFailed = viewModel::markPreviewImageBroken,
            onRetryPreview = viewModel::retryPreviewImage,
            onClose = viewModel::closePreviewGallery,
            modifier = modifier
        )
    } else {
        DocumentDetailContent(
            detailState = detailState,
            onBackClick = handleBack,
            onRetryDetail = viewModel::retryDocumentDetail,
            onBuyClick = onBuyClick,
            onSellerClick = onSellerClick,
            onOpenGallery = { index -> viewModel.openPreviewGallery(index) },
            onSelectPreviewIndex = viewModel::selectPreviewPage,
            onPreviewLoadFailed = viewModel::markPreviewImageBroken,
            onRetryPreview = viewModel::retryPreviewImage,
            onToggleFavorite = viewModel::toggleFavorite,
            onShareClick = onShareClick,
            onBottomNavSelected = onBottomNavSelected,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DocumentDetailContent(
    detailState: DocumentDetailState,
    onBackClick: () -> Unit,
    onRetryDetail: () -> Unit,
    onBuyClick: (String) -> Unit,
    onSellerClick: (String) -> Unit,
    onOpenGallery: (Int) -> Unit,
    onSelectPreviewIndex: (Int) -> Unit,
    onPreviewLoadFailed: (String) -> Unit,
    onRetryPreview: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: (DocumentPublicDetail) -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val contentDetail = (detailState as? DocumentDetailState.Content)?.detail
    val isFavorite = (detailState as? DocumentDetailState.Content)?.isFavorite == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.White,
        topBar = {
            DetailBrandHeader(
                showActions = contentDetail != null,
                isFavorite = isFavorite,
                onBackClick = onBackClick,
                onToggleFavorite = onToggleFavorite,
                onShareClick = { contentDetail?.let(onShareClick) }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .navigationBarsPadding()
            ) {
                if (contentDetail != null) {
                    HorizontalDivider(color = Color(0xFFE6E9EF))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { onBuyClick(contentDetail.docId) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DetailCtaGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .widthIn(min = 190.dp)
                                .heightIn(min = 48.dp)
                                .testTag("btn_buy_now")
                                .semantics {
                                    contentDescription = "Mua ngay tài liệu ${contentDetail.title}"
                                }
                        ) {
                            Text(
                                text = "Mua ngay",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                UniBottomBar(
                    selectedRoute = "explore",
                    onItemSelected = onBottomNavSelected,
                    mode = UniAppMode.BUYER
                )
            }
        }
    ) { innerPadding ->
        when (detailState) {
            is DocumentDetailState.Idle,
            is DocumentDetailState.Loading -> {
                UiFeedback(
                    state = UiFeedbackState.Loading,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is DocumentDetailState.Error -> {
                UiFeedback(
                    state = UiFeedbackState.Error(detailState.message),
                    onRetry = onRetryDetail,
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is DocumentDetailState.NotFoundOrArchived -> {
                MissingOrArchivedDocumentView(
                    message = detailState.message,
                    onBackClick = onBackClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            is DocumentDetailState.Content -> {
                val detail = detailState.detail
                val previews = detail.previewRefs
                val pagerState = rememberPagerState(
                    initialPage = detailState.activePreviewIndex.coerceIn(0, (previews.size - 1).coerceAtLeast(0)),
                    pageCount = { previews.size.coerceAtLeast(1) }
                )

                LaunchedEffect(pagerState.settledPage) {
                    if (previews.isNotEmpty() && pagerState.settledPage != detailState.activePreviewIndex) {
                        onSelectPreviewIndex(pagerState.settledPage)
                    }
                }

                LaunchedEffect(detailState.activePreviewIndex) {
                    if (previews.isNotEmpty() && detailState.activePreviewIndex != pagerState.currentPage) {
                        pagerState.scrollToPage(detailState.activePreviewIndex.coerceIn(0, previews.lastIndex))
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (previews.isEmpty()) {
                        BrokenPreviewFallback(
                            message = "Tài liệu chưa có ảnh xem trước công khai",
                            isDark = false,
                            onRetry = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(168.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        val currentPreview = previews.getOrNull(pagerState.currentPage)
                        val isCurrentBroken = currentPreview != null &&
                            currentPreview.id in detailState.brokenPreviewIds
                        val heroModifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (!isCurrentBroken) {
                                    Modifier
                                        .clickable { onOpenGallery(pagerState.currentPage) }
                                        .testTag("btn_open_gallery")
                                        .semantics {
                                            contentDescription = "Mở bộ sưu tập ảnh xem trước"
                                        }
                                } else {
                                    Modifier
                                }
                            )

                        Box(modifier = heroModifier) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val preview = previews[page]
                                DocumentPreviewImage(
                                    preview = preview,
                                    isMarkedBroken = preview.id in detailState.brokenPreviewIds,
                                    accentCategory = detail.category,
                                    isDark = false,
                                    showRetryButton = true,
                                    onLoadFailed = { onPreviewLoadFailed(preview.id) },
                                    onRetry = { onRetryPreview(preview.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Surface(
                                color = Color(0xAD303033),
                                shape = RoundedCornerShape(100.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1}/${previews.size}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                        .testTag("detail_image_counter")
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 6.dp),
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                            previews.forEachIndexed { idx, _ ->
                                val active = idx == pagerState.currentPage
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 6.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(if (active) DetailPrimaryGreen else Color(0xFFCBD2DD))
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail.title,
                        color = DetailTitleInk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 23.sp,
                        modifier = Modifier.testTag("detail_title")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = formatPriceVnd(detail.priceVnd),
                            color = DetailPrimaryGreen,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = DetailChipBg,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_file_text),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = detail.formatLabel,
                                    color = DetailPrimaryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    SellerPublicCard(
                        seller = detail.seller,
                        onClick = { onSellerClick(detail.seller.sellerId) }
                    )

                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Mô tả tài liệu",
                        color = DetailTitleInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = detail.description,
                        color = DetailMutedInk,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp
                    )

                    if (detail.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            detail.tags.forEach { tag ->
                                Surface(
                                    color = DetailChipBg,
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = DetailPrimaryGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_file_text),
                            contentDescription = null,
                            tint = DetailTitleInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Xem trước nội dung",
                            color = DetailTitleInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        itemsIndexed(
                            items = previews,
                            key = { _, item -> item.id }
                        ) { index, preview ->
                            val isSelected = index == pagerState.currentPage
                            Column(
                                modifier = Modifier
                                    .width(104.dp)
                                    .clickable { onOpenGallery(index) }
                                    .testTag("detail_preview_thumb_$index"),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(104.dp)
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) DetailPrimaryGreen else Color(0xFFE2E6ED),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                ) {
                                    DocumentPreviewImage(
                                        preview = preview,
                                        isMarkedBroken = preview.id in detailState.brokenPreviewIds,
                                        accentCategory = detail.category,
                                        isDark = false,
                                        compact = true,
                                        showRetryButton = false,
                                        onLoadFailed = { onPreviewLoadFailed(preview.id) },
                                        onRetry = { onRetryPreview(preview.id) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(
                                    text = preview.caption,
                                    color = DetailMutedInk,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailBrandHeader(
    showActions: Boolean,
    isFavorite: Boolean,
    onBackClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShareClick: () -> Unit
) {
    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_detail_back")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Quay lại khám phá",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.unishare_wordmark),
                    contentDescription = "Biểu trưng UniShare",
                    modifier = Modifier
                        .width(120.dp)
                        .height(38.dp)
                )
            }

            if (showActions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_detail_favorite")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_star),
                            contentDescription = if (isFavorite) "Bỏ lưu tài liệu" else "Lưu tài liệu",
                            tint = if (isFavorite) Color(0xFFF9B52B) else Color(0xFF334155),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onShareClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("btn_detail_share")
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_share),
                            contentDescription = "Chia sẻ tài liệu",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerPublicCard(
    seller: SellerPublicSummary,
    onClick: () -> Unit
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(DetailSellerGradientTop, DetailSellerGradientBottom)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(gradient)
            .border(1.dp, Color(0xFFE3ECE4), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("card_seller_public")
            .semantics {
                contentDescription = "Hồ sơ công khai người bán ${seller.displayName}"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE4E7E5))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = seller.displayName,
                            color = DetailTitleInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!seller.badge.isNullOrBlank()) {
                            Surface(
                                color = DetailBadgeBlue,
                                shape = RoundedCornerShape(100.dp)
                            ) {
                                Text(
                                    text = seller.badge,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = seller.school,
                        color = DetailMutedInk,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_star),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (seller.reviewCount == 0) "Chưa có đánh giá" else seller.rating.toString(),
                    color = DetailPrimaryGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "(${seller.reviewCount})",
                    color = DetailMutedInk,
                    fontSize = 11.sp
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(11.dp)
                        .background(DetailDividerColor)
                )
                Text(
                    text = if (seller.soldCount == 0) "Chưa có số liệu bán" else "Đã bán ${seller.soldCount}",
                    color = DetailMutedInk,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun MissingOrArchivedDocumentView(
    message: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(24.dp)
            .testTag("detail_not_found")
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_warning),
            contentDescription = null,
            tint = Color(0xFFD97706),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Tài liệu không khả dụng",
            color = DetailTitleInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = DetailMutedInk,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        UniButton(
            text = "Quay lại khám phá",
            onClick = onBackClick,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .testTag("btn_back_from_missing")
        )
    }
}

@Composable
fun DocumentPreviewGallery(
    previewRefs: List<PreviewImageRef>,
    activeIndex: Int,
    brokenPreviewIds: Set<String>,
    onPageChanged: (Int) -> Unit,
    onPreviewLoadFailed: (String) -> Unit,
    onRetryPreview: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeInitial = activeIndex.coerceIn(0, (previewRefs.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { previewRefs.size.coerceAtLeast(1) }
    )
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.settledPage) {
        if (previewRefs.isNotEmpty() && pagerState.settledPage != activeIndex) {
            onPageChanged(pagerState.settledPage)
        }
    }

    LaunchedEffect(activeIndex) {
        if (previewRefs.isNotEmpty() && activeIndex != pagerState.currentPage) {
            pagerState.scrollToPage(activeIndex.coerceIn(0, previewRefs.lastIndex))
        }
    }

    Surface(
        color = GalleryDarkBg,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("preview_gallery_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_close_gallery")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Đóng xem ảnh",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = if (previewRefs.isEmpty()) "0/0" else "${pagerState.currentPage + 1}/${previewRefs.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("gallery_page_counter")
                )

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "Tùy chọn ảnh xem trước",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tải lại trang hiện tại") },
                            onClick = {
                                showMenu = false
                                previewRefs.getOrNull(pagerState.currentPage)?.let {
                                    onRetryPreview(it.id)
                                }
                            }
                        )
                    }
                }
            }

            // Vùng ảnh xem trước chính
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                if (previewRefs.isEmpty()) {
                    BrokenPreviewFallback(
                        message = "Không có ảnh xem trước",
                        isDark = true,
                        onRetry = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val preview = previewRefs[page]
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                DocumentPreviewImage(
                                    preview = preview,
                                    isMarkedBroken = preview.id in brokenPreviewIds,
                                    accentCategory = ExploreCategory.SUMMARY_NOTE,
                                    isDark = true,
                                    showRetryButton = true,
                                    onLoadFailed = { onPreviewLoadFailed(preview.id) },
                                    onRetry = { onRetryPreview(preview.id) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = preview.caption,
                                color = Color(0xFFD1D5DB),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (previewRefs.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = previewRefs,
                        key = { _, item -> "film_${item.id}" }
                    ) { index, preview ->
                        val isSelected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) DetailPrimaryGreen else GalleryInactiveBorder,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onPageChanged(index)
                                }
                                .testTag("gallery_thumb_$index")
                                .semantics {
                                    contentDescription = "Xem ảnh trang ${index + 1}"
                                }
                        ) {
                            DocumentPreviewImage(
                                preview = preview,
                                isMarkedBroken = preview.id in brokenPreviewIds,
                                accentCategory = ExploreCategory.SUMMARY_NOTE,
                                isDark = true,
                                compact = true,
                                showRetryButton = false,
                                onLoadFailed = { onPreviewLoadFailed(preview.id) },
                                onRetry = { onRetryPreview(preview.id) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface RemoteBitmapState {
    data object Loading : RemoteBitmapState
    data class Loaded(val bitmap: ImageBitmap) : RemoteBitmapState
    data object Failed : RemoteBitmapState
}

@Composable
internal fun DocumentPreviewImage(
    preview: PreviewImageRef,
    isMarkedBroken: Boolean,
    accentCategory: ExploreCategory,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showRetryButton: Boolean = false,
    onLoadFailed: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    var retryNonce by remember(preview.id) { mutableIntStateOf(0) }
    val isRemoteHttp = preview.imageUrl.startsWith("http://") || preview.imageUrl.startsWith("https://")
    val isRemoteStorage = preview.imageUrl.startsWith("storage://")
    val effectiveBroken = isMarkedBroken || (preview.isBroken && retryNonce == 0)

    if (effectiveBroken) {
        BrokenPreviewFallback(
            message = if (compact) "Ảnh lỗi" else "Không thể tải ảnh xem trước",
            isDark = isDark,
            compact = compact,
            onRetry = if (showRetryButton) {
                {
                    retryNonce++
                    onRetry()
                }
            } else {
                null
            },
            modifier = modifier
        )
        return
    }

    if (isRemoteHttp || isRemoteStorage) {
        val remoteState by produceState<RemoteBitmapState>(
            initialValue = RemoteBitmapState.Loading,
            key1 = preview.imageUrl,
            key2 = retryNonce
        ) {
            value = withContext(Dispatchers.IO) {
                try {
                    val bitmap = if (isRemoteStorage) {
                        val path = preview.imageUrl.removePrefix("storage://")
                        val bytes = FirebaseStorage.getInstance()
                            .getReference(path)
                            .getBytes(5L * 1024 * 1024)
                            .await()

                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } else {
                        val connection = (URL(preview.imageUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5_000
                            readTimeout = 5_000
                            instanceFollowRedirects = true
                        }
                        connection.inputStream.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    }

                    if (bitmap != null) {
                        RemoteBitmapState.Loaded(bitmap.asImageBitmap())
                    } else {
                        RemoteBitmapState.Failed
                    }
                } catch (_: Exception) {
                    RemoteBitmapState.Failed
                }
            }
        }

        when (val state = remoteState) {
            is RemoteBitmapState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = modifier.background(if (isDark) Color(0xFF262626) else Color(0xFFF1F5F2))
                ) {
                    CircularProgressIndicator(
                        color = DetailPrimaryGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(if (compact) 18.dp else 28.dp)
                    )
                }
            }

            is RemoteBitmapState.Loaded -> {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = preview.caption,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                )
            }

            is RemoteBitmapState.Failed -> {
                LaunchedEffect(preview.id, retryNonce) {
                    onLoadFailed()
                }
                BrokenPreviewFallback(
                    message = if (compact) "Ảnh lỗi" else "Không thể tải ảnh xem trước",
                    isDark = isDark,
                    compact = compact,
                    onRetry = if (showRetryButton) {
                        {
                            retryNonce++
                            onRetry()
                        }
                    } else {
                        null
                    },
                    modifier = modifier
                )
            }
        }
    } else {
        FakeDocumentPreviewSurface(
            preview = preview,
            accentCategory = accentCategory,
            compact = compact,
            modifier = modifier
        )
    }
}

@Composable
private fun BrokenPreviewFallback(
    message: String,
    isDark: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val containerColor = if (isDark) Color(0xFF262626) else Color(0xFFF3F4F6)
    val textColor = if (isDark) Color(0xFFE5E7EB) else Color(0xFF4B5563)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(containerColor)
            .padding(if (compact) 6.dp else 16.dp)
            .testTag(if (compact) "preview_broken_thumb" else "preview_broken_fallback")
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_warning),
            contentDescription = "Ảnh xem trước bị lỗi",
            tint = Color(0xFFD97706),
            modifier = Modifier.size(if (compact) 20.dp else 34.dp)
        )
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = message,
            color = textColor,
            fontSize = if (compact) 9.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (onRetry != null && !compact) {
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DetailPrimaryGreen),
                modifier = Modifier.testTag("btn_retry_preview")
            ) {
                Text(
                    text = "Thử lại",
                    color = DetailPrimaryGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun FakeDocumentPreviewSurface(
    preview: PreviewImageRef,
    accentCategory: ExploreCategory,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val (bgTop, bgBottom, accent) = when (accentCategory) {
        ExploreCategory.SOURCE_CODE -> Triple(Color(0xFF1E293B), Color(0xFF0F172A), Color(0xFF38BDF8))
        ExploreCategory.CHEAT_SHEET -> Triple(Color(0xFFECFDF5), Color(0xFFD1FAE5), DetailPrimaryGreen)
        ExploreCategory.PROJECT -> Triple(Color(0xFFEFF6FF), Color(0xFFDBEAFE), Color(0xFF2563EB))
        else -> Triple(Color(0xFFF8FAF8), Color(0xFFE9F3EC), DetailPrimaryGreen)
    }
    val isCodeSurface = accentCategory == ExploreCategory.SOURCE_CODE
    val lineColor = if (isCodeSurface) Color(0xFF64748B) else Color(0xFFBBD0C1)
    val titleColor = if (isCodeSurface) Color(0xFFF8FAFC) else Color(0xFF1F2937)

    Box(
        modifier = modifier
            .background(Brush.linearGradient(listOf(bgTop, bgBottom)))
            .padding(if (compact) 8.dp else 14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = if (compact) 14.dp else 22.dp, height = if (compact) 4.dp else 5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accent)
                )
                if (!compact) {
                    Text(
                        text = "Trang xem trước #${preview.pageNumber}",
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
                Text(
                    text = preview.caption,
                    color = titleColor,
                    fontSize = if (compact) 9.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(if (compact) 3.dp else 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineColor.copy(alpha = 0.7f))
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(if (compact) 3.dp else 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(lineColor.copy(alpha = 0.5f))
                )
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Document Detail - 360dp")
@Preview(widthDp = 412, heightDp = 860, name = "Document Detail - 412dp")
@Preview(widthDp = 360, heightDp = 800, fontScale = 1.5f, name = "Document Detail - Large Font")
@Composable
private fun PreviewDocumentDetailContent() {
    val sampleDetail = FakeExploreCatalogRepository.defaultSeedCatalog().first().detail
    UniShareTheme(mode = UniAppMode.BUYER) {
        DocumentDetailContent(
            detailState = DocumentDetailState.Content(detail = sampleDetail),
            onBackClick = {},
            onRetryDetail = {},
            onBuyClick = {},
            onSellerClick = {},
            onOpenGallery = {},
            onSelectPreviewIndex = {},
            onPreviewLoadFailed = {},
            onRetryPreview = {},
            onToggleFavorite = {},
            onShareClick = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Preview Gallery")
@Composable
private fun PreviewDocumentGallery() {
    val sampleDetail = FakeExploreCatalogRepository.defaultSeedCatalog().first().detail
    UniShareTheme(mode = UniAppMode.BUYER) {
        DocumentPreviewGallery(
            previewRefs = sampleDetail.previewRefs,
            activeIndex = 0,
            brokenPreviewIds = emptySet(),
            onPageChanged = {},
            onPreviewLoadFailed = {},
            onRetryPreview = {},
            onClose = {}
        )
    }
}
