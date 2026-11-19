
package vn.unishare.domain.model

/**
 * Du lieu tai lieu cong khai tu Firestore catalog/{docId}.
 *
 * Khong dua pdfPath, privatePdfPath hoac thong tin
 * thanh toan vao model nay.
 */
data class CatalogDocument(
    val docId: String,
    val title: String,
    val titleNormalized: String,
    val subjectId: String,
    val categoryId: String,
    val priceVnd: Long,
    val sellerId: String,
    val previewRefs: List<String>,
    val publishedAtMillis: Long,
    val ratingAvg: Double? = null
)
