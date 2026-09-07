package io.ferventio.shared.workspace

import io.ferventio.app.domain.WorkspaceLayout

/** Resolves the channel represented by the active split, falling back to legacy selection. */
fun resolveWorkspaceActiveChannelId(
    layout: WorkspaceLayout,
    selectedChannelId: String?,
    channelIds: List<String>,
): String? {
    val available = channelIds.toSet()
    val activeTab = layout.activeTab
    val splitChannelId = activeTab
        ?.activeSplitId
        ?.let { splitId -> activeTab.splits.firstOrNull { it.id == splitId } }
        ?.channelId
        ?.takeIf(available::contains)
    return splitChannelId
        ?: selectedChannelId?.takeIf(available::contains)
        ?: channelIds.firstOrNull()
}

/** Android-parity drawer routing: multi-split tabs change the focused split, not legacy selection. */
fun activeWorkspaceSplitIdForChannelSelection(layout: WorkspaceLayout): String? {
    val tab = layout.activeTab ?: return null
    if (tab.splits.size <= 1) return null
    val splitId = tab.activeSplitId ?: return null
    return splitId.takeIf { id -> tab.splits.any { it.id == id } }
}
