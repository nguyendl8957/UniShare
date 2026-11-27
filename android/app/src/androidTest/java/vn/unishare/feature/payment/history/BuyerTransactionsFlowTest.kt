package vn.unishare.feature.payment.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.payment.ui.OrderStatus

/**
 * ============================================================================
 * COMPOSE FLOW & INSTRUMENTED TEST CHO BUYER TRANSACTIONS (B05)
 * ============================================================================
 *
 * Kiểm tra các tiêu chí chấp nhận giao diện (Acceptance Criteria):
 * 1. Luồng chuyển màn hình từ List -> Detail khi chạm vào một giao dịch.
 * 2. Render đúng các trạng thái đơn hàng: PAID, PENDING, EXPIRED, REVIEW_REQUIRED.
 * 3. Render đúng thành phần phản hồi C01: Skeleton, Empty, Error.
 * 4. Nút Back từ Detail hoạt động chuẩn xác.
 * 5. Khi đổi UID: Giao diện phản ánh đúng danh sách mới của Buyer đó.
 */
class BuyerTransactionsFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePaidItem = BuyerTransactionItemUiModel(
        orderId = "ord_test_001",
        docId = "doc_dsa_01",
        documentTitle = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
        amountVnd = 50_000L,
        status = OrderStatus.PAID,
        paymentCode = "UNI8A3F12",
        createdAtUtc = "2026-11-24T07:30:00Z",
        formattedDate = "24/11/2026",
        formattedTime = "14:30",
        formattedAmountVnd = "50.000 đ"
    )

    private val samplePendingItem = BuyerTransactionItemUiModel(
        orderId = "ord_test_002",
        docId = "doc_macro_01",
        documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
        amountVnd = 35_000L,
        status = OrderStatus.PENDING,
        paymentCode = "UNIMACRO01",
        createdAtUtc = "2026-11-24T08:00:00Z",
        formattedDate = "24/11/2026",
        formattedTime = "15:00",
        formattedAmountVnd = "35.000 đ"
    )

    private val samplePaidDetail = BuyerTransactionDetailUiModel(
        orderId = "ord_test_001",
        docId = "doc_dsa_01",
        documentTitle = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
        amountVnd = 50_000L,
        status = OrderStatus.PAID,
        paymentCode = "UNI8A3F12",
        createdAtUtc = "2026-11-24T07:30:00Z",
        expiresAtUtc = "2026-11-24T07:45:00Z",
        formattedCreatedAt = "24/11/2026 14:30",
        formattedExpiresAt = "24/11/2026 14:45",
        formattedAmountVnd = "50.000 đ"
    )

    private val samplePendingDetail = BuyerTransactionDetailUiModel(
        orderId = "ord_test_002",
        docId = "doc_macro_01",
        documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
        amountVnd = 35_000L,
        status = OrderStatus.PENDING,
        paymentCode = "UNIMACRO01",
        createdAtUtc = "2026-11-24T08:00:00Z",
        expiresAtUtc = "2026-11-24T08:15:00Z",
        formattedCreatedAt = "24/11/2026 15:00",
        formattedExpiresAt = "24/11/2026 15:15",
        formattedAmountVnd = "35.000 đ"
    )

    private val sampleExpiredDetail = BuyerTransactionDetailUiModel(
        orderId = "ord_test_003",
        docId = "doc_os_01",
        documentTitle = "Hệ điều hành - Đồng bộ tiến trình",
        amountVnd = 40_000L,
        status = OrderStatus.EXPIRED,
        paymentCode = "UNIOS009",
        createdAtUtc = "2026-11-23T07:30:00Z",
        expiresAtUtc = "2026-11-23T07:45:00Z",
        formattedCreatedAt = "23/11/2026 14:30",
        formattedExpiresAt = "23/11/2026 14:45",
        formattedAmountVnd = "40.000 đ"
    )

    private val sampleReviewDetail = BuyerTransactionDetailUiModel(
        orderId = "ord_test_004",
        docId = "doc_net_01",
        documentTitle = "Mạng máy tính - Đề cương ôn tập",
        amountVnd = 45_000L,
        status = OrderStatus.REVIEW_REQUIRED,
        paymentCode = "UNINET888",
        createdAtUtc = "2026-11-22T07:30:00Z",
        expiresAtUtc = "2026-11-22T07:45:00Z",
        formattedCreatedAt = "22/11/2026 14:30",
        formattedExpiresAt = "22/11/2026 14:45",
        formattedAmountVnd = "45.000 đ"
    )

    @Test
    fun listScreen_displaysTransactionItems_and_handlesItemClick() {
        var clickedOrderId: String? = null

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionsContent(
                    uiState = BuyerTransactionsUiState(
                        activeUid = "uid_buyer_01",
                        listState = BuyerTransactionsListState.Content(
                            listOf(samplePaidItem, samplePendingItem)
                        )
                    ),
                    onSelectOrder = { orderId -> clickedOrderId = orderId }
                )
            }
        }

        // 1. Kiểm tra danh sách hiển thị đúng các item
        composeTestRule.onNodeWithTag("list_transactions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_transaction_ord_test_001").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_transaction_ord_test_002").assertIsDisplayed()

        composeTestRule.onNodeWithText("Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân").assertIsDisplayed()
        composeTestRule.onNodeWithText("50.000 đ").assertIsDisplayed()

        // 2. Chạm vào item thứ 2 (đơn PENDING)
        composeTestRule.onNodeWithTag("item_transaction_ord_test_002").performClick()
        assertEquals("ord_test_002", clickedOrderId)
    }

    @Test
    fun detailScreen_paidState_rendersReceiptSnapshot_and_readerButton() {
        var readerDocId: String? = null
        var backClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionDetailContent(
                    detail = samplePaidDetail,
                    onNavigateToReader = { docId -> readerDocId = docId },
                    onBackClick = { backClicked = true }
                )
            }
        }

        // 1. Xác nhận hiển thị Biên nhận và các trường snapshot
        composeTestRule.onNodeWithTag("screen_transaction_detail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("card_receipt").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_detail_amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("text_detail_order_id").assertIsDisplayed()

        composeTestRule.onNodeWithText("BIÊN NHẬN GIAO DỊCH").assertIsDisplayed()
        composeTestRule.onNodeWithText("50.000 đ").assertIsDisplayed()
        composeTestRule.onNodeWithText("ord_test_001").assertIsDisplayed()

        // 2. Trạng thái PAID hiển thị nút đọc tài liệu
        composeTestRule.onNodeWithTag("btn_read_document").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_read_document").performClick()
        assertEquals("doc_dsa_01", readerDocId)

        // 3. Nút Back hoạt động
        composeTestRule.onNodeWithTag("btn_back_detail").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun detailScreen_pendingState_rendersPendingWarning_and_checkoutButton() {
        var checkoutDocId: String? = null

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionDetailContent(
                    detail = samplePendingDetail,
                    onNavigateToCheckout = { docId -> checkoutDocId = docId }
                )
            }
        }

        // Đơn PENDING hiển thị cảnh báo và nút tiếp tục thanh toán
        composeTestRule.onNodeWithText("Chưa thanh toán (Pending)").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_continue_checkout").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_continue_checkout").performClick()
        assertEquals("doc_macro_01", checkoutDocId)
    }

    @Test
    fun emptyState_rendersC01EmptyFeedback() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionsContent(
                    uiState = BuyerTransactionsUiState(
                        activeUid = "uid_empty",
                        listState = BuyerTransactionsListState.Empty
                    )
                )
            }
        }

        composeTestRule.onNodeWithTag("feedback_empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chưa có giao dịch nào").assertIsDisplayed()
    }

    @Test
    fun guestState_rendersLoginWarning_and_triggersLogin() {
        var loginClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionsContent(
                    uiState = BuyerTransactionsUiState(
                        isGuest = true,
                        activeUid = null,
                        listState = BuyerTransactionsListState.Empty
                    ),
                    onLoginClick = { loginClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("guest_warning_container").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yêu cầu đăng nhập").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_login_guest").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_login_guest").performClick()
        assertTrue(loginClicked)
    }

    @Test
    fun detailScreen_failedState_rendersFailedWarning() {
        val sampleFailedDetail = BuyerTransactionDetailUiModel(
            orderId = "ord_test_005",
            docId = "doc_fail_01",
            documentTitle = "Tài liệu kiểm thử thất bại",
            amountVnd = 25_000L,
            status = OrderStatus.FAILED,
            paymentCode = "UNIFAIL01",
            createdAtUtc = "2026-11-22T07:30:00Z",
            expiresAtUtc = "2026-11-22T07:45:00Z",
            formattedCreatedAt = "22/11/2026 14:30",
            formattedExpiresAt = "22/11/2026 14:45",
            formattedAmountVnd = "25.000 đ"
        )

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionDetailContent(
                    detail = sampleFailedDetail
                )
            }
        }

        composeTestRule.onNodeWithText("Giao dịch không thành công").assertIsDisplayed()
        composeTestRule.onNodeWithText("Không thể hoàn tất đơn hàng này do lỗi nghiệp vụ.").assertIsDisplayed()
    }

    @Test
    fun detailScreen_expiredState_rendersExpiredWarning() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionDetailContent(
                    detail = sampleExpiredDetail
                )
            }
        }

        composeTestRule.onNodeWithText("Đơn hàng đã hết hạn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đơn hàng đã quá thời hạn 15 phút. Tuyệt đối KHÔNG chuyển tiền vào mã thanh toán này. Nếu vẫn muốn mua tài liệu, vui lòng tạo đơn hàng mới từ trang chi tiết tài liệu.").assertIsDisplayed()
    }

    @Test
    fun detailScreen_reviewRequiredState_rendersReviewWarning() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionDetailContent(
                    detail = sampleReviewDetail
                )
            }
        }

        composeTestRule.onNodeWithText("Đang kiểm tra đối soát").assertIsDisplayed()
    }

    @Test
    fun initialLoadingState_rendersSkeletonFeedback() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionsContent(
                    uiState = BuyerTransactionsUiState(
                        activeUid = "uid_buyer_01",
                        listState = BuyerTransactionsListState.InitialLoading
                    )
                )
            }
        }

        composeTestRule.onNodeWithTag("feedback_skeleton").assertIsDisplayed()
    }

    @Test
    fun errorState_rendersErrorFeedback_and_triggersRetry() {
        var retried = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                BuyerTransactionsContent(
                    uiState = BuyerTransactionsUiState(
                        activeUid = "uid_buyer_01",
                        listState = BuyerTransactionsListState.Error("Không có kết nối mạng")
                    ),
                    onRetryInitial = { retried = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("feedback_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Không có kết nối mạng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertTrue(retried)
    }
}
