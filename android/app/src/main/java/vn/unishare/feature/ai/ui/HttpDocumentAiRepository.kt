package vn.unishare.feature.ai.ui

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
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
                    connection.readTimeout = 45_000
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
                        throw mapAiHttpStatus(status)
                    }

                    val contentType = connection.contentType
                        ?.substringBefore(';')?.trim()

                    if (contentType != "application/json") {
                        throw IOException("Unexpected AI response type")
                    }

                    val output = ByteArrayOutputStream()

                    connection.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break

                            if (output.size() + count > MAX_RESPONSE_BYTES) {
                                throw IOException("AI response too large")
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
                        throw IOException("Invalid AI response")
                    }

                    val citations = buildList {
                        for (index in 0 until citationsJson.length()) {
                            val page = citationsJson
                                .getJSONObject(index)
                                .getInt("page")

                            if (page !in 1..10000) {
                                throw IOException("Invalid citation page")
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
internal fun mapAiHttpStatus(status: Int): Exception = when (status) {
    401 -> AiChatFailure.Unauthorized()
    403 -> AiChatFailure.Forbidden()
    429 -> AiChatFailure.QuotaExceeded()
    409 -> AiChatFailure.ServiceUnavailable(
        "Câu hỏi đang được xử lý. Vui lòng thử lại sau."
    )
    400, 404, 422 -> IllegalArgumentException(
        "Không thể xử lý câu hỏi hoặc tài liệu này."
    )
    else -> AiChatFailure.ServiceUnavailable()
}