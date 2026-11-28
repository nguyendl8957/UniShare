package vn.unishare.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class PdfApiException(
    val httpStatus: Int,
    message: String
) : IOException(message)

class PurchasedPdfApi(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) {
    companion object {
        const val MAX_PDF_BYTES = 25 * 1024 * 1024
        private val DOCUMENT_ID_PATTERN =
            Regex("[a-zA-Z0-9_-]{1,150}")
    }

    init {
        require(
            apiBaseUrl.startsWith("https://") ||
                apiBaseUrl.startsWith("http://10.0.2.2:") ||
                apiBaseUrl.startsWith("http://127.0.0.1:")
        ) {
            "HTTP is allowed only for local Emulator testing"
        }
    }

    suspend fun downloadPurchasedPdf(docId: String): ByteArray {
        require(DOCUMENT_ID_PATTERN.matches(docId)) {
            "Invalid document ID"
        }

        val user = auth.currentUser
            ?: throw PdfApiException(
                401,
                "Authentication required"
            )

        val token = user.getIdToken(false).await().token
            ?: throw PdfApiException(
                401,
                "Missing authentication token"
            )

        return withContext(Dispatchers.IO) {
            val connection = URL(
                apiBaseUrl.trimEnd('/') +
                    "/v1/documents/$docId/content"
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/pdf"
                )

                val status = connection.responseCode

                if (status !in 200..299) {
                    throw PdfApiException(
                        status,
                        when (status) {
                            401 -> "Authentication required"
                            403 -> "Document access denied"
                            404 -> "PDF version unavailable"
                            else -> "Unable to download PDF"
                        }
                    )
                }

                if (status != 200) {
                    throw IOException(
                        "Unsupported PDF response status"
                    )
                }

                val contentType = connection.contentType
                    ?.substringBefore(';')
                    ?.trim()

                if (contentType != "application/pdf") {
                    throw IOException(
                        "Unexpected PDF content type"
                    )
                }

                val declaredSize = connection.contentLengthLong

                if (
                    declaredSize <= 0L ||
                    declaredSize > MAX_PDF_BYTES
                ) {
                    throw IOException(
                        "Invalid PDF content length"
                    )
                }

                val bytes = ByteArrayOutputStream()

                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0L

                    while (true) {
                        val count = input.read(buffer)

                        if (count == -1) break

                        total += count

                        if (total > MAX_PDF_BYTES) {
                            throw IOException(
                                "PDF exceeds maximum size"
                            )
                        }

                        bytes.write(buffer, 0, count)
                    }
                }

                val result = bytes.toByteArray()

                if (
                    result.size.toLong() != declaredSize ||
                    !hasPdfHeader(result)
                ) {
                    throw IOException(
                        "Invalid PDF response"
                    )
                }

                result
            } finally {
                connection.disconnect()
            }
        }
    }
}