package vn.unishare.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import vn.unishare.data.remote.PDF_HEADER_PROBE_BYTES
import vn.unishare.data.remote.hasPdfHeader
import vn.unishare.data.remote.readAtMost
import vn.unishare.domain.repository.DocumentDraft
import vn.unishare.domain.repository.DocumentUploadRepository
import vn.unishare.domain.repository.DraftMetadata
import vn.unishare.domain.repository.PreviewUpload
import vn.unishare.domain.repository.UploadProgress
import vn.unishare.domain.repository.UploadedFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class InvalidSelectedPdfException(
    message: String = "Tệp PDF không hợp lệ. Vui lòng chọn lại tài liệu."
) : IllegalArgumentException(message)

internal class DocumentUploadApiException(
    val httpStatus: Int,
    val invalidPdf: Boolean
) : Exception()

class DocumentUploadRepositoryImpl(
    private val context: Context,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val apiBaseUrl: String
) : DocumentUploadRepository {

    companion object {
        private const val MAX_PDF_BYTES = 25L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    }

    init {
        require(
            apiBaseUrl.startsWith("https://") ||
                apiBaseUrl.startsWith("http://10.0.2.2:") ||
                apiBaseUrl.startsWith("http://127.0.0.1:")
        ) {
            "HTTP is allowed only for local Emulator testing"
        }
    }

    override suspend fun createDraft(
        metadata: DraftMetadata,
        idempotencyKey: String
    ): DocumentDraft {
        require(idempotencyKey.isNotBlank())

        val body = JSONObject()
            .put("title", metadata.title)
            .put("subject", metadata.subject)
            .put("category", metadata.category)
            .put("description", metadata.description)
            .put("priceVnd", metadata.priceVnd)

        val result = apiRequest(
            method = "POST",
            path = "/v1/documents/drafts",
            body = body,
            idempotencyKey = idempotencyKey
        )

        return DocumentDraft(
            docId = result.getString("docId"),
            versionId = result.getString("versionId"),
            stagingPrefix = result.getString("stagingPrefix")
        )
    }

    override suspend fun uploadPdf(
        draft: DocumentDraft,
        uri: Uri,
        onProgress: (UploadProgress) -> Unit
    ): UploadedFile = uploadFile(
        path = "${draft.stagingPrefix}pdf/source.pdf",
        uri = uri,
        contentType = "application/pdf",
        maxBytes = MAX_PDF_BYTES,
        onProgress = onProgress
    )

    override suspend fun uploadPreview(
        draft: DocumentDraft,
        slot: Int,
        extension: String,
        uri: Uri,
        onProgress: (UploadProgress) -> Unit
    ): UploadedFile {
        require(slot in 1..5)
        require(extension in setOf("png", "jpg", "jpeg"))

        return uploadFile(
            path = "${draft.stagingPrefix}previews/$slot.$extension",
            uri = uri,
            contentType = if (extension == "png") {
                "image/png"
            } else {
                "image/jpeg"
            },
            maxBytes = MAX_IMAGE_BYTES,
            onProgress = onProgress
        )
    }

    private suspend fun uploadFile(
        path: String,
        uri: Uri,
        contentType: String,
        maxBytes: Long,
        onProgress: (UploadProgress) -> Unit
    ): UploadedFile {
        val user = auth.currentUser
            ?: throw IllegalStateException("Sign in required")

        require(path.startsWith("staging/${user.uid}/")) {
            "Staging path does not belong to current user"
        }

        val localHash = hashLocalFile(uri, maxBytes, contentType == "application/pdf")
        val reference = storage.reference.child(path)

        val existing = readMetadataIfExists(reference)

        if (existing != null) {
            return recoverExisting(
                reference = reference,
                expectedHash = localHash,
                expectedContentType = contentType,
                maxBytes = maxBytes
            )
        }

        val metadata = StorageMetadata.Builder()
            .setContentType(contentType)
            .build()

        val task = reference.putFile(uri, metadata)

        try {
            awaitUpload(task, onProgress)
        } catch (error: CancellationException) {
            // User cancellation must remain cancellation.
            // Do not attempt recovery in a cancelled coroutine.
            throw error
        } catch (error: Exception) {
            // A completed upload might lose its response.
            // Recover only when the stored bytes match.
            val recovered = try {
                recoverExisting(
                    reference = reference,
                    expectedHash = localHash,
                    expectedContentType = contentType,
                    maxBytes = maxBytes
                )
            } catch (_: Exception) {
                null
            }

            if (recovered != null) {
                return recovered
            }

            throw error
        }

        return recoverExisting(
            reference = reference,
            expectedHash = localHash,
            expectedContentType = contentType,
            maxBytes = maxBytes
        )
    }

    private suspend fun readMetadataIfExists(
        reference: StorageReference
    ): StorageMetadata? {
        return try {
            reference.metadata.await()
        } catch (error: StorageException) {
            if (
                error.errorCode ==
                StorageException.ERROR_OBJECT_NOT_FOUND
            ) {
                null
            } else {
                throw error
            }
        }
    }

    private suspend fun recoverExisting(
        reference: StorageReference,
        expectedHash: String,
        expectedContentType: String,
        maxBytes: Long
    ): UploadedFile {
        val before = reference.metadata.await()

        require(before.contentType == expectedContentType) {
            "Existing file MIME differs"
        }

        val generation = before.generation
            ?: throw IllegalStateException(
                "Existing file generation missing"
            )

        val size = before.sizeBytes

        require(size in 1..maxBytes) {
            "Existing file size invalid"
        }

        val bytes = reference.getBytes(maxBytes).await()

        require(sha256(bytes) == expectedHash) {
            "Existing file has different content"
        }

        val after = reference.metadata.await()

        require(after.generation == generation) {
            "Existing file changed during recovery"
        }

        // Firebase StorageReference.path may start with "/".
        // Our backend contract uses paths without that slash.
        val normalizedPath = reference.path.removePrefix("/")

        return UploadedFile(
            path = normalizedPath,
            generation = generation
        )
    }

    private suspend fun hashLocalFile(
        uri: Uri,
        maxBytes: Long,
        isPdf: Boolean
    ): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")

        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException(
                "Cannot open selected file"
            )

        stream.use { input ->
            val buffer = ByteArray(8192)
            var total = 0L
            if (isPdf) {
                val header = input.readAtMost(PDF_HEADER_PROBE_BYTES)
                if (!hasPdfHeader(header)) throw InvalidSelectedPdfException()
                digest.update(header)
                total = header.size.toLong()
            }

            while (true) {
                val count = input.read(buffer)
                if (count == -1) break

                total += count

                require(total <= maxBytes) {
                    "Selected file exceeds maximum size"
                }

                digest.update(buffer, 0, count)
            }

            require(total > 0L) {
                "Selected file is empty"
            }
        }

        digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
    }

    private suspend fun awaitUpload(
        task: UploadTask,
        onProgress: (UploadProgress) -> Unit
    ): Unit = suspendCancellableCoroutine { continuation ->

        continuation.invokeOnCancellation {
            task.cancel()
        }

        task.addOnProgressListener { snapshot ->
            if (continuation.isActive) {
                onProgress(
                    UploadProgress(
                        bytesTransferred = snapshot.bytesTransferred,
                        totalBytes = snapshot.totalByteCount
                    )
                )
            }
        }

        task.addOnSuccessListener {
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }

        task.addOnFailureListener { error ->
            if (continuation.isActive) {
                continuation.resumeWithException(error)
            }
        }

        task.addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel()
            }
        }
    }

    override suspend fun publish(
        draft: DocumentDraft,
        pdf: UploadedFile,
        previews: List<PreviewUpload>
    ) {
        require(previews.isNotEmpty() && previews.size <= 5) {
            "Publish requires 1 to 5 previews"
        }

        require(
            pdf.path.removePrefix("/") ==
                "${draft.stagingPrefix}pdf/source.pdf"
        ) {
            "PDF does not match the draft staging path"
        }

        val previewItems = JSONArray()

        for (preview in previews) {
            require(preview.slot in 1..5)
            require(
                preview.extension in setOf("png", "jpg", "jpeg")
            )

            previewItems.put(
                JSONObject()
                    .put("slot", preview.slot)
                    .put("extension", preview.extension)
                    .put("generation", preview.generation)
            )
        }

        val body = JSONObject()
            .put("versionId", draft.versionId)
            .put("pdfGeneration", pdf.generation)
            .put("previews", previewItems)

        apiRequest(
            method = "POST",
            path = "/v1/documents/${draft.docId}/publish",
            body = body
        )
    }

    override suspend fun archive(draft: DocumentDraft) {
        val body = JSONObject()
            .put("action", "archive")
            .put("expectedVersionId", draft.versionId)

        apiRequest(
            method = "PATCH",
            path = "/v1/documents/${draft.docId}",
            body = body
        )
    }

    private suspend fun apiRequest(
        method: String,
        path: String,
        body: JSONObject,
        idempotencyKey: String? = null
    ): JSONObject {
        val user = auth.currentUser
            ?: throw IllegalStateException("Sign in required")

        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException(
                "Missing Firebase ID token"
            )

        return withContext(Dispatchers.IO) {
            val connection = URL(
                apiBaseUrl.trimEnd('/') + path
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                if (idempotencyKey != null) {
                    connection.setRequestProperty(
                        "Idempotency-Key",
                        idempotencyKey
                    )
                }

                connection.outputStream.use {
                    it.write(
                        body.toString().toByteArray(Charsets.UTF_8)
                    )
                }

                val status = connection.responseCode

                val responseText = (
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                )?.use { String(it.readAtMost(16 * 1024), Charsets.UTF_8) }.orEmpty()

                if (status !in 200..299) {
                    val error = runCatching { JSONObject(responseText).optJSONObject("error") }.getOrNull()
                    val requestId = error?.optString("requestId")
                        ?.takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) }
                    Log.w("UniShareUpload", "HTTP $status, requestId=${requestId ?: "unavailable"}")
                    val invalidPdf = error?.optString("code") == "INVALID_PDF" ||
                        (status == 409 && error?.optString("message") in setOf("Invalid PDF header", "Invalid PDF structure"))
                    throw DocumentUploadApiException(status, invalidPdf)
                }

                JSONObject(responseText)
            } finally {
                connection.disconnect()
            }
        }
    }
}