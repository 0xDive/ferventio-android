package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.TwitchSession

/** Per-channel unread/mention counters matching the Android 0.0.5 live-read semantics. */
data class SharedChannelAttention(
    val unreadCount: Int = 0,
    val mentionCount: Int = 0,
    val firstUnreadMessageId: String? = null,
)

/**
 * Keeps attention state independent from the transport message reducer.
 *
 * A channel is considered read only while it is actually visible and its timeline is at the live
 * tail. Merely composing/selecting a channel does not consume unread state while the user is
 * scrolled up.
 */
class ChatAttentionStateHolder {
    var channelAttention by mutableStateOf(emptyMap<String, SharedChannelAttention>())
        private set

    var attentionEntries by mutableStateOf(emptyList<AttentionEntry>())
        private set

    var visibleChannelIds by mutableStateOf(emptySet<String>())
        private set

    var channelsAtLiveTail by mutableStateOf(emptySet<String>())
        private set

    val mentionUnreadCount: Int
        get() = channelAttention.values.sumOf(SharedChannelAttention::mentionCount)

    fun attention(channelId: String): SharedChannelAttention =
        channelAttention[channelId.trim()] ?: SharedChannelAttention()

    fun updateViewport(
        channelId: String,
        visible: Boolean,
        isAtLiveTail: Boolean,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        visibleChannelIds = if (visible) {
            visibleChannelIds + normalizedChannelId
        } else {
            visibleChannelIds - normalizedChannelId
        }
        channelsAtLiveTail = if (visible && isAtLiveTail) {
            channelsAtLiveTail + normalizedChannelId
        } else {
            channelsAtLiveTail - normalizedChannelId
        }
        if (visible && isAtLiveTail) markChannelRead(normalizedChannelId)
    }

    fun recordIncoming(
        message: ChatMessage,
        session: TwitchSession?,
        evaluator: MessageRuleEvaluator,
    ) {
        val channelId = requireChannelId(message.channelId)
        val isSystemMessage = message.isSystem
        val isOwnMessage = session?.userId?.isNotBlank() == true && message.userId == session.userId
        val isVisibleLive = channelId in visibleChannelIds && channelId in channelsAtLiveTail
        val decoration = evaluator.evaluate(message)
        val directMention = evaluator.isDirectMention(message)
        val addHighlightToMentions = decoration.isHighlighted && decoration.addToMentions
        val shouldRecordAttention = !isSystemMessage && !decoration.isIgnored &&
            (directMention || addHighlightToMentions)

        if (shouldRecordAttention) {
            val entry = AttentionEntry(
                messageId = message.id,
                channelId = message.channelId,
                channelLogin = message.channelLogin,
                authorId = message.userId,
                authorLogin = message.userLogin,
                authorDisplayName = message.userDisplayName,
                text = message.text,
                timestamp = message.timestamp,
                timestampMillis = message.timestampMillis,
                isRead = isVisibleLive || isOwnMessage,
                isDirectMention = directMention,
                isHighlight = addHighlightToMentions,
                highlightReasons = decoration.highlightReasons,
                highlightColorArgb = decoration.highlightColorArgb,
            )
            attentionEntries = (attentionEntries.filterNot { it.messageId == entry.messageId } + entry)
                .sortedWith(compareBy(AttentionEntry::timestampMillis, AttentionEntry::messageId))
                .takeLast(MAX_ATTENTION_ENTRIES)
        }

        if (isSystemMessage || isVisibleLive || isOwnMessage) return

        val previous = attention(channelId)
        channelAttention = channelAttention + (
            channelId to previous.copy(
                unreadCount = (previous.unreadCount + 1).coerceAtMost(MAX_ATTENTION_COUNT),
                mentionCount = (previous.mentionCount + if (shouldRecordAttention) 1 else 0)
                    .coerceAtMost(MAX_ATTENTION_COUNT),
                firstUnreadMessageId = previous.firstUnreadMessageId ?: message.id,
            )
        )
    }

    fun markChannelRead(channelId: String) {
        val normalizedChannelId = requireChannelId(channelId)
        if (normalizedChannelId in channelAttention) {
            channelAttention = channelAttention - normalizedChannelId
        }
        if (attentionEntries.any { it.channelId == normalizedChannelId && !it.isRead }) {
            attentionEntries = attentionEntries.map { entry ->
                if (entry.channelId == normalizedChannelId && !entry.isRead) {
                    entry.copy(isRead = true)
                } else {
                    entry
                }
            }
        }
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds.map(String::trim).filter(String::isNotEmpty).toSet()
        channelAttention = channelAttention.filterKeys(allowed::contains)
        attentionEntries = attentionEntries.filter { it.channelId in allowed }
        visibleChannelIds = visibleChannelIds.filterTo(linkedSetOf(), allowed::contains)
        channelsAtLiveTail = channelsAtLiveTail.filterTo(linkedSetOf(), allowed::contains)
    }

    fun clear() {
        channelAttention = emptyMap()
        attentionEntries = emptyList()
        visibleChannelIds = emptySet()
        channelsAtLiveTail = emptySet()
    }

    private fun requireChannelId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat attention channel id must not be blank")

    private companion object {
        const val MAX_ATTENTION_COUNT = 9_999
        const val MAX_ATTENTION_ENTRIES = 2_000
    }
}
