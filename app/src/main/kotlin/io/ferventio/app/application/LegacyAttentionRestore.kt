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

internal data class LegacyAttentionSummary(
    val channelAttention: Map<String, io.ferventio.app.domain.ChannelAttention>,
    val unreadCount: Int,
)

internal fun summarizeLegacyAttention(
    entries: List<AttentionEntry>,
    maxCount: Int,
): LegacyAttentionSummary {
    if (entries.isEmpty()) {
        return LegacyAttentionSummary(
            channelAttention = emptyMap(),
            unreadCount = 0,
        )
    }

    data class MutableChannelSummary(
        var count: Int = 0,
        var firstTimestampMillis: Long = Long.MAX_VALUE,
        var firstMessageId: String? = null,
    )

    val byChannel = linkedMapOf<String, MutableChannelSummary>()
    var unreadCount = 0
    entries.forEach { entry ->
        if (entry.isRead) return@forEach
        unreadCount += 1
        val summary = byChannel.getOrPut(entry.channelId) { MutableChannelSummary() }
        if (summary.count < maxCount) {
            summary.count += 1
        }
        if (entry.timestampMillis < summary.firstTimestampMillis) {
            summary.firstTimestampMillis = entry.timestampMillis
            summary.firstMessageId = entry.messageId
        }
    }

    return LegacyAttentionSummary(
        channelAttention = byChannel.mapValues { (_, summary) ->
            io.ferventio.app.domain.ChannelAttention(
                unreadCount = summary.count,
                mentionCount = summary.count,
                firstUnreadMessageId = summary.firstMessageId,
            )
        },
        unreadCount = unreadCount,
    )
}
