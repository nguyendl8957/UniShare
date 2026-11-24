package vn.unishare.feature.payment.history

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.payment.ui.OrderStatus

/**
 * Fake AuthRepository kiểm soát phiên đăng nhập phục vụ kiểm thử đơn vị cho B05
 */
private class TestFakeAuthRepository(
    initialSession: AuthSession = AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE)
) : AuthRepository {
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = _session

    fun switchSession(newSession: AuthSession) {
        _session.value = newSession
    }

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> {
        _session.value = AuthSession.Guest
        return Result.success(Unit)
    }
}

/**
 * ============================================================================
 * UNIT TEST CHO BUYER TRANSACTIONS VIEWMODEL (B05)
 * ============================================================================
 *
 * Kiểm tra các tiêu chí chấp nhận (Acceptance Criteria):
 * 1. Phân quyền và bảo mật: 2 Buyer UID độc lập, không lộ đơn của nhau.
 * 2. Phân trang: Phân trang Cursor, Cursor hết thì dừng, khử trùng lặp (Dedup).
 * 3. Trạng thái: Hỗ trợ đầy đủ PAID, PENDING, EXPIRED, REVIEW_REQUIRED.
 * 4. Ngoại lệ: Mất mạng, thử lại (Retry), 404 không tồn tại.
 * 5. Chuyển đổi tài khoản: Đổi UID lập tức dọn sạch dữ liệu cũ.
 */
class BuyerTransactionsViewModelTest {

    @Test
    fun init_withSignedInBuyerOne_loadsInitialTransactionsSuccessfully() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isGuest)
        assertEquals(FakeBuyerTransactionsRepository.UID_BUYER_ONE, state.activeUid)
        assertTrue(state.listState is BuyerTransactionsListState.Content)

        val content = state.listState as BuyerTransactionsListState.Content
        assertEquals(2, content.items.size)
        assertTrue(state.hasMore)
        assertEquals(FakeBuyerTransactionsRepository.CURSOR_PAGE_2, state.nextCursor)
    }

    @Test
    fun loadMore_fetchesNextPage_and_deduplicatesItems() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Gọi tải thêm trang 2
        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertTrue(state.listState is BuyerTransactionsListState.Content)
        val content = state.listState as BuyerTransactionsListState.Content

        // Tổng cộng 4 đơn, không có ID nào bị trùng lặp
        assertEquals(4, content.items.size)
        assertEquals(4, content.items.map { it.orderId }.distinct().size)

        // Cursor hết -> hasMore = false
        assertFalse(state.hasMore)
        assertNull(state.nextCursor)
        assertFalse(state.isLoadingMore)
    }

    @Test
    fun cursorExhausted_stopsLoadMore() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.loadMore() // Nạp hết trang 2
        assertFalse(viewModel.uiState.value.hasMore)

        // Gọi thêm lần nữa khi đã hết cursor -> Không có thay đổi nào xảy ra
        viewModel.loadMore()
        val content = viewModel.uiState.value.listState as BuyerTransactionsListState.Content
        assertEquals(4, content.items.size)
    }

    @Test
    fun uidSwitch_cancelsOldJobs_clearsOldData_and_loadsNewBuyerData() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Ban đầu Buyer 1 có các đơn bắt đầu bằng "ord_buyer1_"
        val buyer1Content = viewModel.uiState.value.listState as BuyerTransactionsListState.Content
        assertTrue(buyer1Content.items.any { it.orderId.startsWith("ord_buyer1_") })

        // Đổi sang Buyer 2
        fakeRepo.activeUid = FakeBuyerTransactionsRepository.UID_BUYER_TWO
        authRepo.switchSession(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_TWO))

        val state = viewModel.uiState.value
        assertEquals(FakeBuyerTransactionsRepository.UID_BUYER_TWO, state.activeUid)
        assertTrue(state.listState is BuyerTransactionsListState.Content)

        val buyer2Content = state.listState as BuyerTransactionsListState.Content
        assertEquals(1, buyer2Content.items.size)
        assertEquals("ord_buyer2_999", buyer2Content.items.first().orderId)

        // Tuyệt đối không chứa bất kỳ đơn nào của Buyer 1
        assertFalse(buyer2Content.items.any { it.orderId.startsWith("ord_buyer1_") })
    }

    @Test
    fun guestUser_showsEmptyState_doesNotLoadTransactions() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = null)
        val authRepo = TestFakeAuthRepository(AuthSession.Guest)

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertTrue(state.isGuest)
        assertNull(state.activeUid)
        assertTrue(state.listState is BuyerTransactionsListState.Empty)
    }

    @Test
    fun offlineError_showsErrorState_and_retryRecoversSuccessfully() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(
            activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE,
            simulateOffline = true
        )
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Kiểm tra chuyển sang Error state khi mất mạng
        val state = viewModel.uiState.value
        assertTrue(state.listState is BuyerTransactionsListState.Error)
        assertEquals("Không có kết nối mạng", (state.listState as BuyerTransactionsListState.Error).message)

        // Khôi phục mạng và bấm thử lại
        fakeRepo.simulateOffline = false
        viewModel.loadInitialTransactions()

        val recoveredState = viewModel.uiState.value
        assertTrue(recoveredState.listState is BuyerTransactionsListState.Content)
        assertEquals(2, (recoveredState.listState as BuyerTransactionsListState.Content).items.size)
    }

    @Test
    fun allFourOrderStatuses_areProperlyMapped() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.loadMore() // Tải toàn bộ 4 đơn
        val items = (viewModel.uiState.value.listState as BuyerTransactionsListState.Content).items

        val statuses = items.map { it.status }.toSet()
        assertTrue(statuses.contains(OrderStatus.PAID))
        assertTrue(statuses.contains(OrderStatus.PENDING))
        assertTrue(statuses.contains(OrderStatus.EXPIRED))
        assertTrue(statuses.contains(OrderStatus.REVIEW_REQUIRED))
    }

    @Test
    fun selectOrder_loadsDetail_and_emitsNavigateEvent() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.selectOrder("ord_buyer1_001")

        val state = viewModel.uiState.value
        assertNotNull(state.selectedOrderDetail)
        assertEquals("ord_buyer1_001", state.selectedOrderDetail?.orderId)
        assertEquals(OrderStatus.PAID, state.selectedOrderDetail?.status)
        assertEquals("50.000 đ", state.selectedOrderDetail?.formattedAmountVnd)

        val event = viewModel.events.first()
        assertTrue(event is BuyerTransactionsEvent.NavigateToDetail)
        assertEquals("ord_buyer1_001", (event as BuyerTransactionsEvent.NavigateToDetail).orderId)
    }

    @Test
    fun selectOtherBuyerOrder_failsSafelyWithoutLeaking() = runBlocking {
        val fakeRepo = FakeBuyerTransactionsRepository(activeUid = FakeBuyerTransactionsRepository.UID_BUYER_ONE)
        val authRepo = TestFakeAuthRepository(AuthSession.SignedIn(FakeBuyerTransactionsRepository.UID_BUYER_ONE))

        val viewModel = BuyerTransactionsViewModel(
            authRepository = authRepo,
            transactionsRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Buyer 1 cố tình đoán mã đơn "ord_buyer2_999" của Buyer 2
        viewModel.selectOrder("ord_buyer2_999")

        val state = viewModel.uiState.value
        assertNull(state.selectedOrderDetail)
        assertNotNull(state.detailError)
        assertEquals("Không tìm thấy đơn hàng của bạn", state.detailError)
    }
}
