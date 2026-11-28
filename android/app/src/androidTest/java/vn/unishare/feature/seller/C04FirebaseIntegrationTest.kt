package vn.unishare.feature.seller

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.data.repository.DocumentUploadRepositoryImpl
import vn.unishare.feature.seller.ui.A04SellerRepositoryAdapter
import vn.unishare.feature.seller.ui.SelectedPdfFile
import vn.unishare.feature.seller.ui.SelectedPreviewImage
import vn.unishare.feature.seller.ui.SellerDocumentStatus
import vn.unishare.feature.seller.ui.UploadPublishState
import vn.unishare.feature.seller.ui.UploadWizardScreen
import vn.unishare.feature.seller.ui.UploadWizardViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class C04FirebaseIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun wizardPublishesAndArchivesUsingRealA04() {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1234567890:android:c04integration")
            .setApiKey("demo-key")
            .setProjectId("demo-unishare")
            .setStorageBucket("demo-unishare.firebasestorage.app")
            .build()

        val app = FirebaseApp.initializeApp(
            context,
            options,
            "c04-integration-${UUID.randomUUID()}"
        ) ?: error("Cannot initialize test Firebase app")

        val auth = FirebaseAuth.getInstance(app)
        val firestore = FirebaseFirestore.getInstance(app)
        val storage = FirebaseStorage.getInstance(app)

        auth.useEmulator("127.0.0.1", 9099)
        firestore.useEmulator("127.0.0.1", 8080)
        storage.useEmulator("127.0.0.1", 9199)

        runBlocking {
            auth.signInWithEmailAndPassword(
                "a04-seller-smoke@example.test",
                "EmulatorOnly_12345!"
            ).await()
        }

        assertTrue(auth.currentUser != null)

        val a04 = DocumentUploadRepositoryImpl(
            context = context,
            auth = auth,
            storage = storage,
            apiBaseUrl = "http://127.0.0.1:3000"
        )

        val pdfFile = createPdf(context.cacheDir)
        val pngFile = createPng(context.cacheDir)

        lateinit var viewModel: UploadWizardViewModel

        val adapter = A04SellerRepositoryAdapter(
            a04 = a04,
            metadataProvider = { viewModel.wizardState.value },
            auth = auth,
            apiBaseUrl = "http://127.0.0.1:3000"
        )

        viewModel = UploadWizardViewModel(
            repository = adapter,
            autoLoadDocuments = false
        )

        composeRule.setContent {
            UniShareTheme(mode = UniAppMode.SELLER) {
                UploadWizardScreen(
                    viewModel = viewModel,
                    onClose = {},
                    onPickPreviewImageOverride = {
                        viewModel.addPreviewImage(
                            SelectedPreviewImage(
                                id = "preview-1",
                                uriString = Uri.fromFile(pngFile).toString(),
                                displayName = pngFile.name,
                                mimeType = "image/png",
                                sizeBytes = pngFile.length()
                            )
                        )
                    },
                    onPickPdfOverride = {
                        viewModel.selectPdfFile(
                            SelectedPdfFile(
                                uriString = Uri.fromFile(pdfFile).toString(),
                                displayName = pdfFile.name,
                                mimeType = "application/pdf",
                                sizeBytes = pdfFile.length()
                            )
                        )
                    }
                )
            }
        }

        composeRule.onNodeWithTag("btn_pick_gallery_image")
            .performScrollTo().performClick()

        composeRule.onNodeWithTag("btn_step1_next")
            .performScrollTo().performClick()

        composeRule.onNodeWithTag("btn_pick_pdf")
            .performScrollTo().performClick()

        composeRule.onNodeWithTag("btn_step2_next")
            .performScrollTo().performClick()

        val title = "C04 Real Firebase ${UUID.randomUUID()}"

        composeRule.onNodeWithTag("input_seller_title")
            .performScrollTo().performTextReplacement(title)

        composeRule.onNodeWithTag("dropdown_seller_category")
            .performScrollTo().performClick()

        composeRule.onNodeWithText("Note tóm tắt")
            .performClick()

        composeRule.onNodeWithTag("dropdown_seller_subject")
            .performScrollTo().performClick()

        composeRule.onNodeWithText("Lập trình")
            .performClick()

        composeRule.onNodeWithTag("dropdown_seller_school")
            .performScrollTo().performClick()

        composeRule.onNodeWithText("ĐH Bách Khoa TP.HCM")
            .performClick()

        composeRule.onNodeWithTag("input_seller_price")
            .performScrollTo().performTextReplacement("20000")

        composeRule.onNodeWithTag("checkbox_ownership_consent")
            .performScrollTo().performClick()

        composeRule.onNodeWithTag("btn_publish_document")
            .performScrollTo().performClick()

        composeRule.waitUntil(90_000L) {
            when (viewModel.wizardState.value.uploadPublishState) {
                is UploadPublishState.Published -> true
                is UploadPublishState.Failed -> true
                else -> false
            }
        }

        val state = viewModel.wizardState.value.uploadPublishState

        if (state is UploadPublishState.Failed) {
            fail("Firebase publish failed: ${state.message}")
        }

        assertTrue(state is UploadPublishState.Published)

        val doc = (state as UploadPublishState.Published).document
        val catalogRef = firestore.collection("catalog").document(doc.docId)

        val catalog = runBlocking { catalogRef.get().await() }

        assertTrue(catalog.exists())
        assertEquals(title, catalog.getString("title"))
        assertEquals("programming", catalog.getString("subjectId"))
        assertEquals("note", catalog.getString("categoryId"))
        assertEquals(20000L, catalog.getLong("priceVnd"))

        val archived = runBlocking {
            adapter.archiveDocument(doc.docId, 1).getOrThrow()
        }

        assertEquals(SellerDocumentStatus.ARCHIVED, archived.status)

        val afterArchive = runBlocking {
            catalogRef.get().await()
        }

        assertFalse(afterArchive.exists())
    }

    private fun createPdf(directory: File): File {
        val file = File(directory, "c04-${UUID.randomUUID()}.pdf")
        val pdf = PdfDocument()
        val page = pdf.startPage(
            PdfDocument.PageInfo.Builder(400, 600, 1).create()
        )
        page.canvas.drawColor(Color.WHITE)
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun createPng(directory: File): File {
        val file = File(directory, "c04-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(
            100, 100, Bitmap.Config.ARGB_8888
        )
        bitmap.eraseColor(Color.GREEN)
        FileOutputStream(file).use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        bitmap.recycle()
        return file
    }
}
