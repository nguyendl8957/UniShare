package vn.unishare.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import vn.unishare.domain.repository.ProfileRepository
import vn.unishare.domain.repository.UserProfile
import com.google.firebase.firestore.SetOptions

class FirestoreProfileRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ProfileRepository {

    override suspend fun getMyProfile(): Result<UserProfile?> =
        runCatching {
            val uid = requireNotNull(auth.currentUser?.uid) {
                "Bạn chưa đăng nhập"
            }

            val document = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            if (!document.exists()) {
                null
            } else {
                UserProfile(
                    uid = uid,
                    displayName = document.getString("displayName").orEmpty(),
                    school = document.getString("school").orEmpty(),
                    faculty = document.getString("faculty").orEmpty(),
                    avatarUrl = document.getString("avatarUrl")
                )
            }
        }

    override suspend fun updateMyProfile(
        displayName: String,
        school: String,
        faculty: String
    ): Result<Unit> = runCatching {
        val uid = requireNotNull(auth.currentUser?.uid) {
            "Bạn chưa đăng nhập"
        }

        firestore.collection("users")
            .document(uid)
            .set(
                mapOf(
                    "displayName" to displayName.trim(),
                    "school" to school.trim(),
                    "faculty" to faculty.trim()
                ),
                SetOptions.merge()
            )
            .await()

        Unit
    }
}
