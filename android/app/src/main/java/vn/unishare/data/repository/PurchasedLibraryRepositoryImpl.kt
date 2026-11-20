package vn.unishare.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import vn.unishare.domain.repository.PurchasedDocument
import vn.unishare.domain.repository.PurchasedLibraryPage
import vn.unishare.domain.repository.PurchasedLibraryRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class PurchasedLibraryApiException(
    val httpStatus: Int,
    message: String
) : IOException(message)

class PurchasedLibraryRepositoryImpl(
    private val auth: FirebaseAuth,
    private val apiBaseUrl: String
) : PurchasedLibraryRepository {

    companion object {
        private const val MAX_RESPONSE_BYTES = 512 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]{1,150}")
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

    override suspend fun getPurchasedDocuments(
        cursor: String?,
        limit: Int
    ): PurchasedLibraryPage {
        require(limit in 1..50) {
            "Invalid page size"
        }

        require(cursor == null || ID_PATTERN.matches(cursor)) {
            "Invalid cursor"
        }

        val user = auth.currentUser
            ?: throw PurchasedLibraryApiException(
                401, "Authentication required"
            )

        val uid = user.uid

        val token = user.getIdToken(false).await().token
            ?: throw PurchasedLibraryApiException(
                401, "Missing authentication token"
            )

        val query = buildString {
            append("?limit=")
            append(limit)

            if (cursor != null) {
                append("&cursor=")
                append(
                    URLEncoder.encode(
                        cursor,
                        "UTF-8"
                    )
                )
            }
        }

        return withContext(Dispatchers.IO) {
            val connection = URL(
                apiBaseUrl.trimEnd('/') +
                    "/v1/me/library" + query
            ).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.instanceFollowRedirects = false

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
                    throw PurchasedLibraryApiException(
                        status,
                        when (status) {
                            401 -> "Authentication required"
                            403 -> "Access denied"
                            else -> "Unable to load purchased library"
                        }
                    )
                }

                val bytes = ByteArrayOutputStream()

                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)

                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break

                        if (bytes.size() + count > MAX_RESPONSE_BYTES) {
                            throw IOException(
                                "Library response exceeds size limit"
                            )
                        }

                        bytes.write(buffer, 0, count)
                    }
                }

                val json = JSONObject(
                    bytes.toString("UTF-8")
                )

                val array = json.getJSONArray("items")

                if (array.length() > limit) {
                    throw IOException("Invalid library page size")
                }

                val items = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)

                        val docId = item.getString("docId")
                        val versionId = item.getString("versionId")

                        if (
                            !ID_PATTERN.matches(docId) ||
                            !ID_PATTERN.matches(versionId)
                        ) {
                            throw IOException("Invalid document reference")
                        }

                        add(
                            PurchasedDocument(
                                docId = docId,
                                versionId = versionId,
                                title = item.getString("title"),
                                purchasedAt = item.optString(
                                    "purchasedAt", ""
                                ).ifBlank { null },
                                isSellerArchived = item.getBoolean(
                                    "isSellerArchived"
                                )
                            )
                        )
                    }
                }

                val nextCursor =
                    if (json.isNull("nextCursor")) {
                        null
                    } else {
                        json.getString("nextCursor")
                    }

                if (
                    nextCursor != null &&
                    !ID_PATTERN.matches(nextCursor)
                ) {
                    throw IOException("Invalid next cursor")
                }

                // Do not return one Buyer's data after an account switch.
                if (auth.currentUser?.uid != uid) {
                    throw PurchasedLibraryApiException(
                        401, "Authentication session changed"
                    )
                }

                PurchasedLibraryPage(
                    items = items,
                    nextCursor = nextCursor
                )
            } finally {
                connection.disconnect()
            }
        }
    }
}