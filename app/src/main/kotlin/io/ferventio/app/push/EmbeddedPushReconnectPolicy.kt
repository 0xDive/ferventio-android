package io.ferventio.app.push

import kotlin.math.min

object EmbeddedPushReconnectPolicy {
    const val MAX_BACKOFF_MILLIS = 60_000L

    fun delayMillis(attempt: Int, jitterFraction: Double): Long {
        val normalizedAttempt = attempt.coerceAtLeast(1)
        val base = min(MAX_BACKOFF_MILLIS, 1_000L shl min(normalizedAttempt - 1, 6))
        val clampedJitter = jitterFraction.coerceIn(-0.2, 0.2)
        return (base + (base * clampedJitter).toLong()).coerceAtLeast(500L)
    }
}
