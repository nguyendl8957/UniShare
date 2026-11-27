package vn.unishare.feature.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.domain.repository.AuthSession
import vn.unishare.feature.ai.ui.AiChatErrorState
import vn.unishare.feature.ai.ui.AiChatRequest
import vn.unishare.feature.ai.ui.AiChatResponse
import vn.unishare.feature.ai.ui.AiChatRole
import vn.unishare.feature.ai.ui.AiChatViewModel
import vn.unishare.feature.ai.ui.AiCitation
import vn.unishare.feature.ai.ui.AiMessageDeliveryState
import vn.unishare.feature.ai.ui.DocumentAiRepository
import vn.unishare.feature.ai.ui.FakeAuthRepoForAi
import vn.unishare.feature.ai.ui.FakeDocumentAiRepository
import vn.unishare.feature.ai.ui.MAX_AI_QUESTION_LENGTH

class AiChatViewModelTest {

    @Test
    fun sendQuestion_validOwnedDoc_recordsDocIdAndClientMessageId_andAppendsCitations() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository()
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
            documentTitle = "Tìm kiếm nhị phân"
        )
        viewModel.onInputChanged("Giải thích độ phức tạp của thuật toán này")
        viewModel.sendQuestion()

        assertEquals(1, fakeAiRepo.recordedRequests.size)
        val req = fakeAiRepo.recordedRequests.first()
        assertEquals(FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH, req.docId)
        assertTrue(req.clientMessageId.isNotBlank())

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals("", state.inputText)
        assertEquals(2, state.messages.size)
        assertEquals(AiChatRole.USER, state.messages[0].role)
        assertEquals(AiChatRole.ASSISTANT, state.messages[1].role)
        assertTrue(state.messages[1].citations.isNotEmpty())
    }

    @Test
    fun sendQuestion_emptyOrTooLongInput_blocksSubmitWithoutCallingRepo() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository()
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
            documentTitle = "Tìm kiếm nhị phân"
        )

        viewModel.onInputChanged("   ")
        viewModel.sendQuestion()
        assertTrue(fakeAiRepo.recordedRequests.isEmpty())
        assertNotNull(viewModel.uiState.value.inputError)

        val tooLong = "a".repeat(MAX_AI_QUESTION_LENGTH + 1)
        viewModel.onInputChanged(tooLong)
        viewModel.sendQuestion()
        assertTrue(fakeAiRepo.recordedRequests.isEmpty())
        assertNotNull(viewModel.uiState.value.inputError)
    }

    @Test
    fun sendQuestion_doubleClickWhileSending_doesNotSendDuplicateRequest() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val pendingResponse = CompletableDeferred<Result<AiChatResponse>>()
        val recorded = mutableListOf<AiChatRequest>()
        val controlledRepo = object : DocumentAiRepository {
            override suspend fun askQuestion(
                uid: String?,
                request: AiChatRequest
            ): Result<AiChatResponse> {
                recorded.add(request)
                return pendingResponse.await()
            }
        }
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = controlledRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
            documentTitle = "Tìm kiếm nhị phân"
        )
        viewModel.onInputChanged("Tóm tắt chương 3")

        // Bấm gửi hai lần liên tiếp khi yêu cầu đầu chưa trả về
        viewModel.sendQuestion()
        viewModel.sendQuestion()

        assertTrue(viewModel.uiState.value.isSending)
        assertEquals(1, recorded.size)

        pendingResponse.complete(
            Result.success(
                AiChatResponse(
                    answer = "Đây là bản tóm tắt chương 3.",
                    citations = listOf(AiCitation(1)),
                    requestId = "req_once"
                )
            )
        )

        assertFalse(viewModel.uiState.value.isSending)
        assertEquals(1, recorded.size)
        assertEquals(2, viewModel.uiState.value.messages.size)
    }

    @Test
    fun errorHandling_timeoutOfflineRetrySameClientId_403LocksAnd429Quota() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        val fakeAiRepo = FakeDocumentAiRepository(simulateTimeout = true)
        val viewModel = AiChatViewModel(
            authRepository = FakeAuthRepoForAi(sessionFlow),
            aiRepository = fakeAiRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
            documentTitle = "Tìm kiếm nhị phân"
        )
        viewModel.onInputChanged("Ví dụ tránh tràn số là gì?")
        viewModel.sendQuestion()

        assertTrue(viewModel.uiState.value.errorState is AiChatErrorState.Transient)
        assertEquals(
            AiMessageDeliveryState.FAILED,
            viewModel.uiState.value.messages.last().deliveryState
        )
        val firstClientId = fakeAiRepo.recordedRequests.first().clientMessageId

        // Thử lại sau khi hết timeout -> giữ nguyên clientMessageId và nhận phản hồi thành công
        fakeAiRepo.simulateTimeout = false
        viewModel.retryLastFailedMessage()

        assertEquals(2, fakeAiRepo.recordedRequests.size)
        assertEquals(firstClientId, fakeAiRepo.recordedRequests[1].clientMessageId)
        assertEquals(null, viewModel.uiState.value.errorState)
        assertEquals(2, viewModel.uiState.value.messages.size)

        // Lỗi 429 hết quota
        fakeAiRepo.simulateQuotaExceeded = true
        viewModel.onInputChanged("Tạo thêm quiz")
        viewModel.sendQuestion()
        assertTrue(viewModel.uiState.value.errorState is AiChatErrorState.QuotaExceeded)

        // Tài liệu chưa mua (403) -> khóa luồng nhập câu hỏi
        fakeAiRepo.simulateQuotaExceeded = false
        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_UNPAID_SAMPLE,
            documentTitle = "Tài liệu chưa mua"
        )
        viewModel.onInputChanged("Tóm tắt tài liệu này")
        viewModel.sendQuestion()
        assertTrue(viewModel.uiState.value.errorState is AiChatErrorState.Forbidden)
        assertTrue(viewModel.uiState.value.isAccessLocked)
    }

    @Test
    fun switchUidOrDocOrLogout_discardsInFlightAnswerAndClearsHistory() = runBlocking {
        val sessionFlow = MutableStateFlow<AuthSession>(
            AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_ONE)
        )
        var pendingFirstDoc = CompletableDeferred<Result<AiChatResponse>>()
        var pendingFirstUid = CompletableDeferred<Result<AiChatResponse>>()
        var callCount = 0
        val controlledRepo = object : DocumentAiRepository {
            override suspend fun askQuestion(
                uid: String?,
                request: AiChatRequest
            ): Result<AiChatResponse> {
                callCount++
                return if (callCount == 1) pendingFirstDoc.await() else pendingFirstUid.await()
            }
        }
        val authRepo = FakeAuthRepoForAi(sessionFlow)
        val viewModel = AiChatViewModel(
            authRepository = authRepo,
            aiRepository = controlledRepo,
            mainDispatcher = Dispatchers.Unconfined
        )

        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_DSA_BINARY_SEARCH,
            documentTitle = "Tìm kiếm nhị phân"
        )
        viewModel.onInputChanged("Câu hỏi cho tài liệu 1")
        viewModel.sendQuestion()

        // Đổi sang tài liệu khác giữa chừng -> kết quả của tài liệu 1 phải bị bỏ qua
        viewModel.bindDocument(
            docId = FakeDocumentAiRepository.DOC_MACRO_ECON,
            documentTitle = "Kinh tế vĩ mô"
        )
        pendingFirstDoc.complete(
            Result.success(
                AiChatResponse("Trả lời tài liệu 1", listOf(AiCitation(1)), "req_1")
            )
        )
        assertTrue(viewModel.uiState.value.messages.isEmpty())

        // Đổi sang UID khác giữa chừng -> xóa sạch lịch sử cũ và bỏ kết quả cũ
        viewModel.onInputChanged("Câu hỏi của Buyer 1")
        viewModel.sendQuestion()
        sessionFlow.value = AuthSession.SignedIn(FakeDocumentAiRepository.UID_BUYER_TWO)
        pendingFirstUid.complete(
            Result.success(
                AiChatResponse("Trả lời của Buyer 1", listOf(AiCitation(1)), "req_2")
            )
        )
        assertEquals(FakeDocumentAiRepository.UID_BUYER_TWO, viewModel.uiState.value.activeUid)
        assertTrue(viewModel.uiState.value.messages.isEmpty())

        // Đăng xuất về Guest -> xóa sạch hội thoại trong phiên
        authRepo.signOut()
        assertTrue(viewModel.uiState.value.isGuest)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }
}
