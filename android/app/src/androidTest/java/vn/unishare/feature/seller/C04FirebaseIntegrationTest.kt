package vn.unishare.feature.seller

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.saveRegressionScreenshot
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.core.ui.theme.UniShareTheme
import vn.unishare.data.repository.DocumentUploadRepositoryImpl
import vn.unishare.feature.explore.ui.CatalogQuery
import vn.unishare.feature.explore.ui.FirestoreExploreCatalogRepository
import vn.unishare.feature.seller.ui.A04SellerRepositoryAdapter
import vn.unishare.feature.seller.ui.SelectedPreviewImage
import vn.unishare.feature.seller.ui.SellerDocumentStatus
import vn.unishare.feature.seller.ui.SellerDocumentsListState
import vn.unishare.feature.seller.ui.UploadPublishState
import vn.unishare.feature.seller.ui.UploadWizardScreen
import vn.unishare.feature.seller.ui.UploadWizardViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.Random
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class C04FirebaseIntegrationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun wizardPublishesAndArchivesUsingRealA04() = publishFromPicker()

    @Test
    fun wizardPublishes32KiBPdfWithPrefixAndGenericMetadata() = publishFromPicker(32 * 1024)

    @Test
    fun wizardPublishes24MiBScan() = publishFromPicker(24 * 1024 * 1024)

    @Test
    fun wizardPublishesScanNear25MiBLimit() = publishFromPicker(25 * 1024 * 1024)

    @Test
    fun legacyCatalogNamesAreSharedBySellerAndRefreshAfterRename() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:c04catalog")
                .setApiKey("demo-key")
                .setProjectId("demo-unishare")
                .build(),
            "c04-catalog-${UUID.randomUUID()}"
        )
        val auth = FirebaseAuth.getInstance(app)
        val db = FirebaseFirestore.getInstance(app)
        auth.useEmulator("127.0.0.1", 9099)
        db.useEmulator("127.0.0.1", 8080)
        val repo = FirestoreExploreCatalogRepository("http://127.0.0.1:3000", db)
        val query = CatalogQuery(normalizedPrefix = "c04 legacy")

        suspend fun names() = repo.queryCatalog(query).getOrThrow().items
            .associate { it.docId to it.seller.displayName }

        try {
            assertNull(auth.currentUser)
            assertEquals(mapOf(
                "c04_legacy_first" to "A04 Smoke Seller",
                "c04_legacy_second" to "A04 Smoke Seller",
                "c04_legacy_other" to "Lê Bảo Ngọc"
            ), names())
            val fallback = FirestoreExploreCatalogRepository("http://127.0.0.1:1", db)
            assertTrue(fallback.queryCatalog(query).getOrThrow().items.all { it.seller.displayName == "Người bán" })

            val user = auth.signInWithEmailAndPassword(
                "a04-seller-smoke@example.test", "EmulatorOnly_12345!"
            ).await().user ?: error("Missing test Seller")
            val profile = db.collection("users").document(user.uid)
            profile.update("displayName", "Nguyễn Ánh Dương").await()
            try {
                assertEquals("A04 Smoke Seller", repo.getDocumentDetail("c04_legacy_second").getOrThrow()?.seller?.displayName)
                repo.invalidate()
                val updated = names()
                assertEquals("Nguyễn Ánh Dương", updated["c04_legacy_first"])
                assertEquals("Nguyễn Ánh Dương", updated["c04_legacy_second"])
                assertEquals("Lê Bảo Ngọc", updated["c04_legacy_other"])
            } finally {
                profile.update("displayName", "A04 Smoke Seller").await()
            }
        } finally {
            auth.signOut()
        }
    }

    private fun publishFromPicker(targetBytes: Int? = null) {
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

        val genericMetadata = targetBytes == 32 * 1024
        val pdfFile = if (targetBytes == null) createPdf(context.cacheDir)
            else createScanPdf(context.cacheDir, targetBytes, genericMetadata)
        assertTrue(pdfFile.length() in 1..25L * 1024 * 1024)
        if (targetBytes != null) assertTrue(pdfFile.length() >= targetBytes - 8 * 1024)
        assertPdfRenders(pdfFile)
        val resolver = context.contentResolver
        val downloadUri = requireNotNull(resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, pdfFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (genericMetadata) "application/octet-stream" else "application/pdf")
            }
        ))
        requireNotNull(resolver.openOutputStream(downloadUri)).use { output ->
            pdfFile.inputStream().use { it.copyTo(output) }
        }
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

        composeRule.activityRule.scenario.onActivity {
            it.enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(0, 0))
        }
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

        try {
            if (!clickPickerItem(pdfFile.name, timeoutMs = 3_000)) {
                assertTrue("DocumentsUI roots", clickPickerItem("Show roots"))
                assertTrue("DocumentsUI Downloads", clickPickerItem("Downloads"))
                assertTrue("DocumentsUI PDF", clickPickerItem(pdfFile.name))
            }
            composeRule.waitUntil(15_000) {
                val wizard = viewModel.wizardState.value
                wizard.selectedPdf != null || wizard.fieldErrors.pdfError != null
            }
            val wizard = viewModel.wizardState.value
            val selected = requireNotNull(wizard.selectedPdf) { "PDF picker error: ${wizard.fieldErrors.pdfError}" }
            assertTrue(selected.uriString.startsWith("content://"))
            assertEquals(pdfFile.length(), selected.sizeBytes)
            composeRule.onNodeWithText("(PDF, tối đa 25 MiB)").performScrollTo().assertIsDisplayed()
            saveRegressionScreenshot("seller-upload-pdf")

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

            val publishStarted = SystemClock.elapsedRealtime()
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
            println("C04 published bytes=${pdfFile.length()} durationMs=${SystemClock.elapsedRealtime() - publishStarted}")

            val doc = (state as UploadPublishState.Published).document
            val catalogRef = firestore.collection("catalog").document(doc.docId)

            val catalog = runBlocking { catalogRef.get().await() }

            assertTrue(catalog.exists())
            assertEquals(title, catalog.getString("title"))
            assertEquals("programming", catalog.getString("subjectId"))
            assertEquals("note", catalog.getString("categoryId"))
            assertEquals(20000L, catalog.getLong("priceVnd"))
            val repo = FirestoreExploreCatalogRepository("http://127.0.0.1:3000", firestore)
            val detail = runBlocking { repo.getDocumentDetail(doc.docId).getOrThrow() }
            assertEquals("A04 Smoke Seller", detail?.seller?.displayName)
            composeRule.waitUntil(15_000) {
                val listing = viewModel.documentsState.value.listState as? SellerDocumentsListState.Content
                listing?.items?.any { it.docId == doc.docId && it.title == title } == true
            }

            val archived = runBlocking {
                adapter.archiveDocument(doc.docId, 1).getOrThrow()
            }

            assertEquals(SellerDocumentStatus.ARCHIVED, archived.status)

            val afterArchive = runBlocking {
                catalogRef.get().await()
            }

            assertFalse(afterArchive.exists())
        } finally {
            resolver.delete(downloadUri, null, null)
            pdfFile.delete()
            pngFile.delete()
        }
    }

    private fun clickPickerItem(label: String, timeoutMs: Long = 8_000): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            var node = automation.rootInActiveWindow?.let { findPickerItem(it, label) }
            while (node != null) {
                if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                node = node.parent
            }
            SystemClock.sleep(150)
        }
        return false
    }

    private fun findPickerItem(node: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == label || node.contentDescription?.toString() == label) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            findPickerItem(child, label)?.let { return it }
        }
        return null
    }

    private fun assertPdfRenders(file: File) {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        PdfRenderer(fd).use { renderer ->
            assertEquals(2, renderer.pageCount)
            renderer.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    private fun createScanPdf(directory: File, targetBytes: Int, genericMetadata: Boolean): File {
        val extension = if (genericMetadata) "bin" else "pdf"
        val file = File(directory, "c04-scan-${UUID.randomUUID()}.$extension")
        val width = if (targetBytes < 100_000) 96 else 1024
        val row = ByteArray(width * 3)
        val height = (targetBytes - 2048) / row.size
        val objects = mutableListOf<Int>()
        var offset = 0
        file.outputStream().buffered().use { output ->
            fun text(value: String) {
                val bytes = value.toByteArray(Charsets.US_ASCII)
                output.write(bytes)
                offset += bytes.size
            }
            fun startObject() {
                objects += offset
                text("${objects.size} 0 obj\n")
            }

            if (genericMetadata) text("% Exported PDF fixture\n")
            text("%PDF-1.7 \n")
            startObject()
            text("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")
            startObject()
            text("<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>\nendobj\n")
            repeat(2) {
                startObject()
                text("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] " +
                    "/Resources << /XObject << /Scan 5 0 R >> >> /Contents 6 0 R >>\nendobj\n")
            }
            startObject()
            text("<< /Type /XObject /Subtype /Image /Width $width /Height $height " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Length ${row.size * height} >>\nstream\n")
            val random = Random(41)
            repeat(height) {
                random.nextBytes(row)
                output.write(row)
                offset += row.size
            }
            text("\nendstream\nendobj\n")
            startObject()
            val commands = "q 360 0 0 520 20 40 cm /Scan Do Q\n"
            text("<< /Length ${commands.length} >>\nstream\n${commands}endstream\nendobj\n")

            val xref = offset
            text("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
            for (objectOffset in objects) text(String.format(Locale.ROOT, "%010d 00000 n \n", objectOffset))
            text("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        }
        return file
    }

    private fun createPdf(directory: File): File {
        val file = File(directory, "c04-${UUID.randomUUID()}.pdf")
        val pdf = PdfDocument()
        for (pageNumber in 1..2) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(400, 600, pageNumber).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("UniShare C04 page $pageNumber", 32f, 64f, Paint().apply { textSize = 18f })
            pdf.finishPage(page)
        }
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
