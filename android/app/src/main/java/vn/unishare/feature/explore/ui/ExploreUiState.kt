package vn.unishare.feature.explore.ui

import java.io.IOException
import java.text.Normalizer

const val DEFAULT_EXPLORE_PAGE_SIZE = 5
const val DEFAULT_MIN_PRICE_VND = 5_000
const val DEFAULT_MAX_PRICE_VND = 500_000

enum class ExploreCategory(val id: String, val label: String) {
    ALL("all", "Tất cả"),
    SUMMARY_NOTE("note", "Note tóm tắt"),
    CHEAT_SHEET("cheatsheet", "Cheat-sheet"),
    SOURCE_CODE("source_code", "Mã nguồn"),
    PROJECT("project", "Đồ án"),
    OTHER("other", "Khác")
}

enum class ExploreSubject(val id: String, val label: String) {
    ALL("all", "Tất cả"),
    PROGRAMMING("programming", "Lập trình"),
    DATABASE("database", "Cơ sở dữ liệu"),
    NETWORK("network", "Mạng máy tính"),
    OS("os", "Hệ điều hành"),
    DISCRETE_MATH("discrete_math", "Toán rời rạc"),
    ECONOMICS("economics", "Kinh tế"),
    OTHER("other", "Khác")
}

enum class ExploreDocType(val id: String, val label: String) {
    ALL("all", "Tất cả"),
    TEXTBOOK("textbook", "Giáo trình"),
    EXAM("exam", "Đề thi"),
    NOTES("notes", "Ghi chú"),
    EXERCISE("exercise", "Bài tập")
}

enum class ExploreSortOption(
    val id: String,
    val label: String,
    val description: String
) {
    PRICE_ASC(
        id = "price_asc",
        label = "Giá thấp → cao",
        description = "Hiển thị tài liệu có giá thấp nhất trước"
    ),
    NEWEST(
        id = "newest",
        label = "Mới nhất",
        description = "Hiển thị tài liệu mới đăng trước"
    ),
    HIGHEST_RATED(
        id = "highest_rated",
        label = "Đánh giá cao",
        description = "Hiển thị tài liệu có đánh giá cao nhất"
    )
}

enum class ExploreViewMode {
    HOME,
    SEARCH
}

data class ExploreFilter(
    val category: ExploreCategory = ExploreCategory.ALL,
    val subject: ExploreSubject = ExploreSubject.ALL,
    val school: String? = null,
    val minPriceVnd: Int = DEFAULT_MIN_PRICE_VND,
    val maxPriceVnd: Int = DEFAULT_MAX_PRICE_VND,
    val minSellerRating: Double? = null,
    val docType: ExploreDocType = ExploreDocType.ALL
) {
    val activeAdvancedFilterCount: Int
        get() {
            var count = 0
            if (subject != ExploreSubject.ALL) count++
            if (!school.isNullOrBlank()) count++
            if (minPriceVnd > DEFAULT_MIN_PRICE_VND || maxPriceVnd < DEFAULT_MAX_PRICE_VND) count++
            if (minSellerRating != null) count++
            if (docType != ExploreDocType.ALL) count++
            return count
        }

    val hasAnyFilter: Boolean
        get() = category != ExploreCategory.ALL || activeAdvancedFilterCount > 0

    companion object {
        val AVAILABLE_SCHOOLS = listOf(
            "ĐH Bách Khoa TP.HCM",
            "ĐH Giao thông Vận tải TP.HCM",
            "ĐH Khoa học Tự nhiên",
            "ĐH Công nghệ Thông tin",
            "ĐH Kinh tế TP.HCM"
        )

        val RATING_THRESHOLDS: List<Double?> = listOf(null, 5.0, 4.5, 4.0, 3.5)

        val SUGGESTED_TOPICS = listOf(
            "C++",
            "Kinh tế vĩ mô",
            "Python",
            "SQL",
            "Địa lí xã hội",
            "Toán rời rạc",
            "Marketing"
        )
    }
}

