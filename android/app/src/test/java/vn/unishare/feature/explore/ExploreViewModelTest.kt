package vn.unishare.feature.explore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.feature.explore.ui.CatalogPage
import vn.unishare.feature.explore.ui.CatalogQuery
import vn.unishare.feature.explore.ui.DocumentDetailState
import vn.unishare.feature.explore.ui.DocumentPublicDetail
import vn.unishare.feature.explore.ui.ExploreCatalogRepository
import vn.unishare.feature.explore.ui.ExploreCategory
import vn.unishare.feature.explore.ui.ExploreDocType
import vn.unishare.feature.explore.ui.ExploreDocumentItem
import vn.unishare.feature.explore.ui.ExploreListState
import vn.unishare.feature.explore.ui.ExploreSortOption
import vn.unishare.feature.explore.ui.ExploreSubject
import vn.unishare.feature.explore.ui.ExploreViewModel
import vn.unishare.feature.explore.ui.FakeExploreCatalogRepository

// Repository giả lập có thể chặn/điều khiển thứ tự phản hồi cho unit test
private class ControllableExploreRepository(
    private val delegate: FakeExploreCatalogRepository = FakeExploreCatalogRepository()
) : ExploreCatalogRepository {

    val recordedQueries = mutableListOf<CatalogQuery>()
    val queryGates = mutableMapOf<String, CompletableDeferred<Result<CatalogPage>>>()
    var scriptedPages: MutableList<Result<CatalogPage>> = mutableListOf()

    override suspend fun fetchRecommended(limit: Int): Result<List<ExploreDocumentItem>> {
        return delegate.fetchRecommended(limit)
    }

    override suspend fun queryCatalog(query: CatalogQuery): Result<CatalogPage> {
        recordedQueries.add(query)
        val gate = queryGates.remove(query.normalizedPrefix)
        if (gate != null) {
            return gate.await()
        }
        if (scriptedPages.isNotEmpty()) {
            return scriptedPages.removeAt(0)
        }
        return delegate.queryCatalog(query)
    }

    override suspend fun getDocumentDetail(docId: String): Result<DocumentPublicDetail?> {
        return delegate.getDocumentDetail(docId)
    }
}

class ExploreViewModelTest {

    @Test
    fun filterAndSortCombo_sendsExpectedQueryToRepository() = runBlocking {
        val repo = ControllableExploreRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = repo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = false
        )

        viewModel.onQueryChange("  Lập Trình  ")
        viewModel.selectCategory(ExploreCategory.SUMMARY_NOTE)
        viewModel.openFilterSheet()
        viewModel.updateDraftSubject(ExploreSubject.PROGRAMMING)
        viewModel.updateDraftSchool("ĐH Bách Khoa TP.HCM")
        viewModel.updateDraftPriceRange(10_000, 100_000)
        viewModel.updateDraftMinSellerRating(4.5)
        viewModel.updateDraftDocType(ExploreDocType.NOTES)
        viewModel.applyDraftFilter()

        val appliedQuery = repo.recordedQueries.last()
        assertEquals("lap trinh", appliedQuery.normalizedPrefix)
        assertEquals(ExploreCategory.SUMMARY_NOTE, appliedQuery.filter.category)
        assertEquals(ExploreSubject.PROGRAMMING, appliedQuery.filter.subject)
        assertEquals("ĐH Bách Khoa TP.HCM", appliedQuery.filter.school)
        assertEquals(10_000, appliedQuery.filter.minPriceVnd)
        assertEquals(100_000, appliedQuery.filter.maxPriceVnd)
        assertEquals(4.5, appliedQuery.filter.minSellerRating)
        assertEquals(ExploreDocType.NOTES, appliedQuery.filter.docType)
        assertNull(appliedQuery.cursor)

