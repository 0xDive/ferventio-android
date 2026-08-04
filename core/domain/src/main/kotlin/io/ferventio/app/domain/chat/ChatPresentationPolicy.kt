package io.ferventio.app.domain

object ChatPresentationPolicy {
    const val DELETED_MESSAGE_PLACEHOLDER = "[сообщение удалено]"

    fun visibleText(
        message: ChatMessage,
        showDeletedMessageContent: Boolean,
    ): String = if (message.isDeleted && !showDeletedMessageContent) {
        DELETED_MESSAGE_PLACEHOLDER
    } else {
        message.text
    }

    fun shouldShowModeratorActions(
        isAuthenticated: Boolean,
        isModerator: Boolean,
    ): Boolean = isAuthenticated && isModerator
}
