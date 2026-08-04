package io.ferventio.app.crash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportingPolicyTest {
    @Test
    fun `collection is limited to configured production Play runs`() {
        assertTrue(
            CrashReportingPolicy.shouldCollect(
                isDebug = false,
                performanceTesting = false,
                firebaseConfigured = true,
            ),
        )
        assertFalse(CrashReportingPolicy.shouldCollect(true, false, true))
        assertFalse(CrashReportingPolicy.shouldCollect(false, true, true))
        assertFalse(CrashReportingPolicy.shouldCollect(false, false, false))
    }

    @Test
    fun `non fatal sanitizer removes credentials and preserves failure location`() {
        val original = IllegalStateException(
            "Authorization: Bearer super-secret-token https://example.invalid/callback?state=very-secret-state",
        ).apply {
            stackTrace = arrayOf(StackTraceElement("Example", "run", "Example.kt", 42))
        }

        val sanitized = CrashReportSanitizer.sanitize("token_lease", original)

        assertFalse(sanitized.message.orEmpty().contains("super-secret-token"))
        assertFalse(sanitized.message.orEmpty().contains("very-secret-state"))
        assertTrue(sanitized.message.orEmpty().contains("<redacted>"))
        assertArrayEquals(original.stackTrace, sanitized.stackTrace)
        assertNull(sanitized.cause)
    }
}
