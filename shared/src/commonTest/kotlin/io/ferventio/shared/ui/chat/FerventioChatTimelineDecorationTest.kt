package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FerventioChatTimelineDecorationTest {
    @Test
    fun heldAutoModProjectionReusesAlreadyNormalizedList() {
        val messages = listOf(
            autoMod("one", "2026-09-20T10:00:00Z", AutoModMessageStatus.HELD),
            autoMod("two", "2026-09-20T10:01:00Z", AutoModMessageStatus.HELD),
        )

        val result = normalizeHeldAutoModTimelineMessages(messages)

        assertTrue(result === messages)
    }

    @Test
    fun heldAutoModProjectionFiltersTerminalAndSortsWhenNeeded() {
        val messages = listOf(
            autoMod("later", "2026-09-20T10:02:00Z", AutoModMessageStatus.HELD),
            autoMod("done", "2026-09-20T10:01:00Z", AutoModMessageStatus.EXPIRED),
            autoMod("early", "2026-09-20T10:00:00Z", AutoModMessageStatus.HELD),
        )

        val result = normalizeHeldAutoModTimelineMessages(messages)

        assertEquals(listOf("early", "later"), result.map(AutoModHeldMessage::messageId))
    }

    @Test
    fun repeatProjectionReusesSourceListWhenEveryMessageIsVisible() {
        val messages = listOf(message("one"), message("two"))

        val result = projectVisibleTimelineMessages(
            messages = messages,
            visibleMessageIds = setOf("one", "two"),
        )

        assertTrue(result === messages)
    }

    @Test
    fun repeatProjectionAllocatesOnlyWhenMessagesAreCollapsed() {
        val messages = listOf(message("one"), message("two"), message("three"))

        val result = projectVisibleTimelineMessages(
            messages = messages,
            visibleMessageIds = setOf("one", "three"),
        )

        assertEquals(listOf("one", "three"), result.map(ChatMessage::id))
        assertTrue(result !== messages)
    }

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

    private fun autoMod(
        id: String,
        heldAt: String,
        status: AutoModMessageStatus,
    ) = AutoModHeldMessage(
        channelId = "channel",
        channelLogin = "channel",
        channelName = "Channel",
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
