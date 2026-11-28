package vn.unishare.feature.seller

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import vn.unishare.saveRegressionScreenshot
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.feature.seller.ui.ALLOWED_PDF_MIME
import vn.unishare.feature.seller.ui.FakeDocumentUploadRepository
import vn.unishare.feature.seller.ui.SelectedPdfFile
import vn.unishare.feature.seller.ui.SelectedPreviewImage
import vn.unishare.feature.seller.ui.SellerDocumentsScreen
import vn.unishare.feature.seller.ui.UploadWizardScreen
import vn.unishare.feature.seller.ui.UploadWizardViewModel

class SellerUploadFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun threeStepWizard_backPreservesForm_andPublishCallsOnce() {
        val fakeRepo = FakeDocumentUploadRepository()
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoadDocuments = false
        )

        var imageCounter = 0

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                UploadWizardScreen(
                    viewModel = viewModel,
                    onClose = {},
                    onPickPreviewImageOverride = {
                        imageCounter++
                        viewModel.addPreviewImage(
                            SelectedPreviewImage(
                                id = "img_$imageCounter",
                                uriString = "content://preview/$imageCounter",
                                displayName = "preview_$imageCounter.jpg",
                                mimeType = "image/jpeg",
                                sizeBytes = 420_000L
                            )
                        )
                    },
                    onPickPdfOverride = {
                        viewModel.selectPdfFile(
                            SelectedPdfFile(
                                uriString = "content://docs/kinh_te_vi_mo.pdf",
                                displayName = "Kinh_te_vi_mo.pdf",
                                mimeType = ALLOWED_PDF_MIME,
                                sizeBytes = 3_565_158L
                            )
                        )
                    }
                )
            }
        }

        // Bước 1: Chọn ảnh preview rồi sang bước 2
        composeTestRule.onNodeWithTag("btn_pick_gallery_image").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_step1_next").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Bước 2: Chọn tệp PDF rồi sang bước 3
        composeTestRule.onNodeWithTag("btn_pick_pdf").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("selected_pdf_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_step2_next").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Bước 3: Nhập đủ thông tin bắt buộc và xác nhận quyền đăng
        composeTestRule.onNodeWithTag("input_seller_title").performScrollTo()
            .performTextReplacement("Tóm tắt Kinh tế vi mô - Chương 1 đến 5")
        composeTestRule.onNodeWithTag("dropdown_seller_category").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Note tóm tắt").performClick()

        composeTestRule.onNodeWithTag("dropdown_seller_subject").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Kinh tế vi mô").performClick()

        composeTestRule.onNodeWithTag("dropdown_seller_school").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Đại học Kinh tế Quốc dân").performClick()

        composeTestRule.onNodeWithTag("input_seller_price").performScrollTo().performTextReplacement("15000")
        composeTestRule.onNodeWithTag("checkbox_ownership_consent").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Quay lại bước 2 rồi tiến lại bước 3 -> dữ liệu đã nhập phải được giữ nguyên
        composeTestRule.onNodeWithTag("btn_step3_back").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Kinh_te_vi_mo.pdf").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_step2_next").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("Tóm tắt Kinh tế vi mô - Chương 1 đến 5", viewModel.wizardState.value.title)
        assertEquals("15000", viewModel.wizardState.value.priceText)

        // Nhấn Đăng bán -> xuất bản thành công đúng 1 lần
        composeTestRule.onNodeWithTag("btn_publish_document").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("status_published_success").performScrollTo().assertIsDisplayed()
        assertEquals(1, fakeRepo.publishCallCount)
    }

    @Test
    fun invalidMimeSelection_showsErrorAndBlocksNextStep() {
        val viewModel = UploadWizardViewModel(
            repository = FakeDocumentUploadRepository(),
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoadDocuments = false
        )

        var pickValidImage = false

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                UploadWizardScreen(
                    viewModel = viewModel,
                    onClose = {},
                    onPickPreviewImageOverride = {
                        if (!pickValidImage) {
                            viewModel.addPreviewImage(
                                SelectedPreviewImage(
                                    id = "bad_1",
                                    uriString = "content://bad/file.pdf",
                                    displayName = "sai_dinh_dang.pdf",
                                    mimeType = "application/pdf",
                                    sizeBytes = 200_000L
                                )
                            )
                        } else {
                            viewModel.addPreviewImage(
                                SelectedPreviewImage(
                                    id = "ok_1",
                                    uriString = "content://ok/img.jpg",
                                    displayName = "ok.jpg",
                                    mimeType = "image/jpeg",
                                    sizeBytes = 200_000L
                                )
                            )
                        }
                    },
                    onPickPdfOverride = {
                        viewModel.selectPdfFile(
                            SelectedPdfFile(
                                uriString = "content://bad/img.png",
                                displayName = "khong_phai_pdf.png",
                                mimeType = "image/png",
                                sizeBytes = 500_000L
                            )
                        )
                    }
                )
            }
        }

        // Chọn ảnh sai MIME ở bước 1
        composeTestRule.onNodeWithTag("btn_pick_gallery_image").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("error_step1_preview").performScrollTo().assertIsDisplayed()

        // Chọn ảnh hợp lệ để qua bước 2
        pickValidImage = true
        composeTestRule.onNodeWithTag("btn_pick_gallery_image").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("btn_step1_next").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Chọn tệp sai MIME ở bước 2 -> hiện lỗi và không cho qua bước 3
        composeTestRule.onNodeWithTag("btn_pick_pdf").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("error_step2_pdf").performScrollTo().assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_step2_next").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_pick_pdf").assertIsDisplayed()
    }

    @Test
    fun networkFailureOnPublish_showsRetryAndPreservesMetadata() {
        val fakeRepo = FakeDocumentUploadRepository(simulateNetworkError = true)
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoadDocuments = false
        )

        viewModel.addPreviewImage(
            SelectedPreviewImage("i1", "content://img/1", "1.jpg", "image/jpeg", 300_000L)
        )
        viewModel.validateStep1AndNext()
        viewModel.selectPdfFile(
            SelectedPdfFile("content://docs/a.pdf", "a.pdf", ALLOWED_PDF_MIME, 1_500_000L)
        )
        viewModel.validateStep2AndNext()
        viewModel.onTitleChange("Tài liệu ôn thi Mạng máy tính")
        viewModel.onCategoryChange("Note tóm tắt")
        viewModel.onSubjectChange("Mạng máy tính")
        viewModel.onSchoolChange("ĐH Bách Khoa TP.HCM")
        viewModel.onPriceChange("20000")
        viewModel.onOwnershipConsentChange(true)

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                UploadWizardScreen(
                    viewModel = viewModel,
                    onClose = {}
                )
            }
        }

        // Mất mạng -> hiện lỗi kèm nút Thử lại, giữ nguyên tiêu đề
        composeTestRule.onNodeWithTag("btn_publish_document").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_upload_failed").performScrollTo().assertIsDisplayed()
        assertEquals("Tài liệu ôn thi Mạng máy tính", viewModel.wizardState.value.title)

        // Có mạng lại -> bấm Thử lại để xuất bản thành công
        fakeRepo.simulateNetworkError = false
        composeTestRule.onNodeWithTag("btn_retry_publish").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("status_published_success").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun emptyManagementHasOnePublishActionAndRestoresStatusIconsOnExit() {
        val viewModel = UploadWizardViewModel(
            repository = FakeDocumentUploadRepository(initialDocuments = emptyList()),
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoadDocuments = false
        )
        var showManagement by mutableStateOf(true)
        var publishClicks = 0
        composeTestRule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(0, 0))
        }
        composeTestRule.setContent {
            if (showManagement) {
                UniShareTheme(mode = UniAppMode.SELLER) {
                    SellerDocumentsScreen(
                        viewModel = viewModel,
                        onBackClick = { showManagement = false },
                        onCreateDocumentClick = { publishClicks++ }
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("seller_docs_empty").assertIsDisplayed()
        saveRegressionScreenshot("seller-management-empty")
        composeTestRule.onNodeWithTag("fab_create_document").assertDoesNotExist()
        composeTestRule.onNodeWithText("Đăng tài liệu ngay").assertIsDisplayed().performClick()
        assertEquals(1, publishClicks)

        composeTestRule.runOnIdle {
            val window = composeTestRule.activity.window
            assertFalse(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
        }
        composeTestRule.onNodeWithTag("btn_seller_manage_back").performClick()
        composeTestRule.runOnIdle {
            val window = composeTestRule.activity.window
            assertTrue(WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars)
        }
    }

    @Test
    fun sellerDocuments_archiveRequiresConfirmation_andVersionConflictPromptsRefresh() {
        val fakeRepo = FakeDocumentUploadRepository()
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Main.immediate,
            autoLoadDocuments = true
        )

        composeTestRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                SellerDocumentsScreen(
                    viewModel = viewModel,
                    onBackClick = {},
                    onCreateDocumentClick = {}
                )
            }
        }

        // Bấm Ẩn phải hiện hộp thoại xác nhận, hủy thì không gọi archive
        composeTestRule.onNodeWithTag("btn_hide_seller_doc_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_confirm_archive").assertIsDisplayed()
        assertEquals(0, fakeRepo.archiveCallCount)

        composeTestRule.onNodeWithTag("btn_cancel_archive").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, fakeRepo.archiveCallCount)

        // Xác nhận lưu trữ -> gọi archive và hiện thông báo thành công
        composeTestRule.onNodeWithTag("btn_delete_seller_doc_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_confirm_archive").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, fakeRepo.archiveCallCount)
        composeTestRule.onNodeWithTag("banner_manage_success").assertIsDisplayed()

        // Bấm nút Bỏ ẩn để mở bán lại tài liệu
        composeTestRule.onNodeWithTag("btn_hide_seller_doc_01").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Đã bỏ ẩn và mở bán lại tài liệu.").assertIsDisplayed()

        // Xung đột version khi sửa tài liệu 2 -> hiện cảnh báo và nút Làm mới danh sách
        fakeRepo.bumpServerVersion("seller_doc_02")
        composeTestRule.onNodeWithTag("seller_documents_list")
            .performScrollToNode(hasTestTag("seller_doc_card_seller_doc_02"))
        composeTestRule.onNodeWithTag("btn_edit_seller_doc_02").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_edit_title").performTextReplacement("Tiêu đề cập nhật mới")
        composeTestRule.onNodeWithTag("btn_save_edit").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("banner_version_conflict").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_refresh_conflict").assertIsDisplayed()
    }
}
