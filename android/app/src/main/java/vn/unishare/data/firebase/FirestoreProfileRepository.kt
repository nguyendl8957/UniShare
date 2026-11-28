package vn.unishare.data.firebase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import vn.unishare.domain.repository.ProfileRepository
import vn.unishare.domain.repository.UserProfile
import com.google.firebase.firestore.SetOptions

class FirestoreProfileRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val context: Context? = null
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

    override suspend fun updateMyProfileWithAvatar(
        displayName: String,
        school: String,
        faculty: String,
        imageUri: String,
        previousPath: String?
    ): Result<String> = runCatching {
        val uid = requireNotNull(auth.currentUser?.uid) { "Bạn chưa đăng nhập" }
        val resolver = requireNotNull(context).contentResolver
        val uri = Uri.parse(imageUri)
        val image = withContext(Dispatchers.IO) {
            require(resolver.getType(uri) in setOf("image/jpeg", "image/png", "image/webp")) {
                "Chọn ảnh JPG, PNG hoặc WebP."
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 5 * 1024 * 1024) { "Ảnh vượt quá 5 MB." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } ?: error("Không đọc được ảnh đã chọn.")
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "Ảnh đã chọn không hợp lệ." }
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > 1024) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                ?: error("Không đọc được ảnh đã chọn.")
            try {
                ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) { "Không thể xử lý ảnh." }
                    output.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
        check(auth.currentUser?.uid == uid) { "Phiên đăng nhập đã thay đổi." }
        val storage = FirebaseStorage.getInstance().reference
        val prefix = "avatars/$uid/"
        val path = "$prefix${UUID.randomUUID()}.jpg"
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        storage.child(path).putBytes(image, metadata).await()
        check(auth.currentUser?.uid == uid) { "Phiên đăng nhập đã thay đổi." }
        firestore.collection("users").document(uid).set(
            mapOf("displayName" to displayName, "school" to school, "faculty" to faculty, "avatarUrl" to path),
            SetOptions.merge()
        ).await()
        if (previousPath != null && previousPath.startsWith(prefix) && auth.currentUser?.uid == uid) {
            storage.child(previousPath).delete()
        }
        path
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
