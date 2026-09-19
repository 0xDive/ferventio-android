package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.app.domain.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WorkspacePresentationSelectionTest {
    @Test
    fun compactSingleSplitPrefersImmediateSelectedChannel() {
        val layout = layout(
            splits = listOf(ChatSplit(id = "split-1", channelId = "channel-1")),
            activeSplitId = "split-1",
        )

        assertEquals(
            "channel-2",
            resolveWorkspaceActiveChannelId(
                layout = layout,
                selectedChannelId = "channel-2",
                channelIds = listOf("channel-1", "channel-2"),
            ),
        )
        assertEquals("split-1", activeWorkspaceSplitIdForChannelSelection(layout))
    }

    @Test
    fun wideMultiSplitKeepsFocusedSplitAuthoritative() {
        val layout = layout(
            splits = listOf(
                ChatSplit(id = "split-1", channelId = "channel-1"),
                ChatSplit(id = "split-2", channelId = "channel-2"),
            ),
            activeSplitId = "split-2",
        )

        assertEquals(
            "channel-2",
            resolveWorkspaceActiveChannelId(
                layout = layout,
                selectedChannelId = "channel-1",
                channelIds = listOf("channel-1", "channel-2"),
            ),
        )
        assertNotNull(activeWorkspaceSplitIdForChannelSelection(layout))
    }

    private fun layout(
        splits: List<ChatSplit>,
        activeSplitId: String,
    ): WorkspaceLayout {
        val tab = WorkspaceTab(
            id = "tab-1",
            title = "Chat",
            splits = splits,
            activeSplitId = activeSplitId,
        )
        return WorkspaceLayout(
            activeWorkspaceId = "workspace-1",
            workspaces = listOf(
                Workspace(
                    id = "workspace-1",
                    name = "Main",
                    tabs = listOf(tab),
                    activeTabId = tab.id,
                ),
            ),
        )
    }
}
