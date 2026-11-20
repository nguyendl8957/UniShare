package vn.unishare.data.repository

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import vn.unishare.domain.repository.PurchasedLibraryRepository
import vn.unishare.feature.library.ui.EntitlementStatus
import vn.unishare.feature.library.ui.LibraryRepository
import vn.unishare.feature.library.ui.PdfDocumentContent
import vn.unishare.feature.library.ui.PurchasedDocumentItem
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Bridge between A06 server-backed library and C06 UI.
 *
 * Only the server can determine purchased documents.
 * No fake entitlement or fake PDF content is returned.
 */
class RealLibraryRepository(
    private val auth: FirebaseAuth,
    private val source: PurchasedLibraryRepository
) : LibraryRepository {

    private val dateFormatter = DateTimeFormatter
        .ofPattern("dd/MM/yyyy")
        .withZone(ZoneId.of("Asia/Ho_Chi_Minh"))

    override suspend fun getPurchasedDocuments(
        uid: String
    ): Result<List<PurchasedDocumentItem>> {
        return try {
            require(uid.isNotBlank())

            if (auth.currentUser?.uid != uid) {
                throw SecurityException("Authentication session changed")
            }

            val result = mutableListOf<PurchasedDocumentItem>()
            val seenDocuments = mutableSetOf<String>()
            val seenCursors = mutableSetOf<String>()

            var cursor: String? = null

            // Prevent an unbounded pagination loop.
            for (pageIndex in 0 until 50) {
                if (auth.currentUser?.uid != uid) {
                    throw SecurityException("Authentication session changed")
                }

                val page = source.getPurchasedDocuments(
                    cursor = cursor,
                    limit = 50
                )

                page.items.forEach { document ->
                    if (seenDocuments.add(document.docId)) {
                        val versionNumber = document.versionId
                            .removePrefix("v")
                            .toIntOrNull()
                            ?: 1

                        val purchasedDate = document.purchasedAt
                            ?.let { value ->
                                try {
                                    "Đã mua " + dateFormatter.format(
                                        Instant.parse(value)
                                    )
                                } catch (_: Exception) {
                                    "Đã mua"
                                }
                            }
                            ?: "Đã mua"

                        result.add(
                            PurchasedDocumentItem(
                                docId = document.docId,
                                ownerUid = uid,
                                title = document.title,
                                categoryLabel = "Tài liệu đã mua",
                                authorName = "",
                                schoolName = "",
                                pageCount = 0,
                                versionNumber = versionNumber,
                                purchasedAtLabel = purchasedDate,
                                entitlementStatus = EntitlementStatus.ACTIVE,
                                isSellerArchived = document.isSellerArchived
                            )
                        )
                    }
                }

                val next = page.nextCursor

                if (next == null) {
                    if (auth.currentUser?.uid != uid) {
                        throw SecurityException("Authentication session changed")
                    }

                    return Result.success(result)
                }

                if (!seenCursors.add(next)) {
                    throw IOException("Repeated library pagination cursor")
                }

                cursor = next
            }

            throw IOException("Purchased library pagination limit exceeded")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun getDocumentContent(
        uid: String?,
        docId: String
    ): Result<PdfDocumentContent> {
        // C06.4 will use PdfReaderController and real Bitmap pages.
        // Never substitute fabricated chapter content for a real PDF.
        return Result.failure(
            UnsupportedOperationException(
                "Real PDF reader UI integration is required"
            )
        )
    }
}