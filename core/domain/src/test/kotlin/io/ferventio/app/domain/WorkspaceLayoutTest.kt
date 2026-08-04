package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceLayoutTest {
    @Test
    fun roundTripPreservesWorkspacesTabsAndFilteredSplits() {
        val first = ChatSplit("split-1", "channel-a")
        val second = FilteredSplit("split-2", "channel-b", "moderator")
        val tab = WorkspaceTab(
            id = "tab-1",
            title = "Main",
            splits = listOf(first, second),
            activeSplitId = second.id,
            primaryFraction = 0.62f,
        )
        val workspace = Workspace(
            id = "workspace-1",
            name = "Desk",
            tabs = listOf(tab),
            activeTabId = tab.id,
        )
        val original = WorkspaceLayout(
            workspaces = listOf(workspace),
            activeWorkspaceId = workspace.id,
        )

        val restored = WorkspaceLayoutCodec.decodeOrDefault(WorkspaceLayoutCodec.encode(original))

        assertEquals("workspace-1", restored.activeWorkspaceId)
        assertEquals("tab-1", restored.activeWorkspace?.activeTabId)
        assertEquals(2, restored.activeTab?.splits?.size)
        assertTrue(restored.activeTab?.splits?.get(1) is FilteredSplit)
        assertEquals("moderator", restored.activeTab?.splits?.get(1)?.filterQuery)
        assertEquals(0.62f, restored.activeTab?.primaryFraction ?: 0f, 0.001f)
    }

    @Test
    fun roundTripPreservesSavedFilterReference() {
        val reference = savedFilterReference("filter-id")
        val split = FilteredSplit("split", "channel", reference)
        val tab = WorkspaceTab("tab", "Tab", listOf(split), split.id)
        val workspace = Workspace("workspace", "Workspace", listOf(tab), tab.id)
        val layout = WorkspaceLayout(workspaces = listOf(workspace), activeWorkspaceId = workspace.id)

        val restored = WorkspaceLayoutCodec.decodeOrDefault(WorkspaceLayoutCodec.encode(layout))

        assertEquals(reference, restored.activeTab?.activeSplit?.filterQuery)
    }

    @Test
    fun v1ChannelArrayMigratesToCurrentSchema() {
        val old = """{"schemaVersion":1,"workspaceName":"Legacy","title":"Channels","channelIds":["a","b"]}"""

        val migrated = WorkspaceLayoutCodec.decodeOrDefault(old)

        assertEquals(CURRENT_WORKSPACE_LAYOUT_SCHEMA, migrated.schemaVersion)
        assertEquals("Legacy", migrated.activeWorkspace?.name)
        assertEquals(listOf("a", "b"), migrated.activeTab?.splits?.mapNotNull(SplitLayout::channelId))
    }

    @Test
    fun normalizationDropsMissingChannelsAndCapsSplits() {
        val splits = (1..7).map { index -> ChatSplit("split-$index", "channel-$index") }
        val tab = WorkspaceTab("tab", "Tab", splits, splits.first().id, primaryFraction = 0.99f)
        val workspace = Workspace("workspace", "Workspace", listOf(tab), tab.id)
        val layout = WorkspaceLayout(workspaces = listOf(workspace), activeWorkspaceId = workspace.id)

        val normalized = layout.normalized(setOf("channel-1", "channel-2"))

        assertEquals(MAX_SPLITS_PER_TAB, normalized.activeTab?.splits?.size)
        assertEquals(0.75f, normalized.activeTab?.primaryFraction ?: 0f, 0.001f)
        assertEquals("channel-1", normalized.activeTab?.splits?.first()?.channelId)
        assertEquals(null, normalized.activeTab?.splits?.get(2)?.channelId)
    }
}
