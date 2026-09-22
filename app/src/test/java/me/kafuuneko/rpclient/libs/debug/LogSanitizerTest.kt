package me.kafuuneko.rpclient.libs.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {
    @Test
    fun removesQuotedUnquotedAndHeaderCredentials() {
        val values = listOf(
            "api_key: fixture-secret-1",
            "\"apiKey\": \"fixture-secret-1 with spaces\"",
            "'password'='fixture-secret-1 with spaces'",
            "x-api-key=fixture-secret-1",
            "Authorization: Bearer fixture-secret-1",
            "Authorization: Basic Zml4dHVyZTpzZWNyZXQ=",
            "token=fixture-secret-1&success=true"
        )
        for (value in values) {
            val safe = LogSanitizer.redact(value)
            assertFalse(value, safe.contains("fixture-secret-1"))
            assertFalse(value, safe.contains("Zml4dHVyZTpzZWNyZXQ"))
            assertTrue(value, safe.contains("***"))
        }
    }

    @Test
    fun removesAllUrlParametersFragmentsAndUserInfo() {
        val safe = LogSanitizer.redact(
            "failed https://user:fixture-password@example.invalid/v1?custom=fixture-secret#fixture-fragment"
        )
        assertEquals("failed https://***@example.invalid/v1", safe)
    }

    @Test
    fun preservesUsefulCountersAndOrdinaryText() {
        val message = "token_count=12 inputTokens=30 request completed in 28ms"
        assertEquals(message, LogSanitizer.redact(message))
    }

    @Test
    fun exceptionMessagesNeverEnterTheStackSummary() {
        val error = IllegalStateException("fixture-private-body-8291", RuntimeException("fixture-credential-9713"))
        val safe = LogSanitizer.throwableSummary(error)
        assertTrue(safe.contains("IllegalStateException"))
        assertTrue(safe.contains("RuntimeException"))
        assertFalse(safe.contains("fixture-private-body-8291"))
        assertFalse(safe.contains("fixture-credential-9713"))
    }
}
