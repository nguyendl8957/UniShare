package vn.unishare.feature.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.library.ui.EntitlementStatus
import vn.unishare.feature.library.ui.FakeAuthRepoForLibrary
import vn.unishare.feature.library.ui.FakeLibraryRepository
import vn.unishare.feature.library.ui.PdfReaderLoadState
import vn.unishare.feature.library.ui.PurchasedDocumentItem
import vn.unishare.feature.library.ui.PurchasedLibraryEvent
import vn.unishare.feature.library.ui.PurchasedLibraryListState
import vn.unishare.feature.library.ui.PurchasedLibraryViewModel

class PurchasedLibraryViewModelTest {

    @Test
    fun activeEntitlementsOnly_filtersOutRevokedAndOtherUidDocuments() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        val content = viewModel.uiState.value.listState as PurchasedLibraryListState.Content
        val docIds = content.items.map { it.docId }

        assertTrue(docIds.contains(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH))
        assertTrue(docIds.contains(FakeLibraryRepository.DOC_MACRO_ECON))
        assertFalse(docIds.contains(FakeLibraryRepository.DOC_REVOKED_SAMPLE))
        assertFalse(docIds.contains(FakeLibraryRepository.DOC_OS_BUYER_TWO))
    }

    @Test
    fun offlineAndRefresh_neverInfersNewEntitlementsOffline_andUpdatesWhenOnline() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        val initialCount =
            (viewModel.uiState.value.listState as PurchasedLibraryListState.Content).items.size

        // Mất mạng khi refresh không được tự thêm quyền mới và phải giữ danh sách cũ kèm cảnh báo
        fakeRepo.simulateOffline = true
        fakeRepo.grantEntitlementForTest(
            PurchasedDocumentItem(
                docId = "doc_newly_paid",
                ownerUid = FakeLibraryRepository.UID_BUYER_ONE,
                title = "Tài liệu mới thanh toán",
                categoryLabel = "Note tóm tắt",
                authorName = "Lê Minh",
                schoolName = "ĐH Bách Khoa",
                pageCount = 2,
                versionNumber = 1,
                purchasedAtLabel = "Vừa mua",
                entitlementStatus = EntitlementStatus.ACTIVE
            )
        )
        viewModel.refreshLibrary()

        val offlineContent = viewModel.uiState.value.listState as PurchasedLibraryListState.Content
        assertEquals(initialCount, offlineContent.items.size)
        assertTrue(viewModel.uiState.value.refreshErrorMessage?.isNotBlank() == true)

        // Có mạng trở lại -> refresh cập nhật đúng danh sách từ server
        fakeRepo.simulateOffline = false
        viewModel.refreshLibrary()

        val refreshedContent = viewModel.uiState.value.listState as PurchasedLibraryListState.Content
        assertEquals(initialCount + 1, refreshedContent.items.size)
        assertTrue(refreshedContent.items.any { it.docId == "doc_newly_paid" })
        assertNull(viewModel.uiState.value.refreshErrorMessage)
    }

    @Test
    fun uidSwitchAndLogout_clearsLibraryAndReaderPagesImmediately() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val authRepo = FakeAuthRepoForLibrary(sessionFlow)
        val viewModel = PurchasedLibraryViewModel(
            authRepository = authRepo,
            libraryRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.openDocumentReader(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH)
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Ready)

        // Đổi sang UID thứ hai: dọn sạch trang PDF cũ và chỉ tải tài liệu của UID thứ hai
        sessionFlow.value = AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_TWO)
        val switchedState = viewModel.uiState.value
        assertEquals(FakeLibraryRepository.UID_BUYER_TWO, switchedState.activeUid)
        assertEquals(PdfReaderLoadState.Idle, switchedState.readerState)
        val secondDocs = (switchedState.listState as PurchasedLibraryListState.Content).items
        assertEquals(listOf(FakeLibraryRepository.DOC_OS_BUYER_TWO), secondDocs.map { it.docId })

        // Đăng xuất: đưa về Guest và xóa sạch dữ liệu
        authRepo.signOut()
        val guestState = viewModel.uiState.value
        assertTrue(guestState.isGuest)
        assertNull(guestState.activeUid)
        assertEquals(PurchasedLibraryListState.Idle, guestState.listState)
        assertEquals(PdfReaderLoadState.Idle, guestState.readerState)
    }

    @Test
    fun readerAccessErrors_handles401DeniedRevokedAndOfflineRetry() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        // Chưa mua / thuộc UID khác -> Denied
        viewModel.openDocumentReader(FakeLibraryRepository.DOC_UNPAID_SAMPLE)
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Denied)

        // Tài liệu đã mua nhưng đang ngoại tuyến -> Error và thử lại thành công khi online
        fakeRepo.simulateOffline = true
        viewModel.openDocumentReader(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH)
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Error)

        fakeRepo.simulateOffline = false
        viewModel.retryOpenDocument()
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Ready)

        // Quyền bị thu hồi -> dừng reader và ẩn nội dung ngay lập tức
        fakeRepo.revokeEntitlementForTest(
            FakeLibraryRepository.UID_BUYER_ONE,
            FakeLibraryRepository.DOC_DSA_BINARY_SEARCH
        )
        viewModel.retryOpenDocument()
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Revoked)

        // Phiên lỗi / 401 -> Unauthorized
        fakeRepo.simulateInvalidToken = true
        viewModel.openDocumentReader(FakeLibraryRepository.DOC_MACRO_ECON)
        assertTrue(viewModel.uiState.value.readerState is PdfReaderLoadState.Unauthorized)
    }

    @Test
    fun readerPageNavigation_movesNextPreviousAndBoundsSafely() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        val emittedEvents = mutableListOf<PurchasedLibraryEvent>()
        val collector = launch(Dispatchers.Unconfined) {
            viewModel.events.collect { emittedEvents.add(it) }
        }
        viewModel.requestOpenDocument(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH)
        assertEquals(
            PurchasedLibraryEvent.NavigateToReader(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH),
            emittedEvents.firstOrNull()
        )
        collector.cancel()

        viewModel.openDocumentReader(FakeLibraryRepository.DOC_DSA_BINARY_SEARCH)
        val page1 = viewModel.uiState.value.readerState as PdfReaderLoadState.Ready
        assertEquals(1, page1.currentPage)
        assertEquals(3, page1.totalPages)
        assertTrue((page1.currentPageContent?.sections?.size ?: 0) > 1)

        viewModel.goToPreviousPage()
        assertEquals(1, (viewModel.uiState.value.readerState as PdfReaderLoadState.Ready).currentPage)

        viewModel.goToNextPage()
        assertEquals(2, (viewModel.uiState.value.readerState as PdfReaderLoadState.Ready).currentPage)

        viewModel.jumpToPage(99)
        assertEquals(3, (viewModel.uiState.value.readerState as PdfReaderLoadState.Ready).currentPage)

        viewModel.goToNextPage()
        assertEquals(3, (viewModel.uiState.value.readerState as PdfReaderLoadState.Ready).currentPage)
    }
}
