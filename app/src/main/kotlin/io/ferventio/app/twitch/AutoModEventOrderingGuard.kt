package io.ferventio.app.twitch

/**
 * AutoMod hold/update are separate EventSub subscriptions, so a terminal update can arrive before
 * a delayed hold. Remember a bounded set of terminal message ids and drop those stale holds at the
 * transport boundary before they can resurrect a resolved moderation card.
 */
internal class AutoModEventOrderingGuard(
    private val capacity: Int = 1_024,
) {
    private val terminalIds = LinkedHashSet<String>()

    @Synchronized
    fun markTerminal(messageId: String) {
        val id = messageId.trim()
        if (id.isBlank()) return
        terminalIds.remove(id)
        terminalIds.add(id)
        while (terminalIds.size > capacity) {
            val oldest = terminalIds.firstOrNull() ?: break
            terminalIds.remove(oldest)
        }
    }

    @Synchronized
    fun shouldAcceptHold(messageId: String): Boolean {
        val id = messageId.trim()
        return id.isBlank() || id !in terminalIds
    }
}
