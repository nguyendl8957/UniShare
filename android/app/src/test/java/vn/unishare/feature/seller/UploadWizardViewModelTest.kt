package vn.unishare.feature.seller

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.data.repository.DocumentUploadApiException
import vn.unishare.feature.seller.ui.ALLOWED_PDF_MIME
import vn.unishare.feature.seller.ui.DocumentUploadRepository
import vn.unishare.feature.seller.ui.DraftSession
import vn.unishare.feature.seller.ui.FakeDocumentUploadRepository
import vn.unishare.feature.seller.ui.PublishDocumentRequest
import vn.unishare.feature.seller.ui.SelectedPdfFile
import vn.unishare.feature.seller.ui.SelectedPreviewImage
import vn.unishare.feature.seller.ui.SellerArchiveTrigger
import vn.unishare.feature.seller.ui.SellerDocumentItem
import vn.unishare.feature.seller.ui.SellerDocumentStatus
import vn.unishare.feature.seller.ui.SellerDocumentsListState
import vn.unishare.feature.seller.ui.SellerManagementFeedback
import vn.unishare.feature.seller.ui.UpdateDocumentMetadataRequest
import vn.unishare.feature.seller.ui.UploadPublishState
import vn.unishare.feature.seller.ui.UploadWizardStep
import vn.unishare.feature.seller.ui.UploadWizardViewModel
import vn.unishare.feature.seller.ui.UploadedStagingArtifact

private class ControllableUploadRepository(
    val delegate: FakeDocumentUploadRepository = FakeDocumentUploadRepository()
) : DocumentUploadRepository {

    var uploadGate: CompletableDeferred<Unit>? = null
    var publishGate: CompletableDeferred<Unit>? = null
    var publishError: Exception? = null

    override suspend fun createOrReuseDraft(idempotencyKey: String): Result<DraftSession> {
        return delegate.createOrReuseDraft(idempotencyKey)
    }

    override suspend fun uploadStagingFiles(
        draft: DraftSession,
        previews: List<SelectedPreviewImage>,
        pdf: SelectedPdfFile,
        onProgress: (percent: Int, stageLabel: String) -> Unit
    ): Result<Pair<List<UploadedStagingArtifact>, UploadedStagingArtifact>> {
        onProgress(30, "Đang tải tệp lên...")
        uploadGate?.await()
        return delegate.uploadStagingFiles(draft, previews, pdf, onProgress)
    }

    override suspend fun publishDocument(request: PublishDocumentRequest): Result<SellerDocumentItem> {
        publishGate?.await()
        publishError?.let { return Result.failure(it) }
        return delegate.publishDocument(request)
    }

    override suspend fun fetchSellerDocuments(): Result<List<SellerDocumentItem>> {
        return delegate.fetchSellerDocuments()
    }

    override suspend fun updateDocumentMetadata(
        request: UpdateDocumentMetadataRequest
    ): Result<SellerDocumentItem> {
        return delegate.updateDocumentMetadata(request)
    }

    override suspend fun archiveDocument(docId: String, expectedVersion: Int): Result<SellerDocumentItem> {
        return delegate.archiveDocument(docId, expectedVersion)
    }

    override suspend fun unarchiveDocument(docId: String, expectedVersion: Int): Result<SellerDocumentItem> {
        return delegate.unarchiveDocument(docId, expectedVersion)
    }
}

class UploadWizardViewModelTest {

    private fun validImage() = SelectedPreviewImage(
        id = "img_1",
        uriString = "content://media/preview_1.jpg",
        displayName = "preview_1.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 512_000L
    )

    private fun validPdf() = SelectedPdfFile(
        uriString = "content://docs/kinh_te_vi_mo.pdf",
        displayName = "Kinh_te_vi_mo.pdf",
        mimeType = ALLOWED_PDF_MIME,
        sizeBytes = 3_400_000L
    )

    private fun fillValidWizard(viewModel: UploadWizardViewModel) {
        viewModel.addPreviewImage(validImage())
        viewModel.validateStep1AndNext()
        viewModel.selectPdfFile(validPdf())
        viewModel.validateStep2AndNext()
        viewModel.onTitleChange("Tóm tắt Kinh tế vi mô - Chương 1 đến 5")
        viewModel.onCategoryChange("Note tóm tắt")
        viewModel.onSubjectChange("Kinh tế vi mô")
        viewModel.onSchoolChange("Đại học Kinh tế Quốc dân")
        viewModel.onPriceChange("15000")
        viewModel.onDescriptionChange("Tài liệu ôn thi giữa kỳ và cuối kỳ.")
        viewModel.onOwnershipConsentChange(true)
    }

    @Test
    fun missingFilesMetadataOrConsent_blocksTransitionAndDoesNotSubmit() = runBlocking {
        val fakeRepo = FakeDocumentUploadRepository()
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )

