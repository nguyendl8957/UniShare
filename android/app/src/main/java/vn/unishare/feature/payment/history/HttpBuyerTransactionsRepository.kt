package vn.unishare.feature.payment.history

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.unishare.feature.payment.ui.OrderStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Real Buyer transaction history.
 * UID is derived by backend from Firebase ID token.
 * Never reads Firestore orders directly from Android.
 */
class HttpBuyerTransactionsRepository(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) : BuyerTransactionsRepository {

    init {
        require(
            apiBaseUrl.startsWith("https://") ||
                apiBaseUrl.startsWith("http://10.0.2.2:") ||
                apiBaseUrl.startsWith("http://127.0.0.1:")
        ) {
            "HTTP allowed only for local Emulator testing"
        }
    }

    override suspend fun getMyTransactions(
        cursor: String?,
        limit: Int
    ): Result<BuyerTransactionsPage> = runCatching {
        require(limit in 1..50)

        val path = buildString {
            append("/v1/me/orders?limit=")
            append(limit)

            if (cursor != null) {
                append("&cursor=")
                append(Uri.encode(cursor))
            }
        }

        val response = getJson(path)
        val itemsJson = response.getJSONArray("items")

        val items = (0 until itemsJson.length()).map { index ->
            val item = itemsJson.getJSONObject(index)
            val amount = item.getLong("amountVnd")
            val createdAt = item.getString("createdAt")

            BuyerTransactionItemUiModel(
                orderId = item.getString("orderId"),
                docId = item.getString("docId"),
                documentTitle = item.getString("documentTitle"),
                amountVnd = amount,
                status = parseStatus(item.getString("status")),
                paymentCode = item.getString("paymentCode"),
                createdAtUtc = createdAt,
                formattedDate = formatDate(createdAt, "dd/MM/yyyy"),
                formattedTime = formatDate(createdAt, "HH:mm"),
                formattedAmountVnd = formatMoney(amount)
            )
        }

        val nextCursor = response.optString("nextCursor")
            .takeIf { it.isNotBlank() && it != "null" }

        BuyerTransactionsPage(
            items = items,
            nextCursor = nextCursor
        )
    }

    override suspend fun getTransactionDetail(
        orderId: String
    ): Result<BuyerTransactionDetailUiModel> = runCatching {
        require(orderId.matches(Regex("ord_[a-f0-9]{16}"))) {
            "Invalid order ID"
        }

        val response = getJson("/v1/orders/$orderId")
        val order = response.getJSONObject("order")

        val amount = order.getLong("amountVnd")
        val createdAt = order.getString("createdAt")
        val expiresAt = order.getString("expiresAt")

        BuyerTransactionDetailUiModel(
            orderId = order.getString("orderId"),
            docId = order.getString("docId"),
            documentTitle = order.getString("documentTitle"),
            amountVnd = amount,
            status = parseStatus(order.getString("status")),
            paymentCode = order.getString("paymentCode"),
            createdAtUtc = createdAt,
            expiresAtUtc = expiresAt,
            formattedCreatedAt = formatDate(createdAt, "dd/MM/yyyy HH:mm"),
            formattedExpiresAt = formatDate(expiresAt, "dd/MM/yyyy HH:mm"),
            formattedAmountVnd = formatMoney(amount)
        )
    }

    private suspend fun getJson(path: String): JSONObject {
        val user = auth.currentUser
            ?: throw IOException("Vui lòng đăng nhập")

        val token = user.getIdToken(false).await().token
            ?: throw IOException("Không lấy được phiên đăng nhập")

        return withContext(Dispatchers.IO) {
            val connection = URL(
                apiBaseUrl.trimEnd('/') + path
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
                    "application/json"
                )

                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val body = stream?.bufferedReader(
                    Charsets.UTF_8
                )?.use { it.readText() }.orEmpty()

                if (code !in 200..299) {
                    val errorMessage = try {
                        JSONObject(body)
                            .optJSONObject("error")
                            ?.optString("message")
                    } catch (_: Exception) {
                        null
                    }

                    throw IOException(
                        errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Không tải được giao dịch (HTTP $code)"
                    )
                }

                JSONObject(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseStatus(raw: String): OrderStatus {
        return when (raw) {
            "PENDING" -> OrderStatus.PENDING
            "PAID" -> OrderStatus.PAID
            "EXPIRED" -> OrderStatus.EXPIRED
            "REVIEW_REQUIRED" -> OrderStatus.REVIEW_REQUIRED
            else -> throw IOException("Trạng thái giao dịch không hợp lệ")
        }
    }

    private fun formatMoney(amount: Long): String {
        return NumberFormat.getNumberInstance(
            Locale("vi", "VN")
        ).format(amount) + " đ"
    }

    private fun formatDate(
        iso: String,
        pattern: String
    ): String {
        return try {
            DateTimeFormatter.ofPattern(pattern)
                .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(Instant.parse(iso))
        } catch (_: Exception) {
            iso
        }
    }
}
