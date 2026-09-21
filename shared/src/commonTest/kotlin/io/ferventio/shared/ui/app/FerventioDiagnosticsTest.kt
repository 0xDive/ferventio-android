package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FerventioDiagnosticsTest {
    @Test
    fun compactEventSubSessionIdKeepsDiagnosticsReadable() {
        assertEquals("short-session", compactEventSubSessionId("short-session"))
        assertEquals(
            "abcdefgh…uvwxyz",
            compactEventSubSessionId("abcdefghijklmnopqrstuvwxyz"),
        )
    }

    @Test
    fun diagnosticReportContainsOnlySafeStatusAndCounters() {
        val report = FerventioDiagnosticsSnapshot(
            versionName = "1.2.3",
            connectionStatus = "CONNECTED",
            connectionAttempt = 2,
            authenticationRequired = false,
            workspaceLoadStatus = "READY",
            workspaceChannels = 4,
            moderatorChannels = 2,
            settingsRevision = 17L,
            liveChannels = 3,
            liveMessages = 42,
            historyAvailable = true,
            eventSubTransportLimitReached = false,
        ).toDiagnosticReport()

        assertTrue(report.startsWith("Ferventio diagnostics\n"))
        assertTrue("version=1.2.3" in report)
        assertTrue("eventsub.status=CONNECTED" in report)
        assertTrue("eventsub.attempt=2" in report)
        assertTrue("eventsub.transport_limit=false" in report)
        assertTrue("workspace.channels=4" in report)
        assertTrue("chat.live_messages=42" in report)
        assertTrue("history.available=true" in report)
        assertFalse("channel_id" in report)
        assertFalse("message=" in report)
        assertFalse("token" in report)
        assertFalse("error=" in report)
        assertEquals(13, report.lines().size)
    }
}
