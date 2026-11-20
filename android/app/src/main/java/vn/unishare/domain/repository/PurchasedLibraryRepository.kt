package vn.unishare.domain.repository

data class PurchasedDocument(
    val docId: String,
    val versionId: String,
    val title: String,
    val purchasedAt: String?,
    val isSellerArchived: Boolean
)

data class PurchasedLibraryPage(
    val items: List<PurchasedDocument>,
    val nextCursor: String?
)

/**
 * A06: Purchased documents belonging to the current Firebase user.
 *
 * No UID is accepted from the caller.
 * Authorization is enforced by the backend.
 */
interface PurchasedLibraryRepository {
    suspend fun getPurchasedDocuments(
        cursor: String? = null,
        limit: Int = 20
    ): PurchasedLibraryPage
}