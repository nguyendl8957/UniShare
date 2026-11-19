package vn.unishare.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.domain.repository.DraftMetadata
import vn.unishare.domain.repository.PreviewUpload
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentUploadSmokeTest {

    @Test
    fun sellerCanUploadPublishAndArchive() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        // Separate Firebase app dedicated to local emulators.
        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1234567890:android:a04smoke")
            .setApiKey("demo-key")
            .setProjectId("demo-unishare")
            .setStorageBucket(
                "demo-unishare.firebasestorage.app"
            )
            .build()

        val firebaseApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == "a04-smoke" }
            ?: FirebaseApp.initializeApp(
                context,
                options,
                "a04-smoke"
            )

        requireNotNull(firebaseApp)

        val auth = FirebaseAuth.getInstance(firebaseApp)
        val storage = FirebaseStorage.getInstance(firebaseApp)
        val firestore = FirebaseFirestore.getInstance(firebaseApp)

        // These endpoints require the four adb reverse mappings.
        // They never access the real Firebase project.
        auth.useEmulator("127.0.0.1", 9099)
        storage.useEmulator("127.0.0.1", 9199)
        firestore.useEmulator("127.0.0.1", 8080)

        auth.signInWithEmailAndPassword(
            "a04-seller-smoke@example.test",
            "EmulatorOnly_12345!"
        ).await()

        assertTrue(auth.currentUser != null)

        val repository = DocumentUploadRepositoryImpl(
            context = context,
            auth = auth,
            storage = storage,
            apiBaseUrl = "http://127.0.0.1:3000"
        )

        val draft = repository.createDraft(
            metadata = DraftMetadata(
                title = "A04 Android Smoke ${UUID.randomUUID()}",
                subject = "programming",
                category = "note",
                priceVnd = 20000
            ),
            idempotencyKey = UUID.randomUUID().toString()
        )

        assertTrue(draft.docId.isNotBlank())
        assertEquals("v1", draft.versionId)

        val pdfFile = createPdfFile(context.cacheDir)
        val pngFile = createPngFile(context.cacheDir)

        val pdfProgress = mutableListOf<Int>()

        val pdf = repository.uploadPdf(
            draft = draft,
            uri = Uri.fromFile(pdfFile),
            onProgress = {
                pdfProgress.add(it.percent)
            }
        )

        assertTrue(pdf.generation.isNotBlank())

        // Retry the same PDF: must reuse its existing generation.
        val recoveredPdf = repository.uploadPdf(
            draft = draft,
            uri = Uri.fromFile(pdfFile)
        )

        assertEquals(pdf.generation, recoveredPdf.generation)
        assertEquals(pdf.path, recoveredPdf.path)

        val preview = repository.uploadPreview(
            draft = draft,
            slot = 1,
            extension = "png",
            uri = Uri.fromFile(pngFile)
        )

        assertTrue(preview.generation.isNotBlank())

        // Retry the same PNG.
        val recoveredPreview = repository.uploadPreview(
            draft = draft,
            slot = 1,
            extension = "png",
            uri = Uri.fromFile(pngFile)
        )

        assertEquals(
            preview.generation,
            recoveredPreview.generation
        )

        repository.publish(
            draft = draft,
            pdf = pdf,
            previews = listOf(
                PreviewUpload(
                    slot = 1,
                    extension = "png",
                    generation = preview.generation
                )
            )
        )

        val catalogRef = firestore
            .collection("catalog")
            .document(draft.docId)

        val published = catalogRef.get().await()

        assertTrue(published.exists())
        assertEquals(20000L, published.getLong("priceVnd"))

        repository.archive(draft)

        val archived = catalogRef.get().await()

        assertFalse(archived.exists())
        assertTrue(pdfProgress.isNotEmpty())
    }

    private fun createPdfFile(directory: File): File {
        val file = File(directory, "a04-smoke.pdf")
        val pdf = PdfDocument()

        val page = pdf.startPage(
            PdfDocument.PageInfo.Builder(400, 600, 1).create()
        )

        page.canvas.drawColor(Color.WHITE)
        pdf.finishPage(page)

        FileOutputStream(file).use {
            pdf.writeTo(it)
        }

        pdf.close()
        return file
    }

    private fun createPngFile(directory: File): File {
        val file = File(directory, "a04-smoke.png")

        val bitmap = Bitmap.createBitmap(
            100,
            100,
            Bitmap.Config.ARGB_8888
        )

        bitmap.eraseColor(Color.GREEN)

        FileOutputStream(file).use {
            check(
                bitmap.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    it
                )
            )
        }

        bitmap.recycle()
        return file
    }
}