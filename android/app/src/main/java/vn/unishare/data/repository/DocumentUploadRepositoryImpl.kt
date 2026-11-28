package vn.unishare.data.repository

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
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
import vn.unishare.data.remote.readAtMost
import vn.unishare.domain.repository.DocumentDraft
import vn.unishare.domain.repository.DocumentUploadRepository
import vn.unishare.domain.repository.DraftMetadata
import vn.unishare.domain.repository.PreviewUpload
import vn.unishare.domain.repository.UploadProgress
import vn.unishare.domain.repository.UploadedFile
import java.io.IOException
import java.io.InputStream
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
    val invalidPdf: Boolean,
    val backendCode: String? = null,
    val requestId: String? = null
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

        val started = SystemClock.elapsedRealtime()
        var stage = if (contentType == "application/pdf") "UPLOAD_PDF" else "UPLOAD_PREVIEW"
        var size: Long? = null
        try {
            val local = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open selected file")
                input.use { hashFile(it, maxBytes, includeMd5 = true) }
            }
            size = local.size
            val reference = storage.reference.child(path)
            if (readMetadataIfExists(reference) != null) {
                stage = "VERIFY_STAGING"
                return recoverExisting(reference, local, contentType)
            }

            val metadata = StorageMetadata.Builder().setContentType(contentType).build()
            val uploaded = try {
                awaitUpload(reference.putFile(uri, metadata), onProgress)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A completed upload may have lost its response.
                try {
                    return recoverExisting(reference, local, contentType)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    throw error
                }
            }
            stage = "VERIFY_STAGING"
            return recoverExisting(reference, local, contentType, uploaded.metadata?.generation)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logFailure(stage, started, error, size)
            throw error
        }
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
        expected: FileHash,
        expectedContentType: String,
        freshGeneration: String? = null
    ): UploadedFile {
        val before = reference.metadata.await()
        require(before.contentType == expectedContentType) { "Existing file MIME differs" }
        val generation = before.generation ?: error("Existing file generation missing")
        require(before.sizeBytes == expected.size) { "Existing file size differs" }

        require(freshGeneration == null || freshGeneration == generation) { "Uploaded generation differs" }
        val storageMd5 = before.md5Hash
        if (generation == freshGeneration && !storageMd5.isNullOrBlank()) {
            require(storageMd5 == expected.md5) { "Uploaded checksum differs" }
        } else {
            // Verify actual bytes for retries and Storage responses without a checksum.
            var remoteHash: String? = null
            val task = reference.getStream { _, input ->
                remoteHash = try {
                    input.use { hashFile(it, expected.size, includeMd5 = false).sha256 }
                } catch (_: IllegalArgumentException) {
                    throw IOException("Existing file size differs")
                }
            }
            try {
                task.await()
            } catch (error: CancellationException) {
                task.cancel()
                throw error
            }
            require(remoteHash == expected.sha256) { "Existing file has different content" }
            require(reference.metadata.await().generation == generation) { "Existing file changed during recovery" }
        }

        return UploadedFile(reference.path.removePrefix("/"), generation)
    }

    private data class FileHash(val size: Long, val sha256: String, val md5: String?)

    private fun hashFile(input: InputStream, maxBytes: Long, includeMd5: Boolean): FileHash {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val md5 = if (includeMd5) MessageDigest.getInstance("MD5") else null
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= maxBytes) { "Selected file exceeds maximum size" }
            sha256.update(buffer, 0, count)
            md5?.update(buffer, 0, count)
        }
        require(total > 0L) { "Selected file is empty" }
        return FileHash(
            total,
            sha256.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) },
            md5?.digest()?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        )
    }

    private suspend fun awaitUpload(
        task: UploadTask,
        onProgress: (UploadProgress) -> Unit
    ): UploadTask.TaskSnapshot = suspendCancellableCoroutine { continuation ->

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

        task.addOnSuccessListener { snapshot ->
            if (continuation.isActive) {
                continuation.resume(snapshot)
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

    private fun logFailure(stage: String, started: Long, error: Exception, size: Long? = null) {
        val api = error as? DocumentUploadApiException
        val storageCode = (error as? StorageException)?.errorCode
        Log.w("UniShareUpload", "stage=$stage exception=${error.javaClass.simpleName} " +
            "status=${api?.httpStatus} backendCode=${api?.backendCode} requestId=${api?.requestId} " +
            "storageCode=$storageCode sizeBytes=$size durationMs=${SystemClock.elapsedRealtime() - started}")
    }

    private suspend fun apiRequest(
        method: String,
        path: String,
        body: JSONObject,
        idempotencyKey: String? = null
    ): JSONObject {
        val started = SystemClock.elapsedRealtime()
        val stage = when {
            path.endsWith("/publish") -> "PUBLISH"
            path.endsWith("/drafts") -> "CREATE_DRAFT"
            else -> null
        }
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
                connection.readTimeout = if (stage == "PUBLISH") 65_000 else 30_000
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
                    val code = error?.optString("code")?.takeIf { it.matches(Regex("[A-Z_]{1,40}")) }
                    val invalidPdf = code == "INVALID_PDF" ||
                        (status == 409 && error?.optString("message") in setOf("Invalid PDF header", "Invalid PDF structure"))
                    throw DocumentUploadApiException(status, invalidPdf, code, requestId)
                }

                JSONObject(responseText)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (stage != null) logFailure(stage, started, error)
                throw error
            } finally {
                connection.disconnect()
            }
        }
    }
}
