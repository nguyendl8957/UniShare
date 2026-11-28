package vn.unishare.feature.ai.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import java.io.IOException
import java.util.concurrent.TimeoutException

const val MAX_AI_QUESTION_LENGTH = 500

data class AiCitation(
    val page: Int,
    val label: String = "Trang $page"
)

data class AiChatRequest(
    val docId: String,
    val question: String,
    val clientMessageId: String
)

data class AiChatResponse(
    val answer: String,
    val citations: List<AiCitation>,
    val requestId: String
)

data class AiQuickSuggestion(
    val id: String,
    val label: String,
    val prompt: String
)

enum class AiChatRole {
    USER,
    ASSISTANT
}

enum class AiMessageDeliveryState {
    SENDING,
    SENT,
    FAILED
}

data class AiChatMessage(
    val id: String,
    val clientMessageId: String,
    val role: AiChatRole,
    val text: String,
    val citations: List<AiCitation> = emptyList(),
    val requestId: String? = null,
    val deliveryState: AiMessageDeliveryState = AiMessageDeliveryState.SENT
)

sealed interface AiChatErrorState {
    data class Unauthorized(val message: String) : AiChatErrorState
    data class Forbidden(val message: String) : AiChatErrorState
    data class QuotaExceeded(val message: String) : AiChatErrorState
    data class Transient(val message: String, val failedClientMessageId: String?) : AiChatErrorState
}

