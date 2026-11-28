package vn.unishare.feature.explore.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.roundToInt

private val ExplorePrimaryGreen = Color(0xFF2E7D32)
private val ExploreActionGreen = Color(0xFF17823B)
private val ExploreChipTextGreen = Color(0xFF116B31)
private val ExploreChipBgLight = Color(0xFFE5F2E9)
private val ExploreFilterChipBg = Color(0xFFF1F8F2)
private val ExploreHomeBackground = Color(0xFFF7F8FA)
private val ExploreSearchBackground = Color(0xFFFAFBFC)
private val ExploreTitleInk = Color(0xFF172033)
private val ExploreSearchTitleInk = Color(0xFF253047)
private val ExploreMutedInk = Color(0xFF70798A)
private val ExploreMutedSearchInk = Color(0xFF7C8595)
private val ExploreBorderColor = Color(0xFFE6E9EF)
private val ExploreInputBorderColor = Color(0xFFCAC4D0)
private val ExploreBadgeBlue = Color(0xFF287CF7)
private val ExploreSliderInactive = Color(0xFFDCE8DF)

// Màn hình khám phá: Trang chủ, Tìm kiếm, Bộ lọc và Sắp xếp
@Composable
fun ExploreScreen(
    viewModel: ExploreViewModel,
    onDocumentClick: (String) -> Unit,
    onBottomNavSelected: (String) -> Unit = {},
    onNotificationsClick: (() -> Unit)? = null,
    onProfileIconClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    appMode: UniAppMode = UniAppMode.BUYER,
    hasExtendedCatalogData: Boolean = true,
    onExitSearch: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    val exitSearch = onExitSearch ?: viewModel::backToHome
    BackHandler(enabled = uiState.viewMode == ExploreViewMode.SEARCH) {
        exitSearch()
    }

    ExploreScreenContent(
        uiState = uiState,
        onOpenSearchMode = viewModel::openSearchMode,
        onBackToHome = exitSearch,
        onQueryChange = viewModel::onQueryChange,
        onClearQuery = viewModel::clearQuery,
        onTopicChipClick = viewModel::onTopicChipClick,
        onSelectCategory = viewModel::selectCategory,
        onDocumentClick = onDocumentClick,
        onOpenFilterSheet = viewModel::openFilterSheet,
        onDismissFilterSheet = viewModel::dismissFilterSheet,
        onUpdateDraftSubject = viewModel::updateDraftSubject,
        onUpdateDraftSchool = viewModel::updateDraftSchool,
        onUpdateDraftPriceRange = viewModel::updateDraftPriceRange,
        onUpdateDraftMinRating = viewModel::updateDraftMinSellerRating,
        onUpdateDraftDocType = viewModel::updateDraftDocType,
        onResetDraftFilter = viewModel::resetDraftFilter,
        onApplyDraftFilter = viewModel::applyDraftFilter,
        onResetAllFilters = viewModel::resetAllFiltersAndSearch,
        onOpenSortSheet = viewModel::openSortSheet,
        onDismissSortSheet = viewModel::dismissSortSheet,
        onSelectSortOption = viewModel::selectSortOption,
        onRetry = viewModel::retryCurrentQuery,
        onLoadMore = viewModel::loadNextPage,
        onNotificationsClick = onNotificationsClick,
        onProfileIconClick = onProfileIconClick,
        onBottomNavSelected = { route ->
            when (route) {
                "home" -> if (appMode == UniAppMode.BUYER) viewModel.backToHome()
                "explore" -> viewModel.openSearchMode(showAllResultsImmediately = false)
            }
            onBottomNavSelected(route)
        },
        modifier = modifier,
        appMode = appMode,
        hasExtendedCatalogData = hasExtendedCatalogData
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreenContent(
    uiState: ExploreUiState,
    onOpenSearchMode: (Boolean) -> Unit,
    onBackToHome: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTopicChipClick: (String) -> Unit,
    onSelectCategory: (ExploreCategory) -> Unit,
    onDocumentClick: (String) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onDismissFilterSheet: () -> Unit,
    onUpdateDraftSubject: (ExploreSubject) -> Unit,
    onUpdateDraftSchool: (String?) -> Unit,
    onUpdateDraftPriceRange: (Int, Int) -> Unit,
    onUpdateDraftMinRating: (Double?) -> Unit,
    onUpdateDraftDocType: (ExploreDocType) -> Unit,
    onResetDraftFilter: () -> Unit,
    onApplyDraftFilter: () -> Unit,
    onResetAllFilters: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onDismissSortSheet: () -> Unit,
    onSelectSortOption: (ExploreSortOption) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onNotificationsClick: (() -> Unit)?,
    onProfileIconClick: () -> Unit,
    onBottomNavSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    appMode: UniAppMode = UniAppMode.BUYER,
    hasExtendedCatalogData: Boolean = true
) {
    val selectedBottomRoute = if (uiState.viewMode == ExploreViewMode.HOME && appMode == UniAppMode.BUYER) "home" else "explore"
    val containerBg = if (uiState.viewMode == ExploreViewMode.HOME) ExploreHomeBackground else ExploreSearchBackground

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = containerBg,
        topBar = {
            if (uiState.viewMode == ExploreViewMode.HOME) {
                HomeTopHeader(
                    selectedCategory = uiState.filter.category,
                    onSearchIconClick = { onOpenSearchMode(false) },
                    onNotificationsClick = onNotificationsClick,
                    onProfileIconClick = onProfileIconClick,
                    onSelectCategory = onSelectCategory
                )
            } else {
                SearchTopHeader(
                    query = uiState.query,
                    listState = uiState.listState,
                    filter = uiState.filter,
                    onBackClick = onBackToHome,
                    onQueryChange = onQueryChange,
                    onClearQuery = onClearQuery,
                    onOpenFilterSheet = onOpenFilterSheet,
                    onOpenSortSheet = onOpenSortSheet,
                    onSelectCategory = onSelectCategory
                )
            }
        },
        bottomBar = {
            UniBottomBar(
                selectedRoute = selectedBottomRoute,
                onItemSelected = onBottomNavSelected,
                mode = appMode
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.viewMode == ExploreViewMode.HOME) {
                HomeExploreBody(
                    uiState = uiState,
                    onDocumentClick = onDocumentClick,
                    onViewAllClick = { onOpenSearchMode(true) },
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onResetCategory = { onSelectCategory(ExploreCategory.ALL) }
                )
            } else {
                SearchExploreBody(
                    uiState = uiState,
                    onTopicChipClick = onTopicChipClick,
                    onOpenFilterSheet = onOpenFilterSheet,
                    onOpenSortSheet = onOpenSortSheet,
                    onResetAllFilters = onResetAllFilters,
                    onDocumentClick = onDocumentClick,
                    onRetry = onRetry,
                    onLoadMore = onLoadMore
                )
            }
        }
    }

    // Bottom Sheet bộ lọc nâng cao
    if (uiState.isFilterSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissFilterSheet,
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
        ) {
            AdvancedFilterSheetContent(
                draftFilter = uiState.draftFilter,
                onSelectSubject = onUpdateDraftSubject,
                onSelectSchool = onUpdateDraftSchool,
                onPriceRangeChange = onUpdateDraftPriceRange,
                onSelectMinRating = onUpdateDraftMinRating,
                onSelectDocType = onUpdateDraftDocType,
                onResetClick = onResetDraftFilter,
                onApplyClick = onApplyDraftFilter,
                hasExtendedCatalogData = hasExtendedCatalogData
            )
        }
    }

    // Bottom Sheet sắp xếp kết quả
    if (uiState.isSortSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissSortSheet,
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            SortBottomSheetContent(
                selectedOption = uiState.sortOption,
                onSelectOption = onSelectSortOption,
                onCloseClick = onDismissSortSheet,
                hasRatings = hasExtendedCatalogData
            )
        }
    }
}

