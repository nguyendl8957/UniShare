package vn.unishare.domain.repository

import android.net.Uri

data class DraftMetadata(
    val title: String,
    val subject: String,
    val category: String,
    val description: String = "",
    val priceVnd: Long
)

data class DocumentDraft(
    val docId: String,
    val versionId: String,
    val stagingPrefix: String
)

data class UploadedFile(
    val path: String,
    val generation: String
)

data class PreviewUpload(
    val slot: Int,
    val extension: String,
    val generation: String
)

data class UploadProgress(
    val bytesTransferred: Long,
    val totalBytes: Long
) {
    val percent: Int
        get() = if (totalBytes > 0L) {
            (bytesTransferred * 100L / totalBytes)
                .toInt()
                .coerceIn(0, 100)
        } else {
            0
        }
}

interface DocumentUploadRepository {

    suspend fun createDraft(
        metadata: DraftMetadata,
        idempotencyKey: String
    ): DocumentDraft

    suspend fun uploadPdf(
        draft: DocumentDraft,
        uri: Uri,
        onProgress: (UploadProgress) -> Unit = {}
    ): UploadedFile

    suspend fun uploadPreview(
        draft: DocumentDraft,
        slot: Int,
        extension: String,
        uri: Uri,
        onProgress: (UploadProgress) -> Unit = {}
    ): UploadedFile

    suspend fun publish(
        draft: DocumentDraft,
        pdf: UploadedFile,
        previews: List<PreviewUpload>
    )

    suspend fun archive(draft: DocumentDraft)
}