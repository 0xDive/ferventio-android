package io.ferventio.app.twitch

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoModEventOrderingGuardTest {
    @Test
    fun `terminal update blocks a delayed hold`() {
        val guard = AutoModEventOrderingGuard()

        assertTrue(guard.shouldAcceptHold("message"))
        guard.markTerminal("message")
        assertFalse(guard.shouldAcceptHold("message"))
    }

    @Test
    fun `bounded history eventually releases oldest ids`() {
        val guard = AutoModEventOrderingGuard(capacity = 2)
        guard.markTerminal("one")
        guard.markTerminal("two")
        guard.markTerminal("three")

        assertTrue(guard.shouldAcceptHold("one"))
        assertFalse(guard.shouldAcceptHold("two"))
        assertFalse(guard.shouldAcceptHold("three"))
    }
}
