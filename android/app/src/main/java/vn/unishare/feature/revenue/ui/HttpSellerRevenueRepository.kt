package vn.unishare.feature.revenue.ui

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
 * Production Seller revenue adapter.
 * Seller identity always comes from Firebase ID token on the backend.
 */
class HttpSellerRevenueRepository(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) : SellerRevenueRepository {

    init {
        require(
            apiBaseUrl.startsWith("https://") ||
                apiBaseUrl.startsWith("http://10.0.2.2:") ||
                apiBaseUrl.startsWith("http://127.0.0.1:")
        ) {
            "Insecure HTTP is only allowed for local testing"
        }
    }

    override suspend fun checkSellerRole(): Result<Boolean> = runCatching {
        try {
            // Existing protected Seller endpoint checks sellerEnabled.
            getJson("/v1/seller/documents")
            true
        } catch (error: ApiHttpException) {
            if (error.statusCode == 403) false
            else throw error
        }
    }

    override suspend fun getRevenueSummary(
        from: String?,
        to: String?
    ): Result<SellerRevenueSummaryDto> = runCatching {
        val params = mutableListOf<String>()

        from?.let {
            params.add("from=${Uri.encode(it)}")
        }

        to?.let {
            params.add("to=${Uri.encode(it)}")
        }

        val query = if (params.isEmpty()) {
            ""
        } else {
            "?" + params.joinToString("&")
        }

        val response = getJson("/v1/me/revenue$query")

        SellerRevenueSummaryDto(
            totalRevenueVnd = response.getLong("totalRevenueVnd"),
            paidOrderCount = response.getInt("paidOrderCount"),
            totalDocumentsSold = response.getInt("totalDocumentsSold")
        )
    }

    override suspend fun getSales(
        cursor: String?,
        limit: Int
    ): Result<SellerSalesPageDto> = runCatching {
        require(limit in 1..50)

        val path = buildString {
            append("/v1/me/sales?limit=")
            append(limit)

            if (cursor != null) {
                append("&cursor=")
                append(Uri.encode(cursor))
            }
        }

        val response = getJson(path)
        val array = response.getJSONArray("items")

        val items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val amount = item.getLong("amountVnd")
            val paidAt = item.getString("paidAt")

            SellerTransactionItemUiModel(
                orderId = item.getString("orderId"),
                docId = item.getString("docId"),
                documentTitle = item.getString("documentTitle"),
                amountVnd = amount,
                status = OrderStatus.PAID,
                paymentCode = item.getString("paymentCode"),
                paidAtUtc = paidAt,
                formattedDate = formatDate(paidAt, "dd/MM/yyyy"),
                formattedTime = formatDate(paidAt, "HH:mm"),
                formattedAmountVnd = "+" + formatMoney(amount)
            )
        }

        val nextCursor = response.optString("nextCursor")
            .takeIf { it.isNotBlank() && it != "null" }

        SellerSalesPageDto(items, nextCursor)
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

                val status = connection.responseCode

                val stream = if (status in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val body = stream?.bufferedReader(
                    Charsets.UTF_8
                )?.use { it.readText() }.orEmpty()

                if (status !in 200..299) {
                    throw ApiHttpException(status)
                }

                JSONObject(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun formatMoney(amount: Long): String =
        NumberFormat.getNumberInstance(
            Locale.forLanguageTag("vi-VN")
        ).format(amount) + " đ"

    private fun formatDate(
        iso: String,
        pattern: String
    ): String = try {
        DateTimeFormatter.ofPattern(pattern)
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .format(Instant.parse(iso))
    } catch (_: Exception) {
        iso
    }

    private class ApiHttpException(
        val statusCode: Int
    ) : IOException("Không tải được dữ liệu (HTTP $statusCode)")
}
