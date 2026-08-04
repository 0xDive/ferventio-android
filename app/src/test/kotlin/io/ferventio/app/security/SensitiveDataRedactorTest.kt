package io.ferventio.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataRedactorTest {
    @Test
    fun redactsAuthorizationHeadersAndBearerTokens() {
        val input = "Authorization: Bearer abcdefghijklmnopqrstuvwxyz"
        val output = SensitiveDataRedactor.redact(input).orEmpty()

        assertEquals("Authorization: Bearer <redacted>", output)
        assertFalse(output.contains("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun redactsOAuthQueryAndJsonFields() {
        val input = "https://example.test/callback?code=secret-code&state=secret-state " +
            "{\"sessionToken\":\"session-secret\",\"deviceSecret\":\"device-secret\"}"
        val output = SensitiveDataRedactor.redact(input).orEmpty()

        assertFalse(output.contains("secret-code"))
        assertFalse(output.contains("secret-state"))
        assertFalse(output.contains("session-secret"))
        assertFalse(output.contains("device-secret"))
        assertTrue(output.count { it == '<' } >= 4)
    }

    @Test
    fun redactsIrcOAuthAndJwtValues() {
        val input = "PASS oauth:abcdefghijklmnopqrstuv " +
            "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.signature123" // gitleaks:allow
        val output = SensitiveDataRedactor.redact(input).orEmpty()

        assertEquals("PASS oauth:<redacted> <redacted>", output)
    }

    @Test
    fun urlForLogDropsQueryAndFragment() {
        val output = SensitiveDataRedactor.urlForLog(
            "wss://eventsub.example.test/ws?sessionToken=secret#fragment",
        )

        assertEquals("wss://eventsub.example.test/ws?<redacted>#<redacted>", output)
    }

    @Test
    fun leavesOrdinaryDiagnosticsUntouched() {
        val input = "EventSub subscription revoked: channel.chat.message (authorization_revoked)"
        assertEquals(input, SensitiveDataRedactor.redact(input))
    }
}
