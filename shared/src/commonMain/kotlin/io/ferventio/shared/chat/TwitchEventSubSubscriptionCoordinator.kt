package io.ferventio.shared.chat

import io.ferventio.app.domain.StoredAuthentication
import kotlinx.coroutines.delay

class TwitchEventSubSubscriptionCoordinator(
    private val client: TwitchEventSubSubscriptionClient,
    private val delayAction: suspend (Long) -> Unit = { millis -> delay(millis) },
) {
    constructor() : this(TwitchEventSubSubscriptionClient())

    suspend fun createSubscription(
        authentication: StoredAuthentication,
        sessionId: String,
        spec: TwitchEventSubSubscriptionSpec,
    ): TwitchEventSubSubscriptionRequest {
        var conflictAttempt = 0
        while (true) {
            try {
                return client.createSubscription(
                    authentication = authentication,
                    sessionId = sessionId,
                    broadcasterId = spec.broadcasterId,
                    type = spec.type,
                    version = spec.version,
                    identityConditionKey = spec.identityConditionKey,
                )
            } catch (error: TwitchEventSubSubscriptionException) {
                if (error.statusCode != CONFLICT_STATUS_CODE) throw error
                conflictAttempt += 1
                if (conflictAttempt >= CONFLICT_MAX_ATTEMPTS) throw error
                delayAction(CONFLICT_RETRY_DELAYS_MILLIS[conflictAttempt - 1])
            }
        }
    }

    companion object {
        const val CONFLICT_STATUS_CODE = 409
        const val CONFLICT_MAX_ATTEMPTS = 4
        val CONFLICT_RETRY_DELAYS_MILLIS: List<Long> = listOf(250L, 500L, 1_000L)
    }
}
