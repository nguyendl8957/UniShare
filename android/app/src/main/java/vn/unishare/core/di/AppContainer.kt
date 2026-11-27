package vn.unishare.core.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import vn.unishare.data.firebase.FirebaseAuthRepository
import vn.unishare.data.firebase.FirestoreProfileRepository
import vn.unishare.data.remote.OrderApiAdapter
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.ProfileRepository
import vn.unishare.feature.payment.history.BuyerTransactionsRepository
import vn.unishare.feature.payment.history.FakeBuyerTransactionsRepository
import vn.unishare.feature.payment.ui.ApiOrderRepository
import vn.unishare.feature.payment.ui.OrderRepository

class AppContainer {

    val appName: String = "UniShare"

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(
            firebaseAuth = FirebaseAuth.getInstance()
        )
    }

    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(
            auth = FirebaseAuth.getInstance(),
            firestore = FirebaseFirestore.getInstance()
        )
    }

    /**
     * B04 Checkout uses A07 authenticated Order API.
     *
     * Caller must supply an explicit backend URL.
     * There is deliberately no fake or production bank account.
     */
    fun createOrderRepository(apiBaseUrl: String): OrderRepository {
        return ApiOrderRepository(
            api = OrderApiAdapter(
                auth = FirebaseAuth.getInstance(),
                apiBaseUrl = apiBaseUrl
            )
        )
    }

    /**
     * B05 Buyer Transactions: Tạm dùng FakeBuyerTransactionsRepository với dữ liệu
     * hai Buyer mẫu trong lúc chờ Leader bàn giao backend GET /v1/me/orders và adapter thật.
     */
    fun createBuyerTransactionsRepository(): BuyerTransactionsRepository {
        return FakeBuyerTransactionsRepository()
    }
}
