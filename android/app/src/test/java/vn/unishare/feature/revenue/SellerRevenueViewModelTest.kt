package vn.unishare.feature.revenue

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import vn.unishare.feature.revenue.ui.FakeSellerRevenueRepository
import vn.unishare.feature.revenue.ui.RevenueDateRangeFilter
import vn.unishare.feature.revenue.ui.SellerRevenueListState
import vn.unishare.feature.revenue.ui.SellerRevenueRepository
import vn.unishare.feature.revenue.ui.SellerRevenueSummaryDto
import vn.unishare.feature.revenue.ui.SellerRevenueViewModel
import vn.unishare.feature.revenue.ui.SellerSalesPageDto
import java.time.LocalDate
import java.time.ZoneId

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

    @Test
    fun selectFilter_today_usesVnDateRangeHalfOpen_and_respectsMidnightBoundaries() = runBlocking {
        val zoneVn = ZoneId.of("Asia/Ho_Chi_Minh")
        val todayVn = LocalDate.now(zoneVn)
        val tomorrowVn = todayVn.plusDays(1)

        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        // Thêm đơn ở đúng ranh giới 23:59:59 hôm qua (loại trừ) và 00:00:00 ngày mai (loại trừ vì [from, to))
        fakeRepo.addTestOrder(
            orderId = "ord_s1_yesterday_end",
            sellerId = FakeSellerRevenueRepository.UID_SELLER_ONE,
            docId = "doc_boundary_1",
            documentTitle = "Đơn cuối ngày hôm qua",
            amountVnd = 20_000L,
            status = OrderStatus.PAID,
            paymentCode = "UNIBND01",
            paidAtUtc = todayVn.atStartOfDay(zoneVn).minusSeconds(1).toInstant().toString()
        )
        fakeRepo.addTestOrder(
            orderId = "ord_s1_tomorrow_start",
            sellerId = FakeSellerRevenueRepository.UID_SELLER_ONE,
            docId = "doc_boundary_2",
            documentTitle = "Đơn đầu ngày mai",
            amountVnd = 40_000L,
            status = OrderStatus.PAID,
            paymentCode = "UNIBND02",
            paidAtUtc = tomorrowVn.atStartOfDay(zoneVn).toInstant().toString()
        )

        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))
        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.selectFilter(RevenueDateRangeFilter.TODAY)

        // Xác nhận định dạng YYYY-MM-DD và khoảng nửa mở [today, tomorrow)
        assertEquals(todayVn.toString(), fakeRepo.lastRevenueFrom)
        assertEquals(tomorrowVn.toString(), fakeRepo.lastRevenueTo)

        // Chỉ tính đơn ord_s1_001 (50.000 đ) phát sinh trong ngày hôm nay
        val state = viewModel.uiState.value
        assertEquals(50_000L, state.kpiState.totalRevenueVnd)
        assertEquals(1, state.kpiState.paidOrderCount)
        assertEquals(1, state.kpiState.totalDocumentsSold)
    }

    @Test
    fun totalDocumentsSold_countsSuccessfulSales_evenWhenSameDocumentSoldTwice() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        // Bán thêm 1 lượt nữa cho cùng tài liệu doc_dsa_01
        fakeRepo.addTestOrder(
            orderId = "ord_s1_repeat_doc",
            sellerId = FakeSellerRevenueRepository.UID_SELLER_ONE,
            docId = "doc_dsa_01",
            documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật nâng cao",
            amountVnd = 50_000L,
            status = OrderStatus.PAID,
            paymentCode = "UNIS1R01",
            paidAtUtc = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toString()
        )

        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))
        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val state = viewModel.uiState.value
        // 3 đơn gốc + 1 đơn bán lại = 4 lượt bán thành công
        assertEquals(275_000L, state.kpiState.totalRevenueVnd)
        assertEquals(4, state.kpiState.paidOrderCount)
        assertEquals(4, state.kpiState.totalDocumentsSold)
    }

    @Test
    fun refresh_afterSellerRoleGranted_restoresIsSellerRoleTrue() = runBlocking {
        val fakeRepo = FakeSellerRevenueRepository(
            activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE,
            isSellerRole = false
        )
        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))

        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = fakeRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertFalse(viewModel.uiState.value.isSellerRole)

        // Tài khoản được cấp quyền Seller và bấm refresh
        fakeRepo.isSellerRole = true
        viewModel.refresh()

        val state = viewModel.uiState.value
        assertTrue(state.isSellerRole)
        assertTrue(state.listState is SellerRevenueListState.Content)
        assertEquals(225_000L, state.kpiState.totalRevenueVnd)
    }

    @Test
    fun loadMore_whenAccountSwitchesMidFlight_ignoresStalePageResult() = runBlocking {
        val delegate = FakeSellerRevenueRepository(activeUid = FakeSellerRevenueRepository.UID_SELLER_ONE)
        val page2Gate = CompletableDeferred<Unit>()
        val gatedRepo = object : SellerRevenueRepository {
            override suspend fun checkSellerRole(): Result<Boolean> = delegate.checkSellerRole()
            override suspend fun getRevenueSummary(from: String?, to: String?): Result<SellerRevenueSummaryDto> =
                delegate.getRevenueSummary(from, to)

            override suspend fun getSales(cursor: String?, limit: Int): Result<SellerSalesPageDto> {
                if (cursor == FakeSellerRevenueRepository.CURSOR_PAGE_2) {
                    page2Gate.await()
                }
                return delegate.getSales(cursor, limit)
            }
        }

        val authRepo = RevenueTestFakeAuthRepository(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_ONE))
        val viewModel = SellerRevenueViewModel(
            authRepository = authRepo,
            revenueRepository = gatedRepo,
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Bắt đầu tải trang 2 cho Seller 1 (đang treo chờ page2Gate)
        viewModel.loadMore()

        // Đổi sang Seller 2 trước khi trang 2 của Seller 1 trả về
        delegate.activeUid = FakeSellerRevenueRepository.UID_SELLER_TWO
        authRepo.switchSession(AuthSession.SignedIn(FakeSellerRevenueRepository.UID_SELLER_TWO))

        // Mở khóa trang 2 cũ của Seller 1
        page2Gate.complete(Unit)

        val state = viewModel.uiState.value
        assertEquals(FakeSellerRevenueRepository.UID_SELLER_TWO, state.activeUid)
        val content = state.listState as SellerRevenueListState.Content
        // Tuyệt đối không bị trộn dữ liệu Seller 1 vào Seller 2
        assertEquals(1, content.items.size)
        assertEquals("ord_s2_001", content.items[0].orderId)
    }
}

