package vn.unishare.feature.payment

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
import vn.unishare.feature.payment.ui.BankTransferInfo
import vn.unishare.feature.payment.ui.CheckoutEvent
import vn.unishare.feature.payment.ui.CheckoutViewModel
import vn.unishare.feature.payment.ui.FakeOrderRepository
import vn.unishare.feature.payment.ui.KEY_CHECKOUT_IDEMPOTENCY
import vn.unishare.feature.payment.ui.KEY_CHECKOUT_ORDER_ID
import vn.unishare.feature.payment.ui.OrderStatus
import vn.unishare.feature.payment.ui.OrderUiModel
import java.io.IOException
import java.time.Instant

/**
 * Fake AuthRepository kiểm soát phiên phục vụ test cho B04
 */
private class TestFakeAuthRepository(
    initialSession: AuthSession = AuthSession.SignedIn("buyer_test_01")
) : AuthRepository {
    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = _session

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> = Result.success(Unit)
}

class CheckoutViewModelTest {

    @Test
    fun init_createsOrder_successfully_withDefaultPendingState() = runBlocking {
        val fakeRepo = FakeOrderRepository(
            initialStatus = OrderStatus.PENDING,
            mockAmountVnd = 50_000L,
            mockDocumentTitle = "Cấu trúc dữ liệu & Giải thuật"
        )
        val viewModel = CheckoutViewModel(
            docId = "doc_ctdl_01",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(1, fakeRepo.createCallCount)
        assertEquals(OrderStatus.PENDING, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.networkError)
        assertNull(viewModel.uiState.value.businessError)

        val order = viewModel.uiState.value.order
        assertNotNull(order)
        assertEquals(50_000L, order?.amountVnd)
        assertEquals("Cấu trúc dữ liệu & Giải thuật", order?.documentTitle)
    }

    @Test
    fun idempotencyKey_isPreservedAcrossRetries_and_savedInSavedStateHandle() = runBlocking {
        val fakeRepo = FakeOrderRepository()
        val handle = SavedStateHandle()
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = handle,
            mainDispatcher = Dispatchers.Unconfined
        )

        val firstKey = viewModel.idempotencyKey
        assertEquals(firstKey, handle.get<String>(KEY_CHECKOUT_IDEMPOTENCY))
        assertEquals(firstKey, fakeRepo.lastIdempotencyKey)

        // Gọi nạp/tạo lại -> Khóa Idempotency vẫn giữ nguyên không đổi
        viewModel.manualRefresh()
        assertEquals(firstKey, fakeRepo.lastIdempotencyKey)
        assertEquals(firstKey, viewModel.idempotencyKey)
    }

    @Test
    fun restoreExistingOrderId_fromSavedStateHandle_fetchesOrderStatusDirectly() = runBlocking {
        val fakeRepo = FakeOrderRepository()
        val handle = SavedStateHandle(mapOf(KEY_CHECKOUT_ORDER_ID to "ord_saved_888"))
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = handle,
            mainDispatcher = Dispatchers.Unconfined
        )

