package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatRepeatNavigationTest {
    @Test
    fun `hidden repeat id resolves to visible anchor index`() {
        val messages = listOf(
            message("a", "wave", 1_000),
            message("b", "wave", 1_100),
            message("c", "wave", 1_200),
            message("d", "tail", 1_300),
        )

        val presentation = ChatRepeatPresentationProjector.build(messages)

        assertEquals("a", presentation.anchorFor("c"))
        assertEquals(0, presentation.visibleIndexFor("c"))
        assertEquals("a", presentation.visibleMessageFor("c")?.id)
    }

    @Test
    fun `visible non repeat id resolves to itself`() {
        val messages = listOf(
            message("a", "first", 1_000),
            message("b", "second", 1_100),
        )

        val presentation = ChatRepeatPresentationProjector.build(messages)

        assertEquals("b", presentation.anchorFor("b"))
        assertEquals(1, presentation.visibleIndexFor("b"))
        assertEquals("b", presentation.visibleMessageFor("b")?.id)
    }

    @Test
    fun `unknown message id remains unresolved`() {
        val presentation = ChatRepeatPresentationProjector.build(
            canonicalMessages = listOf(message("a", "first", 1_000)),
        )

        assertEquals("missing", presentation.anchorFor("missing"))
        assertNull(presentation.visibleIndexFor("missing"))
        assertNull(presentation.visibleMessageFor("missing"))
        assertNull(presentation.summaryFor("missing"))
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
