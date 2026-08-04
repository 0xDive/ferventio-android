package io.ferventio.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class EventSubBootstrapPolicyTest {
    @Test
    fun `primary chat is sufficient when notice subscription fails`() {
        assertTrue(EventSubBootstrapPolicy.acceptPrimaryChat(primaryError = null))
        assertEquals(
            "Чат #ferventio подключён; системные события и остальные подписки настраиваются…",
            EventSubBootstrapPolicy.connectedDetail("ferventio", noticeReady = false),
        )
    }

    @Test
    fun `primary chat failure requires trying another channel`() {
        assertFalse(EventSubBootstrapPolicy.acceptPrimaryChat(IllegalStateException("denied")))
    }

    @Test
    fun `ready notice keeps full connected detail`() {
        assertEquals(
            "Чат и системные события #ferventio подключены; остальные подписки настраиваются…",
            EventSubBootstrapPolicy.connectedDetail("ferventio", noticeReady = true),
        )
    }
}
