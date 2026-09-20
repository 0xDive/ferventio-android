package io.ferventio.app.ui

import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatContentFilterTest {
    @Test
    fun autoModProjectionKeepsOnlyHeldMessagesForCurrentChannelInTimeOrder() {
        val result = selectLegacyHeldAutoModMessages(
            queue = listOf(
                autoMod("later", "channel", "2026-09-20T10:02:00Z", AutoModMessageStatus.HELD),
                autoMod("other", "other", "2026-09-20T10:00:00Z", AutoModMessageStatus.HELD),
                autoMod("done", "channel", "2026-09-20T10:01:00Z", AutoModMessageStatus.EXPIRED),
                autoMod("early", "channel", "2026-09-20T10:00:00Z", AutoModMessageStatus.HELD),
            ),
            channelId = "channel",
        )

        assertEquals(listOf("early", "later"), result.map(AutoModHeldMessage::messageId))
    }

    @Test
    fun decorationProjectionDropsOtherChannelMessageIds() {
        val current = listOf(message("one"), message("two"))
        val one = MessageDecoration(highlightColorArgb = 0xFF112233L)

        val result = selectLegacyChatDecorations(
            messages = current,
            decorations = mapOf(
                "one" to one,
                "other" to MessageDecoration(ignoreDisplayMode = IgnoreDisplayMode.HIDE),
            ),
        )

        assertEquals(mapOf("one" to one), result)
    }

    @Test
    fun decorationProjectionReusesSharedEmptyMapWhenNothingMatches() {
        val result = selectLegacyChatDecorations(
            messages = listOf(message("one")),
            decorations = mapOf(
                "other" to MessageDecoration(highlightColorArgb = 0xFF445566L),
            ),
        )

        assertTrue(result.isEmpty())
    }

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

    private fun autoMod(
        id: String,
        channelId: String,
        heldAt: String,
        status: AutoModMessageStatus,
    ) = AutoModHeldMessage(
        channelId = channelId,
        channelLogin = channelId,
        channelName = channelId,
        userId = "user-$id",
        userLogin = "user_$id",
        userName = "User $id",
        messageId = id,
        text = id,
        heldAt = heldAt,
        status = status,
    )

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
