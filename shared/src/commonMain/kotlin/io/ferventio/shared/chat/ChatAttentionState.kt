package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.MessageDecoration
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

    var messageNavigationTargets by mutableStateOf(emptyMap<String, String>())
        private set

    val mentionUnreadCount: Int
        get() = channelAttention.values.sumOf(SharedChannelAttention::mentionCount)

    fun attention(channelId: String): SharedChannelAttention =
        channelAttention[channelId.trim()] ?: SharedChannelAttention()

    fun navigationTarget(channelId: String): String? =
        messageNavigationTargets[channelId.trim()]?.takeIf(String::isNotBlank)

    fun requestMessageNavigation(channelId: String, messageId: String) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedMessageId = requireMessageId(messageId)
        if (messageNavigationTargets[normalizedChannelId] == normalizedMessageId) return
        messageNavigationTargets = messageNavigationTargets + (normalizedChannelId to normalizedMessageId)
    }

    fun consumeMessageNavigation(channelId: String, messageId: String): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedMessageId = requireMessageId(messageId)
        if (messageNavigationTargets[normalizedChannelId] != normalizedMessageId) return false
        messageNavigationTargets = messageNavigationTargets - normalizedChannelId
        return true
    }

    fun updateViewport(
        channelId: String,
        visible: Boolean,
        isAtLiveTail: Boolean,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val shouldBeAtLiveTail = visible && isAtLiveTail
        val wasVisible = normalizedChannelId in visibleChannelIds
        val wasAtLiveTail = normalizedChannelId in channelsAtLiveTail

        if (wasVisible != visible) {
            visibleChannelIds = if (visible) {
                visibleChannelIds + normalizedChannelId
            } else {
                visibleChannelIds - normalizedChannelId
            }
        }
        if (wasAtLiveTail != shouldBeAtLiveTail) {
            channelsAtLiveTail = if (shouldBeAtLiveTail) {
                channelsAtLiveTail + normalizedChannelId
            } else {
                channelsAtLiveTail - normalizedChannelId
            }
        }

        // Any unread attention entry implies channelAttention for the same channel, so avoid
        // scanning the bounded attention list on every unchanged viewport sample.
        if (shouldBeAtLiveTail && normalizedChannelId in channelAttention) {
            markChannelRead(normalizedChannelId)
        }
    }

    /** Convenience overload retained for callers that own only the evaluator. */
    fun recordIncoming(
        message: ChatMessage,
        session: TwitchSession?,
        evaluator: MessageRuleEvaluator,
    ) {
        val decoration = evaluator.evaluate(message)
        recordIncoming(
            message = message,
            session = session,
            decoration = decoration,
            directMention = evaluator.isDirectMention(message),
        )
    }

    /**
     * Records attention using the one-time decoration computed by the live transport.
     *
     * Passing the same result to timeline, Mentions and alert hooks prevents rule edits from
     * changing the meaning of messages that were already received.
     */
    fun recordIncoming(
        message: ChatMessage,
        session: TwitchSession?,
        decoration: MessageDecoration,
        directMention: Boolean,
    ) {
        val channelId = requireChannelId(message.channelId)
        val isSystemMessage = message.isSystem
        val isOwnMessage = session?.userId?.isNotBlank() == true && message.userId == session.userId
        val isVisibleLive = channelId in visibleChannelIds && channelId in channelsAtLiveTail
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
            attentionEntries = upsertAttentionEntry(attentionEntries, entry)
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

    /** Moves all per-channel attention/UI navigation state to a replacement runtime channel id. */
    fun remapChannelId(channelId: String, replacementId: String): Boolean {
        val currentId = requireChannelId(channelId)
        val nextId = requireChannelId(replacementId)
        if (currentId == nextId) return false

        val hadState = currentId in channelAttention ||
            attentionEntries.any { entry -> entry.channelId == currentId } ||
            currentId in visibleChannelIds ||
            currentId in channelsAtLiveTail ||
            currentId in messageNavigationTargets
        if (!hadState) return false

        channelAttention[currentId]?.let { value ->
            channelAttention = (channelAttention - currentId) + (nextId to value)
        }
        if (attentionEntries.any { entry -> entry.channelId == currentId }) {
            attentionEntries = attentionEntries.map { entry ->
                if (entry.channelId == currentId) entry.copy(channelId = nextId) else entry
            }
        }
        visibleChannelIds = visibleChannelIds
            .mapTo(linkedSetOf()) { id -> if (id == currentId) nextId else id }
        channelsAtLiveTail = channelsAtLiveTail
            .mapTo(linkedSetOf()) { id -> if (id == currentId) nextId else id }
        messageNavigationTargets[currentId]?.let { messageId ->
            messageNavigationTargets = (messageNavigationTargets - currentId) + (nextId to messageId)
        }
        return true
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds.map(String::trim).filter(String::isNotEmpty).toSet()
        channelAttention = channelAttention.filterKeys(allowed::contains)
        attentionEntries = attentionEntries.filter { it.channelId in allowed }
        visibleChannelIds = visibleChannelIds.filterTo(linkedSetOf(), allowed::contains)
        channelsAtLiveTail = channelsAtLiveTail.filterTo(linkedSetOf(), allowed::contains)
        messageNavigationTargets = messageNavigationTargets.filterKeys(allowed::contains)
    }

    fun clear() {
        channelAttention = emptyMap()
        attentionEntries = emptyList()
        visibleChannelIds = emptySet()
        channelsAtLiveTail = emptySet()
        messageNavigationTargets = emptyMap()
    }

    private fun upsertAttentionEntry(
        existing: List<AttentionEntry>,
        entry: AttentionEntry,
    ): List<AttentionEntry> {
        val updated = ArrayList<AttentionEntry>(minOf(MAX_ATTENTION_ENTRIES + 1, existing.size + 1))
        existing.forEach { current ->
            if (current.messageId != entry.messageId) updated += current
        }

        var low = 0
        var high = updated.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val current = updated[mid]
            val comparison = compareAttention(current, entry)
            if (comparison <= 0) low = mid + 1 else high = mid
        }
        updated.add(low, entry)

        if (updated.size <= MAX_ATTENTION_ENTRIES) return updated
        return ArrayList<AttentionEntry>(MAX_ATTENTION_ENTRIES).apply {
            for (index in updated.size - MAX_ATTENTION_ENTRIES until updated.size) {
                add(updated[index])
            }
        }
    }

    private fun compareAttention(
        left: AttentionEntry,
        right: AttentionEntry,
    ): Int {
        val timestampComparison = left.timestampMillis.compareTo(right.timestampMillis)
        return if (timestampComparison != 0) {
            timestampComparison
        } else {
            left.messageId.compareTo(right.messageId)
        }
    }

    private fun requireChannelId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat attention channel id must not be blank")

    private fun requireMessageId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat attention message id must not be blank")

    private companion object {
        const val MAX_ATTENTION_COUNT = 9_999
        const val MAX_ATTENTION_ENTRIES = 2_000
    }
}
