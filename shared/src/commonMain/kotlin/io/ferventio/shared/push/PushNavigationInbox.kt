package io.ferventio.shared.push

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Latest-wins handoff for notification-open navigation until the shared app shell consumes it.
 */
class PushNavigationInbox {
    var pendingTarget by mutableStateOf<PushNavigationTarget?>(null)
        private set

    fun offer(
        channelId: String?,
        channelLogin: String?,
        messageId: String?,
        destination: String?,
    ): Boolean {
        val target = PushNavigationPolicy.resolve(
            PushNavigationInput(
                channelId = channelId,
                channelLogin = channelLogin,
                messageId = messageId,
                destination = destination,
            ),
        ) ?: return false
        pendingTarget = target
        return true
    }

    fun consume(): PushNavigationTarget? {
        val target = pendingTarget
        pendingTarget = null
        return target
    }

    /**
     * Consumes [expectedTarget] only if it is still current.
     *
     * Navigation may suspend while loading message context. A newer notification offered during
     * that work must remain pending instead of being cleared by the older navigation attempt.
     */
    fun consume(expectedTarget: PushNavigationTarget): Boolean {
        if (pendingTarget != expectedTarget) return false
        pendingTarget = null
        return true
    }

    fun clear() {
        pendingTarget = null
    }
}
