package vn.unishare.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import vn.unishare.domain.repository.DraftMetadata
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentUploadCancelTest {

    @Test
    fun cancellingUploadMustCancelCoroutineAndNotPublish() =
        runBlocking {

            val context =
                InstrumentationRegistry
                    .getInstrumentation()
                    .targetContext

            // Separate Firebase project for Emulator testing.
            val options = FirebaseOptions.Builder()
                .setApplicationId(
                    "1:1234567890:android:a04cancel"
                )
                .setApiKey("demo-key")
                .setProjectId("demo-unishare")
                .setStorageBucket(
                    "demo-unishare.firebasestorage.app"
                )
                .build()

            val appName = "a04-cancel-${UUID.randomUUID()}"

            val firebaseApp = FirebaseApp.initializeApp(
                context,
                options,
                appName
            )

            requireNotNull(firebaseApp)

            val auth = FirebaseAuth.getInstance(firebaseApp)
            val storage = FirebaseStorage.getInstance(firebaseApp)
            val firestore =
                FirebaseFirestore.getInstance(firebaseApp)

            // Uses adb reverse to reach local Emulators.
            auth.useEmulator("127.0.0.1", 9099)
            storage.useEmulator("127.0.0.1", 9199)
            firestore.useEmulator("127.0.0.1", 8080)

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
                    title = "A04 Cancel ${UUID.randomUUID()}",
                    subject = "programming",
                    category = "note",
                    priceVnd = 20000
                ),
                idempotencyKey = UUID
                    .randomUUID()
                    .toString()
            )

            assertEquals("v1", draft.versionId)

            // Large fixture to observe upload in progress.
            // This is not a valid publishable PDF.
            val pdfFile = createLargePdfFixture(
                directory = context.cacheDir
            )

            val cancellationRequested =
                CompletableDeferred<Unit>()

            val uploadJob = launch(Dispatchers.Default) {
                repository.uploadPdf(
                    draft = draft,
                    uri = Uri.fromFile(pdfFile),
                    onProgress = { progress ->

                        if (
                            progress.totalBytes > 0 &&
                            progress.bytesTransferred <
                                progress.totalBytes
                        ) {
                            cancellationRequested.complete(Unit)

                            cancel(
                                CancellationException(
                                    "A04: Seller cancelled upload"
                                )
                            )
                        }
                    }
                )
            }

            // Ensure that a progress event triggered cancellation.
            withTimeout(30_000L) {
                cancellationRequested.await()
            }

            uploadJob.join()

            assertTrue(
                "Upload coroutine must be cancelled",
                uploadJob.isCancelled
            )

            // A04 security rule:
            // The Android client must NOT read
            // documents/{docId} directly.
            //
            // We only inspect the public catalog projection,
            // which the Firestore Rules allow everyone to get.
            val catalog = firestore
                .collection("catalog")
                .document(draft.docId)
                .get()
                .await()

            // The repository never called Publish.
            // Therefore the document must not be public.
            assertFalse(
                "Cancelled upload must not appear in catalog",
                catalog.exists()
            )
        }

    private fun createLargePdfFixture(
        directory: File
    ): File {
        val file = File(
            directory,
            "a04-cancel-${UUID.randomUUID()}.pdf"
        )

        val targetBytes = 24 * 1024 * 1024
        val chunk = ByteArray(8192)

        FileOutputStream(file).use { output ->
            val header = "%PDF-1.4\n"
                .toByteArray(Charsets.US_ASCII)

            output.write(header)

            var written = header.size

            while (written < targetBytes) {
                val count = minOf(
                    chunk.size,
                    targetBytes - written
                )

                output.write(chunk, 0, count)
                written += count
            }
        }

        return file
    }
}