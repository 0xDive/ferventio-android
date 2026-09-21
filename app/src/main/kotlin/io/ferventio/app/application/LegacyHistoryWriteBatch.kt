package io.ferventio.app.application

import io.ferventio.app.domain.AttentionEntry
import io.ferventio.app.domain.ChatMessage

internal data class HistoryWriteRequest(
    val message: ChatMessage,
    val attention: AttentionEntry? = null,
)

internal data class LegacyHistoryWriteBatch(
    val messages: List<ChatMessage>,
    val attentionEntries: List<AttentionEntry>,
)

internal fun collectLegacyHistoryWriteBatch(
    requests: List<HistoryWriteRequest>,
): LegacyHistoryWriteBatch {
    if (requests.isEmpty()) {
        return LegacyHistoryWriteBatch(
            messages = emptyList(),
            attentionEntries = emptyList(),
        )
    }
    val messages = ArrayList<ChatMessage>(requests.size)
    var attentionEntries: MutableList<AttentionEntry>? = null
    requests.forEach { request ->
        messages += request.message
        request.attention?.let { attention ->
            val target = attentionEntries ?: ArrayList<AttentionEntry>().also {
                attentionEntries = it
            }
            target += attention
        }
    }
    return LegacyHistoryWriteBatch(
        messages = messages,
        attentionEntries = attentionEntries ?: emptyList(),
    )
}
