package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.FilteredSplit
import io.ferventio.app.domain.Workspace
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.app.domain.savedFilterReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class WorkspaceRuntimeLayoutStateTest {
    private val alpha = ChatChannel(id = "1", login = "alpha", displayName = "Alpha")
    private val beta = ChatChannel(id = "2", login = "beta", displayName = "Beta")

    @Test
    fun restoresFilteredLayoutAgainstKnownChannels() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(channels = listOf(alpha, beta)),
        )

        holder.restoreWorkspaceLayout(layout(channelId = "2"))

        val split = assertIs<FilteredSplit>(holder.workspaceLayout.activeTab?.activeSplit)
        assertEquals("2", split.channelId)
        assertEquals(savedFilterReference("filter-1"), split.filterQuery)
        assertEquals(holder.workspaceLayout, holder.snapshot.workspaceLayout)
    }

    @Test
    fun channelRemovalDetachesSplitButKeepsFilterReference() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(channels = listOf(alpha, beta)),
        )
        holder.restoreWorkspaceLayout(layout(channelId = "2"))

        holder.removeChannel("2")

        val split = assertIs<FilteredSplit>(holder.workspaceLayout.activeTab?.activeSplit)
        assertNull(split.channelId)
        assertEquals(savedFilterReference("filter-1"), split.filterQuery)
    }

    private fun layout(channelId: String): WorkspaceLayout {
        val split = FilteredSplit(
            id = "split-1",
            channelId = channelId,
            filterQuery = savedFilterReference("filter-1"),
        )
        val tab = WorkspaceTab(
            id = "tab-1",
            title = "Filtered",
            splits = listOf(split),
            activeSplitId = split.id,
        )
        val workspace = Workspace(
            id = "workspace-1",
            name = "Moderation",
            tabs = listOf(tab),
            activeTabId = tab.id,
        )
        return WorkspaceLayout(
            workspaces = listOf(workspace),
            activeWorkspaceId = workspace.id,
        )
    }
}
