package vn.unishare.feature.profile.ui

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import vn.unishare.core.ui.theme.UniAppMode

/**
 * Buyer-only profile integration.
 *
 * Uses the authenticated user's real Firestore permissions.
 * Seller functionality is deliberately not activated here.
 * No mock seller profiles, sales, reviews or enrollment.
 */
class BuyerProfileRoleContract(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : SellerRoleContract {

    override suspend fun getSellerPermission(
        uid: String
    ): Result<SellerPermissionSnapshot> = runCatching {
        check(auth.currentUser?.uid == uid) {
            "Phiên đăng nhập không hợp lệ."
        }

        val snapshot = firestore.collection("users")
            .document(uid)
            .get()
            .await()

        val sellerEnabled = snapshot.getBoolean("sellerEnabled") == true

        // Do not render the existing sample Seller dashboard.
        check(!sellerEnabled) {
            "Giao diện Seller thật chưa được tích hợp."
        }

        SellerPermissionSnapshot(
            uid = uid,
            sellerEnabled = false,
            lastMode = UniAppMode.BUYER
        )
    }

    override suspend fun updateLastMode(
        uid: String,
        mode: UniAppMode
    ): Result<Unit> {
        return if (
            auth.currentUser?.uid == uid &&
            mode == UniAppMode.BUYER
        ) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    "Chế độ Seller chưa được kích hoạt."
                )
            )
        }
    }

    override suspend fun requestSellerEnrollment(
        uid: String,
        confirmTerms: Boolean
    ): Result<SellerPermissionSnapshot> {
        return Result.failure(
            UnsupportedOperationException(
                "Đăng ký Seller chưa được kết nối với backend."
            )
        )
    }

    override suspend fun getSellerPublicProfile(
        sellerId: String
    ): Result<SellerPublicProfile?> {
        return Result.failure(
            UnsupportedOperationException(
                "Hồ sơ Seller công khai chưa được tích hợp."
            )
        )
    }
}