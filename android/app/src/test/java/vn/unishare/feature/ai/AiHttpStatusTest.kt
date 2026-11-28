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
    fun configurationAndPdfFailuresHaveDistinctMessagesAndDoNotRetry() {
        val codes = listOf("AI_DISABLED", "AI_NOT_ALLOWED", "AI_UNCONFIGURED", "PDF_TEXT_UNAVAILABLE")
        val failures = codes.map { mapAiHttpStatus(503, it) as AiChatFailure.ServiceUnavailable }

        assertTrue(failures.none { it.retryable })
        assertTrue(failures.map { it.message }.distinct().size == codes.size)
    }

    @Test
    fun geminiThrottleAndTimeoutAllowManualRetry() {
        for (code in listOf("GEMINI_RATE_LIMIT", "GEMINI_TIMEOUT", "GEMINI_UNAVAILABLE")) {
            val failure = mapAiHttpStatus(503, code) as AiChatFailure.ServiceUnavailable
            assertTrue(failure.retryable)
        }
        assertTrue(mapAiHttpStatus(429, "QUOTA_EXCEEDED") is AiChatFailure.QuotaExceeded)
    }

    @Test
    fun providerUnavailable503() {
        assertTrue(mapAiHttpStatus(503) is AiChatFailure.ServiceUnavailable)
    }
}