        // Đổi tùy chọn sắp xếp phải gọi lại repository từ đầu với cursor = null
        viewModel.selectSortOption(ExploreSortOption.PRICE_ASC)
        val sortedQuery = repo.recordedQueries.last()
        assertEquals(ExploreSortOption.PRICE_ASC, sortedQuery.sortOption)
        assertNull(sortedQuery.cursor)
    }

    @Test
    fun rapidQueryChange_discardsStaleOutOfOrderResponse() = runBlocking {
        val seeds = FakeExploreCatalogRepository.defaultSeedCatalog()
        val cppItem = seeds.first { it.item.docId == "doc_cpp_01" }.item
        val pythonItem = seeds.first { it.item.docId == "doc_py_09" }.item

        val repo = ControllableExploreRepository()
        val cppDeferred = CompletableDeferred<Result<CatalogPage>>()
        val pythonDeferred = CompletableDeferred<Result<CatalogPage>>()
        repo.queryGates["c++"] = cppDeferred
        repo.queryGates["python"] = pythonDeferred

        val viewModel = ExploreViewModel(
            catalogRepository = repo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = false
        )

        // Gửi truy vấn 1 ("C++"), ngay sau đó đổi sang truy vấn 2 ("Python")
        viewModel.onQueryChange("C++")
        viewModel.onQueryChange("Python")

        // Truy vấn 2 trả về trước
        pythonDeferred.complete(
            Result.success(CatalogPage(items = listOf(pythonItem), totalCount = 1, nextCursor = null))
        )

        // Truy vấn 1 trả về muộn sau đó
        cppDeferred.complete(
            Result.success(CatalogPage(items = listOf(cppItem), totalCount = 1, nextCursor = null))
        )

        val content = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(listOf("doc_py_09"), content.items.map { it.docId })
    }

    @Test
    fun cursorPagination_appendsNextPageWithoutDuplicates() = runBlocking {
        val seeds = FakeExploreCatalogRepository.defaultSeedCatalog().filter { !it.detail.isArchived }
        val doc1 = seeds[0].item
        val doc2 = seeds[1].item
        val doc3 = seeds[2].item

        val repo = ControllableExploreRepository()
        repo.scriptedPages.add(
            Result.success(
                CatalogPage(items = listOf(doc1, doc2), totalCount = 3, nextCursor = "cursor_page_2")
            )
        )
        repo.scriptedPages.add(
            Result.success(
                // Cố tình chèn trùng doc2 ở trang 2 để kiểm tra logic loại trùng
                CatalogPage(items = listOf(doc2, doc3), totalCount = 3, nextCursor = null)
            )
        )

        val viewModel = ExploreViewModel(
            catalogRepository = repo,
            mainDispatcher = Dispatchers.Unconfined,
            pageSize = 2,
            autoLoad = false
        )

        viewModel.loadInitialHome()
        val firstPage = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(listOf(doc1.docId, doc2.docId), firstPage.items.map { it.docId })
        assertEquals("cursor_page_2", firstPage.nextCursor)

        viewModel.loadNextPage()
        val mergedPage = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(listOf(doc1.docId, doc2.docId, doc3.docId), mergedPage.items.map { it.docId })
        assertNull(mergedPage.nextCursor)
        assertFalse(mergedPage.isLoadingMore)
        assertEquals("cursor_page_2", repo.recordedQueries.last().cursor)
    }

    @Test
    fun networkError_showsErrorStateInsteadOfEmpty_andRecoversOnRetry() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository(simulateOffline = true)
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = true
        )

        val failedState = viewModel.uiState.value.listState
        assertTrue(failedState is ExploreListState.Error)
        assertFalse(failedState is ExploreListState.Empty)

        // Khôi phục mạng và bấm thử lại
        fakeRepo.simulateOffline = false
        viewModel.retryCurrentQuery()

        val recoveredState = viewModel.uiState.value.listState
        assertTrue(recoveredState is ExploreListState.Content)
        assertTrue((recoveredState as ExploreListState.Content).items.isNotEmpty())
    }

    @Test
    fun loadMoreFailure_preservesLoadedItemsAndAllowsRetry() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository(simulateLoadMoreOffline = true)
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            pageSize = 4,
            autoLoad = true
        )

        val initialContent = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(4, initialContent.items.size)

        viewModel.loadNextPage()
        val afterFail = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(4, afterFail.items.size)
        assertTrue(!afterFail.loadMoreError.isNullOrBlank())

        fakeRepo.simulateLoadMoreOffline = false
        viewModel.loadNextPage()
        val afterRetry = viewModel.uiState.value.listState as ExploreListState.Content
        assertEquals(8, afterRetry.items.size)
        assertNull(afterRetry.loadMoreError)
    }

    @Test
    fun emptySearch_setsEmptyState_andResetRestoresResults() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = true
        )

        viewModel.onQueryChange("tu_khoa_khong_ton_tai_xyz")
        assertTrue(viewModel.uiState.value.listState is ExploreListState.Empty)

        viewModel.resetAllFiltersAndSearch()
        assertEquals("", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.listState is ExploreListState.Content)
    }

    @Test
    fun documentDetail_handlesArchivedMissingAndPreservesExploreStateOnBack() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = false
        )

        viewModel.onQueryChange("C++")
        val searchBeforeDetail = viewModel.uiState.value.listState as ExploreListState.Content
        assertTrue(searchBeforeDetail.items.isNotEmpty())

        // Tài liệu đã bị ẩn
        viewModel.openDocumentDetail("doc_archived_99")
        assertTrue(viewModel.uiState.value.detailState is DocumentDetailState.NotFoundOrArchived)

        // Tài liệu không tồn tại
        viewModel.openDocumentDetail("doc_khong_ton_tai")
        assertTrue(viewModel.uiState.value.detailState is DocumentDetailState.NotFoundOrArchived)

        // Tài liệu hợp lệ
        viewModel.openDocumentDetail("doc_cpp_01")
        val detailContent = viewModel.uiState.value.detailState as DocumentDetailState.Content
        assertEquals("doc_cpp_01", detailContent.detail.docId)
        assertEquals(5, detailContent.detail.previewRefs.size)

        // Đóng chi tiết quay lại Explore phải giữ nguyên từ khóa và danh sách kết quả
        viewModel.closeDocumentDetail()
        assertEquals(DocumentDetailState.Idle, viewModel.uiState.value.detailState)
        assertEquals("C++", viewModel.uiState.value.query)
        assertEquals(searchBeforeDetail.items, (viewModel.uiState.value.listState as ExploreListState.Content).items)
    }

    @Test
    fun brokenPreviewImage_tracksBrokenIdAndClearsOnRetry() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = false
        )

        viewModel.openDocumentDetail("doc_ctdl_08")
        val loaded = viewModel.uiState.value.detailState as DocumentDetailState.Content
        assertTrue("ctdl_p1" in loaded.brokenPreviewIds)

        viewModel.retryPreviewImage("ctdl_p1")
        val retried = viewModel.uiState.value.detailState as DocumentDetailState.Content
        assertFalse("ctdl_p1" in retried.brokenPreviewIds)
    }

    @Test
    fun detailNetworkError_showsErrorAndRecoversOnRetry() = runBlocking {
        val fakeRepo = FakeExploreCatalogRepository(simulateOffline = true)
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoad = false
        )

        viewModel.openDocumentDetail("doc_cpp_01")
        assertTrue(viewModel.uiState.value.detailState is DocumentDetailState.Error)

        fakeRepo.simulateOffline = false
        viewModel.retryDocumentDetail()
        val recovered = viewModel.uiState.value.detailState as DocumentDetailState.Content
        assertEquals("doc_cpp_01", recovered.detail.docId)
    }
}
