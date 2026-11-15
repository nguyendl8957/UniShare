package vn.unishare.core.di

import com.google.firebase.auth.FirebaseAuth
import vn.unishare.data.firebase.FirebaseAuthRepository
import vn.unishare.domain.repository.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import vn.unishare.data.firebase.FirestoreProfileRepository
import vn.unishare.domain.repository.ProfileRepository

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
}
