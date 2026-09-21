package io.ferventio.shared.chat

import kotlin.math.min

/** Shared lifecycle constants and retry policy for Twitch EventSub WebSockets. */
object TwitchEventSubConnectionPolicy {
    const val DEFAULT_SOCKET_URL =
        "wss://eventsub.wss.twitch.tv/ws?keepalive_timeout_seconds=30"
    const val WELCOME_TIMEOUT_MILLIS = 15_000L
    const val DEFAULT_KEEPALIVE_SECONDS = 30
    const val MIN_KEEPALIVE_SECONDS = 10
    const val MAX_KEEPALIVE_SECONDS = 600
    const val KEEPALIVE_GRACE_SECONDS = 10
    const val MAX_AUTOMATIC_RECONNECT_ATTEMPTS = 5
    const val MAX_TRANSPORT_LIMIT_RECONNECT_ATTEMPTS = 2
    const val AUTHORIZATION_REVOKED = "authorization_revoked"

    private const val TRANSPORT_LIMIT_MIN_RETRY_DELAY_MILLIS = 5_000L
    private const val MAX_BASE_DELAY_MILLIS = 30_000L
    private const val JITTER_PERCENT = 0.25

    fun keepaliveSeconds(value: Int?): Int =
        value?.coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS)
            ?: DEFAULT_KEEPALIVE_SECONDS

    fun receiveTimeoutMillis(value: Int?): Long =
        (keepaliveSeconds(value) + KEEPALIVE_GRACE_SECONDS) * 1_000L

    fun reconnectBaseDelayMillis(attempt: Int): Long {
        val normalizedAttempt = attempt.coerceAtLeast(1)
        val exponent = (normalizedAttempt - 1).coerceAtMost(5)
        return min(MAX_BASE_DELAY_MILLIS, 1_000L * (1L shl exponent))
    }

    fun reconnectDelayMillis(
        attempt: Int,
        jitterFraction: Double,
    ): Long {
        val base = reconnectBaseDelayMillis(attempt)
        val jitter = (base * JITTER_PERCENT * jitterFraction.coerceIn(0.0, 1.0)).toLong()
        return base + jitter
    }

    fun shouldStopAfterRevocation(status: String?): Boolean =
        status == AUTHORIZATION_REVOKED

    fun isWebSocketTransportLimit(error: Throwable): Boolean =
        generateSequence(error) { current -> current.cause }
            .filterIsInstance<TwitchEventSubSubscriptionException>()
            .any { failure ->
                failure.statusCode == 429 &&
                    failure.twitchMessage
                        ?.lowercase()
                        ?.let { message ->
                            "websocket" in message &&
                                "transport" in message &&
                                ("limit" in message || "exceeded" in message)
                        } == true
            }

    fun canRetry(attempt: Int): Boolean =
        attempt < MAX_AUTOMATIC_RECONNECT_ATTEMPTS

    fun canRetryTransportLimit(attempt: Int): Boolean =
        attempt < MAX_TRANSPORT_LIMIT_RECONNECT_ATTEMPTS

    fun transportLimitRetryDelayMillis(
        attempt: Int,
        jitterFraction: Double,
    ): Long = maxOf(
        TRANSPORT_LIMIT_MIN_RETRY_DELAY_MILLIS,
        reconnectDelayMillis(attempt, jitterFraction),
    )
}
