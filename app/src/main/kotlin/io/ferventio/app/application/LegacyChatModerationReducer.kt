package io.ferventio.app.application

import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState

internal fun markLegacyMessageDeleted(
    messages: List<ChatMessage>,
    messageId: String,
    atMillis: Long,
): List<ChatMessage>? {
    val index = messages.indexOfFirst { message -> message.id == messageId }
    if (index < 0) return null
    val current = messages[index]
    val updated = current.copy(
        flags = current.flags.copy(isDeleted = true),
        moderation = ModerationState(
            action = ModerationAction.DELETE,
            atMillis = atMillis,
        ),
    )
    if (updated == current) return null
    return messages.toMutableList().apply {
        this[index] = updated
    }
}

internal fun markLegacyUserMessagesDeleted(
    messages: List<ChatMessage>,
    userId: String,
    atMillis: Long,
): List<ChatMessage>? {
    var updated: MutableList<ChatMessage>? = null
    for (index in messages.indices) {
        val current = messages[index]
        if (current.userId != userId) continue
        val replacement = current.copy(
            flags = current.flags.copy(isDeleted = true),
            moderation = ModerationState(
                action = ModerationAction.TIMEOUT,
                atMillis = atMillis,
            ),
        )
        if (replacement != current) {
            val target = updated ?: messages.toMutableList().also { updated = it }
            target[index] = replacement
        }
    }
    return updated
}
