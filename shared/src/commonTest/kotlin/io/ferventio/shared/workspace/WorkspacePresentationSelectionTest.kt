package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.Workspace
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspacePresentationSelectionTest {
    @Test
    fun activeSplitChannelWinsLegacySelection() {
        assertEquals(
            "channel-2",
            resolveWorkspaceActiveChannelId(
                layout = layout(activeSplitId = "split-2"),
                selectedChannelId = "channel-1",
                channelIds = listOf("channel-1", "channel-2"),
            ),
        )
    }

    @Test
    fun detachedActiveSplitFallsBackToLegacySelection() {
        val detached = layout(activeSplitId = "split-2").copy(
            workspaces = listOf(
                layout(activeSplitId = "split-2").activeWorkspace!!.copy(
                    tabs = listOf(
                        layout(activeSplitId = "split-2").activeTab!!.copy(
                            splits = listOf(
                                ChatSplit("split-1", "channel-1"),
                                ChatSplit("split-2", null),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(
            "channel-1",
            resolveWorkspaceActiveChannelId(
                layout = detached,
                selectedChannelId = "channel-1",
                channelIds = listOf("channel-1", "channel-2"),
            ),
        )
    }

    @Test
    fun multiSplitDrawerSelectionTargetsFocusedSplit() {
        assertEquals(
            "split-2",
            activeWorkspaceSplitIdForChannelSelection(layout(activeSplitId = "split-2")),
        )
    }

    @Test
    fun singleSplitDrawerSelectionUsesLegacySelection() {
        val single = layout(activeSplitId = "split-1").let { original ->
            original.copy(
                workspaces = listOf(
                    original.activeWorkspace!!.copy(
                        tabs = listOf(
                            original.activeTab!!.copy(
                                splits = listOf(ChatSplit("split-1", "channel-1")),
                                activeSplitId = "split-1",
                            ),
                        ),
                    ),
                ),
            )
        }

        assertNull(activeWorkspaceSplitIdForChannelSelection(single))
    }

    private fun layout(activeSplitId: String): WorkspaceLayout {
        val tab = WorkspaceTab(
            id = "tab-1",
            title = "Chat",
            splits = listOf(
                ChatSplit("split-1", "channel-1"),
                ChatSplit("split-2", "channel-2"),
            ),
            activeSplitId = activeSplitId,
        )
        val workspace = Workspace(
            id = "workspace-1",
            name = "Main",
            tabs = listOf(tab),
            activeTabId = tab.id,
        )
        return WorkspaceLayout(
            workspaces = listOf(workspace),
            activeWorkspaceId = workspace.id,
        )
    }
}
