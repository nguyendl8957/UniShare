package vn.unishare.feature.revenue

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.revenue.ui.FakeSellerRevenueRepository
import vn.unishare.feature.revenue.ui.RevenueDateRangeFilter
import vn.unishare.feature.revenue.ui.SellerRevenueListState
import vn.unishare.feature.revenue.ui.SellerRevenueViewModel

/**
 * Fake AuthRepository kiểm soát phiên đăng nhập phục vụ kiểm thử đơn vị cho B06
 */
private class RevenueTestFakeAuthRepository(
    initialSession: AuthSession = AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE)
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
 * UNIT TEST CHO SELLER REVENUE VIEWMODEL (B06)
 * ============================================================================
 *
 * Kiểm tra các tiêu chí chấp nhận (Acceptance Criteria):
 * 1. Chỉ tính đơn PAID: Đơn PENDING hoặc EXPIRED tuyệt đối không làm tăng doanh thu.
 * 2. Cô lập dữ liệu: Hai Seller độc lập chỉ thấy số liệu và giao dịch của chính mình.
 * 3. Đổi Seller: Xóa sạch dữ liệu cũ ngay lập tức, không để lộ dữ liệu chéo.
 * 4. Phân trang con trỏ (Cursor): Phân trang mượt mà, khử trùng lặp orderId.
 * 5. Khách vãng lai (Guest): Hiển thị yêu cầu đăng nhập, ẩn số liệu.
 * 6. Kiểm tra quyền Seller: Từ chối nếu tài khoản chưa được cấp quyền Seller.
 * 7. Ngoại lệ mạng: Mô phỏng mất mạng, thông báo lỗi và thử lại thành công.
 */
class SellerRevenueViewModelTest {

    @Test
    fun init_withSignedInSellerOne_loadsKpiAndInitialSales_onlyForPaidOrders() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isGuest)
        assertEquals(FakeSellerRevenueRepository.UID_SELLER_ONE, state.activeUid)

        // 1. Kiểm tra KPI:
        // Seller 1 có: 50.000 (PAID) + 100.000 (PAID) + 75.000 (PAID) = 225.000 VND
        // Đơn 30.000 (PENDING) và đơn 45.000 (EXPIRED) KHÔNG được cộng vào tổng.
        assertEquals(225_000L, state.kpiState.totalRevenueVnd)
        assertEquals(3, state.kpiState.paidOrderCount)
        assertEquals(3, state.kpiState.totalDocumentsSold)

        // 2. Kiểm tra danh sách sales:
        assertTrue(state.listState is SellerRevenueListState.Content)
        val content = state.listState as SellerRevenueListState.Content
        assertEquals(2, content.items.size) // Trang 1 lấy 2 đơn
        assertTrue(state.hasMore)
        assertEquals(FakeSellerRevenueRepository.CURSOR_PAGE_2, state.nextCursor)
    }

    @Test
    fun loadMore_fetchesNextPage_and_deduplicatesOrders() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Tải trang 2
        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertTrue(state.listState is SellerRevenueListState.Content)
        val content = state.listState as SellerRevenueListState.Content

        // Tổng cộng 3 đơn PAID, không trùng lặp orderId
        assertEquals(3, content.items.size)
        assertEquals(3, content.items.map { it.orderId }.distinct().size)
        assertFalse(state.hasMore)
        assertNull(state.nextCursor)
    }

    @Test
    fun sellerTwo_seesOnlyTheirOwnRevenue_and_sales() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_TWO)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_TWO))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertEquals(FakeSellerRevenueRepository.UID_SELLER_TWO, state.activeUid)

        // Seller 2 chỉ có 1 đơn PAID 120.000 VND
        assertEquals(120_000L, state.kpiState.totalRevenueVnd)
        assertEquals(1, state.kpiState.paidOrderCount)
        assertEquals(1, state.kpiState.totalDocumentsSold)

        assertTrue(state.listState is SellerRevenueListState.Content)
        val content = state.listState as SellerRevenueListState.Content
        assertEquals(1, content.items.size)
        assertEquals("ord_s2_001", content.items[0].orderId)
    }

    @Test
    fun changingActiveSeller_clearsPreviousDataImmediately() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(225_000L, viewModel.uiState.value.kpiState.totalRevenueVnd)

        // Seller 1 đăng xuất, Seller 2 đăng nhập
        fakeRepo.activeUid = FakeSellerRevenueRepository.UID_SELLER_TWO
        authRepo.switchSession(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_TWO))

        val state = viewModel.uiState.value
        assertEquals(FakeSellerRevenueRepository.UID_SELLER_TWO, state.activeUid)
        assertEquals(120_000L, state.kpiState.totalRevenueVnd)
        val content = state.listState as SellerRevenueListState.Content
        assertEquals(1, content.items.size)
        assertEquals("ord_s2_001", content.items[0].orderId)
    }

    @Test
    fun guestUser_showsGuestState_and_noRevenue() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository()
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.Guest)

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertTrue(state.isGuest)
        assertNull(state.activeUid)
        assertTrue(state.listState is SellerRevenueListState.Idle)
    }

    @Test
    fun nonSellerRole_showsDeniedError() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(isSellerRole = false)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        assertFalse(state.isSellerRole)
        assertTrue(state.listState is SellerRevenueListState.Error)
        val error = state.listState as SellerRevenueListState.Error
        assertTrue(error.message.contains("quyền Seller"))
    }

    @Test
    fun simulateOffline_handlesErrorGracefully_and_retryRecovers() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(simulateOffline = true)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Đang mất mạng
        var state = viewModel.uiState.value
        assertTrue(state.listState is SellerRevenueListState.Error)

        // Phục hồi mạng và Thử lại
        fakeRepo.simulateOffline = false
        viewModel.refresh()

        state = viewModel.uiState.value
        assertTrue(state.listState is SellerRevenueListState.Content)
        assertEquals(225_000L, state.kpiState.totalRevenueVnd)
    }

    @Test
    fun loadMoreOffline_setsLoadMoreError_withoutLosingExistingContent() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Bật mất mạng khi tải trang 2
        fakeRepo.simulateOffline = true
        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertNotNull(state.loadMoreError)
        assertFalse(state.isLoadingMore)
        // Danh sách trang 1 vẫn còn nguyên vẹn, không bị mất
        assertTrue(state.listState is SellerRevenueListState.Content)
        val content = state.listState as SellerRevenueListState.Content
        assertEquals(2, content.items.size)
    }

    @Test
    fun selectFilter_updatesSelectedFilter_and_reloadsData() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(225_000L, viewModel.uiState.value.kpiState.totalRevenueVnd)

        // Lọc trong 7 ngày qua: loại trừ đơn 20 ngày trước (75.000 VND), chỉ còn đơn hôm nay (50.000) và 3 ngày trước (100.000)
        viewModel.selectFilter(RevenueDateRangeFilter.LAST_7_DAYS)

        val state = viewModel.uiState.value
        assertEquals(RevenueDateRangeFilter.LAST_7_DAYS, state.selectedFilter)
        assertEquals("7 ngày qua", state.kpiState.periodLabel)
        assertEquals(150_000L, state.kpiState.totalRevenueVnd)
        assertEquals(2, state.kpiState.paidOrderCount)
        assertEquals(2, state.kpiState.totalDocumentsSold)
    }
}

