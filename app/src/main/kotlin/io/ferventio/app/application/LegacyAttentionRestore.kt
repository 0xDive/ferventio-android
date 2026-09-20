package io.ferventio.app.application

import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatChannel

internal fun remapLegacyAttentionEntries(
    entries: List<AttentionEntry>,
    channels: List<ChatChannel>,
): List<AttentionEntry> {
    if (entries.isEmpty() || channels.isEmpty()) return entries

    val channelsById = HashMap<String, ChatChannel>(channels.size)
    val channelsByLogin = HashMap<String, ChatChannel>(channels.size)
    channels.forEach { channel ->
        channelsById[channel.id] = channel
        channelsByLogin[channel.login.lowercase()] = channel
    }

    var updated: MutableList<AttentionEntry>? = null
    for (index in entries.indices) {
        val current = entries[index]
        val channel = channelsById[current.channelId]
            ?: channelsByLogin[current.channelLogin.lowercase()]
            ?: continue
        if (channel.id == current.channelId) continue

        val target = updated ?: entries.toMutableList().also { updated = it }
        target[index] = current.copy(channelId = channel.id)
    }
    return updated ?: entries
}
