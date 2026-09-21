package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

    var mentionUnreadCount by mutableIntStateOf(0)
        private set

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
        val nextMentionCount = (
            previous.mentionCount + if (shouldRecordAttention) 1 else 0
            ).coerceAtMost(MAX_ATTENTION_COUNT)
        if (nextMentionCount != previous.mentionCount) {
            mentionUnreadCount = (mentionUnreadCount + nextMentionCount - previous.mentionCount)
                .coerceAtLeast(0)
        }
        channelAttention = channelAttention + (
            channelId to previous.copy(
                unreadCount = (previous.unreadCount + 1).coerceAtMost(MAX_ATTENTION_COUNT),
                mentionCount = nextMentionCount,
                firstUnreadMessageId = previous.firstUnreadMessageId ?: message.id,
            )
        )
    }

    fun markChannelRead(channelId: String) {
        val normalizedChannelId = requireChannelId(channelId)
        channelAttention[normalizedChannelId]?.let { previous ->
            if (previous.mentionCount > 0) {
                mentionUnreadCount = (mentionUnreadCount - previous.mentionCount).coerceAtLeast(0)
            }
            channelAttention = channelAttention - normalizedChannelId
        }
        attentionEntries.mapAttentionEntriesIfChanged { entry ->
            if (entry.channelId == normalizedChannelId && !entry.isRead) {
                entry.copy(isRead = true)
            } else {
                entry
            }
        }?.let { updated ->
            attentionEntries = updated
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
            val replaced = channelAttention[nextId]
            if (replaced != null && replaced.mentionCount > 0) {
                mentionUnreadCount = (mentionUnreadCount - replaced.mentionCount).coerceAtLeast(0)
            }
            channelAttention = (channelAttention - currentId) + (nextId to value)
        }
        attentionEntries.mapAttentionEntriesIfChanged { entry ->
            if (entry.channelId == currentId) entry.copy(channelId = nextId) else entry
        }?.let { updated ->
            attentionEntries = updated
        }
        if (currentId in visibleChannelIds) {
            visibleChannelIds = visibleChannelIds
                .mapTo(linkedSetOf()) { id -> if (id == currentId) nextId else id }
        }
        if (currentId in channelsAtLiveTail) {
            channelsAtLiveTail = channelsAtLiveTail
                .mapTo(linkedSetOf()) { id -> if (id == currentId) nextId else id }
        }
        messageNavigationTargets[currentId]?.let { messageId ->
            messageNavigationTargets = (messageNavigationTargets - currentId) + (nextId to messageId)
        }
        return true
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (channelAttention.keys.any { it !in allowed }) {
            val removedMentionCount = channelAttention.entries
                .asSequence()
                .filter { (channelId, _) -> channelId !in allowed }
                .sumOf { (_, attention) -> attention.mentionCount }
            if (removedMentionCount > 0) {
                mentionUnreadCount = (mentionUnreadCount - removedMentionCount).coerceAtLeast(0)
            }
            channelAttention = channelAttention.filterKeys(allowed::contains)
        }
        if (attentionEntries.any { it.channelId !in allowed }) {
            attentionEntries = attentionEntries.filter { it.channelId in allowed }
        }
        if (visibleChannelIds.any { it !in allowed }) {
            visibleChannelIds = visibleChannelIds.filterTo(linkedSetOf(), allowed::contains)
        }
        if (channelsAtLiveTail.any { it !in allowed }) {
            channelsAtLiveTail = channelsAtLiveTail.filterTo(linkedSetOf(), allowed::contains)
        }
        if (messageNavigationTargets.keys.any { it !in allowed }) {
            messageNavigationTargets = messageNavigationTargets.filterKeys(allowed::contains)
        }
    }

    fun clear() {
        channelAttention = emptyMap()
        mentionUnreadCount = 0
        attentionEntries = emptyList()
        visibleChannelIds = emptySet()
        channelsAtLiveTail = emptySet()
        messageNavigationTargets = emptyMap()
    }

    private inline fun List<AttentionEntry>.mapAttentionEntriesIfChanged(
        transform: (AttentionEntry) -> AttentionEntry,
    ): List<AttentionEntry>? {
        var updated: MutableList<AttentionEntry>? = null
        for (index in indices) {
            val current = this[index]
            val replacement = transform(current)
            if (replacement !== current) {
                val target = updated ?: toMutableList().also { updated = it }
                target[index] = replacement
            }
        }
        return updated
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
