package vn.unishare.feature.seller.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class UploadWizardViewModel(
    private val repository: DocumentUploadRepository = FakeDocumentUploadRepository(),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    autoLoadDocuments: Boolean = true
) : ViewModel() {

    private val _wizardState = MutableStateFlow(UploadWizardUiState())
    val wizardState: StateFlow<UploadWizardUiState> = _wizardState.asStateFlow()

    private val _documentsState = MutableStateFlow(SellerDocumentsUiState())
    val documentsState: StateFlow<SellerDocumentsUiState> = _documentsState.asStateFlow()

    private var activePublishJob: Job? = null
    private var cachedDraftSession: DraftSession? = null
    private var cachedPreviewArtifacts: List<UploadedStagingArtifact>? = null
    private var cachedPdfArtifact: UploadedStagingArtifact? = null
    private var rawDocumentsCache: List<SellerDocumentItem> = emptyList()

    init {
        if (autoLoadDocuments) {
            loadSellerDocuments()
        }
    }

    // --- Bước 1: Ảnh preview ---

    fun addPreviewImage(image: SelectedPreviewImage) {
        val current = _wizardState.value
        val normalizedMime = image.mimeType.trim().lowercase()
        if (normalizedMime !in ALLOWED_PREVIEW_IMAGE_MIMES) {
            setPreviewError("Chỉ hỗ trợ ảnh JPG hoặc PNG.")
            return
        }
        if (image.sizeBytes <= 0L || image.sizeBytes > MAX_PREVIEW_IMAGE_BYTES) {
            setPreviewError("Dung lượng mỗi ảnh preview không được vượt quá 5 MiB.")
            return
        }
        if (current.previewImages.size >= MAX_PREVIEW_IMAGES) {
            setPreviewError("Chỉ được chọn tối đa $MAX_PREVIEW_IMAGES ảnh preview.")
            return
        }

        invalidateUploadedArtifacts()
        val updated = current.previewImages + image
        _wizardState.update {
            it.copy(
                previewImages = updated,
                selectedPreviewIndex = updated.lastIndex,
                fieldErrors = it.fieldErrors.copy(previewError = null)
            )
        }
    }

    fun removePreviewImage(imageId: String) {
        val current = _wizardState.value
        val updated = current.previewImages.filterNot { it.id == imageId }
        val nextIndex = current.selectedPreviewIndex.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
        invalidateUploadedArtifacts()
        _wizardState.update {
            it.copy(
                previewImages = updated,
                selectedPreviewIndex = nextIndex,
                fieldErrors = it.fieldErrors.copy(previewError = null)
            )
        }
    }

    fun selectPreviewImage(index: Int) {
        val size = _wizardState.value.previewImages.size
        if (size == 0) return
        _wizardState.update { it.copy(selectedPreviewIndex = index.coerceIn(0, size - 1)) }
    }

    fun toggleFlash() {
        _wizardState.update { it.copy(isFlashEnabled = !it.isFlashEnabled) }
    }

    fun toggleCameraFacing() {
        _wizardState.update { it.copy(useFrontCamera = !it.useFrontCamera) }
    }

    fun validateStep1AndNext(): Boolean {
        val images = _wizardState.value.previewImages
        if (images.isEmpty()) {
            setPreviewError("Vui lòng chọn hoặc chụp ít nhất 1 ảnh preview trước khi tiếp tục.")
            return false
        }
        _wizardState.update {
            it.copy(
                currentStep = UploadWizardStep.UPLOAD_PDF,
                fieldErrors = it.fieldErrors.copy(previewError = null)
            )
        }
        return true
    }

    // --- Bước 2: Tệp PDF ---

    fun selectPdfFile(pdf: SelectedPdfFile) {
        val normalizedMime = pdf.mimeType.trim().lowercase()
        val hasPdfExtension = pdf.displayName.trim().lowercase().endsWith(".pdf")
        if (normalizedMime != ALLOWED_PDF_MIME || !hasPdfExtension) {
            setPdfError("Tệp không hợp lệ. Vui lòng chọn đúng định dạng PDF (.pdf).")
            return
        }
        if (pdf.sizeBytes <= 0L) {
            setPdfError("Tệp PDF rỗng hoặc không đọc được.")
            return
        }
        if (pdf.sizeBytes > MAX_PDF_BYTES) {
            setPdfError("Dung lượng tệp PDF vượt quá giới hạn 25 MiB.")
            return
        }

        invalidateUploadedArtifacts()
        _wizardState.update {
            it.copy(
                selectedPdf = pdf,
                fieldErrors = it.fieldErrors.copy(pdfError = null)
            )
        }
    }

    fun clearPdfFile() {
        invalidateUploadedArtifacts()
        _wizardState.update {
            it.copy(
                selectedPdf = null,
                fieldErrors = it.fieldErrors.copy(pdfError = null)
            )
        }
    }

    fun validateStep2AndNext(): Boolean {
        val pdf = _wizardState.value.selectedPdf
        if (pdf == null) {
            setPdfError("Vui lòng chọn tệp PDF trước khi tiếp tục.")
            return false
        }
        _wizardState.update {
            it.copy(
                currentStep = UploadWizardStep.METADATA,
                fieldErrors = it.fieldErrors.copy(pdfError = null)
            )
        }
        return true
    }

    fun goToPreviousStep() {
        if (_wizardState.value.isBusy) return
        val prev = when (_wizardState.value.currentStep) {
            UploadWizardStep.PREVIEW_IMAGES -> UploadWizardStep.PREVIEW_IMAGES
            UploadWizardStep.UPLOAD_PDF -> UploadWizardStep.PREVIEW_IMAGES
            UploadWizardStep.METADATA -> UploadWizardStep.UPLOAD_PDF
        }
        _wizardState.update { it.copy(currentStep = prev) }
    }

    // --- Bước 3: Metadata & xuất bản ---

    fun onTitleChange(value: String) {
        _wizardState.update {
            it.copy(
                title = value,
                fieldErrors = it.fieldErrors.copy(titleError = null)
            )
        }
    }

    fun onCategoryChange(value: String) {
        _wizardState.update {
            it.copy(
                category = value,
                fieldErrors = it.fieldErrors.copy(categoryError = null)
            )
        }
    }

    fun onSubjectChange(value: String) {
        _wizardState.update {
            it.copy(
                subject = value,
                fieldErrors = it.fieldErrors.copy(subjectError = null)
            )
        }
    }

    fun onSchoolChange(value: String) {
        _wizardState.update {
            it.copy(
                school = value,
                fieldErrors = it.fieldErrors.copy(schoolError = null)
            )
        }
    }

    fun onPriceChange(value: String) {
        _wizardState.update {
            it.copy(
                priceText = value,
                fieldErrors = it.fieldErrors.copy(priceError = null)
            )
        }
    }

    fun onDescriptionChange(value: String) {
        val clamped = if (value.length > MAX_DESCRIPTION_CHARS) value.take(MAX_DESCRIPTION_CHARS) else value
        _wizardState.update {
            it.copy(
                description = clamped,
                fieldErrors = it.fieldErrors.copy(descriptionError = null)
            )
        }
    }

    fun onOwnershipConsentChange(accepted: Boolean) {
        _wizardState.update {
            it.copy(
                hasOwnershipConsent = accepted,
                fieldErrors = it.fieldErrors.copy(consentError = null)
            )
        }
    }

    fun submitPublish() {
        val current = _wizardState.value
        if (current.isBusy || current.uploadPublishState is UploadPublishState.Published) return

        val validationErrors = validateAllFields(current)
        if (hasAnyValidationError(validationErrors)) {
            _wizardState.update { it.copy(fieldErrors = validationErrors) }
            return
        }

        val pdf = current.selectedPdf ?: return
        val priceVnd = parsePriceVnd(current.priceText) ?: return
        val key = current.idempotencyKey ?: "idem_${UUID.randomUUID()}"

        _wizardState.update {
            it.copy(
                idempotencyKey = key,
                fieldErrors = UploadFieldErrors(),
                uploadPublishState = UploadPublishState.CreatingDraft(key)
            )
        }

        activePublishJob = viewModelScope.launch(mainDispatcher) {
            try {
                // 1. Tạo hoặc tái sử dụng draft cùng idempotencyKey
                val draft = cachedDraftSession ?: repository.createOrReuseDraft(key).getOrElse { error ->
                    _wizardState.update {
                        it.copy(
                            uploadPublishState = UploadPublishState.Failed(
                                draftId = it.draftId,
                                message = mapUploadError(error)
                            )
                        )
                    }
                    return@launch
                }
                cachedDraftSession = draft
                _wizardState.update {
                    it.copy(
                        draftId = draft.draftId,
                        draftVersionId = draft.versionId
                    )
                }

                // 2. Tải ảnh preview và tệp PDF lên vùng staging nếu chưa có artifact từ lần thử trước
                val previewArtifacts: List<UploadedStagingArtifact>
                val pdfArtifact: UploadedStagingArtifact
                val existingPreviews = cachedPreviewArtifacts
                val existingPdf = cachedPdfArtifact

                if (existingPreviews != null && existingPdf != null) {
                    previewArtifacts = existingPreviews
                    pdfArtifact = existingPdf
                } else {
                    _wizardState.update {
                        it.copy(
                            uploadPublishState = UploadPublishState.Uploading(
                                draftId = draft.draftId,
                                progressPercent = 5,
                                stageLabel = "Đang chuẩn bị tải tệp lên..."
                            )
                        )
                    }
                    val uploadedPair = repository.uploadStagingFiles(
                        draft = draft,
                        previews = current.previewImages,
                        pdf = pdf,
                        onProgress = { percent, stageLabel ->
                            _wizardState.update {
                                it.copy(
                                    uploadPublishState = UploadPublishState.Uploading(
                                        draftId = draft.draftId,
                                        progressPercent = percent.coerceIn(0, 100),
                                        stageLabel = stageLabel
                                    )
                                )
                            }
                        }
                    ).getOrElse { error ->
                        _wizardState.update {
                            it.copy(
                                uploadPublishState = UploadPublishState.Failed(
                                    draftId = draft.draftId,
                                    message = mapUploadError(error)
                                )
                            )
                        }
                        return@launch
                    }
                    previewArtifacts = uploadedPair.first
                    pdfArtifact = uploadedPair.second
                    cachedPreviewArtifacts = previewArtifacts
                    cachedPdfArtifact = pdfArtifact
                }

                // 3. Gọi xác nhận xuất bản sau khi tải tệp đạt 100%
                _wizardState.update {
                    it.copy(uploadPublishState = UploadPublishState.Publishing(draft.draftId))
                }

                val publishRequest = PublishDocumentRequest(
                    draftId = draft.draftId,
                    versionId = draft.versionId,
                    title = current.title.trim(),
                    category = current.category.trim(),
                    subject = current.subject.trim(),
                    school = current.school.trim(),
                    priceVnd = priceVnd,
                    description = current.description.trim(),
                    previewArtifacts = previewArtifacts,
                    pdfArtifact = pdfArtifact,
                    ownershipConfirmed = current.hasOwnershipConsent
                )

                repository.publishDocument(publishRequest).fold(
                    onSuccess = { publishedDoc ->
                        _wizardState.update {
                            it.copy(uploadPublishState = UploadPublishState.Published(publishedDoc))
                        }
                        loadSellerDocuments()
                    },
                    onFailure = { error ->
                        _wizardState.update {
                            it.copy(
                                uploadPublishState = UploadPublishState.Failed(
                                    draftId = draft.draftId,
                                    message = mapUploadError(error)
                                )
                            )
                        }
                    }
                )
            } catch (cancelled: CancellationException) {
                _wizardState.update {
                    it.copy(uploadPublishState = UploadPublishState.Canceled(it.draftId))
                }
                throw cancelled
            }
        }
    }

    fun cancelUpload() {
        val job = activePublishJob
        if (job != null && job.isActive) {
            job.cancel()
            activePublishJob = null
            _wizardState.update {
                it.copy(uploadPublishState = UploadPublishState.Canceled(it.draftId))
            }
        }
    }

    fun retryPublish() {
        submitPublish()
    }

    fun resetWizardForNewUpload() {
        activePublishJob?.cancel()
        activePublishJob = null
        cachedDraftSession = null
        cachedPreviewArtifacts = null
        cachedPdfArtifact = null
        _wizardState.value = UploadWizardUiState()
    }

    // --- Quản lý danh sách tài liệu của Seller ---

    fun loadSellerDocuments() {
        _documentsState.update {
            it.copy(listState = SellerDocumentsListState.Loading)
        }
        viewModelScope.launch(mainDispatcher) {
            repository.fetchSellerDocuments().fold(
                onSuccess = { docs ->
                    rawDocumentsCache = docs
                    applyDocumentsToState()
                },
                onFailure = { error ->
                    _documentsState.update {
                        it.copy(
                            listState = SellerDocumentsListState.Error(mapUploadError(error))
                        )
                    }
                }
            )
        }
    }

    fun selectDocumentsSort(sortOption: SellerDocumentSort) {
        _documentsState.update { it.copy(sortOption = sortOption) }
        applyDocumentsToState()
    }

    fun openActionSheet(document: SellerDocumentItem) {
        _documentsState.update { it.copy(actionSheetDocument = document) }
    }

    fun dismissActionSheet() {
        _documentsState.update { it.copy(actionSheetDocument = null) }
    }

    fun openEditDialog(document: SellerDocumentItem) {
        _documentsState.update {
            it.copy(
                actionSheetDocument = null,
                editDraft = SellerEditDraft(
                    docId = document.docId,
                    expectedVersion = document.version,
                    title = document.title,
                    category = document.category,
                    subject = document.subject,
                    school = document.school,
                    priceText = document.priceVnd.toString(),
                    description = document.description,
                    originalDocument = document
                )
            )
        }
    }

    fun updateEditDraft(
        title: String? = null,
        category: String? = null,
        subject: String? = null,
        school: String? = null,
        priceText: String? = null,
        description: String? = null
    ) {
        _documentsState.update { state ->
            val draft = state.editDraft ?: return@update state
            state.copy(
                editDraft = draft.copy(
                    title = title ?: draft.title,
                    category = category ?: draft.category,
                    subject = subject ?: draft.subject,
                    school = school ?: draft.school,
                    priceText = priceText ?: draft.priceText,
                    description = (description ?: draft.description).take(MAX_DESCRIPTION_CHARS),
                    titleError = if (title != null) null else draft.titleError,
                    priceError = if (priceText != null) null else draft.priceError
                )
            )
        }
    }

    fun dismissEditDialog() {
        _documentsState.update { it.copy(editDraft = null) }
    }

    fun saveEditMetadata() {
        val draft = _documentsState.value.editDraft ?: return
        val titleError = if (draft.title.trim().length < 5) {
            "Tiêu đề phải có ít nhất 5 ký tự."
        } else {
            null
        }
        val parsedPrice = parsePriceVnd(draft.priceText)
        val priceError = when {
            parsedPrice == null -> "Vui lòng nhập giá hợp lệ."
            parsedPrice < MIN_SELLER_PRICE_VND || parsedPrice > MAX_SELLER_PRICE_VND ->
                "Giá bán từ ${formatSellerPriceVnd(MIN_SELLER_PRICE_VND)} đến ${formatSellerPriceVnd(MAX_SELLER_PRICE_VND)}."
            else -> null
        }

        if (titleError != null || priceError != null) {
            _documentsState.update {
                it.copy(editDraft = draft.copy(titleError = titleError, priceError = priceError))
            }
            return
        }

        val validPrice = parsedPrice ?: return
        _documentsState.update { it.copy(feedback = SellerManagementFeedback.Saving) }

        viewModelScope.launch(mainDispatcher) {
            val request = UpdateDocumentMetadataRequest(
                docId = draft.docId,
                expectedVersion = draft.expectedVersion,
                title = draft.title.trim(),
                category = draft.category.trim(),
                subject = draft.subject.trim(),
                school = draft.school.trim(),
                priceVnd = validPrice,
                description = draft.description.trim(),
                expectedMetadata = draft.originalDocument
            )
            repository.updateDocumentMetadata(request).fold(
                onSuccess = { updated ->
                    rawDocumentsCache = rawDocumentsCache.map {
                        if (it.docId == updated.docId) updated else it
                    }
                    _documentsState.update {
                        it.copy(
                            editDraft = null,
                            feedback = SellerManagementFeedback.Success("Đã cập nhật thông tin tài liệu.")
                        )
                    }
                    applyDocumentsToState()
                },
                onFailure = { error ->
                    val feedback = if (error is VersionConflictException) {
                        SellerManagementFeedback.VersionConflict(
                            "Tài liệu đã được cập nhật ở phiên bản mới hơn. Vui lòng làm mới danh sách."
                        )
                    } else {
                        SellerManagementFeedback.Error(mapUploadError(error))
                    }
                    _documentsState.update { it.copy(editDraft = null, feedback = feedback) }
                }
            )
        }
    }

    fun requestArchiveConfirmation(document: SellerDocumentItem, trigger: SellerArchiveTrigger) {
        _documentsState.update {
            it.copy(
                actionSheetDocument = null,
                confirmArchive = SellerConfirmArchive(document = document, trigger = trigger)
            )
        }
    }

    fun dismissArchiveConfirmation() {
        _documentsState.update { it.copy(confirmArchive = null) }
    }

    fun confirmArchiveDocument() {
        val pending = _documentsState.value.confirmArchive ?: return
        _documentsState.update {
            it.copy(
                confirmArchive = null,
                feedback = SellerManagementFeedback.Saving
            )
        }

        viewModelScope.launch(mainDispatcher) {
            repository.archiveDocument(
                docId = pending.document.docId,
                expectedVersion = pending.document.version
            ).fold(
                onSuccess = { archived ->
                    rawDocumentsCache = rawDocumentsCache.map {
                        if (it.docId == archived.docId) archived else it
                    }
                    _documentsState.update {
                        it.copy(
                            feedback = SellerManagementFeedback.Success(
                                "Đã lưu trữ tài liệu. Người đã mua trước đó vẫn giữ quyền đọc."
                            )
                        )
                    }
                    applyDocumentsToState()
                },
                onFailure = { error ->
                    val feedback = if (error is VersionConflictException) {
                        SellerManagementFeedback.VersionConflict(
                            "Phiên bản tài liệu đã thay đổi. Vui lòng làm mới trước khi ẩn tài liệu."
                        )
                    } else {
                        SellerManagementFeedback.Error(mapUploadError(error))
                    }
                    _documentsState.update { it.copy(feedback = feedback) }
                }
            )
        }
    }

    fun unarchiveDocument(document: SellerDocumentItem) {
        _documentsState.update {
            it.copy(
                actionSheetDocument = null,
                feedback = SellerManagementFeedback.Saving
            )
        }

        viewModelScope.launch(mainDispatcher) {
            repository.unarchiveDocument(
                docId = document.docId,
                expectedVersion = document.version
            ).fold(
                onSuccess = { restored ->
                    rawDocumentsCache = rawDocumentsCache.map {
                        if (it.docId == restored.docId) restored else it
                    }
                    _documentsState.update {
                        it.copy(
                            feedback = SellerManagementFeedback.Success(
                                "Đã bỏ ẩn và mở bán lại tài liệu."
                            )
                        )
                    }
                    applyDocumentsToState()
                },
                onFailure = { error ->
                    val feedback = if (error is VersionConflictException) {
                        SellerManagementFeedback.VersionConflict(
                            "Phiên bản tài liệu đã thay đổi. Vui lòng làm mới trước khi bỏ ẩn."
                        )
                    } else {
                        SellerManagementFeedback.Error(mapUploadError(error))
                    }
                    _documentsState.update { it.copy(feedback = feedback) }
                }
            )
        }
    }

    fun refreshAfterConflict() {
        _documentsState.update { it.copy(feedback = SellerManagementFeedback.None) }
        loadSellerDocuments()
    }

    fun dismissManagementFeedback() {
        _documentsState.update { it.copy(feedback = SellerManagementFeedback.None) }
    }

    // --- Hàm nội bộ ---

    private fun invalidateUploadedArtifacts() {
        cachedPreviewArtifacts = null
        cachedPdfArtifact = null
    }

    private fun setPreviewError(message: String) {
        _wizardState.update { it.copy(fieldErrors = it.fieldErrors.copy(previewError = message)) }
    }

    private fun setPdfError(message: String) {
        _wizardState.update { it.copy(fieldErrors = it.fieldErrors.copy(pdfError = message)) }
    }

    private fun validateAllFields(state: UploadWizardUiState): UploadFieldErrors {
        val previewError = if (state.previewImages.isEmpty()) {
            "Cần ít nhất 1 ảnh preview."
        } else {
            null
        }
        val pdfError = if (state.selectedPdf == null) {
            "Vui lòng chọn tệp PDF."
        } else {
            null
        }
        val titleError = when {
            state.title.isBlank() -> "Vui lòng nhập tiêu đề tài liệu."
            state.title.trim().length < 5 -> "Tiêu đề phải có ít nhất 5 ký tự."
            else -> null
        }
        val categoryError = if (state.category.isBlank()) "Vui lòng chọn danh mục." else null
        val subjectError = if (state.subject.isBlank()) "Vui lòng chọn môn học." else null
        val schoolError = if (state.school.isBlank()) "Vui lòng chọn trường áp dụng." else null

        val parsedPrice = parsePriceVnd(state.priceText)
        val priceError = when {
            state.priceText.isBlank() -> "Vui lòng nhập giá bán."
            parsedPrice == null -> "Giá bán phải là số nguyên VNĐ hợp lệ."
            parsedPrice < MIN_SELLER_PRICE_VND || parsedPrice > MAX_SELLER_PRICE_VND ->
                "Giá bán phải từ ${formatSellerPriceVnd(MIN_SELLER_PRICE_VND)} đến ${formatSellerPriceVnd(MAX_SELLER_PRICE_VND)}."
            else -> null
        }

        val consentError = if (!state.hasOwnershipConsent) {
            "Vui lòng xác nhận bạn có quyền đăng bán tài liệu này."
        } else {
            null
        }

        return UploadFieldErrors(
            previewError = previewError,
            pdfError = pdfError,
            titleError = titleError,
            categoryError = categoryError,
            subjectError = subjectError,
            schoolError = schoolError,
            priceError = priceError,
            consentError = consentError
        )
    }

    private fun hasAnyValidationError(errors: UploadFieldErrors): Boolean {
        return errors.previewError != null ||
            errors.pdfError != null ||
            errors.titleError != null ||
            errors.categoryError != null ||
            errors.subjectError != null ||
            errors.schoolError != null ||
            errors.priceError != null ||
            errors.descriptionError != null ||
            errors.consentError != null
    }

    private fun applyDocumentsToState() {
        val sortOption = _documentsState.value.sortOption
        val sorted = when (sortOption) {
            SellerDocumentSort.NEWEST -> rawDocumentsCache
            SellerDocumentSort.MOST_VIEWED -> rawDocumentsCache.sortedByDescending { it.viewCount }
            SellerDocumentSort.PRICE_DESC -> rawDocumentsCache.sortedByDescending { it.priceVnd }
            SellerDocumentSort.PRICE_ASC -> rawDocumentsCache.sortedBy { it.priceVnd }
        }
        val nextListState = if (sorted.isEmpty()) {
            SellerDocumentsListState.Empty
        } else {
            SellerDocumentsListState.Content(sorted)
        }
        _documentsState.update { it.copy(listState = nextListState) }
    }

    private fun mapUploadError(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is IOException ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("mạng", ignoreCase = true) ->
                "Mất kết nối mạng. Dữ liệu đã nhập vẫn được giữ lại, vui lòng thử lại."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi khi xử lý tài liệu. Vui lòng thử lại."
        }
    }
}
