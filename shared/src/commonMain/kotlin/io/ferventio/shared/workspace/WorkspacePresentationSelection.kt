package io.ferventio.shared.workspace

import io.ferventio.app.domain.WorkspaceLayout

/**
 * Resolves the channel represented by the workspace.
 *
 * Compact/single-split layouts follow [selectedChannelId] first so drawer/pager navigation updates
 * the visible chat immediately while the persisted split mutation is in flight. Wide multi-split
 * layouts keep the focused split authoritative.
 */
fun resolveWorkspaceActiveChannelId(
    layout: WorkspaceLayout,
    selectedChannelId: String?,
    channelIds: List<String>,
): String? {
    val available = channelIds.toSet()
    val selected = selectedChannelId?.takeIf(available::contains)
    val activeTab = layout.activeTab
    val splitChannelId = activeTab
        ?.activeSplitId
        ?.let { splitId -> activeTab.splits.firstOrNull { it.id == splitId } }
        ?.channelId
        ?.takeIf(available::contains)

    return if (activeTab?.splits.orEmpty().size <= 1) {
        selected ?: splitChannelId ?: channelIds.firstOrNull()
    } else {
        splitChannelId ?: selected ?: channelIds.firstOrNull()
    }
}

/**
 * Routes channel navigation through the focused split whenever one exists.
 *
 * This intentionally includes single-split tabs: persisting only the legacy selected channel while
 * leaving the sole split on the previous channel makes compact iPhone navigation appear inert.
 */
fun activeWorkspaceSplitIdForChannelSelection(layout: WorkspaceLayout): String? {
    val tab = layout.activeTab ?: return null
    val splitId = tab.activeSplitId ?: return null
    return splitId.takeIf { id -> tab.splits.any { it.id == id } }
}
