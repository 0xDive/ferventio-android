package io.ferventio.shared.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ChatHistoryPagingGateTest {
    @Test
    fun rejectsDuplicateRequestWhileBoundaryIsInFlight() {
        val gate = ChatHistoryPagingGate()

        val first = gate.tryStart(timestampMillis = 2_000L, messageId = "message-2")

        assertNotNull(first)
        assertNull(gate.tryStart(timestampMillis = 2_000L, messageId = "message-2"))
        assertNull(gate.tryStart(timestampMillis = 1_000L, messageId = "message-1"))
    }

    @Test
    fun emptyPageMarksOnlyThatBoundaryExhausted() {
        val gate = ChatHistoryPagingGate()
        val exhausted = requireNotNull(gate.tryStart(2_000L, "message-2"))
        gate.finish(exhausted, loadedCount = 0)

        assertNull(gate.tryStart(2_000L, "message-2"))
        assertNotNull(gate.tryStart(1_000L, "message-1"))
    }

    @Test
    fun successfulOrCancelledRequestAllowsAnotherAttempt() {
        val gate = ChatHistoryPagingGate()
        val successful = requireNotNull(gate.tryStart(2_000L, "message-2"))
        gate.finish(successful, loadedCount = 25)
        val repeated = gate.tryStart(2_000L, "message-2")
        assertEquals(successful, repeated)

        gate.cancel(requireNotNull(repeated))
        assertEquals(successful, gate.tryStart(2_000L, "message-2"))
    }
}
