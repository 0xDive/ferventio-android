package io.ferventio.shared.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ChannelOrder
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.WorkspaceLayout

enum class WorkspaceLoadStatus {
    IDLE,
    LOADING,
    READY,
    FAILED,
}

data class WorkspaceRuntimeSnapshot(
    val channels: List<ChatChannel> = emptyList(),
    val selectedChannelId: String? = null,
    val pinnedChannelIds: List<String> = emptyList(),
    val channelTabTitles: Map<String, String> = emptyMap(),
    val moderatorChannelIds: Set<String> = emptySet(),
    val pushContextRevision: Long = 0L,
    val workspaceLayout: WorkspaceLayout? = null,
) {
    val channelIds: List<String> = channels.map(ChatChannel::id)
}

/** Shared platform-neutral channel/workspace state used by Compose and platform integrations. */
class WorkspaceRuntimeStateHolder(
    initialSnapshot: WorkspaceRuntimeSnapshot = WorkspaceRuntimeSnapshot(),
) {
    var channels by mutableStateOf(emptyList<ChatChannel>())
        private set
    private var channelIdsCache: List<String> = emptyList()
    private var channelIdSetCache: Set<String> = emptySet()

    var selectedChannelId by mutableStateOf<String?>(null)
        private set

    var pinnedChannelIds by mutableStateOf(emptyList<String>())
        private set

    var channelTabTitles by mutableStateOf(emptyMap<String, String>())
        private set

    var moderatorChannelIds by mutableStateOf(emptySet<String>())
        private set

    var workspaceLayout by mutableStateOf(WorkspaceLayout.default())
        private set

    var pushContextRevision by mutableStateOf(0L)
        private set

    var loadStatus by mutableStateOf(WorkspaceLoadStatus.IDLE)
        private set

    var loadErrorMessage by mutableStateOf<String?>(null)
        private set

    var settingsRevision by mutableStateOf(0L)
        private set

    var mutationInFlight by mutableStateOf(false)
        private set

    var mutationErrorMessage by mutableStateOf<String?>(null)
        private set

    val channelIds: List<String>
        get() = channelIdsCache

    val channelIdSet: Set<String>
        get() = channelIdSetCache

    val isReadyForPushRegistration: Boolean
        get() = loadStatus == WorkspaceLoadStatus.READY

    val snapshot: WorkspaceRuntimeSnapshot
        get() = WorkspaceRuntimeSnapshot(
            channels = channels,
            selectedChannelId = selectedChannelId,
            pinnedChannelIds = pinnedChannelIds,
            channelTabTitles = channelTabTitles,
            moderatorChannelIds = moderatorChannelIds,
            pushContextRevision = pushContextRevision,
            workspaceLayout = workspaceLayout,
        )

    init {
        replaceChannels(initialSnapshot.channels)
        selectInitialChannel(initialSnapshot.selectedChannelId)
        initialSnapshot.workspaceLayout?.let(::restoreWorkspaceLayout)
        updatePinnedChannelIds(initialSnapshot.pinnedChannelIds)
        updateChannelTabTitles(initialSnapshot.channelTabTitles)
        updateModeratorChannelIds(initialSnapshot.moderatorChannelIds)
        pushContextRevision = maxOf(pushContextRevision, initialSnapshot.pushContextRevision)
    }

    fun markLoadStarted() {
        loadStatus = WorkspaceLoadStatus.LOADING
        loadErrorMessage = null
    }

    fun markLoadReady(settingsRevision: Long) {
        require(settingsRevision >= 0L) { "Workspace settings revision must not be negative" }
        this.settingsRevision = settingsRevision
        loadStatus = WorkspaceLoadStatus.READY
        loadErrorMessage = null
    }

    fun markLoadFailed(errorMessage: String?) {
        loadStatus = WorkspaceLoadStatus.FAILED
        loadErrorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty)
    }

    fun markMutationStarted() {
        mutationInFlight = true
        mutationErrorMessage = null
    }

    fun markMutationSucceeded() {
        mutationInFlight = false
        mutationErrorMessage = null
    }

    fun markMutationFailed(errorMessage: String?) {
        mutationInFlight = false
        mutationErrorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty)
            ?: "Failed to update workspace"
    }

    fun clearMutationError() {
        mutationErrorMessage = null
    }

    fun replaceChannels(value: List<ChatChannel>) {
        val previousIds = channelIdSet
        val previousModerators = moderatorChannelIds
        updateChannelsSnapshot(normalizeChannels(value))
        reconcileMembership()
        val currentIds = channelIdSet
        if (previousIds != currentIds || previousModerators != moderatorChannelIds) bumpPushContextRevision()
    }

    fun addOrReplaceChannel(channel: ChatChannel) {
        requireValidChannel(channel)
        val index = channels.indexOfFirst { it.id == channel.id }
        updateChannelsSnapshot(
            if (index < 0) {
                channels + channel
            } else {
                channels.toMutableList().apply { this[index] = channel }
            },
        )
        if (selectedChannelId == null) selectedChannelId = channel.id
        if (index < 0) bumpPushContextRevision()
    }

    /**
     * Replaces a channel's runtime identity while preserving workspace presentation membership.
     * Used by anonymous IRC when Twitch upgrades a login-only placeholder to the canonical room id.
     */
    fun remapChannelId(channelId: String, replacementId: String): Boolean {
        val currentId = channelId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Workspace channel id must not be blank")
        val nextId = replacementId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Replacement channel id must not be blank")
        if (currentId == nextId) return false

        val index = channels.indexOfFirst { channel -> channel.id == currentId }
        if (index < 0) return false
        require(channels.none { channel -> channel.id == nextId }) {
            "Replacement channel id is already present in the workspace"
        }

        updateChannelsSnapshot(
            channels.toMutableList().apply {
                this[index] = this[index].copy(id = nextId)
            },
        )
        if (selectedChannelId == currentId) selectedChannelId = nextId
        pinnedChannelIds = pinnedChannelIds.map { id -> if (id == currentId) nextId else id }.distinct()
        channelTabTitles = buildMap {
            channelTabTitles.forEach { (id, title) ->
                put(if (id == currentId) nextId else id, title)
            }
        }
        moderatorChannelIds = moderatorChannelIds
            .mapTo(linkedSetOf()) { id -> if (id == currentId) nextId else id }
        workspaceLayout = workspaceLayout.copy(
            workspaces = workspaceLayout.workspaces.map { workspace ->
                workspace.copy(
                    tabs = workspace.tabs.map { tab ->
                        tab.copy(
                            splits = tab.splits.map { split ->
                                if (split.channelId == currentId) {
                                    split.withChannelId(nextId)
                                } else {
                                    split
                                }
                            },
                        )
                    },
                )
            },
        ).normalized(channelIdSet)
        bumpPushContextRevision()
        return true
    }

    fun removeChannel(channelId: String) {
        val normalizedId = channelId.trim()
        if (normalizedId.isEmpty() || normalizedId !in channelIdSet) return
        updateChannelsSnapshot(channels.filterNot { it.id == normalizedId })
        reconcileMembership()
        bumpPushContextRevision()
    }

    fun selectChannel(channelId: String) {
        val normalizedId = channelId.trim()
        require(normalizedId in channelIdSet) {
            "Cannot select a channel that is not in the workspace"
        }
        selectedChannelId = normalizedId
    }

    fun moveChannel(channelId: String, targetIndex: Int) {
        updateChannelsSnapshot(
            ChannelOrder.move(
                channels = channels,
                channelId = channelId.trim(),
                targetIndex = targetIndex,
            ),
        )
    }

    fun updatePinnedChannelIds(channelIds: Iterable<String>) {
        pinnedChannelIds = normalizeIds(channelIds).filter(channelIdSet::contains)
    }

    fun updateChannelTabTitles(titles: Map<String, String>) {
        channelTabTitles = buildMap {
            titles.forEach { (rawChannelId, rawTitle) ->
                val channelId = rawChannelId.trim()
                val title = rawTitle.trim().take(MAX_TAB_TITLE_LENGTH)
                if (channelId in channelIdSet && title.isNotEmpty()) put(channelId, title)
            }
        }
    }

    fun setChannelTabTitle(channelId: String, title: String?) {
        val normalizedId = channelId.trim()
        require(normalizedId in channelIdSet) {
            "Cannot rename a channel that is not in the workspace"
        }
        val normalizedTitle = title?.trim()?.take(MAX_TAB_TITLE_LENGTH).orEmpty()
        val currentTitle = channelTabTitles[normalizedId].orEmpty()
        if (currentTitle == normalizedTitle) return
        channelTabTitles = if (normalizedTitle.isEmpty()) {
            channelTabTitles - normalizedId
        } else {
            channelTabTitles + (normalizedId to normalizedTitle)
        }
    }

    fun updateModeratorChannelIds(channelIds: Iterable<String>) {
        val normalized = normalizeIds(channelIds).filterTo(linkedSetOf(), channelIdSet::contains)
        if (moderatorChannelIds != normalized) {
            moderatorChannelIds = normalized
            bumpPushContextRevision()
        }
    }

    fun restoreWorkspaceLayout(layout: WorkspaceLayout) {
        workspaceLayout = layout.normalized(channelIdSet)
    }

    fun clear() {
        val affectedPushContext = channels.isNotEmpty() || moderatorChannelIds.isNotEmpty()
        updateChannelsSnapshot(emptyList())
        selectedChannelId = null
        pinnedChannelIds = emptyList()
        channelTabTitles = emptyMap()
        moderatorChannelIds = emptySet()
        workspaceLayout = WorkspaceLayout.default()
        loadStatus = WorkspaceLoadStatus.IDLE
        loadErrorMessage = null
        settingsRevision = 0L
        mutationInFlight = false
        mutationErrorMessage = null
        if (affectedPushContext) bumpPushContextRevision()
    }

    private fun selectInitialChannel(requestedChannelId: String?) {
        val normalized = requestedChannelId?.trim()?.takeIf(String::isNotEmpty)
        selectedChannelId = normalized
            ?.takeIf(channelIdSet::contains)
            ?: channels.firstOrNull()?.id
    }

    private fun reconcileMembership() {
        val available = channelIdSet
        selectedChannelId = selectedChannelId?.takeIf(available::contains) ?: channels.firstOrNull()?.id
        pinnedChannelIds = pinnedChannelIds.filter(available::contains)
        channelTabTitles = channelTabTitles.filterKeys(available::contains)
        moderatorChannelIds = moderatorChannelIds.filterTo(linkedSetOf(), available::contains)
        workspaceLayout = workspaceLayout.normalized(available)
    }

    private fun updateChannelsSnapshot(value: List<ChatChannel>) {
        channels = value
        val nextIds = value.map(ChatChannel::id)
        if (nextIds != channelIdsCache) {
            channelIdsCache = nextIds
            channelIdSetCache = nextIds.toCollection(linkedSetOf())
        }
    }

    private fun normalizeChannels(value: List<ChatChannel>): List<ChatChannel> {
        val seen = hashSetOf<String>()
        return buildList(value.size) {
            value.forEach { channel ->
                requireValidChannel(channel)
                if (seen.add(channel.id)) add(channel)
            }
        }
    }

    private fun requireValidChannel(channel: ChatChannel) {
        require(channel.id.isNotBlank()) { "Workspace channel id must not be blank" }
        require(channel.login.isNotBlank()) { "Workspace channel login must not be blank" }
    }

    private fun normalizeIds(value: Iterable<String>): List<String> {
        val seen = hashSetOf<String>()
        return buildList {
            value.forEach { raw ->
                val normalized = raw.trim()
                if (normalized.isNotEmpty() && seen.add(normalized)) add(normalized)
            }
        }
    }

    private fun bumpPushContextRevision() {
        pushContextRevision += 1L
    }

    private companion object {
        const val MAX_TAB_TITLE_LENGTH = 32
    }
}
