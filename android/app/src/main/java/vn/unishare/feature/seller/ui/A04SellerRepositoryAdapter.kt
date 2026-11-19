package vn.unishare.feature.seller.ui

import android.net.Uri
import kotlinx.coroutines.CancellationException
import vn.unishare.domain.repository.DocumentDraft
import vn.unishare.domain.repository.DocumentUploadRepository as A04Repository
import vn.unishare.domain.repository.PreviewUpload
import vn.unishare.domain.repository.UploadedFile

/**
 * Bridges C04 Seller UI to the real A04 repository.
 *
 * The metadataProvider is evaluated when the user
 * submits the Wizard, not when the adapter is created.
 *
 * Seller listing and metadata editing are not yet
 * supported by the A04 backend.
 */
class A04SellerRepositoryAdapter(
    private val a04: A04Repository,
    private val metadataProvider: () -> UploadWizardUiState
) : DocumentUploadRepository {

    private val drafts = mutableMapOf<String, DocumentDraft>()

    private val publishedDocuments =
        mutableMapOf<String, SellerDocumentItem>()

    private suspend fun <T> attempt(
        operation: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(operation())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun createOrReuseDraft(
        idempotencyKey: String
    ): Result<DraftSession> = attempt {

        val metadata = A04MetadataMapper.toDraftMetadata(
            metadataProvider()
        )

        val draft = a04.createDraft(
            metadata = metadata,
            idempotencyKey = idempotencyKey
        )

        drafts[draft.docId] = draft

        DraftSession(
            draftId = draft.docId,
            versionId = draft.versionId,
            stagingPrefix = draft.stagingPrefix
        )
    }

    override suspend fun uploadStagingFiles(
        draft: DraftSession,
        previews: List<SelectedPreviewImage>,
        pdf: SelectedPdfFile,
        onProgress: (percent: Int, stageLabel: String) -> Unit
    ): Result<
            Pair<
                    List<UploadedStagingArtifact>,
                    UploadedStagingArtifact
                    >
            > = attempt {

        require(previews.size in 1..MAX_PREVIEW_IMAGES) {
            "Cần từ 1 đến 5 ảnh preview."
        }

        require(
            previews.all {
                it.mimeType.lowercase() in
                        ALLOWED_PREVIEW_IMAGE_MIMES &&
                        it.sizeBytes in 1..MAX_PREVIEW_IMAGE_BYTES
            }
        ) {
            "Ảnh preview phải là JPG/PNG, tối đa 5 MiB."
        }

        require(
            pdf.mimeType.lowercase() == ALLOWED_PDF_MIME &&
                    pdf.sizeBytes in 1..MAX_PDF_BYTES
        ) {
            "PDF không hợp lệ hoặc vượt quá 25 MiB."
        }

        val realDraft = drafts[draft.draftId]
            ?: throw IllegalStateException(
                "Không tìm thấy Draft A04 trong phiên làm việc."
            )

        val totalFiles = previews.size + 1
        val uploadedPreviews =
            mutableListOf<UploadedStagingArtifact>()

        previews.forEachIndexed { index, image ->
            val slot = index + 1
            val extension =
                if (image.mimeType.lowercase() == "image/png") {
                    "png"
                } else {
                    "jpg"
                }

            val uploaded = a04.uploadPreview(
                draft = realDraft,
                slot = slot,
                extension = extension,
                uri = Uri.parse(image.uriString),
                onProgress = { progress ->
                    val combined =
                        ((index * 100) + progress.percent) /
                                totalFiles

                    onProgress(
                        combined,
                        "Đang tải ảnh $slot/${previews.size}"
                    )
                }
            )

            uploadedPreviews.add(
                UploadedStagingArtifact(
                    objectPath = uploaded.path,
                    generation = uploaded.generation
                )
            )
        }

        val uploadedPdf = a04.uploadPdf(
            draft = realDraft,
            uri = Uri.parse(pdf.uriString),
            onProgress = { progress ->
                val combined =
                    ((previews.size * 100) +
                            progress.percent) / totalFiles

                onProgress(
                    combined,
                    "Đang tải PDF..."
                )
            }
        )

        onProgress(
            100,
            "Đã tải đủ tệp lên vùng tạm."
        )

        uploadedPreviews to UploadedStagingArtifact(
            objectPath = uploadedPdf.path,
            generation = uploadedPdf.generation
        )
    }

    override suspend fun publishDocument(
        request: PublishDocumentRequest
    ): Result<SellerDocumentItem> = attempt {

        require(request.ownershipConfirmed) {
            "Chưa xác nhận quyền đăng bán."
        }

        val draft = drafts[request.draftId]
            ?: throw IllegalStateException(
                "Không tìm thấy Draft A04."
            )

        require(request.versionId == draft.versionId) {
            "Phiên bản Draft không khớp."
        }

        val previews = request.previewArtifacts.mapIndexed {
                index, artifact ->

            val extension = artifact.objectPath
                .substringAfterLast('.')
                .lowercase()

            require(
                extension in setOf("jpg", "jpeg", "png")
            ) {
                "Định dạng ảnh preview không hợp lệ."
            }

            val expectedPath =
                "${draft.stagingPrefix}previews/${index + 1}.$extension"

            require(artifact.objectPath == expectedPath) {
                "Đường dẫn ảnh preview không khớp Draft."
            }

            PreviewUpload(
                slot = index + 1,
                extension = extension,
                generation = artifact.generation
            )
        }

        val pdf = UploadedFile(
            path = request.pdfArtifact.objectPath,
            generation = request.pdfArtifact.generation
        )

        // This suspending call only returns after the
        // A04 backend confirms publication.
        a04.publish(
            draft = draft,
            pdf = pdf,
            previews = previews
        )

        val item = SellerDocumentItem(
            docId = draft.docId,
            version = 1,
            title = request.title,
            category = request.category,
            subject = request.subject,
            school = request.school,
            priceVnd = request.priceVnd,
            description = request.description,
            viewCount = 0,
            reviewCount = 0,
            publishedDateLabel = "Vừa đăng",
            status = SellerDocumentStatus.PUBLISHED
        )

        publishedDocuments[draft.docId] = item
        item
    }

    override suspend fun fetchSellerDocuments():
            Result<List<SellerDocumentItem>> {

        return Result.failure(
            UnsupportedOperationException(
                "A04 chưa có API tải danh sách tài liệu Seller."
            )
        )
    }

    override suspend fun updateDocumentMetadata(
        request: UpdateDocumentMetadataRequest
    ): Result<SellerDocumentItem> {

        return Result.failure(
            UnsupportedOperationException(
                "A04 chưa hỗ trợ sửa metadata sau Publish."
            )
        )
    }

    override suspend fun archiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem> = attempt {

        require(expectedVersion == 1) {
            "Phiên bản tài liệu không khớp."
        }

        val draft = drafts[docId]
            ?: throw IllegalStateException(
                "Không có phiên Draft để Archive."
            )

        val existing = publishedDocuments[docId]
            ?: throw IllegalStateException(
                "Tài liệu chưa được Publish trong phiên này."
            )

        a04.archive(draft)

        val archived = existing.copy(
            status = SellerDocumentStatus.ARCHIVED
        )

        publishedDocuments[docId] = archived
        archived
    }

    override suspend fun unarchiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem> {

        return Result.failure(
            UnsupportedOperationException(
                "A04 chưa hỗ trợ bỏ lưu trữ tài liệu."
            )
        )
    }
}