package vn.unishare.domain.repository

data class UserProfile(
    val uid: String,
    val displayName: String = "",
    val school: String = "",
    val faculty: String = "",
    val avatarUrl: String? = null
)

interface ProfileRepository {
    suspend fun getMyProfile(): Result<UserProfile?>

    suspend fun updateMyProfileWithAvatar(
        displayName: String,
        school: String,
        faculty: String,
        imageUri: String,
        previousPath: String?
    ): Result<String> = Result.failure(UnsupportedOperationException("Chưa thể cập nhật ảnh đại diện."))

    suspend fun updateMyProfile(
        displayName: String,
        school: String,
        faculty: String
    ): Result<Unit>
}
