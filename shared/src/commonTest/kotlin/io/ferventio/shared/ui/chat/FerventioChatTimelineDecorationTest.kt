package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FerventioChatTimelineDecorationTest {
    @Test
    fun timelineDecorationProjectionIgnoresOtherMessageIds() {
        val current = listOf(
            message("one"),
            message("two"),
        )
        val oneDecoration = MessageDecoration(highlightColorArgb = 0xFF112233L)
        val result = selectTimelineDecorations(
            messages = current,
            decorations = mapOf(
                "one" to oneDecoration,
                "other-channel-message" to MessageDecoration(
                    ignoreDisplayMode = IgnoreDisplayMode.HIDE,
                ),
            ),
        )

        assertEquals(mapOf("one" to oneDecoration), result)
    }

    @Test
    fun timelineDecorationProjectionReturnsSharedEmptyMapWhenNothingIsRelevant() {
        val result = selectTimelineDecorations(
            messages = listOf(message("one")),
            decorations = mapOf(
                "other" to MessageDecoration(highlightColorArgb = 0xFF445566L),
            ),
        )

        assertTrue(result.isEmpty())
    }

    private fun message(id: String) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user_$id",
            displayName = "User $id",
        ),
        text = id,
        timestamp = "2026-09-20T00:00:00Z",
    )
}