sealed class AiChatFailure(message: String) : Exception(message) {
    class Unauthorized(message: String = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.") :
        AiChatFailure(message)

    class Forbidden(message: String = "Bạn chưa mua tài liệu này nên không thể sử dụng trợ lý AI.") :
        AiChatFailure(message)

    class QuotaExceeded(message: String = "Bạn đã hết lượt hỏi AI trong phiên. Vui lòng thử lại sau.") :
        AiChatFailure(message)

    class ServiceUnavailable(
        message: String = "Trợ lý AI tạm thời gián đoạn. Vui lòng thử lại sau.",
        val retryable: Boolean = true
    ) : AiChatFailure(message)
}

// State hội thoại AI chỉ lưu trong phiên hiện tại, không lưu xuống Firestore hay log
data class AiChatUiState(
    val isRestoringSession: Boolean = false,
    val isGuest: Boolean = true,
    val activeUid: String? = null,
    val docId: String = "",
    val documentTitle: String = "",
    val messages: List<AiChatMessage> = emptyList(),
    val inputText: String = "",
    val inputError: String? = null,
    val isSending: Boolean = false,
    val isAccessLocked: Boolean = false,
    val errorState: AiChatErrorState? = null,
    val lastSelectedCitationPage: Int? = null,
    val suggestions: List<AiQuickSuggestion> = defaultSuggestions()
) {
    val canCompose: Boolean
        get() = !isGuest && !isRestoringSession && !isAccessLocked

    val canSend: Boolean
        get() = canCompose && !isSending && inputText.trim().isNotEmpty()

    companion object {
        fun defaultSuggestions(): List<AiQuickSuggestion> = listOf(
            AiQuickSuggestion(
                id = "summary",
                label = "Tóm tắt tài liệu",
                prompt = "Hãy tóm tắt những ý chính quan trọng nhất của tài liệu này."
            ),
            AiQuickSuggestion(
                id = "example",
                label = "Cho ví dụ minh họa",
                prompt = "Hãy cho ví dụ minh họa cụ thể dựa trên nội dung tài liệu này."
            ),
            AiQuickSuggestion(
                id = "explain",
                label = "Giải thích chi tiết",
                prompt = "Hãy giải thích chi tiết các khái niệm và ví dụ cốt lõi trong tài liệu."
            ),
            AiQuickSuggestion(
                id = "quiz",
                label = "Tạo câu hỏi quiz",
                prompt = "Tạo giúp mình 3 câu hỏi trắc nghiệm ôn tập bám sát nội dung tài liệu."
            ),
            AiQuickSuggestion(
                id = "exam_tips",
                label = "Ý chính ôn thi",
                prompt = "Liệt kê các công thức, độ phức tạp và ý quan trọng hay ra thi trong tài liệu."
            )
        )
    }
}

// Hợp đồng gọi AI proxy theo docId và clientMessageId
interface DocumentAiRepository {
    suspend fun askQuestion(uid: String?, request: AiChatRequest): Result<AiChatResponse>
}

// Bộ giả lập AI proxy kiểm tra quyền theo UID/docId và mô phỏng các lỗi nghiệp vụ
class FakeDocumentAiRepository(
    var simulateOffline: Boolean = false,
    var simulateTimeout: Boolean = false,
    var simulateQuotaExceeded: Boolean = false,
    var simulateUnauthorized: Boolean = false,
    var artificialDelayMs: Long = 0L
) : DocumentAiRepository {

    val recordedRequests = mutableListOf<AiChatRequest>()

    private val ownedDocsByUid = mutableMapOf(
        UID_BUYER_ONE to mutableSetOf(DOC_DSA_BINARY_SEARCH, DOC_MACRO_ECON),
        UID_BUYER_TWO to mutableSetOf(DOC_OS_BUYER_TWO)
    )

    fun grantDocOwnership(uid: String, docId: String) {
        ownedDocsByUid.getOrPut(uid) { mutableSetOf() }.add(docId)
    }

    fun revokeDocOwnership(uid: String, docId: String) {
        ownedDocsByUid[uid]?.remove(docId)
    }

    override suspend fun askQuestion(
        uid: String?,
        request: AiChatRequest
    ): Result<AiChatResponse> {
        recordedRequests.add(request)
        if (artificialDelayMs > 0L) {
            delay(artificialDelayMs)
        }
        if (simulateOffline) {
            return Result.failure(IOException("Mất kết nối mạng khi gửi câu hỏi."))
        }
        if (simulateTimeout) {
            return Result.failure(TimeoutException("Hết thời gian chờ phản hồi từ trợ lý AI."))
        }
        if (uid.isNullOrBlank() || simulateUnauthorized) {
            return Result.failure(AiChatFailure.Unauthorized())
        }
        val isOwned = ownedDocsByUid[uid]?.contains(request.docId) == true
        if (!isOwned) {
            return Result.failure(AiChatFailure.Forbidden())
        }
        if (simulateQuotaExceeded) {
            return Result.failure(AiChatFailure.QuotaExceeded())
        }

        val normalized = request.question.lowercase()
        val (answer, citations) = when {
            normalized.contains("tóm tắt") -> {
                buildSummaryReply(request.docId)
            }

            normalized.contains("ví dụ") || normalized.contains("minh họa") -> {
                buildExampleReply(request.docId)
            }

            normalized.contains("quiz") || normalized.contains("trắc nghiệm") -> {
                buildQuizReply(request.docId)
            }

            normalized.contains("ôn thi") || normalized.contains("công thức") -> {
                buildExamTipsReply(request.docId)
            }

            else -> {
                buildDetailedReply(request.docId, request.question)
            }
        }

        return Result.success(
            AiChatResponse(
                answer = answer,
                citations = citations,
                requestId = "req_${request.clientMessageId}"
            )
        )
    }

    private fun buildSummaryReply(docId: String): Pair<String, List<AiCitation>> {
        return if (docId == DOC_MACRO_ECON) {
            "Tài liệu tóm tắt 3 nội dung trọng tâm: cách đo lường GDP danh nghĩa/GDP thực, công thức tính CPI và lạm phát, cùng công cụ chính sách tài khóa – tiền tệ." to
                listOf(AiCitation(1), AiCitation(2), AiCitation(3))
        } else {
            "Tài liệu trình bày nguyên lý chia đôi không gian tìm kiếm trên mảng đã sắp xếp, cách viết Binary Search tránh tràn số int 32-bit, kỹ thuật Lower/Upper Bound và tìm kiếm trên tập đáp án." to
                listOf(AiCitation(1), AiCitation(2))
        }
    }

    private fun buildQuizReply(docId: String): Pair<String, List<AiCitation>> {
        return if (docId == DOC_MACRO_ECON) {
            "Câu hỏi ôn tập:\n1. Thành phần nào không nằm trong công thức GDP = C + I + G + (X - M)?\n2. Khi ngân hàng trung ương tăng tỷ lệ dự trữ bắt buộc, cung tiền thay đổi thế nào?" to
                listOf(AiCitation(1), AiCitation(3))
        } else {
            "Câu hỏi ôn tập:\n1. Điều kiện tiên quyết để áp dụng Binary Search trên mảng là gì?\n2. Vì sao nên tính mid = left + (right - left) / 2 thay vì (left + right) / 2?\n3. Độ phức tạp thời gian trung bình của Binary Search là bao nhiêu?" to
                listOf(AiCitation(1), AiCitation(3))
        }
    }

    private fun buildExampleReply(docId: String): Pair<String, List<AiCitation>> {
        return if (docId == DOC_MACRO_ECON) {
            "Ví dụ minh họa từ tài liệu:\nGiả sử năm gốc giá 1 chiếc bánh là 10.000đ, năm nay sản xuất 100 chiếc giá 12.000đ → GDP danh nghĩa = 1.200.000đ, còn GDP thực = 1.000.000đ." to
                listOf(AiCitation(1))
        } else {
            "Ví dụ minh họa chạy tay Binary Search:\nCho mảng [2, 5, 8, 12, 16, 23, 38, 56, 72] và cần tìm x = 30.\n• Bước 1: phần tử giữa là 23 (< 30) → bỏ nửa trái, tìm tiếp bên phải [38, 56, 72].\n• Bước 2: giữa là 56 (> 30) → thu về [38], sau đó kết luận không tìm thấy." to
                listOf(AiCitation(1))
        }
    }

    private fun buildExamTipsReply(docId: String): Pair<String, List<AiCitation>> {
        return if (docId == DOC_MACRO_ECON) {
            "Trọng tâm ôn thi:\n• GDP = C + I + G + (X - M)\n• Tỷ lệ lạm phát = ((CPI_t - CPI_{t-1}) / CPI_{t-1}) * 100%\n• Phân biệt chính sách tài khóa (G, T) và tiền tệ (lãi suất, dự trữ bắt buộc)." to
                listOf(AiCitation(1), AiCitation(2), AiCitation(3))
        } else {
            "Trọng tâm ôn thi:\n• Điều kiện bắt buộc: mảng phải được sắp xếp.\n• Độ phức tạp: tốt nhất O(1), trung bình/xấu nhất O(log n).\n• Luôn viết mid = left + (right - left) / 2 để tránh tràn số int." to
                listOf(AiCitation(1), AiCitation(3))
        }
    }

    private fun buildDetailedReply(
        docId: String,
        question: String
    ): Pair<String, List<AiCitation>> {
        return if (docId == DOC_MACRO_ECON) {
            "Dựa trên tài liệu Kinh tế vĩ mô cho câu hỏi \"$question\": GDP thực loại bỏ biến động giá bằng năm gốc, còn lạm phát được đo qua mức tăng phần trăm của chỉ số CPI giữa hai kỳ." to
                listOf(AiCitation(1), AiCitation(2))
        } else {
            "Giải đáp dựa trên tài liệu Tìm kiếm nhị phân cho câu hỏi \"$question\": Mỗi bước so sánh phần tử giữa arr[mid] với giá trị cần tìm để loại bỏ một nửa mảng, giúp đạt độ phức tạp O(log n) và tránh tràn số khi cộng chỉ số." to
                listOf(AiCitation(1), AiCitation(2))
        }
    }

    companion object {
        const val UID_BUYER_ONE = "uid_buyer_01"
        const val UID_BUYER_TWO = "uid_buyer_02"
        const val DOC_DSA_BINARY_SEARCH = "doc_dsa_binary_search"
        const val DOC_MACRO_ECON = "doc_macro_econ"
        const val DOC_OS_BUYER_TWO = "doc_os_buyer_02"
        const val DOC_UNPAID_SAMPLE = "doc_unpaid_sample"
    }
}

class FakeAuthRepoForAi(
    private val backingSession: MutableStateFlow<AuthSession>
) : AuthRepository {
    override val session: StateFlow<AuthSession> = backingSession
    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> {
        backingSession.value = AuthSession.Guest
        return Result.success(Unit)
    }
}
