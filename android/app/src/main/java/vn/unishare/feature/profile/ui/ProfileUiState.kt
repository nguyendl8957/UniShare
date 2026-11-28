package vn.unishare.feature.profile.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import vn.unishare.core.ui.theme.UniAppMode
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession
import vn.unishare.domain.repository.ProfileRepository
import vn.unishare.domain.repository.UserProfile
import java.io.IOException

data class ProfileAchievementBadge(
    val code: String?,
    val label: String,
    val containerColorHex: Long,
    val contentColorHex: Long,
    val badgeCircleColorHex: Long? = null
)

data class SellerPublicListingItem(
    val docId: String,
    val title: String,
    val categoryLabel: String,
    val categoryBgHex: Long = 0xFFD9EDFF,
    val categoryTextHex: Long = 0xFF2587E9,
    val priceVnd: Int,
    val rating: Double,
    val reviewCount: Int,
    val viewCount: Int,
    val soldCount: Int
)

data class SellerRecentOrderItem(
    val orderId: String,
    val buyerDisplayName: String,
    val documentTitle: String,
    val timeAgoLabel: String,
    val amountVnd: Int,
    val isPaid: Boolean
)

data class SellerDashboardSummary(
    val monthlyRevenueVnd: Int,
    val revenueGrowthLabel: String,
    val publishedDocCount: Int,
    val todayOrderCount: Int,
    val orderGrowthLabel: String,
    val myDocuments: List<SellerPublicListingItem>,
    val recentOrders: List<SellerRecentOrderItem>
) {
    companion object {
        fun defaultSample(): SellerDashboardSummary = SellerDashboardSummary(
            monthlyRevenueVnd = 1_250_000,
            revenueGrowthLabel = "↑ 12%",
            publishedDocCount = 8,
            todayOrderCount = 5,
            orderGrowthLabel = "↑ 67%",
            myDocuments = listOf(
                SellerPublicListingItem(
                    docId = "doc_cpp_01",
                    title = "Tóm tắt Kinh tế vĩ mô - Chương 1,2,3",
                    categoryLabel = "Note tóm tắt",
                    categoryBgHex = 0xFFD9EDFF,
                    categoryTextHex = 0xFF2587E9,
                    priceVnd = 15_000,
                    rating = 4.9,
                    reviewCount = 42,
                    viewCount = 245,
                    soldCount = 32
                ),
                SellerPublicListingItem(
                    docId = "doc_web_02",
                    title = "Source code Website quản lý sinh viên",
                    categoryLabel = "Mã nguồn",
                    categoryBgHex = 0xFFEAE2FF,
                    categoryTextHex = 0xFF8058CF,
                    priceVnd = 20_000,
                    rating = 4.8,
                    reviewCount = 19,
                    viewCount = 118,
                    soldCount = 18
                ),
                SellerPublicListingItem(
                    docId = "doc_toeic_03",
                    title = "Bộ đề TOEIC Listening & Reading (PDF)",
                    categoryLabel = "Tài liệu ôn thi",
                    categoryBgHex = 0xFFFFF0D5,
                    categoryTextHex = 0xFFBE791C,
                    priceVnd = 25_000,
                    rating = 4.9,
                    reviewCount = 61,
                    viewCount = 402,
                    soldCount = 54
                )
            ),
            recentOrders = listOf(
                SellerRecentOrderItem(
                    orderId = "ord_01",
                    buyerDisplayName = "Trần Minh Đức",
                    documentTitle = "Tóm tắt Kinh tế vĩ mô - Chương 1,2,3",
                    timeAgoLabel = "2 giờ trước",
                    amountVnd = 15_000,
                    isPaid = true
                ),
                SellerRecentOrderItem(
                    orderId = "ord_02",
                    buyerDisplayName = "Lê Thị Mai",
                    documentTitle = "Source code Website quản lý sinh viên",
                    timeAgoLabel = "5 giờ trước",
                    amountVnd = 20_000,
                    isPaid = true
                ),
                SellerRecentOrderItem(
                    orderId = "ord_03",
                    buyerDisplayName = "Phạm Quốc Bảo",
                    documentTitle = "Bộ đề TOEIC Listening & Reading (PDF)",
                    timeAgoLabel = "7 giờ trước",
                    amountVnd = 25_000,
                    isPaid = false
                )
            )
        )
    }
}

// Hồ sơ công khai của người bán (chỉ chứa trường công khai, không chứa email hay thông tin nhạy cảm)
data class SellerPublicProfile(
    val sellerId: String,
    val displayName: String,
    val school: String,
    val faculty: String,
    val bio: String,
    val verifiedBadge: String,
    val rating: Double,
    val reviewCount: Int,
    val totalDocuments: Int,
    val totalSold: Int,
    val joinedLabel: String,
    val achievements: List<ProfileAchievementBadge>,
    val listings: List<SellerPublicListingItem>
)

