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

    suspend fun updateMyProfile(
        displayName: String,
        school: String,
        faculty: String
    ): Result<Unit>
}
