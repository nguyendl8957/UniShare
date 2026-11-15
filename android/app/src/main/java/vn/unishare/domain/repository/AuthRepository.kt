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

    suspend fun sendPasswordReset(
        email: String
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>
}
