package vn.unishare.feature.explore

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.explore.ui.DocumentDetailScreen
import vn.unishare.feature.explore.ui.ExploreScreen
import vn.unishare.feature.explore.ui.ExploreViewModel
import vn.unishare.feature.explore.ui.FakeExploreCatalogRepository

class ExploreFlowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeToSearchFilterSortToDetailGalleryAndBuyCta_passesDocIdAndPreservesStateOnBack() {
        val fakeRepo = FakeExploreCatalogRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate
        )

        var activeDocId by mutableStateOf<String?>(null)
        var requestedBuyDocId: String? = null
        var clickedSellerId: String? = null

        composeTestRule.setContent {
            UniShareTheme {
                val docId = activeDocId
                if (docId == null) {
                    ExploreScreen(
                        viewModel = viewModel,
                        onDocumentClick = { selectedId -> activeDocId = selectedId }
                    )
                } else {
                    DocumentDetailScreen(
                        docId = docId,
                        viewModel = viewModel,
                        onBackClick = { activeDocId = null },
                        onBuyClick = { buyDocId -> requestedBuyDocId = buyDocId },
                        onSellerClick = { sellerId -> clickedSellerId = sellerId }
                    )
                }
            }
        }

        // Kiểm tra màn Trang chủ hiển thị đủ 2 khu vực chính
        composeTestRule.onNodeWithText("Gợi ý cho bạn").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tài liệu mới nhất").assertIsDisplayed()

        // Chọn chip danh mục rồi quay lại Tất cả
        composeTestRule.onNodeWithTag("category_chip_note").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("category_chip_all").performClick()
        composeTestRule.waitForIdle()

        // Mở màn tìm kiếm và chọn chủ đề C++
        composeTestRule.onNodeWithTag("home_search_bar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Hãy nhập từ khóa để tìm kiếm").assertIsDisplayed()

        composeTestRule.onNodeWithTag("topic_chip_C++").performClick()
        composeTestRule.waitForIdle()

        // Mở Bộ lọc nâng cao, chọn Lập trình và áp dụng
        composeTestRule.onNodeWithTag("btn_open_filter").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("filter_subject_programming").performClick()
        composeTestRule.onNodeWithTag("btn_filter_apply").performClick()
        composeTestRule.waitForIdle()

        // Mở Sắp xếp và chọn Giá thấp -> cao
        composeTestRule.onNodeWithTag("btn_open_sort").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sort_option_price_asc").performClick()
        composeTestRule.waitForIdle()

        // Mở chi tiết tài liệu C++
        composeTestRule.onNodeWithTag("doc_card_doc_cpp_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText("Tổng hợp kiến thức Lập trình C++ (Cơ bản → Nâng cao)")
            .assertIsDisplayed()

        // Mở gallery xem ảnh, chuyển sang trang 2 rồi đóng gallery
        composeTestRule.onNodeWithTag("btn_open_gallery").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1/5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gallery_thumb_1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2/5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_close_gallery").performClick()
        composeTestRule.waitForIdle()

        // Bấm thẻ người bán công khai và nút Mua ngay để kiểm tra callback
        composeTestRule.onNodeWithTag("card_seller_public").performScrollTo().performClick()
        assertEquals("seller_hung_01", clickedSellerId)

        composeTestRule.onNodeWithTag("btn_buy_now").performClick()
        assertEquals("doc_cpp_01", requestedBuyDocId)

        // Quay lại Explore phải giữ nguyên từ khóa C++ và danh sách kết quả
        composeTestRule.onNodeWithTag("btn_detail_back").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_card_doc_cpp_01").assertIsDisplayed()
        assertEquals("C++", viewModel.uiState.value.query)
    }

    @Test
    fun noResults_showsEmptyStateAndResetRecoversList() {
        val viewModel = ExploreViewModel(
            catalogRepository = FakeExploreCatalogRepository(),
            mainDispatcher = Dispatchers.Main.immediate
        )

        composeTestRule.setContent {
            UniShareTheme {
                ExploreScreen(
                    viewModel = viewModel,
                    onDocumentClick = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("home_search_bar").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_explore_search").performTextReplacement("khong_ton_tai_xyz")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("explore_no_results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Không tìm thấy tài liệu phù hợp").assertIsDisplayed()

        // Bấm Xóa bộ lọc để phục hồi danh sách
        composeTestRule.onNodeWithTag("btn_reset_filters_empty").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("doc_card_doc_cpp_01").assertIsDisplayed()
    }

    @Test
    fun networkError_showsRetryActionAndRecoversWhenOnline() {
        val fakeRepo = FakeExploreCatalogRepository(simulateOffline = true)
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate
        )

        composeTestRule.setContent {
            UniShareTheme {
                ExploreScreen(
                    viewModel = viewModel,
                    onDocumentClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Thử lại").assertIsDisplayed()

        fakeRepo.simulateOffline = false
        composeTestRule.onNodeWithText("Thử lại").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Tài liệu mới nhất").assertIsDisplayed()
    }

    @Test
    fun brokenPreviewShowsFallback_andArchivedDocumentShowsUnavailable() {
        val fakeRepo = FakeExploreCatalogRepository()
        val viewModel = ExploreViewModel(
            catalogRepository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoad = false
        )

        var currentDocId by mutableStateOf("doc_ctdl_08")

        composeTestRule.setContent {
            UniShareTheme {
                DocumentDetailScreen(
                    docId = currentDocId,
                    viewModel = viewModel,
                    onBackClick = {},
                    onBuyClick = {},
                    onSellerClick = {}
                )
            }
        }

        // Tài liệu có ảnh xem trước bị lỗi phải hiện fallback an toàn
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("preview_broken_fallback").assertIsDisplayed()
        composeTestRule.onNodeWithText("Không thể tải ảnh xem trước").assertIsDisplayed()

        // Tài liệu đã bị ẩn phải hiện thông báo không khả dụng
        currentDocId = "doc_archived_99"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("detail_not_found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tài liệu không khả dụng").assertIsDisplayed()
    }
}
