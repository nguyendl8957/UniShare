package vn.unishare.feature.explore.ui

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * C07: Public Firestore catalog adapter.
 *
 * Only reads backend-managed catalog documents.
 * Reads at most 20 documents because Firestore Rules require it.
 * Filtering and pagination apply ONLY to these fetched documents.
 *
 * No mock sellers, review ratings, or sales numbers.
 */
class FirestoreExploreCatalogRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ExploreCatalogRepository {

    private suspend fun fetchWindow(): List<ExploreDocumentItem> {
        val snapshot = db.collection("catalog")
            .limit(20)
            .get()
            .await()

        return snapshot.documents.mapNotNull(::toItem)
    }

    private fun readPreviewRefs(doc: DocumentSnapshot): List<PreviewImageRef> {
        val rawPaths = doc.get("previewRefs") as? List<*> ?: return emptyList()

        val validPath = Regex(
            "^previews/${Regex.escape(doc.id)}/[A-Za-z0-9_-]+/[1-5]\\.(png|jpg|jpeg)$"
        )

        return rawPaths
            .filterIsInstance<String>()
            .distinct()
            .filter { validPath.matches(it) }
            .take(5)
            .mapIndexed { index, path ->
                PreviewImageRef(
                    id = path,
                    caption = "Trang xem trước ${index + 1}",
                    imageUrl = "storage://$path",
                    pageNumber = index + 1
                )
            }
    }
    private fun toItem(doc: DocumentSnapshot): ExploreDocumentItem? {
        val title = doc.getString("title")?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null

        val priceLong = doc.getLong("priceVnd") ?: return null
        if (priceLong < 0 || priceLong > Int.MAX_VALUE) return null

        val categoryId = doc.getString("categoryId")
        val subjectId = doc.getString("subjectId")

        val category = ExploreCategory.entries.firstOrNull {
            it.id == categoryId
        } ?: ExploreCategory.OTHER

        val subject = ExploreSubject.entries.firstOrNull {
            it.id == subjectId
        } ?: ExploreSubject.OTHER

        val sellerId = doc.getString("sellerId").orEmpty()

        val seller = SellerPublicSummary(
            sellerId = sellerId,
            displayName = "Người bán chưa cập nhật",
            badge = null,
            school = "Chưa cập nhật",
            rating = 0.0,
            reviewCount = 0,
            soldCount = 0
        )

        val titleNorm = doc.getString("titleNormalized")
            ?: normalizeTitlePrefix(title)

        val published = doc.getTimestamp("publishedAt")
            ?.toDate()?.time ?: 0L

        return ExploreDocumentItem(
            docId = doc.id,
            title = title,
            titleNormalized = titleNorm,
            shortSubtitle = subject.label,
            category = category,
            subject = subject,
            // Type not currently stored in public catalog.
            docType = ExploreDocType.ALL,
            priceVnd = priceLong.toInt(),
            rating = 0.0,
            reviewCount = 0,
            createdAtEpochMs = published,
            seller = seller,
            coverPreview = readPreviewRefs(doc).firstOrNull()
                ?: PreviewImageRef(
                    id = "preview_${doc.id}",
                    caption = "Chưa có ảnh xem trước",
                    imageUrl = "",
                    pageNumber = 1,
                    isBroken = true
                ),
            isRecommended = false
        )
    }

    override suspend fun fetchRecommended(
        limit: Int
    ): Result<List<ExploreDocumentItem>> = runCatching {
        // Recommendations do not yet exist in the backend.
        // Do not invent a recommendation ranking.
        emptyList()
    }

    override suspend fun queryCatalog(
        query: CatalogQuery
    ): Result<CatalogPage> = runCatching {
        val prefix = normalizeTitlePrefix(query.normalizedPrefix)

        val filtered = fetchWindow().filter { item ->
            val words = item.titleNormalized
                .split(Regex("[\\s(),-]+"))

            val matchesSearch = prefix.isBlank() ||
                item.titleNormalized.startsWith(prefix) ||
                words.any { it.startsWith(prefix) }

            val filter = query.filter

            matchesSearch &&
                (filter.category == ExploreCategory.ALL ||
                    filter.category == item.category) &&
                (filter.subject == ExploreSubject.ALL ||
                    filter.subject == item.subject) &&
                (filter.school.isNullOrBlank() ||
                    filter.school == item.seller.school) &&
                item.priceVnd >= filter.minPriceVnd &&
                item.priceVnd <= filter.maxPriceVnd &&
                (filter.minSellerRating == null ||
                    item.rating >= filter.minSellerRating) &&
                (filter.docType == ExploreDocType.ALL ||
                    filter.docType == item.docType)
        }

        val sorted = when (query.sortOption) {
            ExploreSortOption.PRICE_ASC ->
                filtered.sortedWith(
                    compareBy<ExploreDocumentItem> { it.priceVnd }
                        .thenBy { it.docId }
                )
            ExploreSortOption.NEWEST ->
                filtered.sortedWith(
                    compareByDescending<ExploreDocumentItem> {
                        it.createdAtEpochMs
                    }.thenBy { it.docId }
                )
            ExploreSortOption.HIGHEST_RATED ->
                // No real rating data yet; retain stable newest order.
                filtered.sortedWith(
                    compareByDescending<ExploreDocumentItem> {
                        it.createdAtEpochMs
                    }.thenBy { it.docId }
                )
        }

        val offset = query.cursor
            ?.removePrefix("offset:")
            ?.toIntOrNull()
            ?.coerceAtLeast(0) ?: 0

        val size = query.pageSize.coerceIn(1, 20)
        val page = sorted.drop(offset).take(size)
        val end = offset + page.size

        CatalogPage(
            items = page,
            totalCount = sorted.size,
            nextCursor = if (end < sorted.size) {
                "offset:$end"
            } else {
                null
            }
        )
    }

    override suspend fun getDocumentDetail(
        docId: String
    ): Result<DocumentPublicDetail?> = runCatching {
        if (!Regex("^[A-Za-z0-9_-]{1,150}$").matches(docId)) {
            return@runCatching null
        }

        val snapshot = db.collection("catalog")
            .document(docId)
            .get()
            .await()

        if (!snapshot.exists()) return@runCatching null

        val item = toItem(snapshot) ?: return@runCatching null

        DocumentPublicDetail(
            docId = item.docId,
            title = item.title,
            description = "Chưa có mô tả công khai.",
            category = item.category,
            subject = item.subject,
            docType = item.docType,
            priceVnd = item.priceVnd,
            rating = 0.0,
            reviewCount = 0,
            tags = emptyList(),
            seller = item.seller,
            // Private PDF is never exposed. Public previews
            // require a separate rules-aware image loader.
            previewRefs = readPreviewRefs(snapshot)
        )
    }
}