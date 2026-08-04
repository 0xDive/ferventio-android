package io.ferventio.app.domain

object ScrollRestorationPolicy {
    fun targetIndex(
        messages: List<ChatMessage>,
        savedPosition: ChatScrollPosition?,
    ): Int? {
        if (messages.isEmpty()) return null
        if (savedPosition == null || savedPosition.isAtBottom) return messages.lastIndex

        val anchorIndex = savedPosition.anchorMessageId
            ?.let { anchorId -> messages.indexOfFirst { message -> message.id == anchorId } }
            ?.takeIf { index -> index >= 0 }
        return anchorIndex
            ?: savedPosition.firstVisibleItemIndex.coerceIn(0, messages.lastIndex)
    }
}
