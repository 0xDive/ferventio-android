package io.ferventio.app.domain

import androidx.compose.runtime.Immutable

@Immutable
data class PreparedChatMessageText(
    val fragmentText: String,
    val replyPreview: String?,
)

/**
 * Keeps string concatenation and reply-preview preparation out of the Compose hot path.
 * Messages are warmed by the controller before they are published to UI state.
 */
object ChatMessageTextPreparation {
    private val cache = BoundedLruCache<String, PreparedChatMessageText>(MAX_PREPARED_MESSAGES)

    fun warm(message: ChatMessage): PreparedChatMessageText {
        val prepared = prepare(message)
        cache[message.cacheKey()] = prepared
        return prepared
    }

    fun warm(messages: Iterable<ChatMessage>) {
        messages.forEach(::warm)
    }

    fun get(message: ChatMessage): PreparedChatMessageText =
        cache[message.cacheKey()] ?: warm(message)

    fun clear() = cache.clear()

    private fun prepare(message: ChatMessage): PreparedChatMessageText = PreparedChatMessageText(
        fragmentText = message.fragments.joinToString(separator = "") { fragment -> fragment.text },
        replyPreview = message.reply?.let { reply ->
            buildString {
                append("Ответ для @")
                append(reply.parentUserName ?: reply.parentUserLogin.orEmpty())
                reply.parentMessageBody?.takeIf(String::isNotBlank)?.let { body ->
                    append(": ")
                    append(body)
                }
            }
        },
    )

    private fun ChatMessage.cacheKey(): String = buildString {
        append(id)
        append('|')
        append(serverMessageId.orEmpty())
        append('|')
        append(text.hashCode())
        append('|')
        append(fragments.hashCode())
        append('|')
        append(reply.hashCode())
    }

    private const val MAX_PREPARED_MESSAGES = 12_000
}
