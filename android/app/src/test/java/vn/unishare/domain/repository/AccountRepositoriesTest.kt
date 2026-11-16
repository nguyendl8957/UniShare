package vn.unishare.domain.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRepositoriesTest {

    @Test
    fun signInChangesGuestToSignedIn() = runBlocking {
        val repository = FakeAuthRepository()

        assertEquals(AuthSession.Guest, repository.session.value)

        val result = repository.signIn("test@example.com", "password123")

        assertTrue(result.isSuccess)
        assertEquals(
            AuthSession.SignedIn("fake-user-001"),
            repository.session.value
        )
    }

    @Test
    fun signOutReturnsToGuest() = runBlocking {
        val repository = FakeAuthRepository()
        repository.signIn("test@example.com", "password123")

        assertTrue(repository.signOut().isSuccess)
        assertEquals(AuthSession.Guest, repository.session.value)
    }

    @Test
    fun profileCanBeCreatedAndRead() = runBlocking {
        val repository = FakeProfileRepository()

        assertNull(repository.getMyProfile().getOrThrow())

        repository.updateMyProfile(
            displayName = "Sinh vien UniShare",
            school = "UTH",
            faculty = "CNTT"
        ).getOrThrow()

        val profile = repository.getMyProfile().getOrThrow()

        assertEquals("fake-user-001", profile?.uid)
        assertEquals("Sinh vien UniShare", profile?.displayName)
        assertEquals("UTH", profile?.school)
    }
}