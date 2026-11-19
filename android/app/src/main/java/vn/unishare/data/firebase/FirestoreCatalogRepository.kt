package vn.unishare.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import vn.unishare.domain.model.CatalogDocument
import vn.unishare.domain.repository.CatalogRepository
import vn.unishare.domain.repository.CatalogSearchPage
import vn.unishare.domain.repository.CatalogSearchRequest
import vn.unishare.domain.repository.CatalogSort
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

class FirestoreCatalogRepository(
    private val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()
) : CatalogRepository {

    private val catalog = firestore.collection("catalog")

    override suspend fun search(
        request: CatalogSearchRequest
    ): Result<CatalogSearchPage> = runCatching {

        // Lay them 1 ban ghi de biet con trang sau hay khong.
        // Firestore Rules gioi han limit <= 20.
        require(request.pageSize in 1..19) {
            "pageSize must be between 1 and 19"
        }

        require(
            request.minPriceVnd == null ||
                request.minPriceVnd >= 0L
        ) {
            "Invalid minimum price"
        }

        require(
            request.maxPriceVnd == null ||
                request.maxPriceVnd >= 0L
        ) {
            "Invalid maximum price"
        }

        if (
            request.minPriceVnd != null &&
            request.maxPriceVnd != null
        ) {
            require(request.minPriceVnd <= request.maxPriceVnd) {
                "Minimum price exceeds maximum price"
            }
        }

        val prefix = normalizeTitle(request.titlePrefix)

        val hasPriceRange =
            request.minPriceVnd != null ||
                request.maxPriceVnd != null

        // Firestore MVP: tim prefix khong ket hop voi
        // cac bo loc khac.
        if (prefix.isNotEmpty()) {
            require(
                request.categoryId == null &&
                    request.subjectId == null &&
                    !hasPriceRange &&
                    request.sort == CatalogSort.NEWEST
            ) {
                "Prefix search with filters is not supported"
            }
        }

        // Range theo gia phai sap xep theo gia.
        if (hasPriceRange) {
            require(request.sort != CatalogSort.NEWEST) {
                "Price range requires price sorting"
            }
        }

        var query: Query = catalog

        if (request.categoryId != null) {
            require(request.categoryId.isNotBlank())
            query = query.whereEqualTo(
                "categoryId",
                request.categoryId
            )
        }

        if (request.subjectId != null) {
            require(request.subjectId.isNotBlank())
            query = query.whereEqualTo(
                "subjectId",
                request.subjectId
            )
        }

        if (request.minPriceVnd != null) {
            query = query.whereGreaterThanOrEqualTo(
                "priceVnd",
                request.minPriceVnd
            )
        }

        if (request.maxPriceVnd != null) {
            query = query.whereLessThanOrEqualTo(
                "priceVnd",
                request.maxPriceVnd
            )
        }

        query = if (prefix.isNotEmpty()) {
            query
                .orderBy("titleNormalized")
                .orderBy(FieldPath.documentId())
                .startAt(prefix)
                .endAt(prefix + "\uf8ff")
        } else {
            when (request.sort) {
                CatalogSort.NEWEST ->
                    query
                        .orderBy(
                            "publishedAt",
                            Query.Direction.DESCENDING
                        )
                        .orderBy(FieldPath.documentId())

                CatalogSort.PRICE_ASC ->
                    query
                        .orderBy(
                            "priceVnd",
                            Query.Direction.ASCENDING
                        )
                        .orderBy(FieldPath.documentId())

                CatalogSort.PRICE_DESC ->
                    query
                        .orderBy(
                            "priceVnd",
                            Query.Direction.DESCENDING
                        )
                        .orderBy(FieldPath.documentId())
            }
        }

        val signature = querySignature(request, prefix)

        val cursor = request.cursor
        if (cursor != null) {
            val separator = cursor.indexOf(':')
            require(separator == 64) {
                "Invalid cursor format"
            }

            val cursorSignature = cursor.substring(0, separator)
            val cursorDocId = cursor.substring(separator + 1)

            require(
                cursorSignature == signature &&
                    cursorDocId.isNotBlank() &&
                    !cursorDocId.contains('/')
            ) {
                "Cursor does not match current query"
            }

            val cursorDoc = catalog
                .document(cursorDocId)
                .get()
                .await()

            require(cursorDoc.exists()) {
                "Cursor document no longer exists"
            }

            query = query.startAfter(cursorDoc)
        }

        val snapshot = query
            .limit(request.pageSize.toLong() + 1L)
            .get()
            .await()

        val hasNextPage =
            snapshot.documents.size > request.pageSize

        val pageDocs =
            snapshot.documents.take(request.pageSize)

        val nextCursor = if (hasNextPage) {
            pageDocs.lastOrNull()?.id?.let { docId ->
                "$signature:$docId"
            }
        } else {
            null
        }

        CatalogSearchPage(
            documents = pageDocs.map(::mapDocument),
            nextCursor = nextCursor
        )
    }

    override suspend fun getById(
        docId: String
    ): Result<CatalogDocument?> = runCatching {
        require(
            docId.isNotBlank() &&
                !docId.contains('/')
        ) {
            "Invalid document ID"
        }

        val snapshot = catalog
            .document(docId)
            .get()
            .await()

        if (snapshot.exists()) {
            mapDocument(snapshot)
        } else {
            null
        }
    }

    private fun mapDocument(
        doc: DocumentSnapshot
    ): CatalogDocument {
        val previewRefs = doc.get("previewRefs") as? List<*>
            ?: emptyList<Any>()

        require(previewRefs.all { it is String }) {
            "Invalid previewRefs for document ${doc.id}"
        }

        return CatalogDocument(
            docId = doc.id,
            title = requireNotNull(doc.getString("title")),
            titleNormalized = requireNotNull(
                doc.getString("titleNormalized")
            ),
            subjectId = requireNotNull(
                doc.getString("subjectId")
            ),
            categoryId = requireNotNull(
                doc.getString("categoryId")
            ),
            priceVnd = requireNotNull(
                doc.getLong("priceVnd")
            ),
            sellerId = requireNotNull(
                doc.getString("sellerId")
            ),
            previewRefs = previewRefs.filterIsInstance<String>(),
            publishedAtMillis = requireNotNull(
                doc.getTimestamp("publishedAt")
            ).toDate().time,
            ratingAvg = doc.getDouble("ratingAvg")
        )
    }

    private fun querySignature(
        request: CatalogSearchRequest,
        prefix: String
    ): String {
        val input = listOf(
            prefix,
            request.categoryId ?: "",
            request.subjectId ?: "",
            request.minPriceVnd?.toString() ?: "",
            request.maxPriceVnd?.toString() ?: "",
            request.sort.name,
            request.pageSize.toString()
        ).joinToString("\u001F")

        return MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }

    private fun normalizeTitle(value: String): String {
        val normalized = Normalizer.normalize(
            value.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        )

        return normalized
            .replace("\\p{M}+".toRegex(), "")
            .replace("đ", "d")
            .replace("\\s+".toRegex(), " ")
    }
}