package io.ferventio.shared.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ChannelOrder
import io.ferventio.app.domain.ChatChannel

data class WorkspaceRuntimeSnapshot(
    val channels: List<ChatChannel> = emptyList(),
    val selectedChannelId: String? = null,
    val pinnedChannelIds: List<String> = emptyList(),
    val moderatorChannelIds: Set<String> = emptySet(),
) {
    val channelIds: List<String>
        get() = channels.map(ChatChannel::id)
}

/**
 * Shared channel/workspace state used by Compose navigation and platform integrations.
 *
 * The holder intentionally owns only platform-neutral channel identity, order, selection, and
 * role metadata. Android/iOS loaders remain responsible for networking and persistence while the
 * migration is incremental.
 */
class WorkspaceRuntimeStateHolder(
    initialSnapshot: WorkspaceRuntimeSnapshot = WorkspaceRuntimeSnapshot(),
) {
    var channels by mutableStateOf(emptyList<ChatChannel>())
        private set

    var selectedChannelId by mutableStateOf<String?>(null)
        private set

    var pinnedChannelIds by mutableStateOf(emptyList<String>())
        private set

    var moderatorChannelIds by mutableStateOf(emptySet<String>())
        private set

    val channelIds: List<String>
        get() = channels.map(ChatChannel::id)

    val snapshot: WorkspaceRuntimeSnapshot
        get() = WorkspaceRuntimeSnapshot(
            channels = channels,
            selectedChannelId = selectedChannelId,
            pinnedChannelIds = pinnedChannelIds,
            moderatorChannelIds = moderatorChannelIds,
        )

    init {
        replaceChannels(initialSnapshot.channels)
        selectInitialChannel(initialSnapshot.selectedChannelId)
        updatePinnedChannelIds(initialSnapshot.pinnedChannelIds)
        updateModeratorChannelIds(initialSnapshot.moderatorChannelIds)
    }

    fun replaceChannels(value: List<ChatChannel>) {
        val normalized = normalizeChannels(value)
        channels = normalized
        reconcileMembership()
    }

    fun addOrReplaceChannel(channel: ChatChannel) {
        requireValidChannel(channel)
        val index = channels.indexOfFirst { it.id == channel.id }
        channels = if (index < 0) {
            channels + channel
        } else {
            channels.toMutableList().apply { this[index] = channel }
        }
        if (selectedChannelId == null) {
            selectedChannelId = channel.id
        }
    }

    fun removeChannel(channelId: String) {
        val normalizedId = channelId.trim()
        if (normalizedId.isEmpty() || channels.none { it.id == normalizedId }) return
        channels = channels.filterNot { it.id == normalizedId }
        reconcileMembership()
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
        pinnedChannelIds = normalizeIds(channelIds)
            .filter(available::contains)
    }

    fun updateModeratorChannelIds(channelIds: Iterable<String>) {
        val available = channels.mapTo(hashSetOf()) { it.id }
        moderatorChannelIds = normalizeIds(channelIds)
            .filterTo(linkedSetOf(), available::contains)
    }

    fun clear() {
        channels = emptyList()
        selectedChannelId = null
        pinnedChannelIds = emptyList()
        moderatorChannelIds = emptySet()
    }

    private fun selectInitialChannel(requestedChannelId: String?) {
        val normalized = requestedChannelId?.trim()?.takeIf(String::isNotEmpty)
        selectedChannelId = normalized
            ?.takeIf { requested -> channels.any { it.id == requested } }
            ?: channels.firstOrNull()?.id
    }

    private fun reconcileMembership() {
        val available = channels.mapTo(hashSetOf()) { it.id }
        selectedChannelId = selectedChannelId
            ?.takeIf(available::contains)
            ?: channels.firstOrNull()?.id
        pinnedChannelIds = pinnedChannelIds.filter(available::contains)
        moderatorChannelIds = moderatorChannelIds.filterTo(linkedSetOf(), available::contains)
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
}
