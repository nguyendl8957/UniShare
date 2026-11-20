package vn.unishare.feature.library.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import java.io.IOException

enum class EntitlementStatus {
    ACTIVE,
    REVOKED
}

data class PurchasedDocumentItem(
    val docId: String,
    val ownerUid: String,
    val title: String,
    val categoryLabel: String,
    val authorName: String,
    val schoolName: String,
    val pageCount: Int,
    val versionNumber: Int,
    val purchasedAtLabel: String,
    val entitlementStatus: EntitlementStatus,
    val isSellerArchived: Boolean = false
)

data class PdfSectionContent(
    val sectionTitle: String,
    val bulletPoints: List<String>,
    val codeTitle: String? = null,
    val codeSnippet: String? = null,
    val tableHeaderLeft: String? = null,
    val tableHeaderRight: String? = null,
    val tableRows: List<Pair<String, String>> = emptyList()
)

// Mỗi trang tương ứng một chương, nối liền các mục trong cùng chương
data class PdfPageContent(
    val pageNumber: Int,
    val chapterTitle: String,
    val sections: List<PdfSectionContent>,
    val watermarkText: String
)

// Chỉ giữ metadata trang và nội dung kết xuất an toàn, không lưu URL tệp PDF trực tiếp
data class PdfDocumentContent(
    val docId: String,
    val title: String,
    val versionNumber: Int,
    val totalPages: Int,
    val pages: List<PdfPageContent>
)

sealed class PdfAccessFailure(message: String) : Exception(message) {
    class Unauthorized(message: String = "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.") :
        PdfAccessFailure(message)

    class Denied(message: String = "Bạn chưa sở hữu tài liệu này.") :
        PdfAccessFailure(message)

    class Revoked(message: String = "Quyền truy cập tài liệu này đã bị thu hồi.") :
        PdfAccessFailure(message)

    class Corrupted(message: String = "Không thể giải mã nội dung PDF của tài liệu.") :
        PdfAccessFailure(message)
}

sealed interface PurchasedLibraryListState {
    data object Idle : PurchasedLibraryListState
    data object Loading : PurchasedLibraryListState
    data object Empty : PurchasedLibraryListState
    data class Content(val items: List<PurchasedDocumentItem>) : PurchasedLibraryListState
    data class Error(val message: String) : PurchasedLibraryListState
}

sealed interface PdfReaderLoadState {
    data object Idle : PdfReaderLoadState
    data object Loading : PdfReaderLoadState
    data class Ready(
        val docId: String,
        val title: String,
        val versionNumber: Int,
        val totalPages: Int,
        val currentPage: Int,
        val pages: List<PdfPageContent>
    ) : PdfReaderLoadState {
        val currentPageContent: PdfPageContent?
            get() = pages.firstOrNull { it.pageNumber == currentPage } ?: pages.firstOrNull()
    }

    data class Unauthorized(val message: String) : PdfReaderLoadState
    data class Denied(val message: String) : PdfReaderLoadState
    data class Revoked(val message: String) : PdfReaderLoadState
    data class Error(val message: String) : PdfReaderLoadState
}

data class PurchasedLibraryUiState(
    val isRestoringSession: Boolean = false,
    val isGuest: Boolean = true,
    val activeUid: String? = null,
    val listState: PurchasedLibraryListState = PurchasedLibraryListState.Idle,
    val isRefreshing: Boolean = false,
    val refreshErrorMessage: String? = null,
    val activeReaderDocId: String? = null,
    val readerState: PdfReaderLoadState = PdfReaderLoadState.Idle
)

sealed interface PurchasedLibraryEvent {
    data class NavigateToReader(val docId: String) : PurchasedLibraryEvent
    data class ShowMessage(val message: String) : PurchasedLibraryEvent
}

