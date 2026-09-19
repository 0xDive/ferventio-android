package io.ferventio.shared.chat

/**
 * AutoMod hold/update are distinct EventSub subscriptions, so a terminal update may arrive before
 * a delayed hold. Keep a bounded terminal-id set so stale holds cannot resurrect resolved cards.
 *
 * TwitchEventSubSocketClient delivers envelopes serially to one session runtime, so this common
 * implementation does not need platform-specific synchronization.
 */
internal class AutoModEventOrderingGuard(
    private val capacity: Int = 1_024,
) {
    private val terminalIds = LinkedHashSet<String>()

    init {
        require(capacity > 0) { "AutoMod ordering guard capacity must be positive" }
    }

    fun markTerminal(messageId: String) {
        val id = messageId.trim()
        if (id.isEmpty()) return
        terminalIds.remove(id)
        terminalIds.add(id)
        while (terminalIds.size > capacity) {
            val oldest = terminalIds.firstOrNull() ?: break
            terminalIds.remove(oldest)
        }
    }

    fun shouldAcceptHold(messageId: String): Boolean {
        val id = messageId.trim()
        return id.isEmpty() || id !in terminalIds
    }
}
