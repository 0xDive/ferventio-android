package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.savedFilterReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkspaceSplitMessageFilterTest {
    @Test
    fun savedFilterReferenceUsesAndroidExpressionSemantics() {
        val saved = SavedMessageFilter(
            id = "urgent",
            name = "Urgent",
            expression = "message.content contains \"urgent\"",
        )
        val filter = compileWorkspaceSplitMessageFilter(
            filterQuery = savedFilterReference(saved.id),
            savedFilters = listOf(saved),
        )

        assertTrue(filter.matches(message("1", "urgent ping"), null))
        assertFalse(filter.matches(message("2", "regular chat"), null))
    }

    @Test
    fun highlightsQueryUsesStoredDecorationInsteadOfReevaluatingRules() {
        val filter = compileWorkspaceSplitMessageFilter(
            filterQuery = HIGHLIGHTS_FILTER_QUERY,
            savedFilters = emptyList(),
        )
        val message = message("1", "anything")

        assertTrue(filter.matches(message, MessageDecoration(filteredSplit = true)))
        assertFalse(filter.matches(message, MessageDecoration(filteredSplit = false)))
    }

    @Test
    fun projectionAppliesSystemVisibilityAndIgnoreHideAfterFilterMatch() {
        val saved = SavedMessageFilter(
            id = "all-chat",
            name = "Chat",
            expression = "message.content contains \"chat\"",
        )
        val visible = message("visible", "chat visible")
        val ignored = message("ignored", "chat hidden")
        val system = message("system", "chat system", type = ChatMessageType.SYSTEM)

        val result = filterWorkspaceSplitMessages(
            messages = listOf(visible, ignored, system),
            filterQuery = savedFilterReference(saved.id),
            savedFilters = listOf(saved),
            decorations = mapOf(
                ignored.id to MessageDecoration(ignoreDisplayMode = IgnoreDisplayMode.HIDE),
            ),
            showSystemMessages = false,
        )

        assertEquals(listOf("visible"), result.map(ChatMessage::id))
    }

    @Test
    fun blankQueryKeepsNormalMessages() {
        val visible = message("visible", "hello")
        val result = filterWorkspaceSplitMessages(
            messages = listOf(visible),
            filterQuery = "",
            savedFilters = emptyList(),
            decorations = emptyMap(),
            showSystemMessages = true,
        )

        assertEquals(listOf(visible), result)
    }

    private fun message(
        id: String,
        text: String,
        type: ChatMessageType = ChatMessageType.CHAT,
    ) = ChatMessage(
        id = id,
        channelId = "channel-1",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user$id",
            displayName = "User $id",
        ),
        text = text,
        timestamp = "2026-08-21T00:00:00Z",
        type = type,
    )
}