// Hợp đồng thư viện đã mua và trình đọc PDF theo quyền từng UID
interface LibraryRepository {
    suspend fun getPurchasedDocuments(uid: String): Result<List<PurchasedDocumentItem>>
    suspend fun getDocumentContent(uid: String?, docId: String): Result<PdfDocumentContent>
}

// Bộ giả lập thư viện hỗ trợ hai tài khoản, thu hồi quyền và mất mạng
class FakeLibraryRepository(
    initialSession: AuthSession = AuthSession.SignedIn(UID_BUYER_ONE),
    var simulateOffline: Boolean = false
) : LibraryRepository {

    private val _session = MutableStateFlow(initialSession)
    val sessionFlow: StateFlow<AuthSession> = _session

    private val entitlements = mutableListOf(
        PurchasedDocumentItem(
            docId = DOC_DSA_BINARY_SEARCH,
            ownerUid = UID_BUYER_ONE,
            title = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
            categoryLabel = "Note tóm tắt",
            authorName = "Nguyễn Văn A",
            schoolName = "ĐH Bách Khoa TP.HCM",
            pageCount = 3,
            versionNumber = 2,
            purchasedAtLabel = "Đã mua 18/11/2026",
            entitlementStatus = EntitlementStatus.ACTIVE
        ),
        PurchasedDocumentItem(
            docId = DOC_MACRO_ECON,
            ownerUid = UID_BUYER_ONE,
            title = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
            categoryLabel = "Giáo trình",
            authorName = "Đặng Lâm Nguyên",
            schoolName = "ĐH Kinh tế TP.HCM",
            pageCount = 3,
            versionNumber = 1,
            purchasedAtLabel = "Đã mua 15/11/2026",
            entitlementStatus = EntitlementStatus.ACTIVE,
            isSellerArchived = true
        ),
        PurchasedDocumentItem(
            docId = DOC_REVOKED_SAMPLE,
            ownerUid = UID_BUYER_ONE,
            title = "Đề cương Mạng máy tính (Đã hoàn tiền/thu hồi)",
            categoryLabel = "Tài liệu ôn thi",
            authorName = "Trần Minh Đức",
            schoolName = "ĐH Công nghệ Thông tin",
            pageCount = 2,
            versionNumber = 1,
            purchasedAtLabel = "Đã mua 10/11/2026",
            entitlementStatus = EntitlementStatus.REVOKED
        ),
        PurchasedDocumentItem(
            docId = DOC_OS_BUYER_TWO,
            ownerUid = UID_BUYER_TWO,
            title = "Hệ điều hành - Đồng bộ tiến trình và Deadlock",
            categoryLabel = "Note tóm tắt",
            authorName = "Lê Thị Mai",
            schoolName = "ĐH Khoa học Tự nhiên",
            pageCount = 3,
            versionNumber = 1,
            purchasedAtLabel = "Đã mua 19/11/2026",
            entitlementStatus = EntitlementStatus.ACTIVE
        )
    )

    private val documentContents = mapOf(
        DOC_DSA_BINARY_SEARCH to PdfDocumentContent(
            docId = DOC_DSA_BINARY_SEARCH,
            title = "Cấu trúc dữ liệu & Giải thuật - Tìm kiếm nhị phân",
            versionNumber = 2,
            totalPages = 3,
            pages = listOf(
                PdfPageContent(
                    pageNumber = 1,
                    chapterTitle = "Chương 3: Thuật toán Tìm kiếm nhị phân",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "3.2. Điều kiện áp dụng & Minh họa",
                            bulletPoints = listOf(
                                "Mảng phải được sắp xếp (tăng dần hoặc giảm dần).",
                                "Các phần tử có thể so sánh trực tiếp với nhau theo thứ tự.",
                                "Nếu giá trị giữa nhỏ hơn khóa cần tìm (23 < 30), thu hẹp phạm vi sang nửa bên phải."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "3.4. Pseudocode",
                            bulletPoints = listOf(
                                "Thu hẹp một nửa không gian tìm kiếm sau mỗi lần so sánh."
                            ),
                            codeTitle = "Mã giả chuẩn",
                            codeSnippet = "int binarySearch(int arr[], int n, int x) {\n    int left = 0, right = n - 1;\n    while (left <= right) {\n        int mid = left + (right - left) / 2;\n        if (arr[mid] == x) return mid;\n        if (arr[mid] < x) left = mid + 1;\n        else right = mid - 1;\n    }\n    return -1;\n}"
                        ),
                        PdfSectionContent(
                            sectionTitle = "3.5. Độ phức tạp thuật toán",
                            bulletPoints = listOf(
                                "Không tốn thêm bộ nhớ phụ khi cài đặt bằng vòng lặp."
                            ),
                            tableHeaderLeft = "Trường hợp",
                            tableHeaderRight = "Độ phức tạp",
                            tableRows = listOf("Tốt nhất" to "O(1)", "Trung bình" to "O(log n)")
                        ),
                        PdfSectionContent(
                            sectionTitle = "3.6. Tránh tràn số khi tính chỉ số giữa",
                            bulletPoints = listOf(
                                "Không nên viết mid = (left + right) / 2 với mảng rất lớn.",
                                "Thay bằng mid = left + (right - left) / 2 để tránh tràn kiểu int 32-bit."
                            ),
                            codeTitle = "Ví dụ an toàn trong Kotlin / C++",
                            codeSnippet = "val mid = left + (right - left) ushr 1"
                        ),
                        PdfSectionContent(
                            sectionTitle = "3.7. Tìm biên trái & biên phải (Lower / Upper Bound)",
                            bulletPoints = listOf(
                                "Biên trái tìm vị trí đầu tiên thỏa arr[mid] >= x bằng cách dời right = mid - 1.",
                                "Biên phải tìm vị trí cuối cùng thỏa arr[mid] <= x bằng cách dời left = mid + 1."
                            )
                        )
                    ),
                    watermarkText = "Nguyễn Văn A - nguyenvana@gmail.com"
                ),
                PdfPageContent(
                    pageNumber = 2,
                    chapterTitle = "Chương 4: Tìm kiếm nhị phân trên tập đáp án",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "4.1. Ý tưởng đơn điệu trên miền giá trị",
                            bulletPoints = listOf(
                                "Áp dụng khi bài toán có tính chất đơn điệu: nếu X thỏa thì mọi giá trị lớn hơn (hoặc nhỏ hơn) cũng thỏa.",
                                "Kiểm tra tính hợp lệ của giá trị giữa mid trong O(n) để loại bỏ một nửa miền đáp án."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "4.2. Ví dụ tính căn bậc hai nguyên",
                            bulletPoints = listOf(
                                "Tìm số nguyên lớn nhất mid sao cho mid * mid <= n trong khoảng [0, n]."
                            ),
                            codeSnippet = "while (left <= right) {\n    val mid = left + (right - left) / 2\n    if (mid <= n / maxOf(mid, 1)) { ans = mid; left = mid + 1 } else right = mid - 1\n}"
                        )
                    ),
                    watermarkText = "Nguyễn Văn A - nguyenvana@gmail.com"
                ),
                PdfPageContent(
                    pageNumber = 3,
                    chapterTitle = "Chương 5: Tổng kết & Bài tập tự luyện",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "5.1. Các lỗi hay gặp khi cài đặt",
                            bulletPoints = listOf(
                                "Sai điều kiện dừng left < right so với left <= right dẫn tới lặp vô hạn.",
                                "Quên cập nhật left = mid + 1 hoặc right = mid - 1 khi thu hẹp đoạn."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "5.2. Bài tập ôn tập",
                            bulletPoints = listOf(
                                "Bài 1: Đếm số lần xuất hiện của x trong mảng đã sắp xếp trong O(log n).",
                                "Bài 2: Tìm phần tử nhỏ nhất trong mảng xoay vòng đã sắp xếp."
                            )
                        )
                    ),
                    watermarkText = "Nguyễn Văn A - nguyenvana@gmail.com"
                )
            )
        ),
        DOC_MACRO_ECON to PdfDocumentContent(
            docId = DOC_MACRO_ECON,
            title = "Tóm tắt Kinh tế vĩ mô - Chương 1, 2, 3",
            versionNumber = 1,
            totalPages = 3,
            pages = listOf(
                PdfPageContent(
                    pageNumber = 1,
                    chapterTitle = "Chương 1: Đo lường sản lượng quốc gia (GDP)",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "1.1. Khái niệm GDP danh nghĩa và GDP thực",
                            bulletPoints = listOf(
                                "GDP danh nghĩa tính theo giá hiện hành; GDP thực tính theo giá năm gốc.",
                                "Chỉ số điều chỉnh GDP = (GDP danh nghĩa / GDP thực) * 100."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "1.2. Phương pháp chi tiêu",
                            bulletPoints = listOf(
                                "Công thức tổng quát: GDP = C + I + G + (X - M)."
                            ),
                            tableHeaderLeft = "Thành phần",
                            tableHeaderRight = "Ý nghĩa",
                            tableRows = listOf("C" to "Tiêu dùng hộ gia đình", "I" to "Đầu tư tư nhân")
                        )
                    ),
                    watermarkText = "Đặng Lâm Nguyên - unishare"
                ),
                PdfPageContent(
                    pageNumber = 2,
                    chapterTitle = "Chương 2: Chỉ số giá tiêu dùng (CPI) & Lạm phát",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "2.1. Cách tính CPI",
                            bulletPoints = listOf(
                                "CPI đo mức giá trung bình của giỏ hàng hóa tiêu biểu."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "2.2. Tỷ lệ lạm phát",
                            bulletPoints = listOf(
                                "Tỷ lệ lạm phát = ((CPI_t - CPI_{t-1}) / CPI_{t-1}) * 100%."
                            )
                        )
                    ),
                    watermarkText = "Đặng Lâm Nguyên - unishare"
                ),
                PdfPageContent(
                    pageNumber = 3,
                    chapterTitle = "Chương 3: Chính sách tài khóa và tiền tệ",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "3.1. Chính sách tài khóa",
                            bulletPoints = listOf(
                                "Chính sách tài khóa mở rộng tăng chi tiêu công G hoặc giảm thuế T."
                            )
                        ),
                        PdfSectionContent(
                            sectionTitle = "3.2. Chính sách tiền tệ",
                            bulletPoints = listOf(
                                "Ngân hàng trung ương điều tiết cung tiền thông qua lãi suất và dự trữ bắt buộc."
                            )
                        )
                    ),
                    watermarkText = "Đặng Lâm Nguyên - unishare"
                )
            )
        ),
        DOC_OS_BUYER_TWO to PdfDocumentContent(
            docId = DOC_OS_BUYER_TWO,
            title = "Hệ điều hành - Đồng bộ tiến trình và Deadlock",
            versionNumber = 1,
            totalPages = 3,
            pages = listOf(
                PdfPageContent(
                    pageNumber = 1,
                    chapterTitle = "Chương 1: Vùng găng (Critical Section)",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "1.1. Ba điều kiện đồng bộ",
                            bulletPoints = listOf(
                                "Mutual Exclusion, Progress và Bounded Waiting.",
                                "Dùng Mutex hoặc Semaphore để bảo vệ tài nguyên dùng chung."
                            )
                        )
                    ),
                    watermarkText = "Lê Thị Mai - unishare"
                ),
                PdfPageContent(
                    pageNumber = 2,
                    chapterTitle = "Chương 2: Hiện tượng bế tắc (Deadlock)",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "2.1. Bốn điều kiện cần của Deadlock",
                            bulletPoints = listOf(
                                "Mutual Exclusion, Hold and Wait, No Preemption, Circular Wait."
                            )
                        )
                    ),
                    watermarkText = "Lê Thị Mai - unishare"
                ),
                PdfPageContent(
                    pageNumber = 3,
                    chapterTitle = "Chương 3: Phòng tránh Deadlock",
                    sections = listOf(
                        PdfSectionContent(
                            sectionTitle = "3.1. Thuật toán Banker",
                            bulletPoints = listOf(
                                "Chỉ cấp phát tài nguyên khi trạng thái sau cấp phát vẫn tồn tại chuỗi an toàn."
                            )
                        )
                    ),
                    watermarkText = "Lê Thị Mai - unishare"
                )
            )
        )
    )

    var simulateInvalidToken: Boolean = false

    fun grantEntitlementForTest(item: PurchasedDocumentItem) {
        entitlements.removeAll { it.ownerUid == item.ownerUid && it.docId == item.docId }
        entitlements.add(0, item)
    }

    fun revokeEntitlementForTest(uid: String, docId: String) {
        val index = entitlements.indexOfFirst { it.ownerUid == uid && it.docId == docId }
        if (index >= 0) {
            entitlements[index] = entitlements[index].copy(entitlementStatus = EntitlementStatus.REVOKED)
        }
    }

    fun setSessionForTest(session: AuthSession) {
        _session.value = session
    }

    override suspend fun getPurchasedDocuments(uid: String): Result<List<PurchasedDocumentItem>> {
        if (simulateOffline) {
            return Result.failure(IOException("Không có kết nối mạng"))
        }
        val items = entitlements.filter {
            it.ownerUid == uid && it.entitlementStatus == EntitlementStatus.ACTIVE
        }
        return Result.success(items)
    }

    override suspend fun getDocumentContent(uid: String?, docId: String): Result<PdfDocumentContent> {
        if (simulateOffline) {
            return Result.failure(IOException("Không thể tải tài liệu do mất kết nối mạng"))
        }
        if (uid.isNullOrBlank() || simulateInvalidToken) {
            return Result.failure(PdfAccessFailure.Unauthorized())
        }
        val entry = entitlements.firstOrNull { it.ownerUid == uid && it.docId == docId }
            ?: return Result.failure(PdfAccessFailure.Denied())

        if (entry.entitlementStatus == EntitlementStatus.REVOKED) {
            return Result.failure(PdfAccessFailure.Revoked())
        }
        val content = documentContents[docId]
            ?: return Result.failure(PdfAccessFailure.Corrupted())
        return Result.success(content)
    }

    companion object {
        const val UID_BUYER_ONE = "uid_buyer_01"
        const val UID_BUYER_TWO = "uid_buyer_02"
        const val UID_EMPTY_BUYER = "uid_buyer_empty"
        const val DOC_DSA_BINARY_SEARCH = "doc_dsa_binary_search"
        const val DOC_MACRO_ECON = "doc_macro_econ"
        const val DOC_REVOKED_SAMPLE = "doc_revoked_sample"
        const val DOC_OS_BUYER_TWO = "doc_os_buyer_02"
        const val DOC_UNPAID_SAMPLE = "doc_unpaid_sample"
    }
}

