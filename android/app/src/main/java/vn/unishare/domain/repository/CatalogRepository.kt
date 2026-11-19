package vn.unishare.domain.repository

import vn.unishare.domain.model.CatalogDocument

enum class CatalogSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC
}

data class CatalogSearchRequest(
    val titlePrefix: String = "",
    val categoryId: String? = null,
    val subjectId: String? = null,
    val minPriceVnd: Long? = null,
    val maxPriceVnd: Long? = null,
    val sort: CatalogSort = CatalogSort.NEWEST,
    val cursor: String? = null,
    val pageSize: Int = 10
)

data class CatalogSearchPage(
    val documents: List<CatalogDocument>,
    val nextCursor: String?
)

interface CatalogRepository {

    suspend fun search(
        request: CatalogSearchRequest
    ): Result<CatalogSearchPage>

    suspend fun getById(
        docId: String
    ): Result<CatalogDocument?>
}
