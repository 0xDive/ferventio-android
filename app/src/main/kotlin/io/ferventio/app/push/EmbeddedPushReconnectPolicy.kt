package io.ferventio.app.push

import io.ferventio.shared.push.EmbeddedPushReconnectPolicy as SharedEmbeddedPushReconnectPolicy

/** Compatibility adapter while the Android embedded push service remains app-specific. */
object EmbeddedPushReconnectPolicy {
    const val MAX_BACKOFF_MILLIS = SharedEmbeddedPushReconnectPolicy.MAX_BACKOFF_MILLIS

    fun delayMillis(attempt: Int, jitterFraction: Double): Long =
        SharedEmbeddedPushReconnectPolicy.delayMillis(attempt, jitterFraction)
}
