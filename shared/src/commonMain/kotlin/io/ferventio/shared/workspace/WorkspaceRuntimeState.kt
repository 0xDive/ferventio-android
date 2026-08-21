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
    val channelIds: List<String>
        get() = channels.map { it.id }
}

/** Shared platform-neutral channel/workspace state used by Compose and platform integrations. */
class WorkspaceRuntimeStateHolder(
    initialSnapshot: WorkspaceRuntimeSnapshot = WorkspaceRuntimeSnapshot(),
) {
    var channels by mutableStateOf(emptyList<ChatChannel>())
        private set

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
        get() = channels.map { it.id }

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
        val previousIds = channels.mapTo(linkedSetOf()) { it.id }
        val previousModerators = moderatorChannelIds
        channels = normalizeChannels(value)
        reconcileMembership()
        val currentIds = channels.mapTo(linkedSetOf()) { it.id }
        if (previousIds != currentIds || previousModerators != moderatorChannelIds) bumpPushContextRevision()
    }

    fun addOrReplaceChannel(channel: ChatChannel) {
        requireValidChannel(channel)
        val index = channels.indexOfFirst { it.id == channel.id }
        channels = if (index < 0) channels + channel else channels.toMutableList().apply { this[index] = channel }
        if (selectedChannelId == null) selectedChannelId = channel.id
        if (index < 0) bumpPushContextRevision()
    }

    fun removeChannel(channelId: String) {
        val normalizedId = channelId.trim()
        if (normalizedId.isEmpty() || channels.none { it.id == normalizedId }) return
        channels = channels.filterNot { it.id == normalizedId }
        reconcileMembership()
        bumpPushContextRevision()
    }

    fun selectChannel(channelId: String) {
        val normalizedId = channelId.trim()
        require(channels.any { it.id == normalizedId }) {
            "Cannot select a channel that is not in the workspace"
        }
        selectedChannelId = normalizedId
    }

    fun moveChannel(channelId: String, targetIndex: Int) {
        channels = ChannelOrder.move(
            channels = channels,
            channelId = channelId.trim(),
            targetIndex = targetIndex,
        )
    }

    fun updatePinnedChannelIds(channelIds: Iterable<String>) {
        val available = channels.mapTo(hashSetOf()) { it.id }
        pinnedChannelIds = normalizeIds(channelIds).filter(available::contains)
    }

    fun updateChannelTabTitles(titles: Map<String, String>) {
        val available = channels.mapTo(hashSetOf()) { it.id }
        channelTabTitles = buildMap {
            titles.forEach { (rawChannelId, rawTitle) ->
                val channelId = rawChannelId.trim()
                val title = rawTitle.trim().take(MAX_TAB_TITLE_LENGTH)
                if (channelId in available && title.isNotEmpty()) put(channelId, title)
            }
        }
    }

    fun setChannelTabTitle(channelId: String, title: String?) {
        val normalizedId = channelId.trim()
        require(channels.any { it.id == normalizedId }) {
            "Cannot rename a channel that is not in the workspace"
        }
        val normalizedTitle = title?.trim()?.take(MAX_TAB_TITLE_LENGTH).orEmpty()
        channelTabTitles = if (normalizedTitle.isEmpty()) {
            channelTabTitles - normalizedId
        } else {
            channelTabTitles + (normalizedId to normalizedTitle)
        }
    }

    fun updateModeratorChannelIds(channelIds: Iterable<String>) {
        val available = channels.mapTo(hashSetOf()) { it.id }
        val normalized = normalizeIds(channelIds).filterTo(linkedSetOf(), available::contains)
        if (moderatorChannelIds != normalized) {
            moderatorChannelIds = normalized
            bumpPushContextRevision()
        }
    }

    fun restoreWorkspaceLayout(layout: WorkspaceLayout) {
        workspaceLayout = layout.normalized(channelIds.toSet())
    }

    fun clear() {
        val affectedPushContext = channels.isNotEmpty() || moderatorChannelIds.isNotEmpty()
        channels = emptyList()
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
            ?.takeIf { requested -> channels.any { it.id == requested } }
            ?: channels.firstOrNull()?.id
    }

    private fun reconcileMembership() {
        val available = channels.mapTo(hashSetOf()) { it.id }
        selectedChannelId = selectedChannelId?.takeIf(available::contains) ?: channels.firstOrNull()?.id
        pinnedChannelIds = pinnedChannelIds.filter(available::contains)
        channelTabTitles = channelTabTitles.filterKeys(available::contains)
        moderatorChannelIds = moderatorChannelIds.filterTo(linkedSetOf(), available::contains)
        workspaceLayout = workspaceLayout.normalized(available)
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
