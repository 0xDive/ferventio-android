package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScrollRestorationPolicyTest {
    @Test
    fun `restores by message id after list indices change`() {
        val messages = listOf(message("new-1"), message("anchor"), message("new-2"))
        val saved = ChatScrollPosition(
            channelId = "channel",
            anchorMessageId = "anchor",
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 12,
            isAtBottom = false,
        )

        assertEquals(1, ScrollRestorationPolicy.targetIndex(messages, saved))
    }

    @Test
    fun `falls back to bounded numeric index`() {
        val messages = listOf(message("1"), message("2"))
        val saved = ChatScrollPosition(
            channelId = "channel",
            anchorMessageId = "missing",
            firstVisibleItemIndex = 50,
            firstVisibleItemScrollOffset = 0,
            isAtBottom = false,
        )

        assertEquals(1, ScrollRestorationPolicy.targetIndex(messages, saved))
    }


    @Test
    fun `opens latest message when previous session followed live chat`() {
        val messages = listOf(message("1"), message("2"), message("3"))
        val saved = ChatScrollPosition(
            channelId = "channel",
            anchorMessageId = "1",
            firstVisibleItemIndex = 0,
            firstVisibleItemScrollOffset = 20,
            isAtBottom = true,
        )

        assertEquals(2, ScrollRestorationPolicy.targetIndex(messages, saved))
    }

    @Test
    fun `returns null for empty history`() {
        assertNull(ScrollRestorationPolicy.targetIndex(emptyList(), null))
    }

    private fun message(id: String): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(id = "user", login = "user", displayName = "User"),
        text = id,
        timestamp = "2026-07-21T00:00:00Z",
    )
}
