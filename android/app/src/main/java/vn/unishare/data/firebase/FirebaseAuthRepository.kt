package vn.unishare.data.firebase

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import vn.unishare.domain.repository.AuthRepository
import vn.unishare.domain.repository.AuthSession

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
    private val context: Context? = null
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

    override suspend fun signInWithGoogle(idToken: String): Result<Unit> = runCatching {
        firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
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
        try {
            context?.let { CredentialManager.create(it).clearCredentialState(ClearCredentialStateRequest()) }
        } catch (_: ClearCredentialException) {
            // Firebase sign-out already succeeded.
        }
        Unit
    }
}
