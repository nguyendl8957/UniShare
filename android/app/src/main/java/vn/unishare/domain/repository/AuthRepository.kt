package vn.unishare.domain.repository

import kotlinx.coroutines.flow.StateFlow

sealed interface AuthSession {
    data object Restoring : AuthSession
    data object Guest : AuthSession
    data class SignedIn(val uid: String) : AuthSession
}

interface AuthRepository {
    val session: StateFlow<AuthSession>

    suspend fun signIn(
        email: String,
        password: String
    ): Result<Unit>

    suspend fun signUp(
        email: String,
        password: String
    ): Result<Unit>

    suspend fun signInWithGoogle(idToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Đăng nhập Google chưa được cấu hình."))
    suspend fun sendPasswordReset(
        email: String
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>
}
