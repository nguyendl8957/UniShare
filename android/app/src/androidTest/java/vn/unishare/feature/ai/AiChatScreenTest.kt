package vn.unishare.feature.ai

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import vn.unishare.saveRegressionScreenshot
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.ai.ui.AiChatScreen
import vn.unishare.feature.ai.ui.AiChatViewModel
import vn.unishare.feature.ai.ui.FakeAuthRepoForAi
import vn.unishare.feature.ai.ui.FakeDocumentAiRepository

class AiChatScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickSuggestion_sendsPrompt_andDisplaysAnswerWithClickableCitations() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository()
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo
        )
        var citedDocId: String? = null
        var citedPage: Int? = null

        composeRule.setContent {
            AiChatScreen(
                docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
                documentTitle = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
                viewModel = viewModel,
                onCitationClick = { docId, page ->
                    citedDocId = docId
                    citedPage = page
                }
            )
        }

        composeRule.onNodeWithTag("ai_chat_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("img_ai_header_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_suggest_summary").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_suggest_example").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_suggest_explain").assertIsDisplayed()
        composeRule.onNodeWithTag("chip_suggest_quiz").performScrollTo().assertIsDisplayed()

        // Bấm gợi ý tóm tắt tài liệu -> hiển thị câu trả lời và chip trích dẫn trang
        composeRule.onNodeWithTag("chip_suggest_summary").performClick()

        composeRule.onNodeWithText("Trang 1").assertIsDisplayed()
        composeRule.onNodeWithText("Trang 2").assertIsDisplayed()

        saveRegressionScreenshot("ai-answer")
        composeRule.onNodeWithText("Trang 1").performClick()
        assertEquals(FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH, citedDocId)
        assertEquals(1, citedPage)
    }

    @Test
    fun manualQuestion_sendsQuestion_andDisplaysResponse() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository()
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo
        )

        composeRule.setContent {
            AiChatScreen(
                docId = FakeDocumentAiRepository.DOC_MACRO_ECON,
                documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithTag("input_ai_question")
            .performTextInput("GDP thực khác GDP danh nghĩa chỗ nào?")
        composeRule.onNodeWithTag("btn_ai_send").performClick()

        composeRule.onNodeWithText("GDP thực khác GDP danh nghĩa chỗ nào?").assertIsDisplayed()
        composeRule.onNodeWithText("Trang 1").assertIsDisplayed()
        composeRule.onNodeWithText("Trang 2").assertIsDisplayed()
    }

    @Test
    fun unownedDocument403_locksComposer_andQuota429ShowsWarning() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository(simulateQuotaExceeded = true)
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo
        )

        composeRule.setContent {
            AiChatScreen(
                docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
                documentTitle = "Tìm kiếm nhị phân",
                viewModel = viewModel
            )
        }

        // Lỗi 429 hết lượt hỏi hiển thị banner cảnh báo
        composeRule.onNodeWithTag("chip_suggest_quiz").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_quota_banner").assertIsDisplayed()

        // Tài liệu chưa mua (403) -> khóa luồng nhập và hiện banner từ chối
        fakeAiRepo.simulateQuotaExceeded = false
        composeRule.runOnIdle {
            viewModel.bindDocument(
                docId = FakeDocumentAiRepository.DOC_UNPAID_SAMPLE,
                documentTitle = "Tài liệu chưa mua"
            )
        }
        composeRule.onNodeWithTag("chip_suggest_summary").performClick()

        composeRule.onNodeWithTag("ai_denied_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_ai_denied_back").assertIsDisplayed()
        composeRule.onNodeWithTag("input_ai_question").assertDoesNotExist()
    }

    @Test
    fun offlineOrTimeout_showsRetryBanner_andRetrySucceedsWhenBackOnline() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository(simulateOffline = true)
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo
        )

        composeRule.setContent {
            AiChatScreen(
                docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
                documentTitle = "Tìm kiếm nhị phân",
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithTag("chip_suggest_explain").performClick()
        composeRule.onNodeWithTag("ai_retry_banner").assertIsDisplayed()
        saveRegressionScreenshot("ai-retry")

        composeRule.runOnIdle {
            fakeAiRepo.simulateOffline = false
        }
        composeRule.onNodeWithTag("btn_ai_retry_last").performClick()

        composeRule.onNodeWithTag("ai_retry_banner").assertDoesNotExist()
        composeRule.onNodeWithText("Trang 1").assertIsDisplayed()
    }
}
