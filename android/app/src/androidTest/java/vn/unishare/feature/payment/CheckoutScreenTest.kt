package vn.unishare.feature.payment

//import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.payment.ui.BankTransferInfo
import vn.unishare.feature.payment.ui.CheckoutScreenContent
import vn.unishare.feature.payment.ui.CheckoutUiState
import vn.unishare.feature.payment.ui.OrderStatus
import vn.unishare.feature.payment.ui.OrderUiModel

class CheckoutScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleOrder = OrderUiModel(
        orderId = "ord_test_001",
        docId = "doc_test_101",
        documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật",
        amountVnd = 50_000L,
        status = OrderStatus.PENDING,
        paymentCode = "UNI8A3F12",
        createdAtUtc = "2026-11-19T10:00:00Z",
        expiresAtUtc = "2026-11-19T10:15:00Z",
        transferInfo = BankTransferInfo(
            bankName = "Ngân hàng Quân đội",
            bankCode = "MB",
            accountNumber = "0000000000",
            accountHolder = "UNISHARE SYSTEM",
            amountVnd = 50_000L,
            paymentCode = "UNI8A3F12"
        )
    )

    @Test
    fun pendingState_displaysBankTransferInstructions_and_copyButtons() {
        var copiedLabel: String? = null
        var copiedValue: String? = null
        var refreshClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.PENDING,
                        order = sampleOrder,
                        remainingSeconds = 850L
                    ),
                    onCopyField = { label, value ->
                        copiedLabel = label
                        copiedValue = value
                    },
                    onManualRefresh = { refreshClicked = true },
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        // 1. Xác nhận hiển thị thông tin ngân hàng & các nút sao chép
        composeTestRule.onNodeWithTag("section_bank_instructions").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thông tin chuyển khoản").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_copy_account").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_copy_amount").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_copy_code").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_refresh_status").performScrollTo().assertIsDisplayed()

        // 2. Thao tác sao chép mã thanh toán
        composeTestRule.onNodeWithTag("btn_copy_code").performScrollTo().performClick()
        assertEquals("Nội dung chuyển khoản", copiedLabel)
        assertEquals("UNI8A3F12", copiedValue)

        // 3. Thao tác bấm nút "Tôi đã chuyển tiền / Kiểm tra ngay"
        composeTestRule.onNodeWithTag("btn_refresh_status").performScrollTo().performClick()
        assertTrue(refreshClicked)
    }

    @Test
    fun paidState_hidesBankInstructions_and_showsReadDocumentButton() {
        var openReaderClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.PAID,
                        order = sampleOrder.copy(status = OrderStatus.PAID)
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = {},
                    onOpenReader = { openReaderClicked = true },
                    onBack = {}
                )
            }
        }

        // Bất biến: Ẩn hoàn toàn thông tin ngân hàng khi PAID
        composeTestRule.onNodeWithTag("section_bank_instructions").assertDoesNotExist()

        // Hiển thị thẻ chúc mừng và nút đọc tài liệu
        composeTestRule.onNodeWithTag("section_paid_success").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thanh toán thành công!").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_open_reader").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_open_reader").performClick()
        assertTrue(openReaderClicked)
    }

    @Test
    fun expiredState_hidesBankInstructions_and_showsRetryNotice() {
        var retryClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.EXPIRED,
                        order = sampleOrder.copy(status = OrderStatus.EXPIRED)
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = { retryClicked = true },
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        // Bất biến: Ẩn thông tin ngân hàng khi EXPIRED
        composeTestRule.onNodeWithTag("section_bank_instructions").assertDoesNotExist()

        // Kiểm tra lại trạng thái đơn đã hết hạn.
        composeTestRule.onNodeWithTag("section_expired").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đơn hàng đã hết hạn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_retry_expired").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_retry_expired").performClick()
        assertTrue(retryClicked)
    }

    @Test
    fun reviewRequiredState_hidesBankInstructions_and_showsWaitingNotice() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.REVIEW_REQUIRED,
                        order = sampleOrder.copy(status = OrderStatus.REVIEW_REQUIRED)
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = {},
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        // Bất biến: Ẩn thông tin ngân hàng khi REVIEW_REQUIRED
        composeTestRule.onNodeWithTag("section_bank_instructions").assertDoesNotExist()

        // Hiển thị thông báo đang chờ đối soát
        composeTestRule.onNodeWithTag("section_review_required").assertIsDisplayed()
        composeTestRule.onNodeWithText("Đơn hàng đang chờ đối soát").assertIsDisplayed()
    }

    @Test
    fun networkErrorBanner_displaysRetryButton_and_callsRefresh() {
        var retryClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.PENDING,
                        order = sampleOrder,
                        networkError = "Mất kết nối mạng. Vui lòng kiểm tra Internet và bấm Thử lại.",
                        remainingSeconds = 850L
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = { retryClicked = true },
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        // Banner lỗi mạng hiển thị nhưng thông tin chuyển khoản vẫn giữ nguyên (không bị mất)
        composeTestRule.onNodeWithTag("banner_network_error").assertIsDisplayed()
        composeTestRule.onNodeWithTag("section_bank_instructions").assertIsDisplayed()

        // Bấm nút "Thử lại" trên banner
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertTrue(retryClicked)
    }

    @Test
    fun backButton_triggersBackCallback() {
        var backClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.PENDING,
                        order = sampleOrder
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = {},
                    onOpenReader = {},
                    onBack = { backClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithTag("btn_checkout_back").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun failedState_hidesBankInstructions_and_showsErrorMessage() {
        var backClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        status = OrderStatus.FAILED,
                        order = sampleOrder.copy(status = OrderStatus.FAILED),
                        businessError = "Bạn là tác giả của tài liệu này nên không thể tự mua."
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = {},
                    onOpenReader = {},
                    onBack = { backClicked = true }
                )
            }
        }

        // Bất biến: Ẩn thông tin ngân hàng khi FAILED
        composeTestRule.onNodeWithTag("section_bank_instructions").assertDoesNotExist()

        // Hiển thị thông báo lỗi nghiệp vụ và nút Quay lại
        composeTestRule.onNodeWithText("Bạn là tác giả của tài liệu này nên không thể tự mua.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quay lại").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun initialOfflineLoad_showsUiFeedbackError_andAllowsRetry() {
        var retryClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.BUYER) {
                CheckoutScreenContent(
                    uiState = CheckoutUiState(
                        isLoading = false,
                        order = null,
                        networkError = "Mất kết nối mạng. Vui lòng kiểm tra Internet và bấm Thử lại.",
                        remainingSeconds = 850L
                    ),
                    onCopyField = { _, _ -> },
                    onManualRefresh = { retryClicked = true },
                    onOpenReader = {},
                    onBack = {}
                )
            }
        }

        // Không bị màn hình trắng, hiển thị thông báo lỗi mạng và nút Thử lại
        composeTestRule.onNodeWithText("Mất kết nối mạng. Vui lòng kiểm tra Internet và bấm Thử lại.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertTrue(retryClicked)
    }
}

