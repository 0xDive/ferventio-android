package io.ferventio.shared.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushNavigationInboxTest {
    @Test
    fun acceptsAndConsumesResolvedTargetOnce() {
        val inbox = PushNavigationInbox()

        assertTrue(
            inbox.offer(
                channelId = "123",
                channelLogin = "Channel",
                messageId = "message",
                destination = null,
            ),
        )
        assertEquals(
            PushNavigationTarget.Message(
                channel = PushChannelReference(id = "123", login = "channel"),
                messageId = "message",
            ),
            inbox.consume(),
        )
        assertNull(inbox.consume())
    }

    @Test
    fun latestValidOfferWins() {
        val inbox = PushNavigationInbox()

        assertTrue(inbox.offer("1", "first", null, null))
        assertTrue(inbox.offer("2", "second", null, "moderation"))

        assertEquals(
            PushNavigationTarget.Moderation(
                PushChannelReference(id = "2", login = "second"),
            ),
            inbox.pendingTarget,
        )
    }

    @Test
    fun invalidOfferDoesNotDiscardPendingTarget() {
        val inbox = PushNavigationInbox()
        assertTrue(inbox.offer("1", "channel", null, null))
        val pending = inbox.pendingTarget

        assertFalse(inbox.offer(null, null, "message", null))

        assertEquals(pending, inbox.pendingTarget)
    }

    @Test
    fun clearDropsPendingTarget() {
        val inbox = PushNavigationInbox()
        assertTrue(inbox.offer("1", "channel", null, null))

        inbox.clear()

        assertNull(inbox.pendingTarget)
    }
}