data class PreviewImageRef(
    val id: String,
    val caption: String,
    val imageUrl: String,
    val pageNumber: Int,
    val isBroken: Boolean = false
)

data class SellerPublicSummary(
    val sellerId: String,
    val displayName: String,
    val badge: String? = "A+",
    val school: String,
    val rating: Double,
    val reviewCount: Int,
    val soldCount: Int
)

data class ExploreDocumentItem(
    val docId: String,
    val title: String,
    val titleNormalized: String,
    val shortSubtitle: String,
    val category: ExploreCategory,
    val subject: ExploreSubject,
    val docType: ExploreDocType,
    val priceVnd: Int,
    val formatLabel: String = "PDF",
    val rating: Double,
    val reviewCount: Int,
    val createdAtEpochMs: Long,
    val seller: SellerPublicSummary,
    val coverPreview: PreviewImageRef,
    val isRecommended: Boolean = false
)

data class DocumentPublicDetail(
    val docId: String,
    val title: String,
    val description: String,
    val category: ExploreCategory,
    val subject: ExploreSubject,
    val docType: ExploreDocType,
    val priceVnd: Int,
    val formatLabel: String = "PDF",
    val rating: Double,
    val reviewCount: Int,
    val tags: List<String>,
    val seller: SellerPublicSummary,
    val previewRefs: List<PreviewImageRef>,
    val isArchived: Boolean = false
)

data class CatalogQuery(
    val normalizedPrefix: String = "",
    val filter: ExploreFilter = ExploreFilter(),
    val sortOption: ExploreSortOption = ExploreSortOption.NEWEST,
    val cursor: String? = null,
    val pageSize: Int = DEFAULT_EXPLORE_PAGE_SIZE
)

data class CatalogPage(
    val items: List<ExploreDocumentItem>,
    val totalCount: Int,
    val nextCursor: String?
)

sealed interface ExploreListState {
    data object Initial : ExploreListState
    data object Loading : ExploreListState
    data class Content(
        val items: List<ExploreDocumentItem>,
        val totalCount: Int,
        val nextCursor: String?,
        val isLoadingMore: Boolean = false,
        val loadMoreError: String? = null
    ) : ExploreListState

    data object Empty : ExploreListState
    data class Error(val message: String) : ExploreListState
}

sealed interface DocumentDetailState {
    data object Idle : DocumentDetailState
    data object Loading : DocumentDetailState
    data class Content(
        val detail: DocumentPublicDetail,
        val activePreviewIndex: Int = 0,
        val brokenPreviewIds: Set<String> = emptySet(),
        val isFavorite: Boolean = false
    ) : DocumentDetailState

    data class NotFoundOrArchived(
        val docId: String,
        val message: String = "Tài liệu không tồn tại hoặc đã bị người bán ẩn khỏi danh mục."
    ) : DocumentDetailState

    data class Error(
        val docId: String,
        val message: String
    ) : DocumentDetailState
}

data class ExploreUiState(
    val viewMode: ExploreViewMode = ExploreViewMode.HOME,
    val query: String = "",
    val filter: ExploreFilter = ExploreFilter(),
    val draftFilter: ExploreFilter = ExploreFilter(),
    val sortOption: ExploreSortOption = ExploreSortOption.NEWEST,
    val recommendedItems: List<ExploreDocumentItem> = emptyList(),
    val listState: ExploreListState = ExploreListState.Loading,
    val isFilterSheetVisible: Boolean = false,
    val isSortSheetVisible: Boolean = false,
    val selectedDocId: String? = null,
    val detailState: DocumentDetailState = DocumentDetailState.Idle,
    val isPreviewGalleryOpen: Boolean = false
)

fun normalizeTitlePrefix(raw: String): String {
    val trimmed = raw.trim().lowercase().replace(Regex("\\s+"), " ")
    if (trimmed.isEmpty()) return ""
    val decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
    return decomposed
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace('đ', 'd')
}

