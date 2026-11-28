package vn.unishare.feature.explore.ui

import android.os.SystemClock
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.Source
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * C07: Public Firestore catalog adapter.
 *
 * Only reads backend-managed catalog documents.
 * Each query page respects the 20-document limit in Firestore Rules.
 *
 * No mock sellers, review ratings, or sales numbers.
 */
class FirestoreExploreCatalogRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ExploreCatalogRepository {

    private var catalogSnapshot = emptyList<ExploreDocumentItem>()
    private var loadedAt: Long? = null
    private var lastQuery: CatalogQuery? = null
    private var lastQueryItems = emptyList<ExploreDocumentItem>()

    override fun invalidate() {
        loadedAt = null
        lastQuery = null
    }

    private suspend fun fetchCatalog(): List<ExploreDocumentItem> {
        if (loadedAt?.let { SystemClock.elapsedRealtime() - it < 30_000 } == true) {
            return catalogSnapshot
        }

        val base = db.collection("catalog").orderBy(FieldPath.documentId()).limit(20)
        val items = mutableListOf<ExploreDocumentItem>()
        var cursor: DocumentSnapshot? = null
        do {
            val page = (cursor?.let { base.startAfter(it) } ?: base).get(Source.SERVER).await()
            items.addAll(page.documents.mapNotNull(::toItem))
            cursor = page.documents.lastOrNull().takeIf { page.size() == 20 }
        } while (cursor != null)

        currentCoroutineContext().ensureActive()
        catalogSnapshot = items
        loadedAt = SystemClock.elapsedRealtime()
        return items
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
            displayName = "Người bán",
            badge = null,
            school = "Chưa cập nhật",
            rating = 0.0,
            reviewCount = 0,
            soldCount = 0
        )

        val titleNorm = normalizeTitlePrefix(title)

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
        val key = query.copy(cursor = null)
        val sorted = if (query.cursor != null && lastQuery == key) lastQueryItems else queryItems(query)
        if (query.cursor == null) {
            currentCoroutineContext().ensureActive()
            lastQuery = key
            lastQueryItems = sorted
        }
        val offset = query.cursor?.removePrefix("offset:")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val size = query.pageSize.coerceIn(1, 20)
        val items = sorted.drop(offset).take(size)
        val end = offset + items.size
        CatalogPage(items, sorted.size, if (end < sorted.size) "offset:$end" else null)
    }

    private suspend fun queryItems(query: CatalogQuery): List<ExploreDocumentItem> {
        val prefix = normalizeTitlePrefix(query.normalizedPrefix)
        val filtered = fetchCatalog().filter { item ->
            val searchable = "${item.titleNormalized} ${normalizeTitlePrefix(item.subject.label)}"
            val matchesSearch = prefix.isBlank() || searchable.contains(prefix)

            val filter = query.filter

            matchesSearch &&
                (filter.category == ExploreCategory.ALL ||
                    filter.category == item.category) &&
                (filter.subject == ExploreSubject.ALL ||
                    filter.subject == item.subject) &&
                (filter.school.isNullOrBlank() ||
                    filter.school == item.seller.school) &&
                item.priceVnd >= filter.minPriceVnd &&
                (filter.maxPriceVnd == DEFAULT_MAX_PRICE_VND || item.priceVnd <= filter.maxPriceVnd) &&
                (filter.minSellerRating == null ||
                    item.rating >= filter.minSellerRating) &&
                (filter.docType == ExploreDocType.ALL ||
                    filter.docType == item.docType)
        }

        return when (query.sortOption) {
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

    }

    override suspend fun getDocumentDetail(
        docId: String
    ): Result<DocumentPublicDetail?> = runCatching {
        if (!Regex("^[A-Za-z0-9_-]{1,150}$").matches(docId)) {
            return@runCatching null
        }

        val snapshot = db.collection("catalog")
            .document(docId)
            .get(Source.SERVER)
            .await()

        if (!snapshot.exists()) return@runCatching null

        val item = toItem(snapshot) ?: return@runCatching null

        DocumentPublicDetail(
            docId = item.docId,
            title = item.title,
            description = snapshot.getString("description")?.takeIf { it.isNotBlank() }
                ?: "Chưa có mô tả công khai.",
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