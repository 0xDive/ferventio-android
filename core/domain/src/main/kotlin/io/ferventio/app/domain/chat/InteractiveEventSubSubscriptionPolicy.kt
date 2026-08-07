package io.ferventio.app.domain

/**
 * Twitch Polls and Predictions are broadcaster-owned APIs. Their Helix reads and
 * EventSub subscriptions use the broadcaster user's authorization, so the
 * direct-device client must only request them for the authenticated user's own
 * channel and only when the access token contains an appropriate scope.
 */
object InteractiveEventSubSubscriptionPolicy {
    const val POLL_BEGIN = "channel.poll.begin"
    const val POLL_PROGRESS = "channel.poll.progress"
    const val POLL_END = "channel.poll.end"
    const val PREDICTION_BEGIN = "channel.prediction.begin"
    const val PREDICTION_PROGRESS = "channel.prediction.progress"
    const val PREDICTION_LOCK = "channel.prediction.lock"
    const val PREDICTION_END = "channel.prediction.end"

    val POLL_EVENT_TYPES: List<String> = listOf(
        POLL_BEGIN,
        POLL_PROGRESS,
        POLL_END,
    )

    val PREDICTION_EVENT_TYPES: List<String> = listOf(
        PREDICTION_BEGIN,
        PREDICTION_PROGRESS,
        PREDICTION_LOCK,
        PREDICTION_END,
    )

    val ALL_EVENT_TYPES: Set<String> =
        (POLL_EVENT_TYPES + PREDICTION_EVENT_TYPES).toSet()

    fun eventTypesFor(
        session: TwitchSession,
        channel: ChatChannel,
    ): List<String> {
        if (channel.id.isBlank() || channel.id != session.userId) return emptyList()

        return buildList {
            if (session.scopes.hasAny(POLL_READ_SCOPE, POLL_MANAGE_SCOPE)) {
                addAll(POLL_EVENT_TYPES)
            }
            if (session.scopes.hasAny(PREDICTION_READ_SCOPE, PREDICTION_MANAGE_SCOPE)) {
                addAll(PREDICTION_EVENT_TYPES)
            }
        }
    }

    private fun Set<String>.hasAny(vararg scopes: String): Boolean = scopes.any(::contains)

    private const val POLL_READ_SCOPE = "channel:read:polls"
    private const val POLL_MANAGE_SCOPE = "channel:manage:polls"
    private const val PREDICTION_READ_SCOPE = "channel:read:predictions"
    private const val PREDICTION_MANAGE_SCOPE = "channel:manage:predictions"
}
