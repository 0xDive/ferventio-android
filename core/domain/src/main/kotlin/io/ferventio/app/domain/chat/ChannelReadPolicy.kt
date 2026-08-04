package io.ferventio.app.domain

/**
 * Keeps unread state tied to chats that are really visible, not merely composed by a pager.
 */
object ChannelReadPolicy {
    fun canMarkRead(channelId: String, visibleChannelIds: Set<String>): Boolean =
        channelId in visibleChannelIds

    fun isLiveVisible(
        channelId: String,
        visibleChannelIds: Set<String>,
        scrollPosition: ChatScrollPosition?,
    ): Boolean = channelId in visibleChannelIds && scrollPosition?.isAtBottom != false
}
