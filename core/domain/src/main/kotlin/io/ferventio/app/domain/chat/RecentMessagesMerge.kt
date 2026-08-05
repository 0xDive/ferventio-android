package io.ferventio.app.domain

/**
 * Merges a remote recent-message snapshot with messages already held by the client.
 *
 * Existing messages win on duplicate IDs because EventSub/IRC rows may already contain richer
 * local state (optimistic-send reconciliation, moderation state, or hydrated author data).
 */
object RecentMessagesMerge {
    data class Result(
        val messages: List<ChatMessage>,
        val addedMessages: List<ChatMessage>,
    )

    fun merge(
        existing: List<ChatMessage>,
        recent: List<ChatMessage>,
        limit: Int,
    ): Result {
        if (limit <= 0) return Result(emptyList(), emptyList())

        val existingIds = existing.asSequence()
            .map(ChatMessage::id)
            .filter(String::isNotBlank)
            .toHashSet()
        val byId = LinkedHashMap<String, ChatMessage>(existing.size + recent.size)

        recent.forEach { message ->
            if (message.id.isNotBlank()) byId[message.id] = message
        }
        existing.forEach { message ->
            if (message.id.isNotBlank()) byId[message.id] = message
        }

        val merged = byId.values
            .sortedWith(
                compareBy<ChatMessage>(ChatMessage::timestampMillis)
                    .thenBy(ChatMessage::id),
            )
            .takeLast(limit)
        val retainedIds = merged.asSequence().map(ChatMessage::id).toHashSet()
        val added = recent.asSequence()
            .filter { message ->
                message.id.isNotBlank() &&
                    message.id !in existingIds &&
                    message.id in retainedIds
            }
            .distinctBy(ChatMessage::id)
            .sortedWith(
                compareBy<ChatMessage>(ChatMessage::timestampMillis)
                    .thenBy(ChatMessage::id),
            )
            .toList()

        return Result(merged, added)
    }
}
