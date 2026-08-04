package io.ferventio.app.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCrashReportTest {
    @Test
    fun `codec exports sanitized report without recovering secret`() {
        val original = IllegalStateException(
            "Authorization: Bearer super-secret-token https://example.test/oauth?state=secret-state",
        ).apply {
            stackTrace = arrayOf(StackTraceElement("Example", "run", "Example.kt", 42))
        }
        val sanitized = CrashReportSanitizer.sanitize("eventsub", original)
        val report = report(
            id = "report-1",
            timestamp = 1_000L,
            summary = sanitized.message.orEmpty(),
            stackTrace = sanitized.stackTrace.map(StackTraceElement::toString),
        )

        val encoded = LocalCrashReportCodec.encodeBundle(
            LocalCrashReportBundle(
                generatedAtEpochMillis = 2_000L,
                reports = listOf(report),
            ),
        )

        assertFalse(encoded.contains("super-secret-token"))
        assertFalse(encoded.contains("secret-state"))
        assertTrue(encoded.contains("<redacted>"))
        assertTrue(encoded.contains("Example.run(Example.kt:42)"))
    }

    @Test
    fun `codec round trip preserves bounded diagnostic fields`() {
        val report = report(
            id = "report-2",
            timestamp = 2_000L,
            summary = "[database] java.lang.IllegalStateException: failed",
            stackTrace = listOf("Example.run(Example.kt:24)"),
        )

        val decoded = LocalCrashReportCodec.decodeReport(
            LocalCrashReportCodec.encodeReport(report),
        )

        assertEquals(report, decoded)
    }

    @Test
    fun `retention keeps newest reports inside age window`() {
        val now = 100_000L
        val reports = listOf(
            report("old", 10_000L),
            report("first", 80_000L),
            report("newest", 99_000L),
            report("future", 101_000L),
        )

        val retained = LocalCrashReportRetention.retainNewest(
            reports = reports,
            nowEpochMillis = now,
            maxAgeMillis = 30_000L,
            maxReports = 2,
        )

        assertEquals(listOf("newest", "first"), retained.map(LocalCrashReport::id))
    }

    private fun report(
        id: String,
        timestamp: Long,
        summary: String = "summary",
        stackTrace: List<String> = emptyList(),
    ): LocalCrashReport = LocalCrashReport(
        id = id,
        createdAtEpochMillis = timestamp,
        fatal = false,
        threadName = "test",
        summary = summary,
        stackTrace = stackTrace,
        breadcrumbs = listOf("4/Test connected"),
        appVersionName = "0.0.1-test",
        appVersionCode = 74,
        buildType = "release",
    )
}
