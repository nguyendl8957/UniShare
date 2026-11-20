package vn.unishare.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.library.ui.FakeAuthRepoForLibrary
import vn.unishare.feature.library.ui.FakeLibraryRepository
import vn.unishare.feature.library.ui.PdfReaderScreen
import vn.unishare.feature.library.ui.PurchasedLibraryScreen
import vn.unishare.feature.library.ui.PurchasedLibraryViewModel

class PurchasedLibraryFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun libraryList_opensSelectedDocumentInReader_andNavigatesPagesAndBack() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo
        )
        var openedDocId by mutableStateOf<String?>(null)

        composeRule.setContent {
            val currentDoc = openedDocId
            if (currentDoc == null) {
                PurchasedLibraryScreen(
                    viewModel = viewModel,
                    onOpenDocumentReader = { docId -> openedDocId = docId }
                )
            } else {
                PdfReaderScreen(
                    docId = currentDoc,
                    viewModel = viewModel,
                    onBackClick = { openedDocId = null }
                )
            }
        }

        // Tài liệu hợp lệ xuất hiện, tài liệu bị thu hồi không xuất hiện
        composeRule.onNodeWithTag("library_doc_${FakeLibraryRepository.DOC_DSA_BINARY_SEARCH}")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("library_doc_${FakeLibraryRepository.DOC_REVOKED_SAMPLE}")
            .assertDoesNotExist()

        // Bấm mở đúng tài liệu và điều hướng trang PDF
        composeRule.onNodeWithTag("library_doc_${FakeLibraryRepository.DOC_DSA_BINARY_SEARCH}")
            .performClick()

        composeRule.onNodeWithTag("pdf_reader_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pdf_page_card").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_reader_page_indicator").assertTextContains("Trang 1/3")
        composeRule.onNodeWithTag("btn_reader_ask_ai").assertIsDisplayed()

        composeRule.onNodeWithTag("btn_reader_next_page").performClick()
        composeRule.onNodeWithTag("txt_reader_page_indicator").assertTextContains("Trang 2/3")

        composeRule.onNodeWithTag("btn_reader_prev_page").performClick()
        composeRule.onNodeWithTag("txt_reader_page_indicator").assertTextContains("Trang 1/3")

        // Quay lại danh sách thư viện
        composeRule.onNodeWithTag("btn_reader_back").performClick()
        composeRule.onNodeWithTag("purchased_library_screen").assertIsDisplayed()
        assertEquals(null, openedDocId)
    }

    @Test
    fun libraryStates_rendersEmptyGuestAndOfflineErrorWithRetry() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_EMPTY_BUYER)
        )
        val fakeRepo = FakeLibraryRepository(simulateOffline = true)
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo
        )

        composeRule.setContent {
            PurchasedLibraryScreen(viewModel = viewModel)
        }

        // Ban đầu mất mạng -> hiện lỗi và thử lại ra trạng thái rỗng
        composeRule.onNodeWithTag("library_error_view").assertIsDisplayed()
        composeRule.runOnIdle {
            fakeRepo.simulateOffline = false
        }
        composeRule.onNodeWithText("Thử lại").performClick()
        composeRule.onNodeWithTag("library_empty_view").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_library_explore").assertIsDisplayed()

        // Đăng xuất về Khách -> hiện lời nhắc đăng nhập
        composeRule.runOnIdle {
            sessionFlow.value = AuthSession.Guest
        }
        composeRule.onNodeWithTag("library_guest_view").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_library_guest_login").assertIsDisplayed()
    }

    @Test
    fun readerSecurityStates_hidesContentOnDeniedAndRevoked_andClearsOnUidSwitch() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository()
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo
        )
        var targetDocId by mutableStateOf(FakeLibraryRepository.DOC_UNPAID_SAMPLE)

        composeRule.setContent {
            PdfReaderScreen(
                docId = targetDocId,
                viewModel = viewModel
            )
        }

        // Tài liệu chưa mua -> chặn đọc và ẩn hoàn toàn nội dung PDF
        composeRule.onNodeWithTag("reader_denied_view").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pdf_page_card").assertDoesNotExist()
        composeRule.onNodeWithTag("btn_reader_ask_ai").assertDoesNotExist()

        // Chuyển sang tài liệu hợp lệ -> mở đọc bình thường
        composeRule.runOnIdle {
            targetDocId = FakeLibraryRepository.DOC_DSA_BINARY_SEARCH
        }
        composeRule.onNodeWithTag("reader_pdf_page_card").assertIsDisplayed()

        // Đổi sang UID khác không sở hữu DOC_DSA_BINARY_SEARCH -> không còn trang cũ, bị từ chối đọc
        composeRule.runOnIdle {
            sessionFlow.value = AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_TWO)
        }
        composeRule.onNodeWithTag("reader_denied_view").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pdf_page_card").assertDoesNotExist()

        // Đổi lại UID 1 nhưng tài liệu đã bị thu hồi -> hiện thông báo thu hồi
        composeRule.runOnIdle {
            fakeRepo.revokeEntitlementForTest(
                FakeLibraryRepository.UID_BUYER_ONE,
                FakeLibraryRepository.DOC_DSA_BINARY_SEARCH
            )
            sessionFlow.value = AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        }
        composeRule.onNodeWithTag("reader_revoked_view").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pdf_page_card").assertDoesNotExist()
    }

    @Test
    fun readerOfflineError_showsRetry_andLoadsPdfWhenBackOnline() {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeLibraryRepository.UID_BUYER_ONE)
        )
        val fakeRepo = FakeLibraryRepository(simulateOffline = true)
        val viewModel = PurchasedLibraryViewModel(
            authRepository = FakeAuthRepoForLibrary(sessionFlow),
            libraryRepository = fakeRepo
        )

        composeRule.setContent {
            PdfReaderScreen(
                docId = FakeLibraryRepository.DOC_MACRO_ECON,
                viewModel = viewModel
            )
        }

        composeRule.onNodeWithTag("reader_error_view").assertIsDisplayed()
        composeRule.onNodeWithTag("reader_pdf_page_card").assertDoesNotExist()

        composeRule.runOnIdle {
            fakeRepo.simulateOffline = false
        }
        composeRule.onNodeWithText("Thử lại").performClick()

        composeRule.onNodeWithTag("reader_pdf_page_card").assertIsDisplayed()
        composeRule.onNodeWithTag("txt_reader_page_indicator").assertTextContains("Trang 1/3")
    }
}
