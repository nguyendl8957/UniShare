package vn.unishare.feature.ai.ui

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.unishare.data.remote.readAtMost
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android adapter for A08.
 *
 * Never uploads PDF bytes or a client-selected UID.
 * The backend derives UID from the Firebase ID token.
 */
class HttpDocumentAiRepository(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) : DocumentAiRepository {

    companion object {
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private val DOC_ID = Regex("^[A-Za-z0-9_-]{1,150}$")
        private val MESSAGE_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
    }

    init {
        require(
            apiBaseUrl.startsWith("https://") &&
                URL(apiBaseUrl).host ==
                    "asia-southeast1-unishare-db44a.cloudfunctions.net" &&
                URL(apiBaseUrl).path == "/api"
        ) {
            "Invalid production API endpoint"
        }
    }

    override suspend fun askQuestion(
        uid: String?,
        request: AiChatRequest
    ): Result<AiChatResponse> {
        try {
            val user = auth.currentUser
                ?: return Result.failure(AiChatFailure.Unauthorized())

            if (uid.isNullOrBlank() || uid != user.uid) {
                return Result.failure(AiChatFailure.Unauthorized())
            }

            if (
                !DOC_ID.matches(request.docId) ||
                !MESSAGE_ID.matches(request.clientMessageId) ||
                request.question.isBlank() ||
                request.question.length > MAX_AI_QUESTION_LENGTH
            ) {
                return Result.failure(
                    IllegalArgumentException("Câu hỏi hoặc mã tài liệu không hợp lệ.")
                )
            }

            val token = user.getIdToken(false).await().token
                ?: return Result.failure(AiChatFailure.Unauthorized())

            val payload = JSONObject().apply {
                put("docId", request.docId)
                put("question", request.question)
                put("clientMessageId", request.clientMessageId)
            }.toString()

            val response = withContext(Dispatchers.IO) {
                val connection = URL(
                    apiBaseUrl + "/v1/ai/chat"
                ).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 65_000
                    connection.instanceFollowRedirects = false
                    connection.doOutput = true

                    connection.setRequestProperty(
                        "Authorization", "Bearer $token"
                    )
                    connection.setRequestProperty(
                        "Content-Type", "application/json; charset=utf-8"
                    )
                    connection.setRequestProperty(
                        "Accept", "application/json"
                    )

                    connection.outputStream.use {
                        it.write(payload.toByteArray(Charsets.UTF_8))
                    }

                    val status = connection.responseCode

                    if (status != 200) {
                        val errorCode = connection.errorStream?.use { stream ->
                            val error = String(stream.readAtMost(4096), Charsets.UTF_8)
                            runCatching { JSONObject(error).optJSONObject("error")?.optString("code") }.getOrNull()
                        }
                        throw mapAiHttpStatus(status, errorCode)
                    }

                    val contentType = connection.contentType
                        ?.substringBefore(';')?.trim()

                    if (contentType != "application/json") {
                        throw AiChatFailure.ServiceUnavailable("Phản hồi từ AI không hợp lệ. Vui lòng thử lại sau.")
                    }

                    val output = ByteArrayOutputStream()

                    connection.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break

                            if (output.size() + count > MAX_RESPONSE_BYTES) {
                                throw AiChatFailure.ServiceUnavailable("Phản hồi từ AI vượt giới hạn. Hãy thử câu hỏi ngắn hơn.", retryable = false)
                            }

                            output.write(buffer, 0, count)
                        }
                    }

                    val body = JSONObject(
                        output.toString("UTF-8")
                    )

                    val answer = body.getString("answer")
                    val requestId = body.getString("requestId")
                    val citationsJson = body.getJSONArray("citations")

                    if (
                        answer.isBlank() ||
                        answer.length > 12000 ||
                        !MESSAGE_ID.matches(requestId) ||
                        citationsJson.length() > 20
                    ) {
                        throw AiChatFailure.ServiceUnavailable("Phản hồi từ AI không hợp lệ. Vui lòng thử lại sau.")
                    }

                    val citations = buildList {
                        for (index in 0 until citationsJson.length()) {
                            val page = citationsJson
                                .getJSONObject(index)
                                .getInt("page")

                            if (page !in 1..10000) {
                                throw AiChatFailure.ServiceUnavailable("Không xác minh được trang trích dẫn của AI.")
                            }

                            add(AiCitation(page = page))
                        }
                    }

                    AiChatResponse(
                        answer = answer,
                        citations = citations,
                        requestId = requestId
                    )
                } finally {
                    connection.disconnect()
                }
            }

            // Do not return a private answer after an account switch.
            if (auth.currentUser?.uid != uid) {
                return Result.failure(AiChatFailure.Unauthorized())
            }

            return Result.success(response)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }
}
/**
 * Pure HTTP error mapping, independently unit-testable.
 * Does not access Firebase, network or private document data.
 */
internal fun mapAiHttpStatus(status: Int, code: String? = null): Exception {
    val detail = when (code) {
        "AI_DISABLED" -> "Trợ lý AI chưa được bật."
        "AI_NOT_ALLOWED" -> "Tài khoản của bạn chưa được cấp quyền dùng AI."
        "AI_UNCONFIGURED" -> "Trợ lý AI chưa hoàn tất cấu hình. Vui lòng liên hệ hỗ trợ."
        "AI_RESULT_UNKNOWN" -> "Chưa xác nhận được kết quả. Bạn có thể gửi câu hỏi mới."
        "PDF_TEXT_UNAVAILABLE" -> "PDF này không có văn bản trích xuất được để hỏi AI."
        "PDF_TOO_LARGE" -> "Tệp PDF vượt giới hạn 2 MiB dành cho AI."
        "PDF_TOO_MANY_PAGES" -> "Tệp PDF vượt giới hạn 20 trang dành cho AI."
        "INVALID_PDF" -> "AI chưa đọc được tệp PDF này."
        else -> null
    }
    if (detail != null) return AiChatFailure.ServiceUnavailable(detail, retryable = false)
    return when (code) {
        "GEMINI_RATE_LIMIT" -> AiChatFailure.ServiceUnavailable("Dịch vụ AI đang giới hạn số yêu cầu. Vui lòng thử lại sau.")
        "GEMINI_TIMEOUT" -> AiChatFailure.ServiceUnavailable("Gemini phản hồi quá thời gian chờ. Bạn có thể kiểm tra lại yêu cầu.")
        "GEMINI_UNAVAILABLE" -> AiChatFailure.ServiceUnavailable("Chưa kết nối được với Gemini. Vui lòng thử lại sau.")
        "GEMINI_RESPONSE_INVALID" -> AiChatFailure.ServiceUnavailable("Phản hồi Gemini chưa hoàn chỉnh. Bạn có thể gửi câu hỏi mới.", retryable = false)
        "PDF_SOURCE_UNAVAILABLE" -> AiChatFailure.ServiceUnavailable("Chưa tải được PDF để hỏi AI. Vui lòng thử lại.")
        else -> when (status) {
            401 -> AiChatFailure.Unauthorized()
            403 -> AiChatFailure.Forbidden()
            429 -> AiChatFailure.QuotaExceeded()
            409 -> AiChatFailure.ServiceUnavailable("Câu hỏi đang được xử lý. Vui lòng thử lại sau.")
            400, 404, 422 -> IllegalArgumentException("Không thể xử lý câu hỏi hoặc tài liệu này.")
            504 -> AiChatFailure.ServiceUnavailable("Máy chủ chưa phản hồi kịp. Bạn có thể kiểm tra lại yêu cầu.")
            else -> AiChatFailure.ServiceUnavailable()
        }
    }
}