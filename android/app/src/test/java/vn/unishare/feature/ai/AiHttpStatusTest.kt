package vn.unishare.feature.ai

import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unishare.feature.ai.ui.AiChatFailure
import vn.unishare.feature.ai.ui.mapAiHttpStatus

class AiHttpStatusTest {

    @Test
    fun unauthorized401() {
        assertTrue(mapAiHttpStatus(401) is AiChatFailure.Unauthorized)
    }

    @Test
    fun forbidden403() {
        assertTrue(mapAiHttpStatus(403) is AiChatFailure.Forbidden)
    }

    @Test
    fun quota429() {
        assertTrue(mapAiHttpStatus(429) is AiChatFailure.QuotaExceeded)
    }

    @Test
    fun processing409() {
        assertTrue(mapAiHttpStatus(409) is AiChatFailure.ServiceUnavailable)
    }

    @Test
    fun invalidRequests() {
        for (status in listOf(400, 404, 422)) {
            assertTrue(mapAiHttpStatus(status) is IllegalArgumentException)
        }
    }

    @Test
    fun providerUnavailable503() {
        assertTrue(mapAiHttpStatus(503) is AiChatFailure.ServiceUnavailable)
    }
}