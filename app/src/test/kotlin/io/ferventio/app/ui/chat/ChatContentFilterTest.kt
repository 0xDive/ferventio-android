package io.ferventio.app.ui

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatContentFilterTest {
    @Test
    fun unrelatedIgnoreDecorationReusesMessageList() {
        val messages = listOf(message("visible"))

        val result = filterLegacyChatMessages(
            messages = messages,
            showSystemMessages = true,
            filterExpression = "",
            decorations = mapOf(
                "other" to MessageDecoration(ignoreDisplayMode = IgnoreDisplayMode.HIDE),
            ),
            matchesCompiled = { true },
        )

        assertTrue(result === messages)
    }

    @Test
    fun currentMessageIgnoreDecorationFiltersLazily() {
        val messages = listOf(
            message("one"),
            message("hidden"),
            message("three"),
        )

        val result = filterLegacyChatMessages(
            messages = messages,
            showSystemMessages = true,
            filterExpression = "",
            decorations = mapOf(
                "hidden" to MessageDecoration(ignoreDisplayMode = IgnoreDisplayMode.HIDE),
            ),
            matchesCompiled = { true },
        )

        assertEquals(listOf("one", "three"), result.map(ChatMessage::id))
        assertTrue(result !== messages)
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
