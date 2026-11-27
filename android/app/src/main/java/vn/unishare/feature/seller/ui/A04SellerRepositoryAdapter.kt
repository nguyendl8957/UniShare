package vn.unishare.feature.seller.ui

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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
    private val metadataProvider: () -> UploadWizardUiState,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val apiBaseUrl: String =
        "https://asia-southeast1-unishare-db44a.cloudfunctions.net/api"
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
            Result<List<SellerDocumentItem>> = attempt {

        val user = auth.currentUser
            ?: throw IllegalStateException("Vui lòng đăng nhập.")

        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException(
                "Không thể xác thực tài khoản."
            )

        val result = withContext(Dispatchers.IO) {
            val connection = URL(
                "$apiBaseUrl/v1/seller/documents"
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val status = connection.responseCode

                if (status != 200) {
                    val message = when (status) {
                        401 -> "Phiên đăng nhập không hợp lệ."
                        403 -> "Tài khoản chưa có quyền Seller."
                        404 -> "API quản lý Seller chưa được triển khai."
                        503 -> "Dịch vụ Seller tạm thời không khả dụng."
                        else -> "Không thể tải tài liệu (HTTP $status)."
                    }
                    throw IllegalStateException(message)
                }

                val json = connection.inputStream.use {
                    val bytes = it.readNBytes(262145)
                    require(bytes.size <= 262144) {
                        "Phản hồi Seller quá lớn."
                    }
                    JSONObject(String(bytes, Charsets.UTF_8))
                }

                val documents = json.getJSONArray("documents")
                require(documents.length() <= 100) {
                    "Danh sách tài liệu vượt giới hạn."
                }

                val items = mutableListOf<SellerDocumentItem>()

                for (index in 0 until documents.length()) {
                    val doc = documents.getJSONObject(index)
                    val statusValue = doc.getString("status")

                    // Drafts require a separate resume-draft screen.
                    // Never display a draft as published or archived.
                    val documentStatus = when (statusValue) {
                        "PUBLISHED" -> SellerDocumentStatus.PUBLISHED
                        "ARCHIVED" -> SellerDocumentStatus.ARCHIVED
                        "DRAFT" -> null
                        else -> throw IllegalStateException(
                            "Trạng thái tài liệu không hợp lệ."
                        )
                    } ?: continue

                    val versionId = doc.getString("versionId")
                    require(versionId == "v1") {
                        "Phiên bản tài liệu chưa được hỗ trợ."
                    }

                    val docId = doc.getString("docId")
                    require(
                        docId.matches(
                            Regex("[a-zA-Z0-9_-]{1,150}")
                        )
                    ) {
                        "Mã tài liệu không hợp lệ."
                    }

                    val priceVnd = doc.getInt("priceVnd")
                    require(priceVnd >= 0) {
                        "Giá tài liệu không hợp lệ."
                    }

                    val createdAt = doc.optString("createdAt")
                        .takeIf { it.length >= 10 }
                        ?.take(10)
                        ?: "Chưa có ngày"

                    items.add(
                        SellerDocumentItem(
                            docId = docId,
                            version = 1,
                            title = doc.getString("title"),
                            category = doc.optString("category"),
                            subject = doc.optString("subject"),
                            school = "",
                            priceVnd = priceVnd,
                            description = doc.optString("description"),
                            viewCount = 0,
                            reviewCount = 0,
                            publishedDateLabel = createdAt,
                            status = documentStatus
                        )
                    )
                }

                items
            } finally {
                connection.disconnect()
            }
        }

        check(auth.currentUser?.uid == user.uid) {
            "Tài khoản đã thay đổi trong lúc tải dữ liệu."
        }

        result
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
            "Phiên bản tài liệu chưa được hỗ trợ."
        }

        require(docId.matches(Regex("[a-zA-Z0-9_-]{1,150}"))) {
            "Mã tài liệu không hợp lệ."
        }

        val user = auth.currentUser
            ?: throw IllegalStateException("Vui lòng đăng nhập.")

        // Resolve the document before mutation, so a successful archive
        // is not mistaken for a failure caused by a subsequent GET.
        val existing = fetchSellerDocuments()
            .getOrThrow()
            .firstOrNull { it.docId == docId }
            ?: throw IllegalStateException(
                "Không tìm thấy tài liệu trong danh sách Seller."
            )

        require(existing.status == SellerDocumentStatus.PUBLISHED) {
            "Chỉ tài liệu đã xuất bản mới được lưu trữ."
        }

        val token = user.getIdToken(false).await().token
            ?: throw IllegalStateException(
                "Không thể xác thực tài khoản."
            )

        val archived = withContext(Dispatchers.IO) {
            val connection = URL(
                "$apiBaseUrl/v1/documents/$docId"
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "PATCH"
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = false
                connection.doOutput = true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                val body = JSONObject()
                    .put("action", "archive")
                    .put("expectedVersionId", "v1")
                    .toString()

                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }

                when (val status = connection.responseCode) {
                    200 -> {
                        val bytes = connection.inputStream.use {
                            it.readNBytes(8193)
                        }

                        require(bytes.size <= 8192) {
                            "Phản hồi lưu trữ quá lớn."
                        }

                        val json = JSONObject(
                            String(bytes, Charsets.UTF_8)
                        )

                        check(
                            json.optString("docId") == docId &&
                            json.optString("status") == "ARCHIVED"
                        ) {
                            "Phản hồi lưu trữ không hợp lệ."
                        }

                        existing.copy(
                            status = SellerDocumentStatus.ARCHIVED
                        )
                    }

                    401 -> throw IllegalStateException(
                        "Phiên đăng nhập không hợp lệ."
                    )

                    403 -> throw IllegalStateException(
                        "Không có quyền lưu trữ tài liệu."
                    )

                    404 -> throw IllegalStateException(
                        "Không tìm thấy tài liệu."
                    )

                    409 -> throw IllegalStateException(
                        "Phiên bản đã thay đổi. Hãy làm mới danh sách."
                    )

                    else -> throw IllegalStateException(
                        "Lưu trữ thất bại (HTTP $status)."
                    )
                }
            } finally {
                connection.disconnect()
            }
        }

        check(auth.currentUser?.uid == user.uid) {
            "Phiên đăng nhập đã thay đổi."
        }

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