package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoModEventOrderingGuardTest {
    @Test
    fun terminalUpdateRejectsDelayedHold() {
        val guard = AutoModEventOrderingGuard()

        guard.markTerminal("message-id")

        assertFalse(guard.shouldAcceptHold("message-id"))
        assertTrue(guard.shouldAcceptHold("other-id"))
    }

    @Test
    fun boundedGuardEvictsOldestTerminalId() {
        val guard = AutoModEventOrderingGuard(capacity = 2)

        guard.markTerminal("one")
        guard.markTerminal("two")
        guard.markTerminal("three")

        assertTrue(guard.shouldAcceptHold("one"))
        assertFalse(guard.shouldAcceptHold("two"))
        assertFalse(guard.shouldAcceptHold("three"))
    }
}
