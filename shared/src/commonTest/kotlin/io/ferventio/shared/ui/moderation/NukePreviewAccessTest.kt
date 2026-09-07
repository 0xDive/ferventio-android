package io.ferventio.shared.ui.moderation

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NukePreviewAccessTest {
    @Test
    fun moderatorChannelCanOpenPreview() {
        assertTrue(canPreviewNuke("channel-1", setOf("channel-1")))
    }

    @Test
    fun nonModeratorChannelCannotOpenPreview() {
        assertFalse(canPreviewNuke("channel-1", setOf("channel-2")))
        assertFalse(canPreviewNuke("", setOf("")))
    }

    @Test
    fun nukePreviewExcludesDurableHistoryOverlay() {
        val chat = ChatRuntimeStateHolder()
        chat.append(message("live", 20L))
        chat.prependHistory("channel-1", listOf(message("history", 10L)))

        assertEquals(listOf("history", "live"), chat.messages("channel-1").map(ChatMessage::id))
        assertEquals(listOf("live"), nukePreviewMessages(chat, "channel-1").map(ChatMessage::id))
    }

    private fun message(id: String, timestampMillis: Long) = ChatMessage(
        id = id,
        channelId = "channel-1",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user-$id",
            displayName = "User $id",
        ),
        text = id,
        timestamp = "2026-08-19T00:00:00Z",
        timestampMillis = timestampMillis,
    )
}
