package vn.unishare.feature.profile.ui

import vn.unishare.data.remote.readAtMost

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.unishare.core.ui.theme.UniAppMode
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class BuyerProfileRoleContract(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val apiBaseUrl: String =
        "https://asia-southeast1-unishare-db44a.cloudfunctions.net/api"
) : SellerRoleContract {

    init {
        val url = URL(apiBaseUrl)
        require(
            url.protocol == "https" &&
                url.host == "asia-southeast1-unishare-db44a.cloudfunctions.net" &&
                url.path == "/api" &&
                url.query == null
        ) {
            "Invalid Seller API endpoint"
        }
    }

    override suspend fun getSellerPermission(
        uid: String
    ): Result<SellerPermissionSnapshot> = try {
        check(auth.currentUser?.uid == uid) {
            "Phiên đăng nhập không hợp lệ."
        }

        val snapshot = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        check(snapshot.exists()) {
            "Chưa tìm thấy hồ sơ người dùng."
        }

        Result.success(
            SellerPermissionSnapshot(
                uid = uid,
                sellerEnabled =
                    snapshot.getBoolean("sellerEnabled") == true,
                lastMode = UniAppMode.BUYER
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun updateLastMode(
        uid: String,
        mode: UniAppMode
    ): Result<Unit> {
        return if (auth.currentUser?.uid == uid) {
            // UI mode only. No Firebase permission mutation.
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException("Phiên đăng nhập không hợp lệ.")
            )
        }
    }

    override suspend fun requestSellerEnrollment(
        uid: String,
        confirmTerms: Boolean
    ): Result<SellerPermissionSnapshot> {
        if (!confirmTerms) {
            return Result.failure(
                IllegalArgumentException(
                    "Bạn cần xác nhận điều khoản Người bán."
                )
            )
        }

        try {
            val user = auth.currentUser
                ?: return Result.failure(
                    IllegalStateException("Vui lòng đăng nhập.")
                )

            if (user.uid != uid) {
                return Result.failure(
                    IllegalStateException("Phiên đăng nhập đã thay đổi.")
                )
            }

            val token = user.getIdToken(false).await().token
                ?: return Result.failure(
                    IllegalStateException("Không thể xác thực tài khoản.")
                )

            val accepted = withContext(Dispatchers.IO) {
                val connection = URL(
                    "$apiBaseUrl/v1/seller/enroll"
                ).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
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
                        it.write(
                            """{"acceptTerms":true}"""
                                .toByteArray(Charsets.UTF_8)
                        )
                    }

                    when (val status = connection.responseCode) {
                        200 -> {
                            val contentType = connection.contentType
                                ?.substringBefore(';')
                                ?.trim()

                            if (contentType != "application/json") {
                                throw IOException(
                                    "Unexpected Seller response"
                                )
                            }

                            val bytes = connection.inputStream.use {
                                it.readAtMost(8193)
                            }

                            if (bytes.size > 8192) {
                                throw IOException(
                                    "Seller response too large"
                                )
                            }

                            val json = JSONObject(
                                String(bytes, Charsets.UTF_8)
                            )

                            check(
                                json.optBoolean("sellerEnabled") &&
                                json.optString("status") == "ACTIVE"
                            ) {
                                "Phản hồi Seller không hợp lệ."
                            }

                            true
                        }

                        400 -> throw IllegalStateException(
                            "Hãy hoàn thiện hồ sơ và xác nhận điều khoản."
                        )

                        401 -> throw IllegalStateException(
                            "Phiên đăng nhập hết hạn."
                        )

                        404 -> throw IllegalStateException(
                            "Chưa tìm thấy hồ sơ hoặc API chưa triển khai."
                        )

                        503 -> throw IllegalStateException(
                            "Dịch vụ Seller chưa sẵn sàng."
                        )

                        else -> throw IllegalStateException(
                            "Đăng ký Seller thất bại (HTTP $status)."
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }

            if (!accepted || auth.currentUser?.uid != uid) {
                return Result.failure(
                    IllegalStateException("Phiên đăng nhập đã thay đổi.")
                )
            }

            return Result.success(
                SellerPermissionSnapshot(
                    uid = uid,
                    sellerEnabled = true,
                    lastMode = UniAppMode.SELLER
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    override suspend fun getSellerPublicProfile(
        sellerId: String
    ): Result<SellerPublicProfile?> {
        if (!sellerId.matches(Regex("[A-Za-z0-9_-]{1,150}"))) {
            return Result.failure(
                IllegalArgumentException("Mã người bán không hợp lệ.")
            )
        }

        try {
            val token = auth.currentUser?.getIdToken(false)?.await()?.token
            val profile = withContext(Dispatchers.IO) {
                val connection = URL(
                    "$apiBaseUrl/v1/seller/$sellerId/public-profile"
                ).openConnection() as HttpURLConnection

                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 20000
                    connection.instanceFollowRedirects = false

                    if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty(
                        "Accept", "application/json"
                    )

                    when (val status = connection.responseCode) {
                        200 -> {
                            val contentType = connection.contentType
                                ?.substringBefore(';')
                                ?.trim()

                            check(contentType == "application/json") {
                                "Phản hồi hồ sơ không hợp lệ."
                            }

                            val bytes = connection.inputStream.use {
                                it.readAtMost(131073)
                            }

                            check(bytes.size <= 131072) {
                                "Dữ liệu hồ sơ vượt giới hạn."
                            }

                            val json = JSONObject(
                                String(bytes, Charsets.UTF_8)
                            )

                            check(json.optString("sellerId") == sellerId) {
                                "Mã người bán không khớp."
                            }

                            val items = json.optJSONArray("listings")
                                ?: throw IllegalStateException(
                                    "Danh sách tài liệu không hợp lệ."
                                )

                            check(items.length() <= 50) {
                                "Danh sách tài liệu vượt giới hạn."
                            }

                            val listings = (0 until items.length()).map { i ->
                                val item = items.getJSONObject(i)

                                val docId = item.getString("docId")
                                val title = item.getString("title")
                                val category = item.getString("categoryLabel")
                                val price = item.getInt("priceVnd")

                                check(
                                    docId.matches(
                                        Regex("[A-Za-z0-9_-]{1,150}")
                                    ) &&
                                    title.isNotBlank() &&
                                    price >= 0
                                ) {
                                    "Dữ liệu tài liệu công khai không hợp lệ."
                                }

                                SellerPublicListingItem(
                                    docId = docId,
                                    title = title,
                                    categoryLabel = category,
                                    priceVnd = price,
                                    rating = -1.0,
                                    reviewCount = -1,
                                    viewCount = -1,
                                    soldCount = -1
                                )
                            }

                            SellerPublicProfile(
                                sellerId = sellerId,
                                displayName = json.getString("displayName"),
                                school = json.optString("school"),
                                faculty = json.optString("faculty"),
                                bio = "",
                                verifiedBadge = "",
                                rating = -1.0,
                                reviewCount = -1,
                                totalDocuments = listings.size,
                                totalSold = -1,
                                joinedLabel = "",
                                achievements = emptyList(),
                                listings = listings
                            )
                        }

                        404 -> null

                        401 -> throw IllegalStateException(
                            "Phiên đăng nhập hết hạn."
                        )

                        400 -> throw IllegalArgumentException(
                            "Mã người bán không hợp lệ."
                        )

                        503 -> throw IllegalStateException(
                            "Dịch vụ hồ sơ tạm thời không khả dụng."
                        )

                        else -> throw IllegalStateException(
                            "Tải hồ sơ thất bại (HTTP $status)."
                        )
                    }
                } finally {
                    connection.disconnect()
                }
            }

            return Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}