        // Bước 1 chưa có ảnh -> không chuyển sang bước 2
        assertFalse(viewModel.validateStep1AndNext())
        assertEquals(UploadWizardStep.PREVIEW_IMAGES, viewModel.wizardState.value.currentStep)
        assertNotNull(viewModel.wizardState.value.fieldErrors.previewError)

        // Bước 2 chưa có PDF -> không chuyển sang bước 3
        viewModel.addPreviewImage(validImage())
        assertTrue(viewModel.validateStep1AndNext())
        assertFalse(viewModel.validateStep2AndNext())
        assertEquals(UploadWizardStep.UPLOAD_PDF, viewModel.wizardState.value.currentStep)
        assertNotNull(viewModel.wizardState.value.fieldErrors.pdfError)

        // Bước 3 thiếu metadata và chưa xác nhận quyền đăng -> không gọi repository
        viewModel.selectPdfFile(validPdf())
        assertTrue(viewModel.validateStep2AndNext())
        viewModel.submitPublish()

        val errors = viewModel.wizardState.value.fieldErrors
        assertNotNull(errors.titleError)
        assertNotNull(errors.categoryError)
        assertNotNull(errors.subjectError)
        assertNotNull(errors.schoolError)
        assertNotNull(errors.priceError)
        assertNotNull(errors.consentError)
        assertEquals(0, fakeRepo.createDraftCallCount)
        assertEquals(0, fakeRepo.publishCallCount)
    }

    @Test
    fun invalidMimeOrOversizedFiles_rejectedWithErrors() = runBlocking {
        val viewModel = UploadWizardViewModel(
            repository = FakeDocumentUploadRepository(),
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )

        // Ảnh sai MIME
        viewModel.addPreviewImage(validImage().copy(mimeType = "application/pdf"))
        assertTrue(viewModel.wizardState.value.previewImages.isEmpty())
        assertNotNull(viewModel.wizardState.value.fieldErrors.previewError)

        // Ảnh quá 10MB
        viewModel.addPreviewImage(validImage().copy(sizeBytes = 12L * 1024 * 1024))
        assertTrue(viewModel.wizardState.value.previewImages.isEmpty())
        assertNotNull(viewModel.wizardState.value.fieldErrors.previewError)

        // PDF sai MIME
        viewModel.selectPdfFile(validPdf().copy(mimeType = "image/png", displayName = "anh.png"))
        assertNull(viewModel.wizardState.value.selectedPdf)
        assertNotNull(viewModel.wizardState.value.fieldErrors.pdfError)

        // PDF quá 50MB
        viewModel.selectPdfFile(validPdf().copy(sizeBytes = 55L * 1024 * 1024))
        assertNull(viewModel.wizardState.value.selectedPdf)
        assertNotNull(viewModel.wizardState.value.fieldErrors.pdfError)
    }

    @Test
    fun upload100PercentIsPublishingNotPublished_andBlocksDoubleSubmit() = runBlocking {
        val controllable = ControllableUploadRepository()
        val publishGate = CompletableDeferred<Unit>()
        controllable.publishGate = publishGate

        val viewModel = UploadWizardViewModel(
            repository = controllable,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )
        fillValidWizard(viewModel)

        viewModel.submitPublish()

        // Sau khi upload đạt 100%, trạng thái phải là Publishing chứ chưa được coi là Published
        assertTrue(viewModel.wizardState.value.uploadPublishState is UploadPublishState.Publishing)
        assertFalse(viewModel.wizardState.value.uploadPublishState is UploadPublishState.Published)

        // Nhấn đăng lại nhiều lần khi đang xử lý không được gọi publish lần 2
        viewModel.submitPublish()
        viewModel.submitPublish()
        assertEquals(1, controllable.delegate.uploadCallCount)

        publishGate.complete(Unit)
        assertTrue(viewModel.wizardState.value.uploadPublishState is UploadPublishState.Published)
        assertEquals(1, controllable.delegate.publishCallCount)
    }

    @Test
    fun failureAfterUpload_keepsDraftAndMetadata_andRetrySucceeds() = runBlocking {
        val fakeRepo = FakeDocumentUploadRepository(simulatePublishErrorAfterUpload = true)
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )
        fillValidWizard(viewModel)

        viewModel.submitPublish()

        val failedState = viewModel.wizardState.value
        assertTrue(failedState.uploadPublishState is UploadPublishState.Failed)
        val savedDraftId = failedState.draftId
        assertNotNull(savedDraftId)
        assertEquals("Tóm tắt Kinh tế vi mô - Chương 1 đến 5", failedState.title)
        assertEquals("15000", failedState.priceText)
        assertEquals(1, failedState.previewImages.size)
        assertNotNull(failedState.selectedPdf)
        assertEquals(1, fakeRepo.uploadCallCount)

        // Thử lại sau khi mạng ổn định -> tái sử dụng draft & artifact đã tải lên
        fakeRepo.simulatePublishErrorAfterUpload = false
        viewModel.retryPublish()

        val publishedState = viewModel.wizardState.value
        assertTrue(publishedState.uploadPublishState is UploadPublishState.Published)
        assertEquals(savedDraftId, publishedState.draftId)
        assertEquals(1, fakeRepo.uploadCallCount)
        assertEquals(2, fakeRepo.publishCallCount)
    }

    @Test
    fun replacingPdfAfterFailedPublishCreatesNewDraftAndPreservesMetadata() = runBlocking {
        val repository = ControllableUploadRepository().apply {
            publishError = DocumentUploadApiException(httpStatus = 409, invalidPdf = true)
        }
        val viewModel = UploadWizardViewModel(
            repository = repository,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )
        fillValidWizard(viewModel)
        viewModel.submitPublish()
        val failed = viewModel.wizardState.value
        val failure = failed.uploadPublishState as UploadPublishState.Failed
        assertEquals("Tệp PDF không hợp lệ. Vui lòng chọn lại tài liệu.", failure.message)
        assertFalse(failure.canRetry)
        assertNotNull(failed.draftId)

        viewModel.selectPdfFile(
            SelectedPdfFile("content://docs/replacement.pdf", "replacement.pdf", ALLOWED_PDF_MIME, 10_000L)
        )
        repository.publishError = null
        viewModel.submitPublish()

        val published = viewModel.wizardState.value
        assertTrue(published.uploadPublishState is UploadPublishState.Published)
        assertTrue(published.draftId != failed.draftId)
        assertEquals(failed.title, published.title)
        assertEquals(2, repository.delegate.uploadCallCount)
    }

    @Test
    fun cancelDuringUpload_stopsWithoutPublish_andKeepsFormData() = runBlocking {
        val controllable = ControllableUploadRepository()
        controllable.uploadGate = CompletableDeferred()

        val viewModel = UploadWizardViewModel(
            repository = controllable,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = false
        )
        fillValidWizard(viewModel)

        viewModel.submitPublish()
        assertTrue(viewModel.wizardState.value.uploadPublishState is UploadPublishState.Uploading)

        viewModel.cancelUpload()

        val canceledState = viewModel.wizardState.value
        assertTrue(canceledState.uploadPublishState is UploadPublishState.Canceled)
        assertEquals(0, controllable.delegate.publishCallCount)
        assertEquals("Tóm tắt Kinh tế vi mô - Chương 1 đến 5", canceledState.title)
        assertNotNull(canceledState.selectedPdf)
    }

    @Test
    fun editAndArchive_handlesVersionConflictAndSoftArchivesDocument() = runBlocking {
        val fakeRepo = FakeDocumentUploadRepository()
        val viewModel = UploadWizardViewModel(
            repository = fakeRepo,
            mainDispatcher = Dispatchers.Unconfined,
            autoLoadDocuments = true
        )

        val initialDocs = (viewModel.documentsState.value.listState as SellerDocumentsListState.Content).items
        val firstDoc = initialDocs.first { it.docId == "seller_doc_01" }

        // Tăng version trên server giả lập để tạo xung đột phiên bản
        fakeRepo.bumpServerVersion("seller_doc_01")

        viewModel.openEditDialog(firstDoc)
        viewModel.updateEditDraft(title = "Tiêu đề đã sửa mới")
        viewModel.saveEditMetadata()

        assertTrue(viewModel.documentsState.value.feedback is SellerManagementFeedback.VersionConflict)

        // Làm mới lại danh sách rồi sửa và lưu trữ thành công
        viewModel.refreshAfterConflict()
        val refreshedDoc = (viewModel.documentsState.value.listState as SellerDocumentsListState.Content)
            .items.first { it.docId == "seller_doc_01" }
        assertEquals(2, refreshedDoc.version)

        viewModel.requestArchiveConfirmation(refreshedDoc, SellerArchiveTrigger.REMOVE)
        viewModel.confirmArchiveDocument()

        val afterArchive = (viewModel.documentsState.value.listState as SellerDocumentsListState.Content)
            .items.first { it.docId == "seller_doc_01" }
        assertEquals(SellerDocumentStatus.ARCHIVED, afterArchive.status)
        assertEquals(1, fakeRepo.archiveCallCount)

        // Bỏ ẩn tài liệu -> chuyển lại sang trạng thái Đang bán
        viewModel.unarchiveDocument(afterArchive)
        val afterUnarchive = (viewModel.documentsState.value.listState as SellerDocumentsListState.Content)
            .items.first { it.docId == "seller_doc_01" }
        assertEquals(SellerDocumentStatus.PUBLISHED, afterUnarchive.status)
    }
}
