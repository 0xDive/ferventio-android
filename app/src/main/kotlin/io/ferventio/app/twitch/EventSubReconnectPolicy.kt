package io.ferventio.app.twitch

import kotlin.math.min

internal object EventSubReconnectPolicy {
    private const val MAX_BASE_DELAY_MILLIS = 30_000L
    private const val JITTER_PERCENT = 0.25

    fun baseDelayMillis(attempt: Int): Long {
        val normalizedAttempt = attempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(5)
        return min(MAX_BASE_DELAY_MILLIS, 1_000L * (1L shl exponent))
    }

    fun delayMillis(attempt: Int, jitterFraction: Double): Long {
        val base = baseDelayMillis(attempt)
        val jitter = (base * JITTER_PERCENT * jitterFraction.coerceIn(0.0, 1.0)).toLong()
        return base + jitter
    }
}
