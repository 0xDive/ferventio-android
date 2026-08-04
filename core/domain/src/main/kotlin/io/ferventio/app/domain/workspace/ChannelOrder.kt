package io.ferventio.app.domain

/** Stable user-defined order shared by the drawer and the channel pager. */
object ChannelOrder {
    fun move(
        channels: List<ChatChannel>,
        channelId: String,
        targetIndex: Int,
    ): List<ChatChannel> {
        if (channels.size < 2) return channels
        val fromIndex = channels.indexOfFirst { it.id == channelId }
        if (fromIndex < 0) return channels
        val safeTarget = targetIndex.coerceIn(0, channels.lastIndex)
        if (fromIndex == safeTarget) return channels
        return channels.toMutableList().apply {
            val channel = removeAt(fromIndex)
            add(safeTarget, channel)
        }
    }
}
