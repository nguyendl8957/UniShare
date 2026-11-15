package vn.unishare.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeAuthRepository : AuthRepository {

    private val _session =
        MutableStateFlow<AuthSession>(AuthSession.Guest)

    override val session: StateFlow<AuthSession> = _session

    override suspend fun signIn(
        email: String,
        password: String
    ): Result<Unit> {
        _session.value = AuthSession.SignedIn("fake-user-001")
        return Result.success(Unit)
    }

    override suspend fun signUp(
        email: String,
        password: String
    ): Result<Unit> = signIn(email, password)

    override suspend fun sendPasswordReset(
        email: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun signOut(): Result<Unit> {
        _session.value = AuthSession.Guest
        return Result.success(Unit)
    }
}

class FakeProfileRepository : ProfileRepository {

    private var profile: UserProfile? = null

    override suspend fun getMyProfile(): Result<UserProfile?> =
        Result.success(profile)

    override suspend fun updateMyProfile(
        displayName: String,
        school: String,
        faculty: String
    ): Result<Unit> {
        profile = UserProfile(
            uid = "fake-user-001",
            displayName = displayName,
            school = school,
            faculty = faculty
        )
        return Result.success(Unit)
    }
}
