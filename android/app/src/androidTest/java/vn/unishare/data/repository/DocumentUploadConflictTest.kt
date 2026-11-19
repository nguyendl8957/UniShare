package vn.unishare.data.repository

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.domain.repository.DraftMetadata
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentUploadConflictTest {

    @Test
    fun retryWithDifferentBytesMustNotOverwrite() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val options = FirebaseOptions.Builder()
            .setApplicationId("1:1234567890:android:a04conflict")
            .setApiKey("demo-key")
            .setProjectId("demo-unishare")
            .setStorageBucket(
                "demo-unishare.firebasestorage.app"
            )
            .build()

        val firebaseApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == "a04-conflict" }
            ?: FirebaseApp.initializeApp(
                context,
                options,
                "a04-conflict"
            )

        requireNotNull(firebaseApp)

        val auth = FirebaseAuth.getInstance(firebaseApp)
        val storage = FirebaseStorage.getInstance(firebaseApp)

        auth.useEmulator("127.0.0.1", 9099)
        storage.useEmulator("127.0.0.1", 9199)

        auth.signInWithEmailAndPassword(
            "a04-seller-smoke@example.test",
            "EmulatorOnly_12345!"
        ).await()

        val repository = DocumentUploadRepositoryImpl(
            context = context,
            auth = auth,
            storage = storage,
            apiBaseUrl = "http://127.0.0.1:3000"
        )

        val draft = repository.createDraft(
            metadata = DraftMetadata(
                title = "A04 Conflict ${UUID.randomUUID()}",
                subject = "programming",
                category = "note",
                priceVnd = 20000
            ),
            idempotencyKey = UUID.randomUUID().toString()
        )

        val fileA = File(context.cacheDir, "a04-file-a.pdf")
        val fileB = File(context.cacheDir, "a04-file-b.pdf")

        val pdfA = createPdfBytes(context, "Document A")
        val pdfB = createPdfBytes(context, "Document B")

        fileA.writeBytes(pdfA)
        fileB.writeBytes(pdfB)

        val uploaded = repository.uploadPdf(
            draft = draft,
            uri = Uri.fromFile(fileA)
        )

        val error = try {
            repository.uploadPdf(
                draft = draft,
                uri = Uri.fromFile(fileB)
            )
            null
        } catch (exception: IllegalArgumentException) {
            exception
        }

        // Different content must be rejected.
        org.junit.Assert.assertNotNull(error)

        // Original Storage generation must remain unchanged.
        val stored = storage.reference
            .child(uploaded.path)
            .metadata
            .await()

        assertEquals(
            uploaded.generation,
            stored.generation
        )

        // Original PDF must still be recoverable.
        val recovered = repository.uploadPdf(
            draft = draft,
            uri = Uri.fromFile(fileA)
        )

        assertEquals(
            uploaded.generation,
            recovered.generation
        )
    }

    private fun createPdfBytes(
        context: Context,
        text: String
    ): ByteArray {
        val pdf = android.graphics.pdf.PdfDocument()

        val page = pdf.startPage(
            android.graphics.pdf.PdfDocument.PageInfo
                .Builder(400, 600, 1)
                .create()
        )

        val paint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.BLACK
        }

        page.canvas.drawText(text, 40f, 100f, paint)
        pdf.finishPage(page)

        val output = java.io.ByteArrayOutputStream()
        pdf.writeTo(output)
        pdf.close()

        return output.toByteArray()
    }
}