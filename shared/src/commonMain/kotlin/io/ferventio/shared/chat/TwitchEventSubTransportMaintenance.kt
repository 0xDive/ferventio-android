package io.ferventio.shared.chat

import io.ferventio.app.domain.StoredAuthentication

data class TwitchEventSubWebSocketSessionSnapshot(
    val sessionId: String,
    val subscriptionIds: List<String>,
    val subscriptionTypes: Set<String>,
    val connectedAt: String?,
    val disconnectedAt: String?,
) {
    val subscriptionCount: Int
        get() = subscriptionIds.size
}

data class TwitchEventSubTransportRecoverySnapshot(
    val sessions: List<TwitchEventSubWebSocketSessionSnapshot>,
) {
    val sessionCount: Int
        get() = sessions.size

    val subscriptionCount: Int
        get() = sessions.sumOf(TwitchEventSubWebSocketSessionSnapshot::subscriptionCount)
}

data class TwitchEventSubTransportCleanupResult(
    val deletedSubscriptionCount: Int,
    val targetedSessionIds: Set<String>,
)

class TwitchEventSubTransportMaintenanceRuntime(
    private val client: TwitchEventSubSubscriptionClient = TwitchEventSubSubscriptionClient(),
) {
    suspend fun load(authentication: StoredAuthentication): TwitchEventSubTransportRecoverySnapshot =
        summarize(
            client.listEnabledWebSocketSubscriptions(authentication),
        )

    suspend fun clearOtherSessions(
        authentication: StoredAuthentication,
        protectedSessionId: String?,
    ): TwitchEventSubTransportCleanupResult {
        val snapshot = load(authentication)
        val protectedId = protectedSessionId
            ?.trim()
            ?.takeIf(String::isNotEmpty)

        val targets = snapshot.sessions.filter { session ->
            session.sessionId != protectedId
        }
        var deleted = 0
        for (subscriptionId in targets.flatMap(TwitchEventSubWebSocketSessionSnapshot::subscriptionIds)) {
            if (client.deleteSubscription(authentication, subscriptionId)) {
                deleted += 1
            }
        }
        return TwitchEventSubTransportCleanupResult(
            deletedSubscriptionCount = deleted,
            targetedSessionIds = targets.mapTo(linkedSetOf()) { it.sessionId },
        )
    }

    internal fun summarize(
        subscriptions: List<TwitchEventSubSubscriptionSnapshot>,
    ): TwitchEventSubTransportRecoverySnapshot {
        val sessions = subscriptions
            .asSequence()
            .filter { subscription ->
                subscription.status == ENABLED_STATUS &&
                    subscription.transport.method.equals(WEBSOCKET_METHOD, ignoreCase = true)
            }
            .mapNotNull { subscription ->
                val sessionId = subscription.transport.session_id
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                sessionId to subscription
            }
            .groupBy(
                keySelector = { (sessionId, _) -> sessionId },
                valueTransform = { (_, subscription) -> subscription },
            )
            .map { (sessionId, entries) ->
                TwitchEventSubWebSocketSessionSnapshot(
                    sessionId = sessionId,
                    subscriptionIds = entries
                        .map(TwitchEventSubSubscriptionSnapshot::id)
                        .filter(String::isNotBlank)
                        .distinct(),
                    subscriptionTypes = entries
                        .map(TwitchEventSubSubscriptionSnapshot::type)
                        .filter(String::isNotBlank)
                        .toSet(),
                    connectedAt = entries
                        .mapNotNull { it.transport.connected_at?.trim()?.takeIf(String::isNotEmpty) }
                        .minOrNull(),
                    disconnectedAt = entries
                        .mapNotNull { it.transport.disconnected_at?.trim()?.takeIf(String::isNotEmpty) }
                        .maxOrNull(),
                )
            }
            .sortedWith(
                compareBy<TwitchEventSubWebSocketSessionSnapshot> { it.connectedAt.orEmpty() }
                    .thenBy(TwitchEventSubWebSocketSessionSnapshot::sessionId),
            )

        return TwitchEventSubTransportRecoverySnapshot(sessions)
    }

    private companion object {
        const val ENABLED_STATUS = "enabled"
        const val WEBSOCKET_METHOD = "websocket"
    }
}
