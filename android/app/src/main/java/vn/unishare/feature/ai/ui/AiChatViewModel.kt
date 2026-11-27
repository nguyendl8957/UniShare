package vn.unishare.feature.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeoutException

class AiChatViewModel(
    private val authRepository: AuthRepository,
    private val aiRepository: DocumentAiRepository,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiChatUiState())
    val uiState: StateFlow<AiChatUiState> = _uiState.asStateFlow()

    private var activeRequestJob: Job? = null
    private var sessionEpoch: Long = 0L

    init {
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { session ->
                handleSessionChanged(session)
            }
        }
    }

    fun bindDocument(docId: String, documentTitle: String) {
        val current = _uiState.value
        if (current.docId == docId) {
            if (documentTitle.isNotBlank() && current.documentTitle != documentTitle) {
                _uiState.update { it.copy(documentTitle = documentTitle) }
            }
            return
        }

        // Chuyển sang tài liệu khác phải hủy request cũ và làm sạch lịch sử phiên trước
        activeRequestJob?.cancel()
        sessionEpoch++
        _uiState.update {
            it.copy(
                docId = docId,
                documentTitle = documentTitle,
                messages = emptyList(),
                inputError = null,
                isSending = false,
                isAccessLocked = false,
                errorState = null,
                lastSelectedCitationPage = null
            )
        }
    }

    private fun handleSessionChanged(session: AuthSession) {
        when (session) {
            is AuthSession.Restoring -> {
                resetSessionChat(isRestoring = true, isGuest = false, newUid = null)
            }

            is AuthSession.Guest -> {
                resetSessionChat(isRestoring = false, isGuest = true, newUid = null)
            }

            is AuthSession.SignedIn -> {
                val previousUid = _uiState.value.activeUid
                if (previousUid != session.uid) {
                    resetSessionChat(
                        isRestoring = false,
                        isGuest = false,
                        newUid = session.uid
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isRestoringSession = false,
                            isGuest = false
                        )
                    }
                }
            }
        }
    }

    private fun resetSessionChat(
        isRestoring: Boolean,
        isGuest: Boolean,
        newUid: String?
    ) {
        activeRequestJob?.cancel()
        sessionEpoch++
        _uiState.update {
            it.copy(
                isRestoringSession = isRestoring,
                isGuest = isGuest,
                activeUid = newUid,
                messages = emptyList(),
                inputError = null,
                isSending = false,
                isAccessLocked = false,
                errorState = null,
                lastSelectedCitationPage = null
            )
        }
    }

    fun onInputChanged(value: String) {
        val lengthError = if (value.length > MAX_AI_QUESTION_LENGTH) {
            "Câu hỏi tối đa $MAX_AI_QUESTION_LENGTH ký tự."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                inputText = value,
                inputError = lengthError
            )
        }
    }

    fun sendSuggestion(suggestion: AiQuickSuggestion) {
        val current = _uiState.value
        if (!current.canCompose || current.isSending || current.docId.isBlank()) return
        submitQuestionInternal(
            rawQuestion = suggestion.prompt,
            clientMessageId = UUID.randomUUID().toString(),
            existingFailedMessageId = null
        )
    }

    fun sendQuestion() {
        val current = _uiState.value
        if (!current.canCompose || current.isSending || current.docId.isBlank()) return

        val trimmed = current.inputText.trim()
        if (trimmed.isEmpty()) {
            _uiState.update {
                it.copy(inputError = "Vui lòng nhập câu hỏi về tài liệu.")
            }
            return
        }
        if (trimmed.length > MAX_AI_QUESTION_LENGTH) {
            _uiState.update {
                it.copy(inputError = "Câu hỏi tối đa $MAX_AI_QUESTION_LENGTH ký tự.")
            }
            return
        }

        submitQuestionInternal(
            rawQuestion = trimmed,
            clientMessageId = UUID.randomUUID().toString(),
            existingFailedMessageId = null
        )
    }

    fun retryLastFailedMessage() {
        val state = _uiState.value
        val targetClientId = (state.errorState as? AiChatErrorState.Transient)?.failedClientMessageId
            ?: state.messages.lastOrNull {
                it.role == AiChatRole.USER && it.deliveryState == AiMessageDeliveryState.FAILED
            }?.clientMessageId
            ?: return

        val failedMsg = state.messages.firstOrNull { it.clientMessageId == targetClientId } ?: return
        if (!state.canCompose || state.isSending || state.docId.isBlank()) return

        submitQuestionInternal(
            rawQuestion = failedMsg.text,
            clientMessageId = failedMsg.clientMessageId,
            existingFailedMessageId = failedMsg.id
        )
    }

    fun onCitationClicked(page: Int) {
        _uiState.update { it.copy(lastSelectedCitationPage = page) }
    }

    fun dismissErrorBanner() {
        _uiState.update { it.copy(errorState = null) }
    }

    private fun submitQuestionInternal(
        rawQuestion: String,
        clientMessageId: String,
        existingFailedMessageId: String?
    ) {
        val current = _uiState.value
        val requestUid = current.activeUid
        val requestDocId = current.docId
        val requestEpoch = sessionEpoch
        val messageId = existingFailedMessageId ?: "user_$clientMessageId"

        val updatedMessages = if (existingFailedMessageId != null) {
            current.messages.map { msg ->
                if (msg.id == existingFailedMessageId) {
                    msg.copy(deliveryState = AiMessageDeliveryState.SENDING)
                } else {
                    msg
                }
            }
        } else {
            current.messages + AiChatMessage(
                id = messageId,
                clientMessageId = clientMessageId,
                role = AiChatRole.USER,
                text = rawQuestion,
                deliveryState = AiMessageDeliveryState.SENDING
            )
        }

        _uiState.update {
            it.copy(
                messages = updatedMessages,
                inputText = if (existingFailedMessageId == null) "" else it.inputText,
                inputError = null,
                isSending = true,
                errorState = null
            )
        }

        activeRequestJob = viewModelScope.launch(mainDispatcher) {
            val result = aiRepository.askQuestion(
                uid = requestUid,
                request = AiChatRequest(
                    docId = requestDocId,
                    question = rawQuestion,
                    clientMessageId = clientMessageId
                )
            )

            // Bỏ qua kết quả nếu đã đổi UID, đổi tài liệu hoặc đăng xuất trong lúc chờ
            val latest = _uiState.value
            if (requestEpoch != sessionEpoch ||
                latest.activeUid != requestUid ||
                latest.docId != requestDocId
            ) {
                return@launch
            }

            result.fold(
                onSuccess = { response ->
                    val assistantMessage = AiChatMessage(
                        id = "ai_${response.requestId}",
                        clientMessageId = clientMessageId,
                        role = AiChatRole.ASSISTANT,
                        text = response.answer,
                        citations = response.citations,
                        requestId = response.requestId,
                        deliveryState = AiMessageDeliveryState.SENT
                    )
                    _uiState.update { state ->
                        val finalized = state.messages.map { msg ->
                            if (msg.id == messageId) {
                                msg.copy(deliveryState = AiMessageDeliveryState.SENT)
                            } else {
                                msg
                            }
                        } + assistantMessage
                        state.copy(
                            messages = finalized,
                            isSending = false,
                            errorState = null
                        )
                    }
                },
                onFailure = { error ->
                    val mappedError = mapFailureToState(error, clientMessageId)
                    val shouldLock = mappedError is AiChatErrorState.Forbidden
                    _uiState.update { state ->
                        val markedFailed = state.messages.map { msg ->
                            if (msg.id == messageId) {
                                msg.copy(deliveryState = AiMessageDeliveryState.FAILED)
                            } else {
                                msg
                            }
                        }
                        state.copy(
                            messages = markedFailed,
                            isSending = false,
                            isAccessLocked = state.isAccessLocked || shouldLock,
                            errorState = mappedError
                        )
                    }
                }
            )
        }
    }

    private fun mapFailureToState(
        error: Throwable,
        clientMessageId: String
    ): AiChatErrorState {
        return when (error) {
            is AiChatFailure.Unauthorized -> AiChatErrorState.Unauthorized(
                error.message ?: "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại."
            )

            is AiChatFailure.Forbidden -> AiChatErrorState.Forbidden(
                error.message ?: "Bạn chưa mua tài liệu này nên không thể hỏi AI."
            )

            is AiChatFailure.QuotaExceeded -> AiChatErrorState.QuotaExceeded(
                error.message ?: "Bạn đã hết lượt hỏi AI trong phiên."
            )

            is TimeoutException -> AiChatErrorState.Transient(
                message = "Quá thời gian chờ phản hồi từ AI. Bạn có thể bấm Thử lại.",
                failedClientMessageId = clientMessageId
            )

            is IOException -> AiChatErrorState.Transient(
                message = "Mất kết nối mạng. Kiểm tra Internet rồi bấm Thử lại.",
                failedClientMessageId = clientMessageId
            )

            else -> AiChatErrorState.Transient(
                message = error.message ?: "Trợ lý AI tạm thời gián đoạn. Vui lòng thử lại.",
                failedClientMessageId = clientMessageId
            )
        }
    }
}