fun formatPriceVnd(amountVnd: Int): String {
    val raw = amountVnd.coerceAtLeast(0).toString()
    val reversed = raw.reversed()
    val grouped = StringBuilder()
    for (i in reversed.indices) {
        if (i > 0 && i % 3 == 0) grouped.append('.')
        grouped.append(reversed[i])
    }
    return "${grouped.reverse()}đ"
}

interface ExploreCatalogRepository {
    suspend fun fetchRecommended(limit: Int = 6): Result<List<ExploreDocumentItem>>
    suspend fun queryCatalog(query: CatalogQuery): Result<CatalogPage>
    suspend fun getDocumentDetail(docId: String): Result<DocumentPublicDetail?>
}

class FakeExploreCatalogRepository(
    var simulateOffline: Boolean = false,
    var simulateLoadMoreOffline: Boolean = false,
    private val customDocuments: List<SeedDocument>? = null
) : ExploreCatalogRepository {

    data class SeedDocument(
        val item: ExploreDocumentItem,
        val detail: DocumentPublicDetail,
        val searchKeywords: List<String> = emptyList()
    )

    private val catalog: List<SeedDocument> = customDocuments ?: defaultSeedCatalog()

    override suspend fun fetchRecommended(limit: Int): Result<List<ExploreDocumentItem>> {
        if (simulateOffline) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }
        val recommended = catalog
            .filter { !it.detail.isArchived && it.item.isRecommended }
            .map { it.item }
            .take(limit)
        return Result.success(recommended)
    }

    override suspend fun queryCatalog(query: CatalogQuery): Result<CatalogPage> {
        if (simulateOffline || (query.cursor != null && simulateLoadMoreOffline)) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }

        val normalizedQuery = normalizeTitlePrefix(query.normalizedPrefix)
        val filtered = catalog
            .filter { !it.detail.isArchived }
            .filter { seed ->
                matchesPrefix(seed, normalizedQuery) && matchesFilter(seed.item, query.filter)
            }

        val sorted = when (query.sortOption) {
            ExploreSortOption.PRICE_ASC -> filtered.sortedWith(
                compareBy<SeedDocument> { it.item.priceVnd }.thenBy { it.item.docId }
            )
            ExploreSortOption.NEWEST -> filtered.sortedWith(
                compareByDescending<SeedDocument> { it.item.createdAtEpochMs }.thenBy { it.item.docId }
            )
            ExploreSortOption.HIGHEST_RATED -> filtered.sortedWith(
                compareByDescending<SeedDocument> { it.item.rating }
                    .thenByDescending { it.item.reviewCount }
                    .thenBy { it.item.docId }
            )
        }

        val startIndex = query.cursor?.removePrefix("offset:")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val safePageSize = query.pageSize.coerceAtLeast(1)
        val pageSlice = sorted.drop(startIndex).take(safePageSize).map { it.item }
        val nextOffset = startIndex + pageSlice.size
        val nextCursor = if (nextOffset < sorted.size) "offset:$nextOffset" else null

        return Result.success(
            CatalogPage(
                items = pageSlice,
                totalCount = sorted.size,
                nextCursor = nextCursor
            )
        )
    }

    override suspend fun getDocumentDetail(docId: String): Result<DocumentPublicDetail?> {
        if (simulateOffline) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }
        val match = catalog.firstOrNull { it.detail.docId == docId }?.detail
        return Result.success(match)
    }

    private fun matchesPrefix(seed: SeedDocument, normalizedPrefix: String): Boolean {
        if (normalizedPrefix.isBlank()) return true
        val titleNorm = seed.item.titleNormalized
        if (titleNorm.startsWith(normalizedPrefix)) return true
        // Khớp theo tiền tố từng từ trong tiêu đề hoặc từ khóa môn học chuẩn hóa
        val titleTokens = titleNorm.split(" ", "(", ")", "-", ",", "→").filter { it.isNotBlank() }
        if (titleTokens.any { it.startsWith(normalizedPrefix) }) return true
        return seed.searchKeywords.any { normalizeTitlePrefix(it).startsWith(normalizedPrefix) }
    }

    private fun matchesFilter(item: ExploreDocumentItem, filter: ExploreFilter): Boolean {
        if (filter.category != ExploreCategory.ALL && item.category != filter.category) return false
        if (filter.subject != ExploreSubject.ALL && item.subject != filter.subject) return false
        if (!filter.school.isNullOrBlank() && item.seller.school != filter.school) return false
        if (item.priceVnd < filter.minPriceVnd) return false
        if (filter.maxPriceVnd < DEFAULT_MAX_PRICE_VND && item.priceVnd > filter.maxPriceVnd) return false
        if (filter.minSellerRating != null && item.seller.rating < filter.minSellerRating) return false
        if (filter.docType != ExploreDocType.ALL && item.docType != filter.docType) return false
        return true
    }

    companion object {
        fun defaultSeedCatalog(): List<SeedDocument> {
            val sellerHung = SellerPublicSummary(
                sellerId = "seller_hung_01",
                displayName = "Nguyễn Tuấn Hùng",
                badge = "TP",
                school = "ĐH Bách Khoa TP.HCM",
                rating = 4.9,
                reviewCount = 124,
                soldCount = 532
            )
            val sellerMai = SellerPublicSummary(
                sellerId = "seller_mai_02",
                displayName = "Phạm Thị Mai",
                badge = "A+",
                school = "ĐH Khoa học Tự nhiên",
                rating = 4.9,
                reviewCount = 156,
                soldCount = 410
            )
            val sellerNam = SellerPublicSummary(
                sellerId = "seller_nam_03",
                displayName = "Lê Hoàng Nam",
                badge = "A+",
                school = "ĐH Kinh tế TP.HCM",
                rating = 4.8,
                reviewCount = 102,
                soldCount = 290
            )
            val sellerMinh = SellerPublicSummary(
                sellerId = "seller_minh_04",
                displayName = "Lê Minh C",
                badge = "A+",
                school = "ĐH Công nghệ Thông tin",
                rating = 4.7,
                reviewCount = 78,
                soldCount = 195
            )
            val sellerAnh = SellerPublicSummary(
                sellerId = "seller_anh_05",
                displayName = "Hoàng Đức Anh",
                badge = "A+",
                school = "ĐH Giao thông Vận tải TP.HCM",
                rating = 4.7,
                reviewCount = 88,
                soldCount = 215
            )
            val sellerHang = SellerPublicSummary(
                sellerId = "seller_hang_06",
                displayName = "Nguyễn Thị Hằng",
                badge = "A+",
                school = "ĐH Giao thông Vận tải TP.HCM",
                rating = 4.6,
                reviewCount = 72,
                soldCount = 164
            )
            val sellerPhuc = SellerPublicSummary(
                sellerId = "seller_phuc_07",
                displayName = "Phạm Minh D",
                badge = "A+",
                school = "ĐH Bách Khoa TP.HCM",
                rating = 4.8,
                reviewCount = 85,
                soldCount = 240
            )

            fun buildSeed(
                docId: String,
                title: String,
                subtitle: String,
                description: String,
                category: ExploreCategory,
                subject: ExploreSubject,
                docType: ExploreDocType,
                priceVnd: Int,
                rating: Double,
                reviewCount: Int,
                createdAt: Long,
                seller: SellerPublicSummary,
                tags: List<String>,
                previews: List<PreviewImageRef>,
                recommended: Boolean = false,
                archived: Boolean = false
            ): SeedDocument {
                val item = ExploreDocumentItem(
                    docId = docId,
                    title = title,
                    titleNormalized = normalizeTitlePrefix(title),
                    shortSubtitle = subtitle,
                    category = category,
                    subject = subject,
                    docType = docType,
                    priceVnd = priceVnd,
                    rating = rating,
                    reviewCount = reviewCount,
                    createdAtEpochMs = createdAt,
                    seller = seller,
                    coverPreview = previews.first(),
                    isRecommended = recommended
                )
                val detail = DocumentPublicDetail(
                    docId = docId,
                    title = title,
                    description = description,
                    category = category,
                    subject = subject,
                    docType = docType,
                    priceVnd = priceVnd,
                    rating = rating,
                    reviewCount = reviewCount,
                    tags = tags,
                    seller = seller,
                    previewRefs = previews,
                    isArchived = archived
                )
                return SeedDocument(
                    item = item,
                    detail = detail,
                    searchKeywords = tags + listOf(subject.label, category.label)
                )
            }

            return listOf(
                buildSeed(
                    docId = "doc_cpp_01",
                    title = "Tổng hợp kiến thức Lập trình C++ (Cơ bản → Nâng cao)",
                    subtitle = "Lý thuyết, ví dụ minh họa & bài tập thực hành",
                    description = "Tài liệu tổng hợp đầy đủ kiến thức C++ từ cơ bản đến nâng cao, bao gồm lý thuyết, ví dụ minh họa và bài tập. Phù hợp cho sinh viên năm 1, 2, 3 và người tự học. Đặc biệt hữu ích cho kỳ thi và các dự án lập trình.",
                    category = ExploreCategory.SUMMARY_NOTE,
                    subject = ExploreSubject.PROGRAMMING,
                    docType = ExploreDocType.NOTES,
                    priceVnd = 25_000,
                    rating = 4.9,
                    reviewCount = 124,
                    createdAt = 1_760_000_012_000L,
                    seller = sellerHung,
                    tags = listOf("C++", "Lập trình", "Cơ bản", "Nâng cao", "Code mẫu"),
                    previews = listOf(
                        PreviewImageRef("cpp_p1", "1. C++ cơ bản", "fake://preview/cpp_1", 1),
                        PreviewImageRef("cpp_p2", "2. Hàm & con trỏ", "fake://preview/cpp_2", 2),
                        PreviewImageRef("cpp_p3", "3. Con trỏ", "fake://preview/cpp_3", 3),
                        PreviewImageRef("cpp_p4", "4. Bài tập", "fake://preview/cpp_4", 4),
                        PreviewImageRef("cpp_p5", "5. Đề ôn tập", "fake://preview/cpp_5", 5)
                    ),
                    recommended = true
                ),
                buildSeed(
                    docId = "doc_math_02",
                    title = "Cheat-sheet Toán cao cấp (Đại số tuyến tính)",
                    subtitle = "Tóm tắt ma trận, định thức & không gian vector",
                    description = "Bảng tổng hợp công thức Đại số tuyến tính ngắn gọn trong 8 trang, có mẹo giải nhanh bài thi trắc nghiệm và tự luận.",
                    category = ExploreCategory.CHEAT_SHEET,
                    subject = ExploreSubject.DISCRETE_MATH,
                    docType = ExploreDocType.NOTES,
                    priceVnd = 15_000,
                    rating = 4.8,
                    reviewCount = 96,
                    createdAt = 1_760_000_011_000L,
                    seller = sellerMai,
                    tags = listOf("Toán cao cấp", "Đại số tuyến tính", "Cheat-sheet"),
                    previews = listOf(
                        PreviewImageRef("math_p1", "1. Ma trận & định thức", "fake://preview/math_1", 1),
                        PreviewImageRef("math_p2", "2. Hệ phương trình", "fake://preview/math_2", 2),
                        PreviewImageRef("math_p3", "3. Trị riêng & vector riêng", "fake://preview/math_3", 3)
                    ),
                    recommended = true
                ),
                buildSeed(
                    docId = "doc_web_03",
                    title = "Source code Web Fullstack (React + Node)",
                    subtitle = "Dự án quản lý khóa học + API REST chuẩn",
                    description = "Bộ mã nguồn mẫu React kết hợp Node.js Express có phân quyền cơ bản, tài liệu cài đặt từng bước và sơ đồ cơ sở dữ liệu.",
                    category = ExploreCategory.SOURCE_CODE,
                    subject = ExploreSubject.PROGRAMMING,
                    docType = ExploreDocType.EXERCISE,
                    priceVnd = 40_000,
                    rating = 4.7,
                    reviewCount = 78,
                    createdAt = 1_760_000_010_000L,
                    seller = sellerMinh,
                    tags = listOf("React", "Node", "Web", "Lập trình"),
                    previews = listOf(
                        PreviewImageRef("web_p1", "1. Kiến trúc thư mục", "fake://preview/web_1", 1),
                        PreviewImageRef("web_p2", "2. Giao diện trang chủ", "fake://preview/web_2", 2),
                        PreviewImageRef("web_p3", "3. Thiết kế API", "fake://preview/web_3", 3)
                    ),
                    recommended = true
                ),
                buildSeed(
                    docId = "doc_econ_04",
                    title = "Note tóm tắt môn Kinh tế vi mô",
                    subtitle = "Tóm tắt lý thuyết + sơ đồ tư duy",
                    description = "Ghi chú môn Kinh tế vi mô gồm cung cầu, độ co giãn, hành vi người tiêu dùng và cấu trúc thị trường kèm đồ thị dễ nhớ.",
                    category = ExploreCategory.SUMMARY_NOTE,
                    subject = ExploreSubject.ECONOMICS,
                    docType = ExploreDocType.NOTES,
                    priceVnd = 18_000,
                    rating = 4.8,
                    reviewCount = 102,
                    createdAt = 1_760_000_009_000L,
                    seller = sellerNam,
                    tags = listOf("Kinh tế vi mô", "Kinh tế", "Sơ đồ tư duy"),
                    previews = listOf(
                        PreviewImageRef("econ_p1", "1. Cung và cầu", "fake://preview/econ_1", 1),
                        PreviewImageRef("econ_p2", "2. Cân bằng thị trường", "fake://preview/econ_2", 2)
                    ),
                    recommended = true
                ),
                buildSeed(
                    docId = "doc_calc_05",
                    title = "Tài liệu ôn thi Giải tích 1",
                    subtitle = "Công thức + ví dụ + bài tập trọng tâm",
                    description = "Tuyển tập đề thi và lời giải chi tiết Giải tích 1: giới hạn, đạo hàm, tích phân suy rộng và chuỗi số.",
                    category = ExploreCategory.CHEAT_SHEET,
                    subject = ExploreSubject.DISCRETE_MATH,
                    docType = ExploreDocType.EXAM,
                    priceVnd = 20_000,
                    rating = 4.9,
                    reviewCount = 156,
                    createdAt = 1_760_000_008_000L,
                    seller = sellerMai,
                    tags = listOf("Giải tích 1", "Đề thi", "Toán rời rạc"),
                    previews = listOf(
                        PreviewImageRef("calc_p1", "1. Bảng đạo hàm", "fake://preview/calc_1", 1),
                        PreviewImageRef("calc_p2", "2. Tích phân từng phần", "fake://preview/calc_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_php_06",
                    title = "Đồ án mẫu Web bán hàng (PHP + MySQL)",
                    subtitle = "Full source code + hướng dẫn cài đặt",
                    description = "Đồ án mẫu môn Lập trình Web dùng PHP thuần và MySQL, có báo cáo mẫu 45 trang và file SQL khởi tạo dữ liệu.",
                    category = ExploreCategory.PROJECT,
                    subject = ExploreSubject.DATABASE,
                    docType = ExploreDocType.EXERCISE,
                    priceVnd = 45_000,
                    rating = 4.7,
                    reviewCount = 88,
                    createdAt = 1_760_000_007_000L,
                    seller = sellerAnh,
                    tags = listOf("PHP", "MySQL", "SQL", "Đồ án"),
                    previews = listOf(
                        PreviewImageRef("php_p1", "1. Sơ đồ ERD", "fake://preview/php_1", 1),
                        PreviewImageRef("php_p2", "2. Màn hình giỏ hàng", "fake://preview/php_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_intern_07",
                    title = "Báo cáo thực tập ngành CNTT",
                    subtitle = "File Word + Slide thuyết trình",
                    description = "Mẫu báo cáo thực tập tốt nghiệp ngành Công nghệ thông tin chuẩn bố cục, hướng dẫn cách trình bày nhật ký và kết quả.",
                    category = ExploreCategory.PROJECT,
                    subject = ExploreSubject.OTHER,
                    docType = ExploreDocType.TEXTBOOK,
                    priceVnd = 30_000,
                    rating = 4.6,
                    reviewCount = 72,
                    createdAt = 1_760_000_006_000L,
                    seller = sellerHang,
                    tags = listOf("Thực tập", "CNTT", "Báo cáo"),
                    previews = listOf(
                        PreviewImageRef("int_p1", "1. Mục lục mẫu", "fake://preview/int_1", 1),
                        PreviewImageRef("int_p2", "2. Slide bảo vệ", "fake://preview/int_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_ctdl_08",
                    title = "Tóm tắt môn Cấu trúc dữ liệu và giải thuật",
                    subtitle = "Cây nhị phân, đồ thị, sắp xếp & độ phức tạp (C++)",
                    description = "Tài liệu ôn thi Cấu trúc dữ liệu và giải thuật bằng C++, minh họa từng bước thuật toán BFS, DFS, Dijkstra và cây AVL.",
                    category = ExploreCategory.SUMMARY_NOTE,
                    subject = ExploreSubject.PROGRAMMING,
                    docType = ExploreDocType.NOTES,
                    priceVnd = 20_000,
                    rating = 4.8,
                    reviewCount = 85,
                    createdAt = 1_760_000_005_000L,
                    seller = sellerPhuc,
                    tags = listOf("C++", "Cấu trúc dữ liệu", "Giải thuật", "Lập trình"),
                    previews = listOf(
                        PreviewImageRef("ctdl_p1", "1. Độ phức tạp Big-O", "fake://preview/broken_ctdl_1", 1, isBroken = true),
                        PreviewImageRef("ctdl_p2", "2. Danh sách liên kết", "fake://preview/ctdl_2", 2),
                        PreviewImageRef("ctdl_p3", "3. Cây nhị phân & AVL", "fake://preview/ctdl_3", 3)
                    ),
                    recommended = true
                ),
                buildSeed(
                    docId = "doc_py_09",
                    title = "Python cho phân tích dữ liệu (Pandas & NumPy)",
                    subtitle = "Notebook bài tập + bộ dữ liệu thực hành",
                    description = "Hướng dẫn thực hành Python với NumPy, Pandas và trực quan hóa dữ liệu cho sinh viên ngành Khoa học dữ liệu.",
                    category = ExploreCategory.SOURCE_CODE,
                    subject = ExploreSubject.PROGRAMMING,
                    docType = ExploreDocType.EXERCISE,
                    priceVnd = 35_000,
                    rating = 5.0,
                    reviewCount = 64,
                    createdAt = 1_760_000_004_000L,
                    seller = sellerHung,
                    tags = listOf("Python", "Lập trình", "Pandas", "Dữ liệu"),
                    previews = listOf(
                        PreviewImageRef("py_p1", "1. Thao tác DataFrame", "fake://preview/py_1", 1),
                        PreviewImageRef("py_p2", "2. Làm sạch dữ liệu", "fake://preview/py_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_sql_10",
                    title = "SQL nâng cao & tối ưu truy vấn cơ sở dữ liệu",
                    subtitle = "100 câu truy vấn có lời giải + thiết kế Index",
                    description = "Tổng hợp bài tập SQL từ JOIN, Subquery, Window Function đến phân tích Execution Plan trong PostgreSQL và SQL Server.",
                    category = ExploreCategory.CHEAT_SHEET,
                    subject = ExploreSubject.DATABASE,
                    docType = ExploreDocType.EXAM,
                    priceVnd = 22_000,
                    rating = 4.9,
                    reviewCount = 91,
                    createdAt = 1_760_000_003_000L,
                    seller = sellerMinh,
                    tags = listOf("SQL", "Cơ sở dữ liệu", "Index", "Đề thi"),
                    previews = listOf(
                        PreviewImageRef("sql_p1", "1. Bảng cú pháp JOIN", "fake://preview/sql_1", 1),
                        PreviewImageRef("sql_p2", "2. Window Functions", "fake://preview/sql_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_os_11",
                    title = "Giáo trình tóm tắt Hệ điều hành (Deadlock & Lập lịch CPU)",
                    subtitle = "Sơ đồ Gantt, bộ nhớ ảo & phân trang",
                    description = "Tài liệu ôn tập trọng tâm môn Hệ điều hành: tiến trình, luồng, đồng bộ hóa Semaphore, Deadlock và quản lý bộ nhớ ảo.",
                    category = ExploreCategory.SUMMARY_NOTE,
                    subject = ExploreSubject.OS,
                    docType = ExploreDocType.TEXTBOOK,
                    priceVnd = 19_000,
                    rating = 4.5,
                    reviewCount = 54,
                    createdAt = 1_760_000_002_000L,
                    seller = sellerPhuc,
                    tags = listOf("Hệ điều hành", "Lập lịch CPU", "Deadlock"),
                    previews = listOf(
                        PreviewImageRef("os_p1", "1. Giải thuật Round Robin", "fake://preview/os_1", 1),
                        PreviewImageRef("os_p2", "2. Thuật toán Banker", "fake://preview/os_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_mkt_12",
                    title = "Kinh tế vĩ mô & Marketing căn bản (Đề cương ôn thi)",
                    subtitle = "Ngân hàng 200 câu trắc nghiệm có giải thích",
                    description = "Đề cương ôn thi Kinh tế vĩ mô và nguyên lý Marketing cho sinh viên khối ngành kinh tế, cập nhật theo chương trình mới.",
                    category = ExploreCategory.OTHER,
                    subject = ExploreSubject.ECONOMICS,
                    docType = ExploreDocType.EXAM,
                    priceVnd = 12_000,
                    rating = 4.6,
                    reviewCount = 47,
                    createdAt = 1_760_000_001_000L,
                    seller = sellerNam,
                    tags = listOf("Kinh tế vĩ mô", "Marketing", "Địa lí xã hội", "Kinh tế"),
                    previews = listOf(
                        PreviewImageRef("mkt_p1", "1. Chỉ số GDP & lạm phát", "fake://preview/mkt_1", 1),
                        PreviewImageRef("mkt_p2", "2. Mô hình 4P Marketing", "fake://preview/mkt_2", 2)
                    ),
                    recommended = false
                ),
                buildSeed(
                    docId = "doc_archived_99",
                    title = "Tài liệu mẫu đã ẩn bởi người bán",
                    subtitle = "Không xuất hiện trên catalog công khai",
                    description = "Listing này đã bị chuyển sang trạng thái archive.",
                    category = ExploreCategory.OTHER,
                    subject = ExploreSubject.OTHER,
                    docType = ExploreDocType.NOTES,
                    priceVnd = 10_000,
                    rating = 4.0,
                    reviewCount = 5,
                    createdAt = 1_760_000_000_000L,
                    seller = sellerHang,
                    tags = listOf("Archived"),
                    previews = listOf(
                        PreviewImageRef("arc_p1", "1. Trang bìa", "fake://preview/arc_1", 1)
                    ),
                    recommended = false,
                    archived = true
                )
            )
        }
    }
}
