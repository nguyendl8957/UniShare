package vn.unishare.feature.profile.ui

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.unishare.feature.revenue.ui.SellerRevenueRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId

interface SellerDashboardRepository {
    suspend fun load(): Result<SellerDashboardSummary>
}

/**
 * Reads real Seller data. The backend identifies the Seller
 * exclusively from the authenticated Firebase ID token.
 */
class HttpSellerDashboardRepository(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String,
    private val revenue: SellerRevenueRepository
) : SellerDashboardRepository {

    override suspend fun load(): Result<SellerDashboardSummary> = runCatching {
        val today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
        val monthStart = today.withDayOfMonth(1)

        val monthly = revenue.getRevenueSummary(
            from = monthStart.toString()
        ).getOrThrow()

        val todaySummary = revenue.getRevenueSummary(
            from = today.toString(),
            to = today.plusDays(1).toString()
        ).getOrThrow()

        val recentSales = revenue.getSales(
            cursor = null,
            limit = 5
        ).getOrThrow()

        val publishedDocs = fetchPublishedDocuments()

        require(monthly.totalRevenueVnd in 0..Int.MAX_VALUE.toLong()) {
            "Doanh thu vuot gioi han hien thi"
        }

        SellerDashboardSummary(
            monthlyRevenueVnd = monthly.totalRevenueVnd.toInt(),
            revenueGrowthLabel = "",
            publishedDocCount = publishedDocs.size,
            todayOrderCount = todaySummary.paidOrderCount,
            orderGrowthLabel = "",
            myDocuments = publishedDocs.take(3),
            recentOrders = recentSales.items.map { sale ->
                SellerRecentOrderItem(
                    orderId = sale.orderId,
                    buyerDisplayName = "Nguoi mua",
                    documentTitle = sale.documentTitle,
                    timeAgoLabel = sale.formattedDate,
                    amountVnd = sale.amountVnd.toInt().also {
                        require(sale.amountVnd in 0..Int.MAX_VALUE.toLong())
                    },
                    isPaid = true
                )
            }
        )
    }

    private suspend fun fetchPublishedDocuments(): List<SellerPublicListingItem> {
        val user = auth.currentUser
            ?: throw IOException("Chua dang nhap")

        val token = user.getIdToken(false).await().token
            ?: throw IOException("Khong lay duoc phien dang nhap")

        return withContext(Dispatchers.IO) {
            val url = URL(
                apiBaseUrl.trimEnd('/') + "/v1/seller/documents"
            )

            val connection = url.openConnection() as HttpURLConnection

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
                if (status != 200) {
                    throw IOException("Seller documents HTTP $status")
                }

                val body = connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }

                val response = JSONObject(body)

                if (response.optBoolean("truncated", false)) {
                    throw IOException("Danh sach tai lieu vuot gioi han API")
                }

                val documents = response.getJSONArray("documents")
                val items = mutableListOf<SellerPublicListingItem>()

                for (i in 0 until documents.length()) {
                    val doc = documents.getJSONObject(i)

                    if (doc.optString("status") != "PUBLISHED") {
                        continue
                    }

                    val price = doc.getLong("priceVnd")
                    require(price in 0..Int.MAX_VALUE.toLong()) {
                        "Gia tai lieu khong hop le"
                    }

                    items.add(
                        SellerPublicListingItem(
                            docId = doc.getString("docId"),
                            title = doc.getString("title"),
                            categoryLabel = doc.optString("category")
                                .ifBlank { "Tài liệu" },
                            priceVnd = price.toInt(),
                            rating = 0.0,
                            reviewCount = 0,
                            viewCount = 0,
                            soldCount = 0
                        )
                    )
                }

                items
            } finally {
                connection.disconnect()
            }
        }
    }
}
