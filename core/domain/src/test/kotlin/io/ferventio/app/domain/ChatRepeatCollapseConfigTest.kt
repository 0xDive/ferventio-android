package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatRepeatCollapseConfigTest {
    @Test
    fun `disabled collapse preserves every message`() {
        val messages = listOf(
            message("1", "alpha", 1_000),
            message("2", "alpha", 1_100),
            message("3", "alpha", 1_200),
        )

        val presentation = ChatRepeatPresentationProjector.build(
            canonicalMessages = messages,
            config = ChatRepeatCollapseConfig(enabled = false),
        )

        assertEquals(messages.map(ChatMessage::id), presentation.messages.map(ChatMessage::id))
        assertNull(presentation.summaryFor("1"))
    }

    @Test
    fun `custom threshold is applied by projection`() {
        val messages = listOf(
            message("1", "alpha", 1_000),
            message("2", "alpha", 1_100),
        )

        val presentation = ChatRepeatPresentationProjector.build(
            canonicalMessages = messages,
            config = ChatRepeatCollapseConfig(minRepeatCount = 2),
        )

        assertEquals(listOf("1"), presentation.messages.map(ChatMessage::id))
        assertEquals(2, presentation.summaryFor("2")?.count)
    }

    @Test
    fun `summary reports omitted participants`() {
        val messages = listOf(
            message("1", "alpha", 1_000, userId = "a"),
            message("2", "alpha", 1_100, userId = "b"),
            message("3", "alpha", 1_200, userId = "c"),
        )

        val presentation = ChatRepeatPresentationProjector.build(
            canonicalMessages = messages,
            config = ChatRepeatCollapseConfig(maxParticipants = 1),
        )

        val summary = presentation.summaryFor("1")
        assertEquals(3, summary?.totalParticipantCount)
        assertEquals(2, summary?.omittedParticipantCount)
        assertEquals(2, summary?.collapsedMessageCount)
    }

    private fun message(
        id: String,
        text: String,
        timestampMillis: Long,
        userId: String = "user-$id",
    ): ChatMessage = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = userId,
            login = userId,
            displayName = userId,
        ),
        text = text,
        timestamp = "1970-01-01T00:00:00Z",
        timestampMillis = timestampMillis,
    )
}
