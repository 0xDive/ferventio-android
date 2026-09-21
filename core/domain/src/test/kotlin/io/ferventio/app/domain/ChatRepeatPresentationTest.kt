package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatRepeatPresentationTest {
    @Test
    fun `projects collapsed run to anchor row`() {
        val messages = listOf(
            message("1", "One", 1_000),
            message("2", "one", 1_100),
            message("3", "  ONE  ", 1_200),
            message("4", "different", 1_300),
        )

        val presentation = ChatRepeatPresentationProjector.build(messages)

        assertEquals(listOf("1", "4"), presentation.messages.map(ChatMessage::id))
        assertEquals("1", presentation.anchorFor("3"))
        assertEquals(0, presentation.visibleIndexFor("2"))
        assertSame(presentation.messages[0], presentation.visibleMessageFor("3"))
        assertEquals(3, presentation.summaryFor("2")?.count)
    }

    @Test
    fun `disabled collapse reuses canonical message list without building plan`() {
        val messages = listOf(
            message("1", "same", 1_000),
            message("2", "same", 1_100),
            message("3", "same", 1_200),
        )

        val presentation = ChatRepeatPresentationProjector.build(
            canonicalMessages = messages,
            config = ChatRepeatCollapseConfig(enabled = false),
        )

        assertSame(messages, presentation.messages)
        assertTrue(presentation.anchorByMessageId.isEmpty())
        assertTrue(presentation.summariesByAnchorId.isEmpty())
    }

    @Test
    fun `enabled collapse with no matching run reuses canonical list`() {
        val messages = listOf(
            message("1", "first", 1_000),
            message("2", "second", 1_100),
        )

        val presentation = ChatRepeatPresentationProjector.build(messages)

        assertSame(messages, presentation.messages)
    }

    @Test
    fun `uncollapsed message keeps its own visible index`() {
        val messages = listOf(
            message("1", "first", 1_000),
            message("2", "second", 1_100),
        )

        val presentation = ChatRepeatPresentationProjector.build(messages)

        assertEquals(1, presentation.visibleIndexFor("2"))
        assertEquals("2", presentation.anchorFor("2"))
        assertNull(presentation.summaryFor("2"))
    }

    @Test
    fun `empty plan falls back to canonical messages`() {
        val messages = listOf(message("1", "hello", 1_000))

        val presentation = ChatRepeatPresentationProjector.project(
            canonicalMessages = messages,
            plan = ChatRepeatCollapsePlan.Empty,
        )

        assertSame(messages, presentation.messages)
        assertEquals(0, presentation.visibleIndexFor("1"))
    }

    private fun message(
        id: String,
        text: String,
        timestampMillis: Long,
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user$id",
            displayName = "User $id",
        ),
        text = text,
        timestamp = "1970-01-01T00:00:00Z",
        timestampMillis = timestampMillis,
    )
}