// --- Phần Trang chủ ---

@Composable
private fun HomeTopHeader(
    selectedCategory: ExploreCategory,
    onSearchIconClick: () -> Unit,
    onNotificationsClick: (() -> Unit)?,
    onProfileIconClick: () -> Unit,
    onSelectCategory: (ExploreCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ExploreHomeBackground)
            .statusBarsPadding()
    ) {
        Surface(color = Color.White) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.unishare_wordmark),
                        contentDescription = "Biểu trưng UniShare",
                        modifier = Modifier
                            .width(134.dp)
                            .height(40.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSearchIconClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("btn_home_search_icon")
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.nav_buyer_search_unselected_home),
                                contentDescription = "Mở tìm kiếm",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(21.dp)
                            )
                        }

                        if (onNotificationsClick != null) IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bell),
                                    contentDescription = "Thông báo",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(21.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }

                        IconButton(
                            onClick = onProfileIconClick,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.nav_buyer_profile_unselected),
                                contentDescription = "Tài khoản",
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = ExploreBorderColor)
            }
        }

        // Ô mở tìm kiếm nhanh
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0xFFD7DBE4)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSearchIconClick)
                .testTag("home_search_bar")
                .semantics {
                    contentDescription = "Tìm tài liệu, môn học"
                }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.nav_buyer_search_unselected_home),
                    contentDescription = null,
                    tint = ExploreMutedInk,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Tìm tài liệu, môn học…",
                    color = ExploreMutedInk,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Hàng chip danh mục ngang
        CategoryChipRow(
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
private fun HomeExploreBody(
    uiState: ExploreUiState,
    onDocumentClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onResetCategory: () -> Unit
) {
    when (val listState = uiState.listState) {
        is ExploreListState.Loading -> {
            UiFeedback(state = UiFeedbackState.Skeleton)
        }

        is ExploreListState.Error -> {
            UiFeedback(
                state = UiFeedbackState.Error(listState.message),
                onRetry = onRetry
            )
        }

        is ExploreListState.Empty -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeEmptyCard(
                    onRefreshClick = onRetry,
                    showResetCategory = uiState.filter.category != ExploreCategory.ALL,
                    onResetCategory = onResetCategory
                )
            }
        }

        is ExploreListState.Initial -> {
            UiFeedback(state = UiFeedbackState.Skeleton)
        }

        is ExploreListState.Content -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home_document_list")
            ) {
                // Gợi ý cho bạn
                if (uiState.recommendedItems.isNotEmpty() && uiState.filter.category == ExploreCategory.ALL) {
                    item(key = "section_recommended_header") {
                        SectionHeaderRow(
                            title = "Gợi ý cho bạn",
                            onViewAllClick = onViewAllClick
                        )
                    }

                    item(key = "section_recommended_carousel") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = uiState.recommendedItems,
                                key = { "rec_${it.docId}" }
                            ) { item ->
                                RecommendedDocumentCard(
                                    item = item,
                                    onClick = { onDocumentClick(item.docId) }
                                )
                            }
                        }
                    }
                }

                // Tài liệu mới nhất
                item(key = "section_latest_header") {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeaderRow(
                        title = "Tài liệu mới nhất",
                        onViewAllClick = onViewAllClick
                    )
                }

                items(
                    items = listState.items,
                    key = { "latest_${it.docId}" }
                ) { item ->
                    LatestDocumentRowCard(
                        item = item,
                        onClick = { onDocumentClick(item.docId) }
                    )
                }

                if (listState.nextCursor != null || listState.isLoadingMore || listState.loadMoreError != null) {
                    item(key = "home_pagination_footer") {
                        PaginationFooter(
                            isLoadingMore = listState.isLoadingMore,
                            loadMoreError = listState.loadMoreError,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    onViewAllClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            color = ExploreTitleInk,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onViewAllClick)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = "Xem tất cả",
                color = ExplorePrimaryGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = ExplorePrimaryGreen,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun RecommendedDocumentCard(
    item: ExploreDocumentItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ExploreBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("rec_card_${item.docId}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                DocumentPreviewImage(
                    preview = item.coverPreview,
                    isMarkedBroken = item.coverPreview.isBroken,
                    accentCategory = item.category,
                    isDark = false,
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
                CategoryBadgeOverlay(
                    label = item.category.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                Text(
                    text = item.title,
                    color = ExploreTitleInk,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                AuthorBadgeRow(
                    sellerName = item.seller.displayName,
                    badge = item.seller.badge
                )

                Text(
                    text = formatPriceVnd(item.priceVnd),
                    color = ExplorePrimaryGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                RatingCompactRow(
                    rating = item.rating,
                    reviewCount = item.reviewCount
                )
            }
        }
    }
}

@Composable
private fun LatestDocumentRowCard(
    item: ExploreDocumentItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ExploreBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("doc_card_${item.docId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(88.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(6.dp))
            ) {
                DocumentPreviewImage(
                    preview = item.coverPreview,
                    isMarkedBroken = item.coverPreview.isBroken,
                    accentCategory = item.category,
                    isDark = false,
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
                CategoryBadgeOverlay(
                    label = item.category.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    color = ExploreTitleInk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.shortSubtitle,
                    color = ExploreMutedInk,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AuthorBadgeRow(
                        sellerName = item.seller.displayName,
                        badge = item.seller.badge,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatPriceVnd(item.priceVnd),
                            color = ExplorePrimaryGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        RatingCompactRow(
                            rating = item.rating,
                            reviewCount = item.reviewCount
                        )
                    }
                }
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun HomeEmptyCard(
    onRefreshClick: () -> Unit,
    showResetCategory: Boolean,
    onResetCategory: () -> Unit
) {
    Surface(
        color = Color(0xFFF1F4F7),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_empty_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_search_empty_illustration),
                contentDescription = null,
                modifier = Modifier
                    .width(86.dp)
                    .height(68.dp)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Chưa có tài liệu nào.",
                    color = ExploreTitleInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Hãy quay lại sau nhé!",
                    color = ExploreMutedInk,
                    fontSize = 11.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onRefreshClick,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ExplorePrimaryGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_history),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(text = "Làm mới", fontSize = 11.sp)
                    }
                    if (showResetCategory) {
                        OutlinedButton(
                            onClick = onResetCategory,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, ExplorePrimaryGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Text(text = "Tất cả", color = ExplorePrimaryGreen, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- Phần Tìm kiếm & Kết quả ---

@Composable
private fun SearchTopHeader(
    query: String,
    listState: ExploreListState,
    filter: ExploreFilter,
    onBackClick: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onOpenFilterSheet: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onSelectCategory: (ExploreCategory) -> Unit
) {
    val headingText = when (listState) {
        is ExploreListState.Initial -> "Tìm kiếm"
        is ExploreListState.Loading -> "Đang tìm kiếm..."
        is ExploreListState.Content -> "${listState.totalCount} kết quả"
        is ExploreListState.Empty -> "0 kết quả"
        is ExploreListState.Error -> "Lỗi tải dữ liệu"
    }

    Surface(
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 8.dp)
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_explore_back")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_left),
                        contentDescription = "Quay lại trang chủ",
                        tint = ExploreSearchTitleInk,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = headingText,
                    color = ExploreSearchTitleInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("search_result_count")
                )

                IconButton(
                    onClick = onOpenSortSheet,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("btn_open_sort")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_list_filter),
                        contentDescription = "Mở tùy chọn sắp xếp",
                        tint = ExploreSearchTitleInk,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Ô nhập từ khóa và nút mở bộ lọc
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Tìm tài liệu, môn học...",
                            color = ExploreMutedSearchInk,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.nav_buyer_search_unselected_home),
                            contentDescription = null,
                            tint = ExploreMutedSearchInk,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = onClearQuery,
                                modifier = Modifier.testTag("btn_clear_query")
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Xóa từ khóa tìm kiếm",
                                    tint = ExploreMutedSearchInk,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onQueryChange(query) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ExplorePrimaryGreen,
                        unfocusedBorderColor = ExploreInputBorderColor,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = ExploreSearchTitleInk,
                        unfocusedTextColor = ExploreSearchTitleInk
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("input_explore_search")
                        .semantics {
                            contentDescription = "Ô nhập từ khóa tìm kiếm tài liệu"
                        }
                )

                val activeCount = filter.activeAdvancedFilterCount
                OutlinedButton(
                    onClick = onOpenFilterSheet,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ExplorePrimaryGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (activeCount > 0) ExploreFilterChipBg else Color.White,
                        contentColor = ExplorePrimaryGreen
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("btn_open_filter")
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_list_filter),
                        contentDescription = null,
                        tint = ExplorePrimaryGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (activeCount > 0) "Bộ lọc ($activeCount)" else "Bộ lọc",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            CategoryChipRow(
                selectedCategory = filter.category,
                onSelectCategory = onSelectCategory,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp)
            )

            HorizontalDivider(color = ExploreBorderColor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchExploreBody(
    uiState: ExploreUiState,
    onTopicChipClick: (String) -> Unit,
    onOpenFilterSheet: () -> Unit,
    onOpenSortSheet: () -> Unit,
    onResetAllFilters: () -> Unit,
    onDocumentClick: (String) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit
) {
    when (val listState = uiState.listState) {
        is ExploreListState.Initial -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .verticalScroll(rememberScrollState())
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    ExploreFilter.SUGGESTED_TOPICS.forEach { topic ->
                        Surface(
                            color = ExploreFilterChipBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onTopicChipClick(topic) }
                                .testTag("topic_chip_$topic")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.nav_buyer_search_unselected_home),
                                    contentDescription = null,
                                    tint = ExploreChipTextGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = topic,
                                    color = ExploreChipTextGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ExploreBorderColor)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFilterSheet)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag("row_advanced_filter")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_list_filter),
                            contentDescription = null,
                            tint = ExploreActionGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Bộ lọc nâng cao",
                            color = ExploreSearchTitleInk,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = ExploreActionGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }

                HorizontalDivider(color = ExploreBorderColor)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_search_empty_illustration),
                        contentDescription = null,
                        modifier = Modifier
                            .width(136.dp)
                            .height(110.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Hãy nhập từ khóa để tìm kiếm",
                        color = ExploreSearchTitleInk,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Bạn có thể tìm theo tên tài liệu, môn học, hoặc nội dung cần tìm.",
                        color = ExploreMutedSearchInk,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is ExploreListState.Loading -> {
            UiFeedback(state = UiFeedbackState.Skeleton)
        }

        is ExploreListState.Error -> {
            UiFeedback(
                state = UiFeedbackState.Error(listState.message),
                onRetry = onRetry
            )
        }

        is ExploreListState.Empty -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp)
                    .testTag("explore_no_results")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_search_empty_illustration),
                    contentDescription = null,
                    modifier = Modifier
                        .width(144.dp)
                        .height(104.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Không tìm thấy tài liệu phù hợp",
                    color = ExploreSearchTitleInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Vui lòng thử lại với từ khóa khác\nhoặc bỏ bớt bộ lọc để có kết quả phù hợp.",
                    color = ExploreMutedSearchInk,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                UniButton(
                    text = "Xóa bộ lọc",
                    onClick = onResetAllFilters,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .testTag("btn_reset_filters_empty")
                )
            }
        }

        is ExploreListState.Content -> {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_results_list")
            ) {
                item(key = "active_sort_filter_bar") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, ExploreBorderColor),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(onClick = onOpenSortSheet)
                                .testTag("chip_current_sort")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_list_filter),
                                    contentDescription = null,
                                    tint = ExplorePrimaryGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Sắp xếp: ${uiState.sortOption.label}",
                                    color = ExploreSearchTitleInk,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (uiState.filter.hasAnyFilter || uiState.query.isNotBlank()) {
                            TextButton(
                                onClick = onResetAllFilters,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("btn_clear_all_filters")
                            ) {
                                Text(
                                    text = "Đặt lại",
                                    color = ExploreActionGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                items(
                    items = listState.items,
                    key = { it.docId }
                ) { item ->
                    SearchResultDocumentCard(
                        item = item,
                        onClick = { onDocumentClick(item.docId) }
                    )
                }

                if (listState.nextCursor != null || listState.isLoadingMore || listState.loadMoreError != null) {
                    item(key = "search_pagination_footer") {
                        PaginationFooter(
                            isLoadingMore = listState.isLoadingMore,
                            loadMoreError = listState.loadMoreError,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultDocumentCard(
    item: ExploreDocumentItem,
    onClick: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ExploreBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("doc_card_${item.docId}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                DocumentPreviewImage(
                    preview = item.coverPreview,
                    isMarkedBroken = item.coverPreview.isBroken,
                    accentCategory = item.category,
                    isDark = false,
                    compact = true,
                    modifier = Modifier.fillMaxSize()
                )
                CategoryBadgeOverlay(
                    label = item.category.label,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    color = ExploreSearchTitleInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                AuthorBadgeRow(
                    sellerName = item.seller.displayName,
                    badge = item.seller.badge
                )

                Text(
                    text = formatPriceVnd(item.priceVnd),
                    color = ExplorePrimaryGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                RatingCompactRow(
                    rating = item.rating,
                    reviewCount = item.reviewCount
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// --- Bottom Sheet bộ lọc nâng cao ---

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedFilterSheetContent(
    draftFilter: ExploreFilter,
    onSelectSubject: (ExploreSubject) -> Unit,
    onSelectSchool: (String?) -> Unit,
    onPriceRangeChange: (Int, Int) -> Unit,
    onSelectMinRating: (Double?) -> Unit,
    onSelectDocType: (ExploreDocType) -> Unit,
    onResetClick: () -> Unit,
    onApplyClick: () -> Unit,
    hasExtendedCatalogData: Boolean
) {
    var schoolMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .testTag("sheet_advanced_filter")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_list_filter),
                    contentDescription = null,
                    tint = ExploreActionGreen,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Bộ lọc nâng cao",
                    color = ExploreTitleInk,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onResetClick)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("btn_filter_reset")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_history),
                    contentDescription = null,
                    tint = ExploreActionGreen,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Đặt lại",
                    color = ExploreActionGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider(color = ExploreBorderColor)

        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 1. Môn học
            FilterSectionBlock(title = "Môn học") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreSubject.entries.forEach { subject ->
                        FilterOptionChip(
                            label = subject.label,
                            selected = draftFilter.subject == subject,
                            onClick = { onSelectSubject(subject) },
                            modifier = Modifier.testTag("filter_subject_${subject.id}")
                        )
                    }
                }
            }

            // 2. Trường
            if (hasExtendedCatalogData) FilterSectionBlock(title = "Trường") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ExploreInputBorderColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { schoolMenuExpanded = true }
                            .testTag("filter_school_selector")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = draftFilter.school ?: "Chọn trường...",
                                color = if (draftFilter.school == null) ExploreMutedInk else ExploreTitleInk,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_down),
                                contentDescription = "Chọn trường học",
                                tint = ExploreMutedInk,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = schoolMenuExpanded,
                        onDismissRequest = { schoolMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tất cả trường") },
                            onClick = {
                                schoolMenuExpanded = false
                                onSelectSchool(null)
                            }
                        )
                        ExploreFilter.AVAILABLE_SCHOOLS.forEach { schoolName ->
                            DropdownMenuItem(
                                text = { Text(schoolName) },
                                onClick = {
                                    schoolMenuExpanded = false
                                    onSelectSchool(schoolName)
                                }
                            )
                        }
                    }
                }
            }

            // 3. Khoảng giá (5.000đ – 500.000đ+)
            FilterSectionBlock(title = "Khoảng giá") {
                val maxLabel = if (draftFilter.maxPriceVnd >= DEFAULT_MAX_PRICE_VND) {
                    "500.000đ+"
                } else {
                    formatPriceVnd(draftFilter.maxPriceVnd)
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatPriceVnd(draftFilter.minPriceVnd),
                        color = ExploreMutedInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = maxLabel,
                        color = ExploreMutedInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                val sliderColors = SliderDefaults.colors(
                    thumbColor = ExploreActionGreen,
                    activeTrackColor = ExploreActionGreen,
                    inactiveTrackColor = ExploreSliderInactive
                )

                RangeSlider(
                    value = draftFilter.minPriceVnd.toFloat()..draftFilter.maxPriceVnd.toFloat(),
                    onValueChange = { range ->
                        val minStep = ((range.start / 5_000f).roundToInt() * 5_000)
                            .coerceIn(DEFAULT_MIN_PRICE_VND, DEFAULT_MAX_PRICE_VND)
                        val maxStep = ((range.endInclusive / 5_000f).roundToInt() * 5_000)
                            .coerceIn(minStep, DEFAULT_MAX_PRICE_VND)
                        onPriceRangeChange(minStep, maxStep)
                    },
                    valueRange = DEFAULT_MIN_PRICE_VND.toFloat()..DEFAULT_MAX_PRICE_VND.toFloat(),
                    colors = sliderColors,
                    startThumb = {
                        Surface(
                            shape = CircleShape,
                            color = ExploreActionGreen,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(20.dp)
                        ) {}
                    },
                    endThumb = {
                        Surface(
                            shape = CircleShape,
                            color = ExploreActionGreen,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(20.dp)
                        ) {}
                    },
                    track = { rangeSliderState ->
                        SliderDefaults.Track(
                            rangeSliderState = rangeSliderState,
                            colors = sliderColors,
                            drawStopIndicator = null,
                            thumbTrackGapSize = 0.dp,
                            modifier = Modifier.height(4.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 4. Điểm số người bán
            if (hasExtendedCatalogData) FilterSectionBlock(title = "Điểm số người bán") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreFilter.RATING_THRESHOLDS.forEach { threshold ->
                        val label = if (threshold == null) "Tất cả" else "${threshold}+"
                        val tagSuffix = threshold?.toString() ?: "all"
                        FilterOptionChip(
                            label = label,
                            selected = draftFilter.minSellerRating == threshold,
                            onClick = { onSelectMinRating(threshold) },
                            modifier = Modifier.testTag("filter_rating_$tagSuffix")
                        )
                    }
                }
            }

            // 5. Loại tài liệu
            if (hasExtendedCatalogData) FilterSectionBlock(title = "Loại tài liệu") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExploreDocType.entries.forEach { docType ->
                        FilterOptionChip(
                            label = docType.label,
                            selected = draftFilter.docType == docType,
                            onClick = { onSelectDocType(docType) },
                            modifier = Modifier.testTag("filter_doctype_${docType.id}")
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Button(
                onClick = onApplyClick,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ExploreActionGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_filter_apply")
            ) {
                Text(
                    text = "Áp dụng bộ lọc",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterSectionBlock(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title,
            color = ExploreTitleInk,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun FilterOptionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ExploreActionGreen else ExploreFilterChipBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else ExploreChipTextGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// --- Bottom Sheet sắp xếp ---

@Composable
private fun SortBottomSheetContent(
    selectedOption: ExploreSortOption,
    onSelectOption: (ExploreSortOption) -> Unit,
    onCloseClick: () -> Unit,
    hasRatings: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
            .testTag("sheet_sort_options")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_list_filter),
                contentDescription = null,
                tint = ExplorePrimaryGreen,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Sắp xếp theo",
                color = ExploreSearchTitleInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("btn_close_sort")
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = "Đóng sắp xếp",
                    tint = ExploreMutedSearchInk,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        ExploreSortOption.entries.filter { hasRatings || it != ExploreSortOption.HIGHEST_RATED }.forEach { option ->
            val isSelected = option == selectedOption
            val iconRes = when (option) {
                ExploreSortOption.PRICE_ASC -> R.drawable.ic_switch_role
                ExploreSortOption.NEWEST -> R.drawable.ic_history
                ExploreSortOption.HIGHEST_RATED -> R.drawable.ic_star
            }

            HorizontalDivider(color = ExploreBorderColor)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelectOption(option) },
                        role = Role.RadioButton
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("sort_option_${option.id}")
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isSelected) ExplorePrimaryGreen else ExploreMutedSearchInk,
                    modifier = Modifier.size(20.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = option.label,
                        color = ExploreSearchTitleInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = option.description,
                        color = ExploreMutedSearchInk,
                        fontSize = 11.5.sp
                    )
                }
                RadioButton(
                    selected = isSelected,
                    onClick = null,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ExplorePrimaryGreen,
                        unselectedColor = Color(0xFF94A3B8)
                    )
                )
            }
        }
    }
}

// --- Component dùng chung ---

@Composable
private fun CategoryChipRow(
    selectedCategory: ExploreCategory,
    onSelectCategory: (ExploreCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(
            items = ExploreCategory.entries,
            key = { it.id }
        ) { category ->
            val isSelected = category == selectedCategory
            Surface(
                color = if (isSelected) ExplorePrimaryGreen else ExploreChipBgLight,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onSelectCategory(category) }
                    .testTag("category_chip_${category.id}")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = category.label,
                        color = if (isSelected) Color.White else ExplorePrimaryGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryBadgeOverlay(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ExplorePrimaryGreen,
        shape = RoundedCornerShape(5.dp),
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AuthorBadgeRow(
    sellerName: String,
    badge: String?,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = R.drawable.nav_sort_profile),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = sellerName,
            color = ExploreMutedSearchInk,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (!badge.isNullOrBlank()) {
            Surface(
                color = ExploreBadgeBlue,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun RatingCompactRow(
    rating: Double,
    reviewCount: Int
) {
    if (reviewCount <= 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_star),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "$rating ($reviewCount)",
            color = ExploreSearchTitleInk,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun PaginationFooter(
    isLoadingMore: Boolean,
    loadMoreError: String?,
    onLoadMore: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        when {
            isLoadingMore -> {
                CircularProgressIndicator(
                    color = ExplorePrimaryGreen,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(26.dp)
                )
            }

            loadMoreError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = loadMoreError,
                        color = Color(0xFFB91C1C),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    OutlinedButton(
                        onClick = onLoadMore,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ExplorePrimaryGreen),
                        modifier = Modifier.testTag("btn_retry_load_more")
                    ) {
                        Text(
                            text = "Thử tải thêm lại",
                            color = ExplorePrimaryGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            else -> {
                OutlinedButton(
                    onClick = onLoadMore,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ExplorePrimaryGreen),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("btn_load_more")
                ) {
                    Text(
                        text = "Xem thêm tài liệu",
                        color = ExplorePrimaryGreen,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Home - 360dp")
@Preview(widthDp = 412, heightDp = 860, name = "Home - 412dp")
@Preview(widthDp = 360, heightDp = 800, fontScale = 1.5f, name = "Home - Large Font")
@Composable
private fun PreviewExploreHome() {
    val seeds = FakeExploreCatalogRepository.defaultSeedCatalog().filter { !it.detail.isArchived }
    val sampleState = ExploreUiState(
        viewMode = ExploreViewMode.HOME,
        recommendedItems = seeds.filter { it.item.isRecommended }.map { it.item },
        listState = ExploreListState.Content(
            items = seeds.take(5).map { it.item },
            totalCount = seeds.size,
            nextCursor = "offset:5"
        )
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        ExploreScreenContent(
            uiState = sampleState,
            onOpenSearchMode = {},
            onBackToHome = {},
            onQueryChange = {},
            onClearQuery = {},
            onTopicChipClick = {},
            onSelectCategory = {},
            onDocumentClick = {},
            onOpenFilterSheet = {},
            onDismissFilterSheet = {},
            onUpdateDraftSubject = {},
            onUpdateDraftSchool = {},
            onUpdateDraftPriceRange = { _, _ -> },
            onUpdateDraftMinRating = {},
            onUpdateDraftDocType = {},
            onResetDraftFilter = {},
            onApplyDraftFilter = {},
            onResetAllFilters = {},
            onOpenSortSheet = {},
            onDismissSortSheet = {},
            onSelectSortOption = {},
            onRetry = {},
            onLoadMore = {},
            onNotificationsClick = {},
            onProfileIconClick = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "Search Results")
@Composable
private fun PreviewExploreSearchResults() {
    val seeds = FakeExploreCatalogRepository.defaultSeedCatalog().filter { !it.detail.isArchived }
    val sampleState = ExploreUiState(
        viewMode = ExploreViewMode.SEARCH,
        query = "C++",
        listState = ExploreListState.Content(
            items = seeds.take(4).map { it.item },
            totalCount = 12,
            nextCursor = "offset:4"
        )
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        ExploreScreenContent(
            uiState = sampleState,
            onOpenSearchMode = {},
            onBackToHome = {},
            onQueryChange = {},
            onClearQuery = {},
            onTopicChipClick = {},
            onSelectCategory = {},
            onDocumentClick = {},
            onOpenFilterSheet = {},
            onDismissFilterSheet = {},
            onUpdateDraftSubject = {},
            onUpdateDraftSchool = {},
            onUpdateDraftPriceRange = { _, _ -> },
            onUpdateDraftMinRating = {},
            onUpdateDraftDocType = {},
            onResetDraftFilter = {},
            onApplyDraftFilter = {},
            onResetAllFilters = {},
            onOpenSortSheet = {},
            onDismissSortSheet = {},
            onSelectSortOption = {},
            onRetry = {},
            onLoadMore = {},
            onNotificationsClick = {},
            onProfileIconClick = {},
            onBottomNavSelected = {}
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, name = "No Results")
@Composable
private fun PreviewExploreNoResults() {
    val sampleState = ExploreUiState(
        viewMode = ExploreViewMode.SEARCH,
        query = "C++",
        listState = ExploreListState.Empty
    )
    UniShareTheme(mode = UniAppMode.BUYER) {
        ExploreScreenContent(
            uiState = sampleState,
            onOpenSearchMode = {},
            onBackToHome = {},
            onQueryChange = {},
            onClearQuery = {},
            onTopicChipClick = {},
            onSelectCategory = {},
            onDocumentClick = {},
            onOpenFilterSheet = {},
            onDismissFilterSheet = {},
            onUpdateDraftSubject = {},
            onUpdateDraftSchool = {},
            onUpdateDraftPriceRange = { _, _ -> },
            onUpdateDraftMinRating = {},
            onUpdateDraftDocType = {},
            onResetDraftFilter = {},
            onApplyDraftFilter = {},
            onResetAllFilters = {},
            onOpenSortSheet = {},
            onDismissSortSheet = {},
            onSelectSortOption = {},
            onRetry = {},
            onLoadMore = {},
            onNotificationsClick = {},
            onProfileIconClick = {},
            onBottomNavSelected = {}
        )
    }
}
