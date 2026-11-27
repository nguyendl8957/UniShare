package vn.unishare.feature.seller.ui

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.UUID

// Match A04 Firebase Storage Rules and backend validation.
const val MAX_PREVIEW_IMAGES = 5
const val MAX_PREVIEW_IMAGE_BYTES = 5L * 1024 * 1024
const val MAX_PDF_BYTES = 25L * 1024 * 1024
const val MIN_SELLER_PRICE_VND = 5_000
const val MAX_SELLER_PRICE_VND = 500_000
const val MAX_DESCRIPTION_CHARS = 500

val ALLOWED_PREVIEW_IMAGE_MIMES = setOf(
    "image/jpeg",
    "image/png"
)
const val ALLOWED_PDF_MIME = "application/pdf"

enum class UploadWizardStep(
    val stepNumber: Int,
    val label: String
) {
    PREVIEW_IMAGES(1, "Ảnh preview"),
    UPLOAD_PDF(2, "Upload PDF"),
    METADATA(3, "Thông tin")
}

enum class SellerDocumentStatus(val label: String) {
    PUBLISHED("Đang bán"),
    ARCHIVED("Đã ẩn")
}

enum class SellerDocumentSort(val label: String) {
    NEWEST("Mới nhất"),
    MOST_VIEWED("Xem nhiều nhất"),
    PRICE_DESC("Giá cao → thấp"),
    PRICE_ASC("Giá thấp → cao")
}

