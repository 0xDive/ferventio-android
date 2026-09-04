package io.ferventio.shared.history

internal data class ChatHistoryPageBoundary(
    val timestampMillis: Long,
    val messageId: String,
)

/** Prevents duplicate or exhausted older-history requests for the same timeline boundary. */
internal class ChatHistoryPagingGate {
    private var inFlight: ChatHistoryPageBoundary? = null
    private var exhausted: ChatHistoryPageBoundary? = null

    fun tryStart(timestampMillis: Long, messageId: String): ChatHistoryPageBoundary? {
        val normalizedMessageId = messageId.trim()
        if (normalizedMessageId.isEmpty()) return null
        val boundary = ChatHistoryPageBoundary(timestampMillis, normalizedMessageId)
        if (inFlight != null || exhausted == boundary) return null
        inFlight = boundary
        return boundary
    }

    fun finish(boundary: ChatHistoryPageBoundary, loadedCount: Int) {
        if (inFlight != boundary) return
        inFlight = null
        if (loadedCount <= 0) {
            exhausted = boundary
        }
    }

    fun cancel(boundary: ChatHistoryPageBoundary) {
        if (inFlight == boundary) inFlight = null
    }
}
