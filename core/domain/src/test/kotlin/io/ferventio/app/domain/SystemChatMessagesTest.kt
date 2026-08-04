package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemChatMessagesTest {
    @Test
    fun `timeout is rendered as compact gray system message`() {
        val message = SystemChatMessages.moderation(
            RemoteModerationAction(
                id = "event-1",
                channelId = "100",
                channelLogin = "channel",
                channelName = "Channel",
                moderatorId = "200",
                moderatorLogin = "mod",
                moderatorName = "Moderator",
                action = "timeout",
                targetUserId = "300",
                targetUserLogin = "viewer",
                targetUserName = "Viewer",
                reason = "spam",
                durationSeconds = 600,
                createdAt = "2026-08-02T08:00:00Z",
            ),
        )

        assertEquals(ChatMessageType.MODERATION, message.type)
        assertTrue(message.isSystem)
        assertEquals("Moderator выдал @Viewer таймаут на 10 мин. Причина: spam", message.text)
    }

    @Test
    fun `anonymous clearchat duration becomes timeout notice`() {
        val message = SystemChatMessages.userMessagesCleared(
            channelId = "100",
            channelLogin = "channel",
            userId = "300",
            userLogin = "viewer",
            durationSeconds = 90,
            isPermanent = false,
            eventId = "clear-1",
            createdAt = "2026-08-02T08:00:00Z",
        )

        assertEquals("@viewer получил таймаут на 90 сек.", message.text)
        assertTrue(message.isSystem)
    }

    @Test
    fun `anonymous clearchat without duration becomes ban notice`() {
        val message = SystemChatMessages.userMessagesCleared(
            channelId = "100",
            channelLogin = "channel",
            userId = "300",
            userLogin = "viewer",
            durationSeconds = null,
            isPermanent = true,
            eventId = "clear-2",
            createdAt = "2026-08-02T08:00:00Z",
        )

        assertEquals("@viewer заблокирован.", message.text)
        assertTrue(message.isSystem)
    }

    @Test
    fun `clear event without moderation detail stays neutral`() {
        val message = SystemChatMessages.userMessagesCleared(
            channelId = "100",
            channelLogin = "channel",
            userId = "300",
            userLogin = "viewer",
            durationSeconds = null,
            isPermanent = null,
            eventId = "clear-3",
            createdAt = "2026-08-02T08:00:00Z",
        )

        assertEquals("Сообщения @viewer удалены модератором.", message.text)
        assertTrue(message.isSystem)
    }
}
