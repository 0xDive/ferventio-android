package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.Workspace
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class TwitchRecentMessagesRuntimeTest {
    private val channel = ChatChannel(
        id = CHANNEL_ID,
        login = "channel",
        displayName = "Channel",
    )

    @Test
    fun historicalRowsUseOverlayAndExistingTimelineDuplicatesWin() = runTest {
        val state = ChatRuntimeStateHolder()
        state.append(message("live-duplicate", 20L, text = "live"))
        state.prependHistory(
            CHANNEL_ID,
            listOf(message("history-duplicate", 5L, text = "local history")),
        )
        var loadCount = 0
        val runtime = TwitchRecentMessagesRuntime(
            state = state,
            history = null,
            loadRecentMessages = { _, limit ->
                loadCount += 1
                assertEquals(TwitchRecentMessagesClient.DEFAULT_LIMIT, limit)
                TwitchRecentMessagesResult(
                    messages = listOf(
                        message("recent", 10L, text = "history"),
                        message("history-duplicate", 6L, text = "remote history"),
                        message("live-duplicate", 15L, text = "stale snapshot"),
                    ),
                )
            },
        )

        runtime.loadChannels(
            listOf(
                channel,
                channel.copy(id = "same-login", displayName = "Same login"),
            ),
        )

        assertEquals(1, loadCount)
        val timeline = state.messages(CHANNEL_ID)
        assertEquals(
            listOf("history-duplicate", "recent", "live-duplicate"),
            timeline.map(ChatMessage::id),
        )
        assertEquals("local history", timeline.single { it.id == "history-duplicate" }.text)
        assertEquals("live", timeline.single { it.id == "live-duplicate" }.text)
        assertEquals(
            listOf("live-duplicate"),
            state.messagesByChannel.getValue(CHANNEL_ID).map(ChatMessage::id),
        )
    }

    @Test
    fun activeRecentMessageChannelsMatchVisibleSplitsPlusLegacySelection() {
        val visibleA = channel
        val visibleB = ChatChannel("visible-b", "visible_b", "Visible B")
        val selected = ChatChannel("selected", "selected", "Selected")
        val hidden = ChatChannel("hidden", "hidden", "Hidden")
        val tab = WorkspaceTab(
            id = "tab",
            title = "Chats",
            splits = listOf(
                ChatSplit(id = "split-a", channelId = visibleA.id),
                ChatSplit(id = "split-b", channelId = visibleB.id),
            ),
            activeSplitId = "split-a",
        )
        val workspace = Workspace(
            id = "workspace",
            name = "Main",
            tabs = listOf(tab),
            activeTabId = tab.id,
        )
        val snapshot = WorkspaceRuntimeSnapshot(
            channels = listOf(visibleA, visibleB, selected, hidden),
            selectedChannelId = selected.id,
            workspaceLayout = WorkspaceLayout(
                workspaces = listOf(workspace),
                activeWorkspaceId = workspace.id,
            ),
        )

        assertEquals(
            listOf(visibleA.id, visibleB.id, selected.id),
            snapshot.activeRecentMessageChannels().map(ChatChannel::id),
        )
    }

    @Test
    fun snapshotFailureDoesNotInterruptLiveChatState() = runTest {
        val state = ChatRuntimeStateHolder()
        state.append(message("live", 20L))
        val runtime = TwitchRecentMessagesRuntime(
            state = state,
            history = null,
            loadRecentMessages = { _, _ -> throw IllegalStateException("offline") },
        )

        runtime.loadChannels(listOf(channel))

        assertEquals(listOf("live"), state.messages(CHANNEL_ID).map(ChatMessage::id))
    }

    private fun message(
        id: String,
        timestampMillis: Long,
        text: String = id,
    ) = ChatMessage(
        id = id,
        channelId = CHANNEL_ID,
        channelLogin = "channel",
        author = ChatAuthor(
            id = "author",
            login = "author",
            displayName = "Author",
        ),
        text = text,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = timestampMillis,
    )

    private companion object {
        const val CHANNEL_ID = "channel-id"
    }
}
