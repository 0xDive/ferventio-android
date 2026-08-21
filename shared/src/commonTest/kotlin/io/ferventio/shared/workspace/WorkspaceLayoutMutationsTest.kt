package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.FilteredSplit
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.MAX_SPLITS_PER_TAB
import io.ferventio.app.domain.Workspace
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.app.domain.savedFilterReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class WorkspaceLayoutMutationsTest {
    @Test
    fun queryTurnsChatSplitIntoFilteredSplitWithoutChangingIdentity() {
        val result = updateWorkspaceSplitFilterQuery(
            layout = layout(),
            splitId = "split-1",
            filterQuery = "message.length > 80",
        )

        val split = assertIs<FilteredSplit>(result.activeTab?.activeSplit)
        assertEquals("split-1", split.id)
        assertEquals("channel-1", split.channelId)
        assertEquals("message.length > 80", split.filterQuery)
        assertEquals("untouched", result.workspaces.last().name)
    }

    @Test
    fun blankQueryTurnsFilteredSplitBackIntoChatSplit() {
        val filtered = updateWorkspaceSplitFilterQuery(
            layout = layout(),
            splitId = "split-1",
            filterQuery = "message.length > 80",
        )

        val result = updateWorkspaceSplitFilterQuery(filtered, "split-1", "   ")

        val split = assertIs<ChatSplit>(result.activeTab?.activeSplit)
        assertEquals("split-1", split.id)
        assertEquals("channel-1", split.channelId)
    }

    @Test
    fun savedFilterAndHighlightsUseAndroidCompatibleQueries() {
        val bound = bindWorkspaceSplitSavedFilter(layout(), "split-1", "filter-1")
        assertEquals(
            savedFilterReference("filter-1"),
            bound.activeTab?.activeSplit?.filterQuery,
        )

        val highlights = setWorkspaceSplitHighlightsOnly(bound, "split-1")
        assertEquals(HIGHLIGHTS_FILTER_QUERY, highlights.activeTab?.activeSplit?.filterQuery)
    }

    @Test
    fun changingSplitChannelAlsoFocusesIt() {
        val withSecond = addWorkspaceChatSplit(layout(), "channel-2")
        val firstId = withSecond.activeTab?.splits?.first()?.id.orEmpty()

        val result = updateWorkspaceSplitChannel(withSecond, firstId, "channel-3")

        assertEquals(firstId, result.activeTab?.activeSplitId)
        assertEquals("channel-3", result.activeTab?.activeSplit?.channelId)
    }

    @Test
    fun focusAndRemoveMirrorAndroidSplitSemantics() {
        val withSecond = addWorkspaceChatSplit(layout(), "channel-2")
        val secondId = withSecond.activeTab?.activeSplitId.orEmpty()
        val firstId = withSecond.activeTab?.splits?.first()?.id.orEmpty()

        val focused = focusWorkspaceSplit(withSecond, firstId)
        assertEquals(firstId, focused.activeTab?.activeSplitId)

        val removed = removeWorkspaceSplit(focused, firstId)
        assertEquals(listOf(secondId), removed.activeTab?.splits?.map { it.id })
        assertEquals(secondId, removed.activeTab?.activeSplitId)

        val lastPaneProtected = removeWorkspaceSplit(removed, secondId)
        assertEquals(1, lastPaneProtected.activeTab?.splits?.size)
    }

    @Test
    fun addCapsAtFourAndPrimaryFractionIsClamped() {
        var value = layout()
        repeat(MAX_SPLITS_PER_TAB + 2) { index ->
            value = addWorkspaceChatSplit(value, "channel-${index + 2}")
        }
        assertEquals(MAX_SPLITS_PER_TAB, value.activeTab?.splits?.size)
        assertNotEquals("split-1", value.activeTab?.activeSplitId)

        assertEquals(0.25f, updateWorkspaceSplitPrimaryFraction(value, 0.1f).activeTab?.primaryFraction)
        assertEquals(0.75f, updateWorkspaceSplitPrimaryFraction(value, 0.9f).activeTab?.primaryFraction)
    }

    @Test
    fun unknownSplitIsRejectedInsteadOfSilentlyChangingLayout() {
        assertFailsWith<IllegalArgumentException> {
            updateWorkspaceSplitFilterQuery(layout(), "missing", "message.length > 80")
        }
    }

    private fun layout(): WorkspaceLayout = WorkspaceLayout(
        workspaces = listOf(
            Workspace(
                id = "workspace-1",
                name = "main",
                tabs = listOf(
                    WorkspaceTab(
                        id = "tab-1",
                        title = "Chat",
                        splits = listOf(ChatSplit("split-1", "channel-1")),
                        activeSplitId = "split-1",
                    ),
                ),
                activeTabId = "tab-1",
            ),
            Workspace(
                id = "workspace-2",
                name = "untouched",
                tabs = listOf(
                    WorkspaceTab(
                        id = "tab-2",
                        title = "Other",
                        splits = listOf(ChatSplit("split-2", "channel-2")),
                        activeSplitId = "split-2",
                    ),
                ),
                activeTabId = "tab-2",
            ),
        ),
        activeWorkspaceId = "workspace-1",
    )
}