data class SelectedPreviewImage(
    val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class SelectedPdfFile(
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long
)

data class DraftSession(
    val draftId: String,
    val versionId: String,
    val stagingPrefix: String
)

data class UploadedStagingArtifact(
    val objectPath: String,
    val generation: String
)

data class PublishDocumentRequest(
    val draftId: String,
    val versionId: String,
    val title: String,
    val category: String,
    val subject: String,
    val school: String,
    val priceVnd: Int,
    val description: String,
    val previewArtifacts: List<UploadedStagingArtifact>,
    val pdfArtifact: UploadedStagingArtifact,
    val ownershipConfirmed: Boolean
)

data class UpdateDocumentMetadataRequest(
    val docId: String,
    val expectedVersion: Int,
    val title: String,
    val category: String,
    val subject: String,
    val school: String,
    val priceVnd: Int,
    val description: String,
    val expectedMetadata: SellerDocumentItem? = null
)

data class SellerDocumentItem(
    val docId: String,
    val version: Int,
    val title: String,
    val category: String,
    val subject: String,
    val school: String,
    val priceVnd: Int,
    val description: String,
    val viewCount: Int,
    val reviewCount: Int,
    val publishedDateLabel: String,
    val status: SellerDocumentStatus,
    val coverUri: String? = null
)

data class UploadFieldErrors(
    val previewError: String? = null,
    val pdfError: String? = null,
    val titleError: String? = null,
    val categoryError: String? = null,
    val subjectError: String? = null,
    val schoolError: String? = null,
    val priceError: String? = null,
    val descriptionError: String? = null,
    val consentError: String? = null
)

sealed interface UploadPublishState {
    data object Idle : UploadPublishState

    data class CreatingDraft(
        val idempotencyKey: String
    ) : UploadPublishState

    data class Uploading(
        val draftId: String,
        val progressPercent: Int,
        val stageLabel: String
    ) : UploadPublishState

    data class Publishing(
        val draftId: String
    ) : UploadPublishState

    data class Published(
        val document: SellerDocumentItem
    ) : UploadPublishState

    data class Canceled(
        val draftId: String?,
        val message: String =
            "Đã hủy quá trình tải lên. Dữ liệu bạn đã nhập vẫn được giữ lại."
    ) : UploadPublishState

    data class Failed(
        val draftId: String?,
        val message: String,
        val canRetry: Boolean = true
    ) : UploadPublishState
}

data class UploadWizardUiState(
    val currentStep: UploadWizardStep =
        UploadWizardStep.PREVIEW_IMAGES,
    val draftId: String? = null,
    val draftVersionId: String? = null,
    val idempotencyKey: String? = null,
    val previewImages: List<SelectedPreviewImage> = emptyList(),
    val selectedPreviewIndex: Int = 0,
    val selectedPdf: SelectedPdfFile? = null,
    val title: String = "",
    val category: String = "",
    val subject: String = "",
    val school: String = "",
    val priceText: String = "",
    val description: String = "",
    val hasOwnershipConsent: Boolean = false,
    val isFlashEnabled: Boolean = false,
    val useFrontCamera: Boolean = false,
    val fieldErrors: UploadFieldErrors = UploadFieldErrors(),
    val uploadPublishState: UploadPublishState =
        UploadPublishState.Idle
) {
    val isBusy: Boolean
        get() =
            uploadPublishState is UploadPublishState.CreatingDraft ||
            uploadPublishState is UploadPublishState.Uploading ||
            uploadPublishState is UploadPublishState.Publishing

    companion object {
        val CATEGORY_OPTIONS = listOf(
            "Note tóm tắt",
            "Cheat-sheet",
            "Mã nguồn",
            "Đồ án",
            "Khác"
        )

        val SUBJECT_OPTIONS = listOf(
            "Kinh tế vi mô",
            "Kinh tế học",
            "Lập trình",
            "Công nghệ thông tin",
            "Cơ sở dữ liệu",
            "Mạng máy tính",
            "Hệ điều hành",
            "Toán cao cấp",
            "Khác"
        )

        val SCHOOL_OPTIONS = listOf(
            "Đại học Kinh tế Quốc dân",
            "ĐH Bách Khoa TP.HCM",
            "ĐH Bách Khoa HN",
            "ĐH Giao thông Vận tải TP.HCM",
            "ĐH Khoa học Tự nhiên",
            "ĐH Công nghệ Thông tin"
        )
    }
}

sealed interface SellerDocumentsListState {
    data object Loading : SellerDocumentsListState

    data class Content(
        val items: List<SellerDocumentItem>
    ) : SellerDocumentsListState

    data object Empty : SellerDocumentsListState

    data class Error(
        val message: String
    ) : SellerDocumentsListState
}

enum class SellerArchiveTrigger {
    HIDE,
    REMOVE
}

data class SellerConfirmArchive(
    val document: SellerDocumentItem,
    val trigger: SellerArchiveTrigger
)

data class SellerEditDraft(
    val docId: String,
    val expectedVersion: Int,
    val title: String,
    val category: String,
    val subject: String,
    val school: String,
    val priceText: String,
    val description: String,
    val originalDocument: SellerDocumentItem? = null,
    val titleError: String? = null,
    val priceError: String? = null
)

sealed interface SellerManagementFeedback {
    data object None : SellerManagementFeedback
    data object Saving : SellerManagementFeedback

    data class Success(
        val message: String
    ) : SellerManagementFeedback

    data class VersionConflict(
        val message: String
    ) : SellerManagementFeedback

    data class Error(
        val message: String
    ) : SellerManagementFeedback
}

data class SellerDocumentsUiState(
    val listState: SellerDocumentsListState =
        SellerDocumentsListState.Loading,
    val sortOption: SellerDocumentSort =
        SellerDocumentSort.NEWEST,
    val actionSheetDocument: SellerDocumentItem? = null,
    val confirmArchive: SellerConfirmArchive? = null,
    val editDraft: SellerEditDraft? = null,
    val feedback: SellerManagementFeedback =
        SellerManagementFeedback.None
)

class VersionConflictException(
    message: String =
        "Phiên bản tài liệu đã thay đổi. Vui lòng làm mới danh sách."
) : Exception(message)

fun parsePriceVnd(raw: String): Int? {
    val digitsOnly = raw.trim()
        .replace(".", "")
        .replace(",", "")
        .replace(" ", "")

    if (
        digitsOnly.isEmpty() ||
        !digitsOnly.all { it.isDigit() }
    ) {
        return null
    }

    return digitsOnly.toIntOrNull()
}

fun formatSellerPriceVnd(amountVnd: Int): String {
    val raw = amountVnd.coerceAtLeast(0).toString()
    val reversed = raw.reversed()
    val sb = StringBuilder()

    for (i in reversed.indices) {
        if (i > 0 && i % 3 == 0) {
            sb.append('.')
        }
        sb.append(reversed[i])
    }

    return "${sb.reverse()}đ"
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"

    val mb = bytes / (1024.0 * 1024.0)

    return if (mb >= 1.0) {
        val tenths = ((mb * 10.0).toInt()) / 10.0
        "$tenths MB"
    } else {
        val kb = (bytes / 1024L).coerceAtLeast(1L)
        "$kb KB"
    }
}

// C04 UI-facing contract.
// This is NOT the A04 Firebase repository interface.
// A separate adapter is required for real integration.
interface DocumentUploadRepository {
    suspend fun createOrReuseDraft(
        idempotencyKey: String
    ): Result<DraftSession>

    suspend fun uploadStagingFiles(
        draft: DraftSession,
        previews: List<SelectedPreviewImage>,
        pdf: SelectedPdfFile,
        onProgress: (percent: Int, stageLabel: String) -> Unit
    ): Result<
        Pair<
            List<UploadedStagingArtifact>,
            UploadedStagingArtifact
        >
    >

    suspend fun publishDocument(
        request: PublishDocumentRequest
    ): Result<SellerDocumentItem>

    suspend fun fetchSellerDocuments():
        Result<List<SellerDocumentItem>>

    suspend fun updateDocumentMetadata(
        request: UpdateDocumentMetadataRequest
    ): Result<SellerDocumentItem>

    suspend fun archiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem>

    suspend fun unarchiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem>
}

class FakeDocumentUploadRepository(
    var simulateNetworkError: Boolean = false,
    var simulatePublishErrorAfterUpload: Boolean = false,
    var simulateVersionConflict: Boolean = false,
    var uploadStepDelayMs: Long = 0L,
    initialDocuments: List<SellerDocumentItem> =
        defaultSellerDocuments()
) : DocumentUploadRepository {

    private val draftsByKey =
        mutableMapOf<String, DraftSession>()

    private val publishedByDraft =
        mutableMapOf<String, SellerDocumentItem>()

    private val documents = initialDocuments.toMutableList()

    var createDraftCallCount = 0
    var uploadCallCount = 0
    var publishCallCount = 0
    var archiveCallCount = 0

    override suspend fun createOrReuseDraft(
        idempotencyKey: String
    ): Result<DraftSession> {
        createDraftCallCount++

        if (simulateNetworkError) {
            return Result.failure(
                IOException("Không có kết nối mạng")
            )
        }

        val existing = draftsByKey[idempotencyKey]

        if (existing != null) {
            return Result.success(existing)
        }

        val draftId =
            "draft_${draftsByKey.size + 1}_${UUID.randomUUID().toString().take(6)}"

        val session = DraftSession(
            draftId = draftId,
            versionId = "v1",
            stagingPrefix = "staging/seller/$draftId"
        )

        draftsByKey[idempotencyKey] = session

        return Result.success(session)
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
    > {
        uploadCallCount++

        if (simulateNetworkError) {
            return Result.failure(
                IOException("Mất kết nối mạng khi tải tệp lên")
            )
        }

        if (
            previews.isEmpty() ||
            previews.any {
                it.mimeType.lowercase() !in
                    ALLOWED_PREVIEW_IMAGE_MIMES ||
                    it.sizeBytes <= 0L ||
                    it.sizeBytes > MAX_PREVIEW_IMAGE_BYTES
            }
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Ảnh preview phải là JPG/PNG, tối đa 5 MiB mỗi ảnh"
                )
            )
        }

        if (
            pdf.mimeType.lowercase() != ALLOWED_PDF_MIME ||
            pdf.sizeBytes <= 0L ||
            pdf.sizeBytes > MAX_PDF_BYTES
        ) {
            return Result.failure(
                IllegalArgumentException(
                    "Tệp PDF phải hợp lệ và không vượt quá 25 MiB"
                )
            )
        }

        val milestones = listOf(
            25 to "Đang tải ảnh xem trước...",
            60 to "Đang tải tệp PDF...",
            100 to "Hoàn tất tải tệp lên vùng tạm"
        )

        for ((percent, label) in milestones) {
            onProgress(percent, label)

            if (uploadStepDelayMs > 0L) {
                delay(uploadStepDelayMs)
            }
        }

        val previewArtifacts =
            previews.mapIndexed { idx, img ->
                UploadedStagingArtifact(
                    objectPath =
                        "${draft.stagingPrefix}/preview_${idx + 1}_${img.displayName}",
                    generation = "gen_img_${idx + 1}"
                )
            }

        val pdfArtifact = UploadedStagingArtifact(
            objectPath =
                "${draft.stagingPrefix}/${pdf.displayName}",
            generation = "gen_pdf_1"
        )

        return Result.success(
            previewArtifacts to pdfArtifact
        )
    }

    override suspend fun publishDocument(
        request: PublishDocumentRequest
    ): Result<SellerDocumentItem> {
        publishCallCount++

        if (
            simulateNetworkError ||
            simulatePublishErrorAfterUpload
        ) {
            return Result.failure(
                IOException(
                    "Không thể xuất bản tài liệu do lỗi kết nối"
                )
            )
        }

        if (!request.ownershipConfirmed) {
            return Result.failure(
                IllegalArgumentException(
                    "Chưa xác nhận quyền đăng bán tài liệu"
                )
            )
        }

        val alreadyPublished =
            publishedByDraft[request.draftId]

        if (alreadyPublished != null) {
            return Result.success(alreadyPublished)
        }

        val created = SellerDocumentItem(
            docId = "doc_seller_${documents.size + 1}",
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

        publishedByDraft[request.draftId] = created
        documents.add(0, created)

        return Result.success(created)
    }

    override suspend fun fetchSellerDocuments():
        Result<List<SellerDocumentItem>> {

        if (simulateNetworkError) {
            return Result.failure(
                IOException(
                    "Không thể tải danh sách tài liệu do mất mạng"
                )
            )
        }

        return Result.success(documents.toList())
    }

    override suspend fun updateDocumentMetadata(
        request: UpdateDocumentMetadataRequest
    ): Result<SellerDocumentItem> {

        if (simulateNetworkError) {
            return Result.failure(
                IOException(
                    "Không thể cập nhật thông tin do mất mạng"
                )
            )
        }

        val idx = documents.indexOfFirst {
            it.docId == request.docId
        }

        if (idx < 0) {
            return Result.failure(
                NoSuchElementException(
                    "Không tìm thấy tài liệu cần chỉnh sửa"
                )
            )
        }

        val existing = documents[idx]

        if (
            simulateVersionConflict ||
            existing.version != request.expectedVersion
        ) {
            return Result.failure(
                VersionConflictException()
            )
        }

        val updated = existing.copy(
            version = existing.version + 1,
            title = request.title,
            category = request.category,
            subject = request.subject,
            school = request.school,
            priceVnd = request.priceVnd,
            description = request.description
        )

        documents[idx] = updated

        return Result.success(updated)
    }

    override suspend fun archiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem> {
        archiveCallCount++

        if (simulateNetworkError) {
            return Result.failure(
                IOException("Không thể ẩn tài liệu do mất mạng")
            )
        }

        val idx = documents.indexOfFirst {
            it.docId == docId
        }

        if (idx < 0) {
            return Result.failure(
                NoSuchElementException(
                    "Không tìm thấy tài liệu"
                )
            )
        }

        val existing = documents[idx]

        if (
            simulateVersionConflict ||
            existing.version != expectedVersion
        ) {
            return Result.failure(
                VersionConflictException()
            )
        }

        val archived = existing.copy(
            version = existing.version + 1,
            status = SellerDocumentStatus.ARCHIVED
        )

        documents[idx] = archived

        return Result.success(archived)
    }

    override suspend fun unarchiveDocument(
        docId: String,
        expectedVersion: Int
    ): Result<SellerDocumentItem> {

        if (simulateNetworkError) {
            return Result.failure(
                IOException(
                    "Không thể bỏ ẩn tài liệu do mất mạng"
                )
            )
        }

        val idx = documents.indexOfFirst {
            it.docId == docId
        }

        if (idx < 0) {
            return Result.failure(
                NoSuchElementException(
                    "Không tìm thấy tài liệu"
                )
            )
        }

        val existing = documents[idx]

        if (
            simulateVersionConflict ||
            existing.version != expectedVersion
        ) {
            return Result.failure(
                VersionConflictException()
            )
        }

        val restored = existing.copy(
            version = existing.version + 1,
            status = SellerDocumentStatus.PUBLISHED
        )

        documents[idx] = restored

        return Result.success(restored)
    }

    fun bumpServerVersion(docId: String) {
        val idx = documents.indexOfFirst {
            it.docId == docId
        }

        if (idx >= 0) {
            val current = documents[idx]
            documents[idx] = current.copy(
                version = current.version + 1
            )
        }
    }

    companion object {
        fun defaultSellerDocuments():
            List<SellerDocumentItem> = listOf(
            SellerDocumentItem(
                docId = "seller_doc_01",
                version = 1,
                title =
                    "Tóm tắt Kinh tế vĩ mô - Chương 1 đến 5",
                category = "Note tóm tắt",
                subject = "Kinh tế học",
                school = "Đại học Kinh tế Quốc dân",
                priceVnd = 15_000,
                description =
                    "Tóm tắt đầy đủ lý thuyết và ví dụ minh họa. Phù hợp cho ôn thi giữa kỳ và cuối kỳ.",
                viewCount = 342,
                reviewCount = 12,
                publishedDateLabel = "Đăng 12/09/2025",
                status = SellerDocumentStatus.PUBLISHED
            ),
            SellerDocumentItem(
                docId = "seller_doc_02",
                version = 1,
                title = "Source code đồ án Web (Full)",
                category = "Mã nguồn",
                subject = "Công nghệ thông tin",
                school = "ĐH Bách Khoa HN",
                priceVnd = 25_000,
                description =
                    "Mã nguồn dự án Web kèm hướng dẫn cài đặt và tài liệu mô tả API.",
                viewCount = 187,
                reviewCount = 8,
                publishedDateLabel = "Đăng 10/09/2025",
                status = SellerDocumentStatus.PUBLISHED
            ),
            SellerDocumentItem(
                docId = "seller_doc_03",
                version = 1,
                title = "Cheat-sheet Toán cao cấp",
                category = "Cheat-sheet",
                subject = "Toán cao cấp",
                school = "Đại học Kinh tế Quốc dân",
                priceVnd = 12_000,
                description =
                    "Bảng tổng hợp nhanh các công thức trọng tâm Toán cao cấp.",
                viewCount = 256,
                reviewCount = 15,
                publishedDateLabel = "Đăng 08/09/2025",
                status = SellerDocumentStatus.PUBLISHED
            ),
            SellerDocumentItem(
                docId = "seller_doc_04",
                version = 1,
                title = "Note ngắn Cơ sở dữ liệu",
                category = "Note tóm tắt",
                subject = "Cơ sở dữ liệu",
                school = "ĐH Bách Khoa HN",
                priceVnd = 18_000,
                description =
                    "Tổng hợp chuẩn hóa dữ liệu, đại số quan hệ và tối ưu câu truy vấn SQL.",
                viewCount = 123,
                reviewCount = 6,
                publishedDateLabel = "Đăng 05/09/2025",
                status = SellerDocumentStatus.PUBLISHED
            ),
            SellerDocumentItem(
                docId = "seller_doc_05",
                version = 1,
                title =
                    "Bài tập Mạng máy tính có lời giải",
                category = "Đồ án",
                subject = "Mạng máy tính",
                school = "ĐH Bách Khoa TP.HCM",
                priceVnd = 20_000,
                description =
                    "Tổng hợp bài tập chia subnet, định tuyến và mô hình TCP/IP.",
                viewCount = 94,
                reviewCount = 5,
                publishedDateLabel = "Đăng 02/09/2025",
                status = SellerDocumentStatus.PUBLISHED
            )
        )
    }
}