package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.MAX_SPLITS_PER_TAB
import io.ferventio.app.domain.SplitLayout
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import io.ferventio.app.domain.newLayoutId
import io.ferventio.app.domain.savedFilterReference

fun updateWorkspaceSplitFilterQuery(
    layout: WorkspaceLayout,
    splitId: String,
    filterQuery: String,
): WorkspaceLayout = mapWorkspaceSplit(layout, splitId) { split ->
    split.withFilterQuery(filterQuery)
}

fun bindWorkspaceSplitSavedFilter(
    layout: WorkspaceLayout,
    splitId: String,
    filterId: String,
): WorkspaceLayout {
    val id = filterId.trim().takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("Saved message filter id must not be blank")
    return updateWorkspaceSplitFilterQuery(
        layout = layout,
        splitId = splitId,
        filterQuery = savedFilterReference(id),
    )
}

fun setWorkspaceSplitHighlightsOnly(
    layout: WorkspaceLayout,
    splitId: String,
): WorkspaceLayout = updateWorkspaceSplitFilterQuery(
    layout = layout,
    splitId = splitId,
    filterQuery = HIGHLIGHTS_FILTER_QUERY,
)

fun updateWorkspaceSplitChannel(
    layout: WorkspaceLayout,
    splitId: String,
    channelId: String,
): WorkspaceLayout {
    val normalizedChannelId = channelId.trim().takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("Workspace split channel id must not be blank")
    return mapWorkspaceSplit(
        layout = layout,
        splitId = splitId,
        activate = true,
    ) { split ->
        split.withChannelId(normalizedChannelId)
    }
}

fun focusWorkspaceSplit(
    layout: WorkspaceLayout,
    splitId: String,
): WorkspaceLayout = mapWorkspaceSplit(
    layout = layout,
    splitId = splitId,
    activate = true,
) { it }

fun addWorkspaceChatSplit(
    layout: WorkspaceLayout,
    channelId: String?,
): WorkspaceLayout = mapActiveWorkspaceTab(layout) { tab ->
    if (tab.splits.size >= MAX_SPLITS_PER_TAB) {
        tab
    } else {
        val split = ChatSplit(
            id = newLayoutId("split"),
            channelId = channelId?.trim()?.takeIf(String::isNotEmpty),
        )
        tab.copy(
            splits = tab.splits + split,
            activeSplitId = split.id,
        )
    }
}

fun removeWorkspaceSplit(
    layout: WorkspaceLayout,
    splitId: String,
): WorkspaceLayout {
    val id = requireSplitId(splitId)
    var matchCount = 0
    val workspaces = layout.workspaces.map { workspace ->
        workspace.copy(
            tabs = workspace.tabs.map { tab ->
                if (tab.splits.none { it.id == id }) {
                    tab
                } else {
                    matchCount += 1
                    if (tab.splits.size <= 1) {
                        tab
                    } else {
                        val remaining = tab.splits.filterNot { it.id == id }
                        tab.copy(
                            splits = remaining,
                            activeSplitId = if (tab.activeSplitId == id) {
                                remaining.firstOrNull()?.id
                            } else {
                                tab.activeSplitId
                            },
                        )
                    }
                }
            },
        )
    }
    requireUniqueSplitMatch(matchCount)
    return layout.copy(workspaces = workspaces)
}

fun updateWorkspaceSplitPrimaryFraction(
    layout: WorkspaceLayout,
    fraction: Float,
): WorkspaceLayout = mapActiveWorkspaceTab(layout) { tab ->
    tab.copy(primaryFraction = fraction.coerceIn(0.25f, 0.75f))
}

private fun mapWorkspaceSplit(
    layout: WorkspaceLayout,
    splitId: String,
    activate: Boolean = false,
    transform: (SplitLayout) -> SplitLayout,
): WorkspaceLayout {
    val id = requireSplitId(splitId)
    var matchCount = 0
    val workspaces = layout.workspaces.map { workspace ->
        workspace.copy(
            tabs = workspace.tabs.map { tab ->
                var matched = false
                val splits = tab.splits.map { split ->
                    if (split.id != id) {
                        split
                    } else {
                        matchCount += 1
                        matched = true
                        transform(split)
                    }
                }
                when {
                    matched && activate -> tab.copy(splits = splits, activeSplitId = id)
                    matched -> tab.copy(splits = splits)
                    else -> tab
                }
            },
        )
    }
    requireUniqueSplitMatch(matchCount)
    return layout.copy(workspaces = workspaces)
}

private fun mapActiveWorkspaceTab(
    layout: WorkspaceLayout,
    transform: (WorkspaceTab) -> WorkspaceTab,
): WorkspaceLayout {
    val workspaceId = layout.activeWorkspace?.id
        ?: throw IllegalArgumentException("Workspace layout does not contain an active workspace")
    val tabId = layout.activeTab?.id
        ?: throw IllegalArgumentException("Workspace layout does not contain an active tab")
    return layout.copy(
        workspaces = layout.workspaces.map { workspace ->
            if (workspace.id != workspaceId) {
                workspace
            } else {
                workspace.copy(
                    tabs = workspace.tabs.map { tab ->
                        if (tab.id == tabId) transform(tab) else tab
                    },
                )
            }
        },
    )
}

private fun requireSplitId(value: String): String = value.trim().takeIf(String::isNotEmpty)
    ?: throw IllegalArgumentException("Workspace split id must not be blank")

private fun requireUniqueSplitMatch(matchCount: Int) {
    require(matchCount == 1) {
        if (matchCount == 0) "Workspace split was not found" else "Workspace split id is ambiguous"
    }
}