        // Khi đã có orderId trong SavedStateHandle, ViewModel gọi getOrderStatus
        assertTrue(fakeRepo.getStatusCallCount >= 1)
        assertEquals("ord_saved_888", viewModel.uiState.value.order?.orderId)
    }

    @Test
    fun networkError_onExistingOrderId_reportsNetworkError_withoutCallingCreateOrder() = runBlocking {
        val fakeRepo = FakeOrderRepository().apply {
            nextResult = Result.failure(IOException("Mất mạng khi lấy đơn"))
        }
        val handle = SavedStateHandle(mapOf(KEY_CHECKOUT_ORDER_ID to "ord_saved_888"))
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = handle,
            mainDispatcher = Dispatchers.Unconfined
        )

        // Không được fallback tạo đơn mới khi lỗi là do mạng
        assertEquals(0, fakeRepo.createCallCount)
        assertTrue(fakeRepo.getStatusCallCount >= 1)
        assertTrue(viewModel.uiState.value.networkError.orEmpty().contains("Mất kết nối mạng"))
    }

    @Test
    fun onScreenResumed_refreshesPendingOrder() = runBlocking {
        val fakeRepo = FakeOrderRepository()
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        val initialStatusCallCount = fakeRepo.getStatusCallCount
        // Khi màn hình Resume trở lại -> tự động kiểm tra lại trạng thái
        viewModel.onScreenResumed()
        assertTrue(fakeRepo.getStatusCallCount > initialStatusCallCount)
    }

    @Test
    fun networkError_keepsPendingStatus_and_setsNetworkErrorMessage() = runBlocking {
        val fakeRepo = FakeOrderRepository().apply {
            nextResult = Result.failure(IOException("Không thể kết nối Internet"))
        }
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Mất mạng: Trạng thái đơn vẫn là PENDING, networkError hiển thị thông báo
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(OrderStatus.PENDING, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.networkError.orEmpty().contains("Mất kết nối mạng"))

        // Thử lại khi có mạng -> Thành công, networkError được dọn sạch
        fakeRepo.nextResult = null
        viewModel.manualRefresh()
        assertNull(viewModel.uiState.value.networkError)
        assertNotNull(viewModel.uiState.value.order)
    }

    @Test
    fun unauthorized401_triggersNavigateToAuthEvent_withCorrectReturnTarget() = runBlocking {
        val fakeRepo = FakeOrderRepository().apply {
            nextResult = Result.failure(IllegalArgumentException("401 Unauthorized"))
        }
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertTrue(viewModel.uiState.value.businessError.orEmpty().contains("hết hạn"))
        val event = viewModel.events.first()
        assertEquals(CheckoutEvent.NavigateToAuth("checkout:doc_test_101"), event)
    }

    @Test
    fun businessErrors_suchAsAlreadyOwnedOrSelfPurchase_transitionToFailedState() = runBlocking {
        val fakeRepo = FakeOrderRepository().apply {
            nextResult = Result.failure(IllegalArgumentException("ALREADY_OWNED"))
        }
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(OrderStatus.FAILED, viewModel.uiState.value.status)
        assertEquals("Bạn đã mua và sở hữu tài liệu này từ trước.", viewModel.uiState.value.businessError)
    }

    @Test
    fun polling_detectsPaidStatus_and_stopsPolling() = runBlocking {
        val fakeRepo = FakeOrderRepository(initialStatus = OrderStatus.PENDING).apply {
            pollsUntilPaid = 2
        }
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(OrderStatus.PENDING, viewModel.uiState.value.status)

        // Kích hoạt làm mới/kiểm tra lần 1
        viewModel.manualRefresh()
        // Kích hoạt làm mới/kiểm tra lần 2 -> Đạt ngưỡng pollsUntilPaid -> chuyển sang PAID
        viewModel.manualRefresh()

        assertEquals(OrderStatus.PAID, viewModel.uiState.value.status)
        assertFalse(viewModel.uiState.value.isPolling)
    }

    @Test
    fun copyField_updatesCopiedMessage_and_emitsShowSnackbarEvent() = runBlocking {
        val fakeRepo = FakeOrderRepository()
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.copyField("Số tài khoản", "0345678999")
        assertEquals("Đã sao chép Số tài khoản", viewModel.uiState.value.copiedFieldMessage)

        val event = viewModel.events.first()
        assertEquals(CheckoutEvent.ShowSnackbar("Đã sao chép Số tài khoản"), event)
    }

    @Test
    fun expiredOrder_calculatesExpiredStatus_and_stopsPolling() = runBlocking {
        // Đơn hàng có expiresAtUtc trong quá khứ -> Phải chuyển sang EXPIRED
        val expiredOrder = OrderUiModel(
            orderId = "ord_expired_111",
            docId = "doc_test_101",
            documentTitle = "Tài liệu test",
            amountVnd = 20_000L,
            status = OrderStatus.PENDING,
            paymentCode = "UNI111222",
            createdAtUtc = Instant.now().minusSeconds(1000).toString(),
            expiresAtUtc = Instant.now().minusSeconds(100).toString(),
            transferInfo = BankTransferInfo()
        )
        val fakeRepo = FakeOrderRepository().apply {
            nextResult = Result.success(expiredOrder)
        }

        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        assertEquals(OrderStatus.EXPIRED, viewModel.uiState.value.status)
        assertEquals(0L, viewModel.uiState.value.remainingSeconds)
    }

    @Test
    fun backAndOpenReader_emitCorrectEvents() = runBlocking {
        val fakeRepo = FakeOrderRepository()
        val viewModel = CheckoutViewModel(
            docId = "doc_test_101",
            orderRepository = fakeRepo,
            authRepository = TestFakeAuthRepository(),
            savedStateHandle = SavedStateHandle(),
            mainDispatcher = Dispatchers.Unconfined
        )

        // Test mở tài liệu khi PAID
        viewModel.onOpenReaderClicked()
        val readerEvent = viewModel.events.first()
        assertEquals(CheckoutEvent.NavigateToReader("doc_test_101"), readerEvent)

        // Test bấm nút Back
        viewModel.onBackClicked()
        val backEvent = viewModel.events.first()
        assertEquals(CheckoutEvent.NavigateBack, backEvent)
    }
}