data class SellerPermissionSnapshot(
    val uid: String,
    val sellerEnabled: Boolean,
    val lastMode: UniAppMode = UniAppMode.BUYER
)

data class ProfileFieldErrors(
    val displayNameError: String? = null,
    val schoolError: String? = null,
    val facultyError: String? = null
)

sealed interface ProfileSaveStatus {
    data object Idle : ProfileSaveStatus
    data object Saving : ProfileSaveStatus
    data class Saved(val message: String = "Đã lưu thông tin hồ sơ.") : ProfileSaveStatus
    data class Error(val message: String) : ProfileSaveStatus
}

sealed interface SellerEnrollmentState {
    data object Idle : SellerEnrollmentState
    data object Enrolling : SellerEnrollmentState
    data class Denied(val message: String) : SellerEnrollmentState
}

sealed interface SellerPublicProfileLoadState {
    data object Idle : SellerPublicProfileLoadState
    data object Loading : SellerPublicProfileLoadState
    data class Content(val profile: SellerPublicProfile) : SellerPublicProfileLoadState
    data object Empty : SellerPublicProfileLoadState
    data class Error(val message: String) : SellerPublicProfileLoadState
}

// State giao diện Hồ sơ cá nhân & Chuyển chế độ Buyer/Seller (không lưu email công khai hoặc credential)
data class ProfileUiState(
    val isRestoringSession: Boolean = false,
    val isGuest: Boolean = true,
    val activeUid: String? = null,
    val isLoadingProfile: Boolean = false,
    val isEditingProfile: Boolean = false,
    val displayName: String = "",
    val school: String = "",
    val faculty: String = "",
    val avatarPath: String? = null,
    val editDisplayName: String = "",
    val editSchool: String = "",
    val editFaculty: String = "",
    val editAvatarPath: String? = null,
    val fieldErrors: ProfileFieldErrors = ProfileFieldErrors(),
    val saveStatus: ProfileSaveStatus = ProfileSaveStatus.Idle,
    val currentMode: UniAppMode = UniAppMode.BUYER,
    val sellerEnabled: Boolean = false,
    val enrollmentState: SellerEnrollmentState = SellerEnrollmentState.Idle,
    val achievements: List<ProfileAchievementBadge> = defaultProfileBadges(),
    val sellerDashboardLoading: Boolean = false,
    val sellerDashboardLoaded: Boolean = false,
    val sellerDashboardError: String? = null,
    val sellerDashboard: SellerDashboardSummary = SellerDashboardSummary(
        monthlyRevenueVnd = 0,
        revenueGrowthLabel = "Chưa có dữ liệu",
        publishedDocCount = 0,
        todayOrderCount = 0,
        orderGrowthLabel = "Chưa có dữ liệu",
        myDocuments = emptyList(),
        recentOrders = emptyList()
    ),
    val loadError: String? = null,
    val selectedPublicSellerId: String? = null,
    val publicSellerState: SellerPublicProfileLoadState = SellerPublicProfileLoadState.Idle
) {
    val isFormComplete: Boolean
        get() = editDisplayName.trim().length >= 2 &&
            editSchool.isNotBlank() &&
            editFaculty.isNotBlank()

    companion object {
        val ALLOWED_SCHOOLS = listOf(
            "Trường đại học Giao thông vận tải TPHCM",
            "ĐH Bách Khoa TP.HCM",
            "ĐH Khoa học Tự nhiên",
            "ĐH Công nghệ Thông tin",
            "ĐH Kinh tế TP.HCM",
            "Đại học Kinh tế Quốc dân"
        )

        val ALLOWED_FACULTIES = listOf(
            "Khoa CNTT",
            "Khoa Kinh tế",
            "Khoa Điện - Điện tử",
            "Khoa Cơ khí",
            "Khoa Quản trị kinh doanh",
            "Khoa Khoa học máy tính"
        )

        fun defaultProfileBadges(): List<ProfileAchievementBadge> = listOf(
            ProfileAchievementBadge(
                code = null,
                label = "Sinh viên giỏi",
                containerColorHex = 0xFFE5F0FF,
                contentColorHex = 0xFF1565C0
            ),
            ProfileAchievementBadge(
                code = "A+",
                label = "Toán rời rạc",
                containerColorHex = 0xFFE3F3F1,
                contentColorHex = 0xFF207C63,
                badgeCircleColorHex = 0xFF207C63
            ),
            ProfileAchievementBadge(
                code = "A",
                label = "Lập trình C++",
                containerColorHex = 0xFFF0E9FF,
                contentColorHex = 0xFF8144CC,
                badgeCircleColorHex = 0xFF8144CC
            )
        )
    }
}

