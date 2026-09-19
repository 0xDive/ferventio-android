package io.ferventio.shared.ui.app

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals

class HistorySearchNavigationTest {
    @Test
    fun removedWorkspaceChannelsAreNotExposedAsNavigableResults() {
        val results = listOf(
            message("current", "channel-1"),
            message("removed", "channel-2"),
        )

        assertEquals(
            listOf("current"),
            filterNavigableHistorySearchResults(
                messages = results,
                navigableChannelIds = setOf("channel-1"),
            ).map(ChatMessage::id),
        )
    }

    @Test
    fun blankOrEmptyWorkspaceIdsDoNotCreateNavigableResults() {
        val results = listOf(message("message", "channel-1"))

        assertEquals(emptyList(), filterNavigableHistorySearchResults(results, emptySet()))
        assertEquals(emptyList(), filterNavigableHistorySearchResults(results, setOf(" ")))
    }

    private fun message(id: String, channelId: String) = ChatMessage(
        id = id,
        channelId = channelId,
        channelLogin = channelId,
        author = ChatAuthor(
            id = "user",
            login = "user",
            displayName = "User",
        ),
        text = id,
        timestamp = "2026-08-19T00:00:00Z",
        timestampMillis = 1L,
    )
}
