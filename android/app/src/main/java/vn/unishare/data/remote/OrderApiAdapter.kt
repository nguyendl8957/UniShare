package vn.unishare.data.remote

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class OrderTransferInstructions(
    val bankName: String,
    val bankCode: String,
    val accountNumber: String,
    val accountHolder: String,
    val amountVnd: Long,
    val paymentCode: String
)

data class RemoteOrder(
    val orderId: String,
    val docId: String,
    val documentTitle: String,
    val amountVnd: Long,
    val currency: String,
    val status: String,
    val paymentCode: String,
    val createdAt: String,
    val expiresAt: String,
    val transferInstructions: OrderTransferInstructions?
)

class OrderApiException(
    val httpStatus: Int,
    val errorCode: String,
    message: String
) : IOException(message)

class OrderApiAdapter(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) {
    init {
        require(
            apiBaseUrl.startsWith("https://") ||
                apiBaseUrl.startsWith("http://10.0.2.2:") ||
                apiBaseUrl.startsWith("http://127.0.0.1:")
        ) {
            "HTTP is allowed only for local Emulator testing"
        }
    }

    suspend fun createOrGetOrder(
        docId: String,
        idempotencyKey: String
    ): RemoteOrder {
        require(docId.matches(Regex("[a-zA-Z0-9_-]{1,150}"))) {
            "Invalid document ID"
        }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= 128) {
            "Invalid idempotency key"
        }

        val body = JSONObject().put("docId", docId)

        return requestOrder(
            method = "POST",
            path = "/v1/orders",
            body = body,
            idempotencyKey = idempotencyKey
        )
    }

    suspend fun getOrderStatus(orderId: String): RemoteOrder {
        require(orderId.matches(Regex("ord_[a-f0-9]{16}"))) {
            "Invalid order ID"
        }

        return requestOrder(
            method = "GET",
            path = "/v1/orders/$orderId"
        )
    }

    private suspend fun requestOrder(
        method: String,
        path: String,
        body: JSONObject? = null,
        idempotencyKey: String? = null
    ): RemoteOrder {
        val user = auth.currentUser
            ?: throw OrderApiException(
                401,
                "UNAUTHENTICATED",
                "Sign in required"
            )

        val token = user.getIdToken(false).await().token
            ?: throw OrderApiException(
                401,
                "UNAUTHENTICATED",
                "Missing Firebase ID token"
            )

        return withContext(Dispatchers.IO) {
            val connection = URL(
                apiBaseUrl.trimEnd('/') + path
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = method
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
                connection.setRequestProperty(
                    "Accept",
                    "application/json"
                )

                if (idempotencyKey != null) {
                    connection.setRequestProperty(
                        "Idempotency-Key",
                        idempotencyKey
                    )
                }

                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/json; charset=UTF-8"
                    )

                    connection.outputStream.use { output ->
                        output.write(
                            body.toString().toByteArray(Charsets.UTF_8)
                        )
                    }
                }

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseText = stream?.bufferedReader(
                    Charsets.UTF_8
                )?.use { it.readText() }.orEmpty()

                val response = try {
                    JSONObject(responseText)
                } catch (_: Exception) {
                    throw IOException(
                        "Invalid JSON response from order API"
                    )
                }

                if (status !in 200..299) {
                    val error = response.optJSONObject("error")

                    throw OrderApiException(
                        httpStatus = status,
                        errorCode = error?.optString("code")
                            ?: "UNKNOWN_ERROR",
                        message = error?.optString("message")
                            ?: "Order request failed"
                    )
                }

                parseOrder(response.getJSONObject("order"))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseOrder(json: JSONObject): RemoteOrder {
        val rawTransfer = json.optJSONObject(
            "transferInstructions"
        )

        val transfer = rawTransfer?.let {
            OrderTransferInstructions(
                bankName = it.getString("bankName"),
                bankCode = it.getString("bankCode"),
                accountNumber = it.getString("accountNumber"),
                accountHolder = it.getString("accountHolder"),
                amountVnd = it.getLong("amountVnd"),
                paymentCode = it.getString("paymentCode")
            )
        }

        val status = json.getString("status")

        require(
            status in setOf(
                "PENDING",
                "PAID",
                "EXPIRED",
                "REVIEW_REQUIRED"
            )
        ) {
            "Unknown server order status"
        }

        val amountVnd = json.getLong("amountVnd")
        require(amountVnd > 0L) {
            "Invalid server order amount"
        }

        if (transfer != null) {
            require(status == "PENDING") {
                "Transfer instructions on non-pending order"
            }
            require(
                transfer.amountVnd == amountVnd &&
                    transfer.paymentCode == json.getString("paymentCode")
            ) {
                "Transfer instructions do not match order"
            }
        }

        return RemoteOrder(
            orderId = json.getString("orderId"),
            docId = json.getString("docId"),
            documentTitle = json.getString("documentTitle"),
            amountVnd = amountVnd,
            currency = json.getString("currency"),
            status = status,
            paymentCode = json.getString("paymentCode"),
            createdAt = json.getString("createdAt"),
            expiresAt = json.getString("expiresAt"),
            transferInstructions = transfer
        )
    }
}
