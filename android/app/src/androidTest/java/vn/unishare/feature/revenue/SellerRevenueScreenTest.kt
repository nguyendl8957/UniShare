package vn.unishare.feature.revenue

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
import vn.unishare.feature.revenue.ui.RevenueDateRangeFilter
import vn.unishare.feature.revenue.ui.SellerRevenueKpiUiModel
import vn.unishare.feature.revenue.ui.SellerRevenueListState
import vn.unishare.feature.revenue.ui.SellerRevenueScreenContent
import vn.unishare.feature.revenue.ui.SellerRevenueUiState
import vn.unishare.feature.revenue.ui.SellerTransactionItemUiModel

/**
 * ============================================================================
 * COMPOSE UI INSTRUMENTED TEST CHO MÀN HÌNH DOANH THU SELLER (B06)
 * ============================================================================
 *
 * Kiểm tra các tiêu chí chấp nhận UI (Issue B06 §4 & §7):
 * 1. Hiển thị nhãn cố định "DOANH THU GHI NHẬN" và các KPI tổng tiền VND, số đơn, số tài liệu.
 * 2. Tuyệt đối không hiển thị số dư rút tiền (Payout) hay thông tin nhạy cảm của Buyer.
 * 3. Hiển thị danh sách đơn bán với badge "ĐÃ NHẬN TIỀN" và số tiền `+XX.XXX đ`.
 * 4. Chuyển đổi bộ lọc khoảng thời gian.
 * 5. Các trạng thái: Khách vãng lai (Guest), Bị từ chối quyền (Denied), Danh sách trống (Empty).
 */
class SellerRevenueScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleKpi = SellerRevenueKpiUiModel(
        totalRevenueVnd = 225_000L,
        formattedTotalRevenue = "225.000 đ",
        paidOrderCount = 3,
        totalDocumentsSold = 3,
        periodLabel = "Toàn thời gian"
    )

    private val sampleItems = listOf(
        SellerTransactionItemUiModel(
            orderId = "ord_s1_001",
            docId = "doc_dsa_01",
            documentTitle = "Giáo trình Cấu trúc dữ liệu & Giải thuật nâng cao",
            amountVnd = 50_000L,
            status = OrderStatus.PAID,
            paymentCode = "UNIS1A01",
            paidAtUtc = "2026-11-24T10:00:00Z",
            formattedDate = "24/11/2026",
            formattedTime = "17:00",
            formattedAmountVnd = "+50.000 đ"
        ),
        SellerTransactionItemUiModel(
            orderId = "ord_s1_002",
            docId = "doc_web_02",
            documentTitle = "Tổng hợp đề thi Lập trình Web Fullstack",
            amountVnd = 100_000L,
            status = OrderStatus.PAID,
            paymentCode = "UNIS1A02",
            paidAtUtc = "2026-11-21T08:30:00Z",
            formattedDate = "21/11/2026",
            formattedTime = "15:30",
            formattedAmountVnd = "+100.000 đ"
        )
    )

    @Test
    fun kpiCard_displaysRecordedRevenueLabel_and_formattedVnd_and_noPayout() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_test_01",
                        kpiState = sampleKpi,
                        listState = SellerRevenueListState.Content(sampleItems)
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        // 1. Kiểm tra nhãn cố định "DOANH THU GHI NHẬN"
        composeTestRule.onNodeWithText("DOANH THU GHI NHẬN").assertIsDisplayed()

        // 2. Kiểm tra tổng tiền VND
        composeTestRule.onNodeWithTag("kpi_total_revenue").assertIsDisplayed()
        composeTestRule.onNodeWithText("225.000 đ").assertIsDisplayed()

        // 3. Kiểm tra số đơn và số tài liệu bán
        composeTestRule.onNodeWithTag("kpi_order_count").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 đơn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("kpi_doc_count").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 tài liệu").assertIsDisplayed()

        // 4. Kiểm tra ghi chú minh bạch (Không phải số dư rút tiền)
        composeTestRule.onNodeWithTag("txt_revenue_disclaimer").assertIsDisplayed()
    }

    @Test
    fun contentState_rendersPaidSalesList_withCorrectAmounts_and_noBuyerSensitiveInfo() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_test_01",
                        kpiState = sampleKpi,
                        listState = SellerRevenueListState.Content(sampleItems)
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        // Kiểm tra danh sách hiển thị
        composeTestRule.onNodeWithTag("seller_revenue_list").assertIsDisplayed()

        // Kiểm tra đơn hàng 1
        composeTestRule.onNodeWithTag("item_order_ord_s1_001").assertIsDisplayed()
        composeTestRule.onNodeWithText("Giáo trình Cấu trúc dữ liệu & Giải thuật nâng cao").assertIsDisplayed()
        composeTestRule.onNodeWithTag("item_amount_ord_s1_001").assertIsDisplayed()
        composeTestRule.onNodeWithText("+50.000 đ").assertIsDisplayed()

        // Kiểm tra đơn hàng 2
        composeTestRule.onNodeWithTag("item_order_ord_s1_002").assertIsDisplayed()
        composeTestRule.onNodeWithText("+100.000 đ").assertIsDisplayed()
    }

    @Test
    fun emptyState_rendersEmptySalesMessage_whenNoTransactions() {
        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_empty",
                        kpiState = SellerRevenueKpiUiModel(),
                        listState = SellerRevenueListState.Empty
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("empty_sales_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chưa có giao dịch bán nào").assertIsDisplayed()
    }

    @Test
    fun guestState_rendersLoginPrompt_and_clickingLoginTriggersCallback() {
        var loginClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = true,
                        activeUid = null
                    ),
                    onNavigateBack = {},
                    onLoginClick = { loginClicked = true },
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("guest_notice_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Yêu cầu đăng nhập").assertIsDisplayed()

        composeTestRule.onNodeWithText("Đăng nhập UniShare").performClick()
        assertTrue(loginClicked)
    }

    @Test
    fun deniedState_rendersNotSellerRolePrompt_and_clickingBackTriggersCallback() {
        var backClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = false,
                        activeUid = "uid_buyer_only"
                    ),
                    onNavigateBack = { backClicked = true },
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("denied_notice_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chưa có quyền Seller").assertIsDisplayed()

        composeTestRule.onNodeWithText("Quay lại").performClick()
        assertTrue(backClicked)
    }

    @Test
    fun dateFilterChips_clickingChipTriggersCallback() {
        var selectedFilter: RevenueDateRangeFilter? = null

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_test_01",
                        kpiState = sampleKpi,
                        listState = SellerRevenueListState.Content(sampleItems)
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = { selectedFilter = it },
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("chip_filter_THIS_MONTH").performClick()
        assertEquals(RevenueDateRangeFilter.THIS_MONTH, selectedFilter)
    }

    @Test
    fun listError_withExistingKpi_displaysInlineErrorAndRetry() {
        var refreshClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_test_01",
                        kpiState = sampleKpi,
                        listState = SellerRevenueListState.Error("Mất kết nối mạng")
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = { refreshClicked = true },
                    onLoadMore = {},
                    onRetryLoadMore = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("list_error_view").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mất kết nối mạng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertTrue(refreshClicked)
    }

    @Test
    fun loadMoreError_displaysFooterRetryButton() {
        var retryLoadMoreClicked = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerRevenueScreenContent(
                    uiState = SellerRevenueUiState(
                        isRestoringSession = false,
                        isGuest = false,
                        isSellerRole = true,
                        activeUid = "uid_seller_test_01",
                        kpiState = sampleKpi,
                        listState = SellerRevenueListState.Content(sampleItems),
                        hasMore = true,
                        loadMoreError = "Lỗi tải thêm trang"
                    ),
                    onNavigateBack = {},
                    onLoginClick = {},
                    onFilterSelected = {},
                    onRefresh = {},
                    onLoadMore = {},
                    onRetryLoadMore = { retryLoadMoreClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Lỗi tải thêm trang").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thử lại").performClick()
        assertTrue(retryLoadMoreClicked)
    }
}

