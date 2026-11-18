package vn.unishare.feature.explore.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class ExploreViewModel(
    private val catalogRepository: ExploreCatalogRepository = FakeExploreCatalogRepository(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val pageSize: Int = DEFAULT_EXPLORE_PAGE_SIZE,
    autoLoad: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var activeQueryJob: Job? = null
    private var activeLoadMoreJob: Job? = null
    private var activeDetailJob: Job? = null

    private var querySequence: Long = 0L
    private var detailSequence: Long = 0L

    init {
        if (autoLoad) {
            loadInitialHome()
        }
    }

    fun loadInitialHome() {
        _uiState.update { it.copy(viewMode = ExploreViewMode.HOME) }
        executeFreshQuery(loadRecommended = true)
    }

    fun openSearchMode(showAllResultsImmediately: Boolean = false) {
        val current = _uiState.value
        _uiState.update { it.copy(viewMode = ExploreViewMode.SEARCH) }
        if (showAllResultsImmediately || current.query.isNotBlank() || current.filter.hasAnyFilter) {
            executeFreshQuery(loadRecommended = false)
        } else {
            cancelPendingListRequests()
            _uiState.update { it.copy(listState = ExploreListState.Initial) }
        }
    }

    fun backToHome() {
        _uiState.update { it.copy(viewMode = ExploreViewMode.HOME) }
        executeFreshQuery(loadRecommended = _uiState.value.recommendedItems.isEmpty())
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update {
            it.copy(
                query = newQuery,
                viewMode = ExploreViewMode.SEARCH
            )
        }
        val current = _uiState.value
        if (newQuery.isBlank() && !current.filter.hasAnyFilter) {
            cancelPendingListRequests()
            _uiState.update { it.copy(listState = ExploreListState.Initial) }
        } else {
            executeFreshQuery(loadRecommended = false)
        }
    }

    fun onTopicChipClick(topic: String) {
        onQueryChange(topic)
    }

    fun clearQuery() {
        val hasFilters = _uiState.value.filter.hasAnyFilter
        _uiState.update { it.copy(query = "") }
        if (_uiState.value.viewMode == ExploreViewMode.SEARCH && !hasFilters) {
            cancelPendingListRequests()
            _uiState.update { it.copy(listState = ExploreListState.Initial) }
        } else {
            executeFreshQuery(loadRecommended = _uiState.value.viewMode == ExploreViewMode.HOME)
        }
    }

    fun selectCategory(category: ExploreCategory) {
        val updatedFilter = _uiState.value.filter.copy(category = category)
        _uiState.update {
            it.copy(
                filter = updatedFilter,
                draftFilter = updatedFilter
            )
        }
        executeFreshQuery(loadRecommended = false)
    }

    fun openFilterSheet() {
        _uiState.update {
            it.copy(
                draftFilter = it.filter,
                isFilterSheetVisible = true
            )
        }
    }

    fun dismissFilterSheet() {
        _uiState.update { it.copy(isFilterSheetVisible = false) }
    }

    fun updateDraftSubject(subject: ExploreSubject) {
        _uiState.update { it.copy(draftFilter = it.draftFilter.copy(subject = subject)) }
    }

    fun updateDraftSchool(school: String?) {
        _uiState.update { it.copy(draftFilter = it.draftFilter.copy(school = school)) }
    }

    fun updateDraftPriceRange(minVnd: Int, maxVnd: Int) {
        val safeMin = minVnd.coerceIn(DEFAULT_MIN_PRICE_VND, DEFAULT_MAX_PRICE_VND)
        val safeMax = maxVnd.coerceIn(safeMin, DEFAULT_MAX_PRICE_VND)
        _uiState.update {
            it.copy(
                draftFilter = it.draftFilter.copy(
                    minPriceVnd = safeMin,
                    maxPriceVnd = safeMax
                )
            )
        }
    }

    fun updateDraftMinSellerRating(minRating: Double?) {
        _uiState.update { it.copy(draftFilter = it.draftFilter.copy(minSellerRating = minRating)) }
    }

    fun updateDraftDocType(docType: ExploreDocType) {
        _uiState.update { it.copy(draftFilter = it.draftFilter.copy(docType = docType)) }
    }

    fun resetDraftFilter() {
        _uiState.update {
            it.copy(draftFilter = ExploreFilter(category = it.draftFilter.category))
        }
    }

    fun applyDraftFilter() {
        val committed = _uiState.value.draftFilter
        _uiState.update {
            it.copy(
                filter = committed,
                isFilterSheetVisible = false,
                viewMode = ExploreViewMode.SEARCH
            )
        }
        executeFreshQuery(loadRecommended = false)
    }

    fun resetAllFiltersAndSearch() {
        _uiState.update {
            it.copy(
                query = "",
                filter = ExploreFilter(),
                draftFilter = ExploreFilter(),
                isFilterSheetVisible = false
            )
        }
        executeFreshQuery(loadRecommended = _uiState.value.viewMode == ExploreViewMode.HOME)
    }

    fun openSortSheet() {
        _uiState.update { it.copy(isSortSheetVisible = true) }
    }

    fun dismissSortSheet() {
        _uiState.update { it.copy(isSortSheetVisible = false) }
    }

    fun selectSortOption(option: ExploreSortOption) {
        _uiState.update {
            it.copy(
                sortOption = option,
                isSortSheetVisible = false
            )
        }
        executeFreshQuery(loadRecommended = false)
    }

    fun retryCurrentQuery() {
        val isHome = _uiState.value.viewMode == ExploreViewMode.HOME
        executeFreshQuery(loadRecommended = isHome && _uiState.value.recommendedItems.isEmpty())
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        val content = currentState.listState as? ExploreListState.Content ?: return
        val cursor = content.nextCursor ?: return
        if (content.isLoadingMore) return

        val requestSequence = querySequence
        _uiState.update {
            it.copy(
                listState = content.copy(
                    isLoadingMore = true,
                    loadMoreError = null
                )
            )
        }

        activeLoadMoreJob?.cancel()
        activeLoadMoreJob = viewModelScope.launch(mainDispatcher) {
            val query = buildCatalogQuery(state = _uiState.value, cursor = cursor)
            val result = catalogRepository.queryCatalog(query)

            // Bỏ qua nếu trong lúc tải trang sau người dùng đã đổi từ khóa/bộ lọc/sắp xếp
            if (requestSequence != querySequence) return@launch

            val latestContent = _uiState.value.listState as? ExploreListState.Content ?: return@launch
            result.fold(
                onSuccess = { page ->
                    val merged = deduplicatePreservingOrder(latestContent.items, page.items)
                    _uiState.update {
                        it.copy(
                            listState = latestContent.copy(
                                items = merged,
                                totalCount = page.totalCount,
                                nextCursor = page.nextCursor,
                                isLoadingMore = false,
                                loadMoreError = null
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            listState = latestContent.copy(
                                isLoadingMore = false,
                                loadMoreError = mapErrorMessage(error)
                            )
                        )
                    }
                }
            )
        }
    }

    fun openDocumentDetail(docId: String) {
        val requestId = ++detailSequence
        activeDetailJob?.cancel()

        _uiState.update {
            it.copy(
                selectedDocId = docId,
                detailState = DocumentDetailState.Loading,
                isPreviewGalleryOpen = false
            )
        }

        activeDetailJob = viewModelScope.launch(mainDispatcher) {
            val result = catalogRepository.getDocumentDetail(docId)
            if (requestId != detailSequence) return@launch

            result.fold(
                onSuccess = { detail ->
                    if (detail == null || detail.isArchived) {
                        _uiState.update {
                            it.copy(detailState = DocumentDetailState.NotFoundOrArchived(docId))
                        }
                    } else {
                        val initialBroken = detail.previewRefs
                            .filter { it.isBroken }
                            .map { it.id }
                            .toSet()
                        _uiState.update {
                            it.copy(
                                detailState = DocumentDetailState.Content(
                                    detail = detail,
                                    activePreviewIndex = 0,
                                    brokenPreviewIds = initialBroken
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            detailState = DocumentDetailState.Error(
                                docId = docId,
                                message = mapErrorMessage(error)
                            )
                        )
                    }
                }
            )
        }
    }

    fun retryDocumentDetail() {
        val docId = _uiState.value.selectedDocId ?: return
        openDocumentDetail(docId)
    }

    fun closeDocumentDetail() {
        activeDetailJob?.cancel()
        detailSequence++
        _uiState.update {
            it.copy(
                selectedDocId = null,
                detailState = DocumentDetailState.Idle,
                isPreviewGalleryOpen = false
            )
        }
    }

    fun selectPreviewPage(index: Int) {
        _uiState.update { state ->
            val content = state.detailState as? DocumentDetailState.Content ?: return@update state
            val maxIndex = (content.detail.previewRefs.size - 1).coerceAtLeast(0)
            state.copy(
                detailState = content.copy(activePreviewIndex = index.coerceIn(0, maxIndex))
            )
        }
    }

    fun openPreviewGallery(initialIndex: Int? = null) {
        _uiState.update { state ->
            val content = state.detailState as? DocumentDetailState.Content ?: return@update state
            if (content.detail.previewRefs.isEmpty()) return@update state
            val maxIndex = (content.detail.previewRefs.size - 1).coerceAtLeast(0)
            val safeIndex = (initialIndex ?: content.activePreviewIndex).coerceIn(0, maxIndex)
            state.copy(
                detailState = content.copy(activePreviewIndex = safeIndex),
                isPreviewGalleryOpen = true
            )
        }
    }

    fun closePreviewGallery() {
        _uiState.update { it.copy(isPreviewGalleryOpen = false) }
    }

    fun markPreviewImageBroken(previewId: String) {
        _uiState.update { state ->
            val content = state.detailState as? DocumentDetailState.Content ?: return@update state
            state.copy(
                detailState = content.copy(
                    brokenPreviewIds = content.brokenPreviewIds + previewId
                )
            )
        }
    }

    fun retryPreviewImage(previewId: String) {
        _uiState.update { state ->
            val content = state.detailState as? DocumentDetailState.Content ?: return@update state
            state.copy(
                detailState = content.copy(
                    brokenPreviewIds = content.brokenPreviewIds - previewId
                )
            )
        }
    }

    fun toggleFavorite() {
        _uiState.update { state ->
            val content = state.detailState as? DocumentDetailState.Content ?: return@update state
            state.copy(detailState = content.copy(isFavorite = !content.isFavorite))
        }
    }

    // --- Nội bộ ---

    private fun executeFreshQuery(loadRecommended: Boolean) {
        val requestId = ++querySequence
        cancelPendingListRequests()

        _uiState.update { it.copy(listState = ExploreListState.Loading) }

        activeQueryJob = viewModelScope.launch(mainDispatcher) {
            if (loadRecommended) {
                catalogRepository.fetchRecommended().onSuccess { recList ->
                    if (requestId == querySequence) {
                        _uiState.update { it.copy(recommendedItems = recList) }
                    }
                }
            }

            val query = buildCatalogQuery(state = _uiState.value, cursor = null)
            val result = catalogRepository.queryCatalog(query)

            // Kìm kết quả trả về muộn (out-of-order)
            if (requestId != querySequence) return@launch

            result.fold(
                onSuccess = { page ->
                    val deduped = deduplicatePreservingOrder(emptyList(), page.items)
                    val nextState = if (deduped.isEmpty()) {
                        ExploreListState.Empty
                    } else {
                        ExploreListState.Content(
                            items = deduped,
                            totalCount = page.totalCount,
                            nextCursor = page.nextCursor,
                            isLoadingMore = false,
                            loadMoreError = null
                        )
                    }
                    _uiState.update { it.copy(listState = nextState) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(listState = ExploreListState.Error(mapErrorMessage(error)))
                    }
                }
            )
        }
    }

    private fun cancelPendingListRequests() {
        activeQueryJob?.cancel()
        activeLoadMoreJob?.cancel()
    }

    private fun buildCatalogQuery(state: ExploreUiState, cursor: String?): CatalogQuery {
        return CatalogQuery(
            normalizedPrefix = normalizeTitlePrefix(state.query),
            filter = state.filter,
            sortOption = state.sortOption,
            cursor = cursor,
            pageSize = pageSize
        )
    }

    private fun deduplicatePreservingOrder(
        existing: List<ExploreDocumentItem>,
        incoming: List<ExploreDocumentItem>
    ): List<ExploreDocumentItem> {
        val seen = LinkedHashMap<String, ExploreDocumentItem>(existing.size + incoming.size)
        for (item in existing) {
            seen[item.docId] = item
        }
        for (item in incoming) {
            if (!seen.containsKey(item.docId)) {
                seen[item.docId] = item
            }
        }
        return seen.values.toList()
    }

    private fun mapErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is IOException ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("offline", ignoreCase = true) ||
                raw.contains("mạng", ignoreCase = true) ->
                "Không thể kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi khi tải danh mục. Vui lòng thử lại."
        }
    }
}
