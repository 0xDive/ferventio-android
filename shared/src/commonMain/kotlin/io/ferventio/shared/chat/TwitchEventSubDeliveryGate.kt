package io.ferventio.shared.chat

/**
 * Bounded EventSub message-id window matching the mature Android controller.
 * Twitch may replay notifications around reconnects, so notifications/revocations are delivered
 * once while lifecycle frames remain observable even when their message id repeats.
 */
internal class TwitchEventSubDeliveryGate(
    private val maxIds: Int = MAX_IDS,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    private val seenAtById = linkedMapOf<String, Long>()

    init {
        require(maxIds > 0) { "EventSub dedupe capacity must be positive" }
        require(ttlMillis > 0L) { "EventSub dedupe TTL must be positive" }
    }

    fun shouldDeliver(
        envelope: TwitchEventSubProtocolEnvelope,
        observedAtMillis: Long,
    ): Boolean {
        val messageId = envelope.messageId?.trim().orEmpty()
        if (messageId.isEmpty()) return true

        evictExpired(observedAtMillis)
        val previous = seenAtById[messageId]
        val isNew = previous == null || observedAtMillis - previous > ttlMillis
        if (isNew) {
            seenAtById.remove(messageId)
            seenAtById[messageId] = observedAtMillis
            trimToCapacity()
        }
        return isNew || envelope.type !in DUPLICATE_SENSITIVE_TYPES
    }

    fun clear() {
        seenAtById.clear()
    }

    private fun evictExpired(nowMillis: Long) {
        val iterator = seenAtById.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowMillis - entry.value > ttlMillis) {
                iterator.remove()
            }
        }
    }

    private fun trimToCapacity() {
        while (seenAtById.size > maxIds) {
            val iterator = seenAtById.entries.iterator()
            if (!iterator.hasNext()) return
            iterator.next()
            iterator.remove()
        }
    }

    private companion object {
        const val MAX_IDS = 4_000
        const val TTL_MILLIS = 10 * 60 * 1_000L
        val DUPLICATE_SENSITIVE_TYPES = setOf("notification", "revocation")
    }
}
