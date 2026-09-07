package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChatRuntimeModerationIdempotencyTest {
    @Test
    fun weakerReplaysDoNotDowngradeExistingBan() {
        val holder = ChatRuntimeStateHolder()
        holder.append(message("target", "user-a"))

        assertEquals(
            1,
            holder.markUserMessagesDeleted(
                channelId = CHANNEL_ID,
                userId = "user-a",
                atMillis = 10L,
                action = ModerationAction.BAN,
            ),
        )
        assertEquals(
            0,
            holder.markUserMessagesDeleted(
                channelId = CHANNEL_ID,
                userId = "user-a",
                atMillis = 20L,
            ),
        )
        assertFalse(
            holder.markMessageDeleted(
                channelId = CHANNEL_ID,
                messageId = "target",
                atMillis = 30L,
            ),
        )

        val target = holder.messages(CHANNEL_ID).single()
        assertEquals(ModerationAction.BAN, target.moderation.action)
        assertEquals(10L, target.moderation.atMillis)
    }

    @Test
    fun strongerMutationUpgradesExistingModerationState() {
        val holder = ChatRuntimeStateHolder()
        holder.append(message("target", "user-a"))

        assertEquals(
            1,
            holder.markUserMessagesDeleted(
                channelId = CHANNEL_ID,
                userId = "user-a",
                atMillis = 10L,
                action = ModerationAction.TIMEOUT,
            ),
        )
        assertEquals(
            1,
            holder.markUserMessagesDeleted(
                channelId = CHANNEL_ID,
                userId = "user-a",
                atMillis = 20L,
                action = ModerationAction.BAN,
            ),
        )

        val target = holder.messages(CHANNEL_ID).single()
        assertEquals(ModerationAction.BAN, target.moderation.action)
        assertEquals(20L, target.moderation.atMillis)
    }

    private fun message(id: String, userId: String) = ChatMessage(
        id = id,
        channelId = CHANNEL_ID,
        channelLogin = "channel",
        author = ChatAuthor(
            id = userId,
            login = userId,
            displayName = userId,
        ),
        text = id,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = 1L,
    )

    private companion object {
        const val CHANNEL_ID = "channel-id"
    }
}
