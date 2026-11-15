package vn.unishare.data.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth
) : AuthRepository {

    private val _session = MutableStateFlow<AuthSession>(
        firebaseAuth.currentUser?.let {
            AuthSession.SignedIn(it.uid)
        } ?: AuthSession.Guest
    )

    override val session: StateFlow<AuthSession> = _session

    private val authListener =
        FirebaseAuth.AuthStateListener { auth ->
            _session.value = auth.currentUser?.let {
                AuthSession.SignedIn(it.uid)
            } ?: AuthSession.Guest
        }

    init {
        firebaseAuth.addAuthStateListener(authListener)
    }

    override suspend fun signIn(
        email: String,
        password: String
    ): Result<Unit> = runCatching {
        firebaseAuth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun signUp(
        email: String,
        password: String
    ): Result<Unit> = runCatching {
        firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        Unit
    }

    override suspend fun sendPasswordReset(
        email: String
    ): Result<Unit> = runCatching {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Unit
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        firebaseAuth.signOut()
        Unit
    }
}