class FakeAuthRepoForLibrary(
    private val backingSession: MutableStateFlow<AuthSession>
) : AuthRepository {
    override val session: StateFlow<AuthSession> = backingSession
    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)
    override suspend fun signOut(): Result<Unit> {
        backingSession.value = AuthSession.Guest
        return Result.success(Unit)
    }
}

class PurchasedLibraryViewModel(
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchasedLibraryUiState())
    val uiState: StateFlow<PurchasedLibraryUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PurchasedLibraryEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<PurchasedLibraryEvent> = _events.asSharedFlow()

    private var libraryLoadJob: Job? = null
    private var readerLoadJob: Job? = null
    private var libraryLoadSequence: Long = 0L
    private var readerLoadSequence: Long = 0L

    init {
        viewModelScope.launch(mainDispatcher) {
            authRepository.session.collect { session ->
                handleSessionChanged(session)
            }
        }
    }

    private fun handleSessionChanged(session: AuthSession) {
        when (session) {
            is AuthSession.Restoring -> {
                clearAllUserLibraryState(isRestoring = true, isGuest = false)
            }

            is AuthSession.Guest -> {
                clearAllUserLibraryState(isRestoring = false, isGuest = true)
            }

            is AuthSession.SignedIn -> {
                val previousUid = _uiState.value.activeUid
                if (previousUid != session.uid) {
                    // Xóa ngay danh sách và trang PDF cũ khi đổi sang UID khác
                    clearAllUserLibraryState(
                        isRestoring = false,
                        isGuest = false,
                        newActiveUid = session.uid
                    )
                    loadPurchasedLibrary(uid = session.uid, keepVisibleContent = false)
                }
            }
        }
    }

    private fun clearAllUserLibraryState(
        isRestoring: Boolean,
        isGuest: Boolean,
        newActiveUid: String? = null
    ) {
        libraryLoadJob?.cancel()
        readerLoadJob?.cancel()
        libraryLoadSequence++
        readerLoadSequence++
        _uiState.value = PurchasedLibraryUiState(
            isRestoringSession = isRestoring,
            isGuest = isGuest,
            activeUid = newActiveUid,
            listState = if (newActiveUid != null) {
                PurchasedLibraryListState.Loading
            } else {
                PurchasedLibraryListState.Idle
            },
            readerState = PdfReaderLoadState.Idle
        )
    }

    fun onLibraryResumed() {
        val uid = _uiState.value.activeUid ?: return
        val hasContent = _uiState.value.listState is PurchasedLibraryListState.Content
        loadPurchasedLibrary(uid = uid, keepVisibleContent = hasContent)
    }

    fun refreshLibrary() {
        val uid = _uiState.value.activeUid ?: return
        val hasContent = _uiState.value.listState is PurchasedLibraryListState.Content
        loadPurchasedLibrary(uid = uid, keepVisibleContent = hasContent)
    }

    private fun loadPurchasedLibrary(uid: String, keepVisibleContent: Boolean) {
        val requestId = ++libraryLoadSequence
        libraryLoadJob?.cancel()

        _uiState.update { state ->
            if (keepVisibleContent) {
                state.copy(isRefreshing = true, refreshErrorMessage = null)
            } else {
                state.copy(
                    listState = PurchasedLibraryListState.Loading,
                    isRefreshing = false,
                    refreshErrorMessage = null
                )
            }
        }

        libraryLoadJob = viewModelScope.launch(mainDispatcher) {
            val result = libraryRepository.getPurchasedDocuments(uid)
            if (requestId != libraryLoadSequence || _uiState.value.activeUid != uid) {
                return@launch
            }

            result.fold(
                onSuccess = { docs ->
                    val activeDocs = docs.filter {
                        it.ownerUid == uid && it.entitlementStatus == EntitlementStatus.ACTIVE
                    }
                    val nextListState = if (activeDocs.isEmpty()) {
                        PurchasedLibraryListState.Empty
                    } else {
                        PurchasedLibraryListState.Content(activeDocs)
                    }
                    _uiState.update {
                        it.copy(
                            listState = nextListState,
                            isRefreshing = false,
                            refreshErrorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    val message = mapErrorMessage(error)
                    _uiState.update { state ->
                        if (keepVisibleContent && state.listState is PurchasedLibraryListState.Content) {
                            state.copy(
                                isRefreshing = false,
                                refreshErrorMessage = message
                            )
                        } else {
                            state.copy(
                                listState = PurchasedLibraryListState.Error(message),
                                isRefreshing = false,
                                refreshErrorMessage = null
                            )
                        }
                    }
                }
            )
        }
    }

    fun requestOpenDocument(docId: String) {
        val current = _uiState.value
        if (current.isGuest || current.activeUid == null) {
            _events.tryEmit(
                PurchasedLibraryEvent.ShowMessage("Vui lòng đăng nhập để đọc tài liệu đã mua.")
            )
            return
        }
        _events.tryEmit(PurchasedLibraryEvent.NavigateToReader(docId))
    }

    // --- Điều khiển trình đọc PDF theo docId ---

    fun openDocumentReader(docId: String) {
        val uid = _uiState.value.activeUid
        val requestId = ++readerLoadSequence
        readerLoadJob?.cancel()

        _uiState.update {
            it.copy(
                activeReaderDocId = docId,
                readerState = PdfReaderLoadState.Loading
            )
        }

        readerLoadJob = viewModelScope.launch(mainDispatcher) {
            val result = libraryRepository.getDocumentContent(uid = uid, docId = docId)
            if (requestId != readerLoadSequence) return@launch

            result.fold(
                onSuccess = { content ->
                    if (uid == null || _uiState.value.activeUid != uid) return@launch
                    _uiState.update {
                        it.copy(
                            activeReaderDocId = docId,
                            readerState = PdfReaderLoadState.Ready(
                                docId = content.docId,
                                title = content.title,
                                versionNumber = content.versionNumber,
                                totalPages = content.totalPages,
                                currentPage = 1,
                                pages = content.pages
                            )
                        )
                    }
                },
                onFailure = { error ->
                    // Xóa hoàn toàn trang PDF nếu phiên thiếu hoặc quyền đã bị thu hồi
                    val failureState = when (error) {
                        is PdfAccessFailure.Unauthorized ->
                            PdfReaderLoadState.Unauthorized(error.message ?: "Phiên đăng nhập không hợp lệ.")
                        is PdfAccessFailure.Revoked ->
                            PdfReaderLoadState.Revoked(error.message ?: "Quyền truy cập đã bị thu hồi.")
                        is PdfAccessFailure.Denied ->
                            PdfReaderLoadState.Denied(error.message ?: "Bạn chưa sở hữu tài liệu này.")
                        else ->
                            PdfReaderLoadState.Error(mapErrorMessage(error))
                    }
                    _uiState.update {
                        it.copy(
                            activeReaderDocId = docId,
                            readerState = failureState
                        )
                    }
                }
            )
        }
    }

    fun retryOpenDocument() {
        val docId = _uiState.value.activeReaderDocId ?: return
        openDocumentReader(docId)
    }

    fun goToNextPage() {
        _uiState.update { state ->
            val ready = state.readerState as? PdfReaderLoadState.Ready ?: return@update state
            if (ready.currentPage >= ready.totalPages) return@update state
            state.copy(readerState = ready.copy(currentPage = ready.currentPage + 1))
        }
    }

    fun goToPreviousPage() {
        _uiState.update { state ->
            val ready = state.readerState as? PdfReaderLoadState.Ready ?: return@update state
            if (ready.currentPage <= 1) return@update state
            state.copy(readerState = ready.copy(currentPage = ready.currentPage - 1))
        }
    }

    fun jumpToPage(page: Int) {
        _uiState.update { state ->
            val ready = state.readerState as? PdfReaderLoadState.Ready ?: return@update state
            val clamped = page.coerceIn(1, ready.totalPages)
            state.copy(readerState = ready.copy(currentPage = clamped))
        }
    }

    fun clearReaderState() {
        readerLoadJob?.cancel()
        readerLoadSequence++
        _uiState.update {
            it.copy(
                activeReaderDocId = null,
                readerState = PdfReaderLoadState.Idle
            )
        }
    }

    private fun mapErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when {
            error is IOException ||
                raw.contains("network", ignoreCase = true) ||
                raw.contains("mạng", ignoreCase = true) ->
                "Mất kết nối mạng. Vui lòng kiểm tra Internet và thử lại."
            raw.isNotBlank() -> raw
            else -> "Đã xảy ra lỗi. Vui lòng thử lại."
        }
    }
}