fun formatProfileVnd(amountVnd: Int): String {
    val raw = amountVnd.coerceAtLeast(0).toString()
    val reversed = raw.reversed()
    val sb = StringBuilder()
    for (i in reversed.indices) {
        if (i > 0 && i % 3 == 0) sb.append('.')
        sb.append(reversed[i])
    }
    return "${sb.reverse()}đ"
}

// Hợp đồng bổ sung cho quyền Seller, lastMode và hồ sơ Seller công khai
interface SellerRoleContract {
    suspend fun getSellerPermission(uid: String): Result<SellerPermissionSnapshot>
    suspend fun updateLastMode(uid: String, mode: UniAppMode): Result<Unit>
    suspend fun requestSellerEnrollment(uid: String, confirmTerms: Boolean): Result<SellerPermissionSnapshot>
    suspend fun getSellerPublicProfile(sellerId: String): Result<SellerPublicProfile?>
}

// Bộ giả lập nhiều UID hỗ trợ kiểm thử phân tách dữ liệu, quyền Seller và chế độ ngoại tuyến
class FakeProfileEnvironment(
    initialSession: AuthSession = AuthSession.SignedIn(UID_SELLER_READY),
    var simulateOffline: Boolean = false,
    var denySellerEnrollment: Boolean = false
) : AuthRepository, ProfileRepository, SellerRoleContract {

    private val _session = MutableStateFlow(initialSession)
    override val session: StateFlow<AuthSession> = _session

    private val privateProfilesByUid = mutableMapOf(
        UID_SELLER_READY to UserProfile(
            uid = UID_SELLER_READY,
            displayName = "Đặng Lâm Nguyên",
            school = "Trường đại học Giao thông vận tải TPHCM",
            faculty = "Khoa CNTT",
            avatarUrl = "avatar://nguyen"
        ),
        UID_BUYER_ONLY to UserProfile(
            uid = UID_BUYER_ONLY,
            displayName = "Trần Minh Đức",
            school = "ĐH Bách Khoa TP.HCM",
            faculty = "Khoa Khoa học máy tính",
            avatarUrl = "avatar://duc"
        )
    )

    private val permissionsByUid = mutableMapOf(
        UID_SELLER_READY to SellerPermissionSnapshot(
            uid = UID_SELLER_READY,
            sellerEnabled = true,
            lastMode = UniAppMode.BUYER
        ),
        UID_BUYER_ONLY to SellerPermissionSnapshot(
            uid = UID_BUYER_ONLY,
            sellerEnabled = false,
            lastMode = UniAppMode.BUYER
        )
    )

    private val publicProfilesBySellerId = mutableMapOf(
        UID_SELLER_READY to SellerPublicProfile(
            sellerId = UID_SELLER_READY,
            displayName = "Đặng Lâm Nguyên",
            school = "Trường đại học Giao thông vận tải TPHCM",
            faculty = "Khoa CNTT",
            bio = "Sinh viên năm 3 chuyên ngành Công nghệ phần mềm. Chia sẻ ghi chú môn học, sơ đồ tư duy và mã nguồn đồ án thực hành đạt điểm A+.",
            verifiedBadge = "A+",
            rating = 4.9,
            reviewCount = 124,
            totalDocuments = 8,
            totalSold = 532,
            joinedLabel = "Tham gia 09/2025",
            achievements = ProfileUiState.defaultProfileBadges(),
            listings = SellerDashboardSummary.defaultSample().myDocuments
        ),
        "seller_hung_01" to SellerPublicProfile(
            sellerId = "seller_hung_01",
            displayName = "Nguyễn Tuấn Hùng",
            school = "ĐH Bách Khoa TP.HCM",
            faculty = "Khoa Khoa học máy tính",
            bio = "Tác giả bộ tài liệu Lập trình C++ và Cấu trúc dữ liệu giải thuật dành cho sinh viên CNTT.",
            verifiedBadge = "TP",
            rating = 4.9,
            reviewCount = 124,
            totalDocuments = 6,
            totalSold = 532,
            joinedLabel = "Tham gia 08/2025",
            achievements = ProfileUiState.defaultProfileBadges(),
            listings = SellerDashboardSummary.defaultSample().myDocuments
        )
    )

    fun setActiveSession(newSession: AuthSession) {
        _session.value = newSession
    }

    fun setSellerPermission(uid: String, enabled: Boolean, lastMode: UniAppMode = UniAppMode.BUYER) {
        permissionsByUid[uid] = SellerPermissionSnapshot(
            uid = uid,
            sellerEnabled = enabled,
            lastMode = lastMode
        )
    }

    fun getStoredProfileForTest(uid: String): UserProfile? = privateProfilesByUid[uid]

    override suspend fun signIn(email: String, password: String): Result<Unit> {
        if (simulateOffline) return Result.failure(IOException("Không có kết nối mạng"))
        _session.value = AuthSession.SignedIn(UID_SELLER_READY)
        return Result.success(Unit)
    }

    override suspend fun signUp(email: String, password: String): Result<Unit> {
        if (simulateOffline) return Result.failure(IOException("Không có kết nối mạng"))
        _session.value = AuthSession.SignedIn(UID_BUYER_ONLY)
        return Result.success(Unit)
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        _session.value = AuthSession.Guest
        return Result.success(Unit)
    }

    override suspend fun getMyProfile(): Result<UserProfile?> {
        if (simulateOffline) return Result.failure(IOException("Không thể tải hồ sơ do mất mạng"))
        val signedIn = _session.value as? AuthSession.SignedIn
            ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))
        return Result.success(privateProfilesByUid[signedIn.uid])
    }

    override suspend fun updateMyProfile(
        displayName: String,
        school: String,
        faculty: String
    ): Result<Unit> {
        if (simulateOffline) return Result.failure(IOException("Không thể lưu hồ sơ do mất kết nối mạng"))
        val signedIn = _session.value as? AuthSession.SignedIn
            ?: return Result.failure(IllegalStateException("Chưa đăng nhập"))

        // Chỉ cập nhật đúng các trường trong allowlist của đúng UID đang đăng nhập
        val current = privateProfilesByUid[signedIn.uid] ?: UserProfile(uid = signedIn.uid)
        val updated = current.copy(
            displayName = displayName,
            school = school,
            faculty = faculty
        )
        privateProfilesByUid[signedIn.uid] = updated

        publicProfilesBySellerId[signedIn.uid]?.let { pub ->
            publicProfilesBySellerId[signedIn.uid] = pub.copy(
                displayName = displayName,
                school = school,
                faculty = faculty
            )
        }
        return Result.success(Unit)
    }

    override suspend fun getSellerPermission(uid: String): Result<SellerPermissionSnapshot> {
        if (simulateOffline) return Result.failure(IOException("Không thể kiểm tra quyền Seller do mất mạng"))
        val signedIn = _session.value as? AuthSession.SignedIn
        if (signedIn == null || signedIn.uid != uid) {
            return Result.failure(SecurityException("Không có quyền đọc hồ sơ của tài khoản khác"))
        }
        val snapshot = permissionsByUid[uid] ?: SellerPermissionSnapshot(uid = uid, sellerEnabled = false)
        return Result.success(snapshot)
    }

    override suspend fun updateLastMode(uid: String, mode: UniAppMode): Result<Unit> {
        if (simulateOffline) return Result.failure(IOException("Không có kết nối mạng"))
        val signedIn = _session.value as? AuthSession.SignedIn
        if (signedIn == null || signedIn.uid != uid) {
            return Result.failure(SecurityException("Không thể đổi chế độ cho tài khoản khác"))
        }
        val existing = permissionsByUid[uid] ?: SellerPermissionSnapshot(uid = uid, sellerEnabled = false)
        if (mode == UniAppMode.SELLER && !existing.sellerEnabled) {
            return Result.failure(SecurityException("Tài khoản chưa được máy chủ cấp quyền Người bán"))
        }
        permissionsByUid[uid] = existing.copy(lastMode = mode)
        return Result.success(Unit)
    }

    override suspend fun requestSellerEnrollment(
        uid: String,
        confirmTerms: Boolean
    ): Result<SellerPermissionSnapshot> {
        if (simulateOffline) return Result.failure(IOException("Không thể đăng ký quyền Người bán do mất mạng"))
        val signedIn = _session.value as? AuthSession.SignedIn
        if (signedIn == null || signedIn.uid != uid) {
            return Result.failure(SecurityException("Phiên đăng nhập không hợp lệ"))
        }
        if (!confirmTerms || denySellerEnrollment) {
            return Result.failure(SecurityException("Máy chủ từ chối cấp quyền Người bán: hồ sơ chưa đủ điều kiện."))
        }
        val granted = SellerPermissionSnapshot(
            uid = uid,
            sellerEnabled = true,
            lastMode = UniAppMode.SELLER
        )
        permissionsByUid[uid] = granted
        return Result.success(granted)
    }

    override suspend fun getSellerPublicProfile(sellerId: String): Result<SellerPublicProfile?> {
        if (simulateOffline) return Result.failure(IOException("Không thể tải hồ sơ người bán do mất mạng"))
        return Result.success(publicProfilesBySellerId[sellerId])
    }

    companion object {
        const val UID_SELLER_READY = "uid_student_01"
        const val UID_BUYER_ONLY = "uid_student_02"
    }
}
