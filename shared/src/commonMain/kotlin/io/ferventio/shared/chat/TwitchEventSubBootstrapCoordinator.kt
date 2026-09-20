package io.ferventio.shared.chat

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.StoredAuthentication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal data class TwitchEventSubBootstrapFailure(
    val channel: ChatChannel,
    val type: String,
    val message: String,
    val cause: Throwable? = null,
)

internal data class TwitchEventSubBootstrapResult(
    val channels: List<ChatChannel>,
    val connectedChannel: ChatChannel,
    val createdSubscriptions: List<TwitchEventSubSubscriptionSpec>,
    val remainingSubscriptions: List<TwitchEventSubSubscriptionSpec>,
    val failures: List<TwitchEventSubBootstrapFailure>,
) {
    val subscriptionCount: Int get() = createdSubscriptions.size
    val noticeReady: Boolean
        get() = createdSubscriptions.any {
            it.broadcasterId == connectedChannel.id &&
                it.type == TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE
        }
}

internal class TwitchEventSubBootstrapException(
    val failures: List<TwitchEventSubBootstrapFailure>,
    cause: Throwable? = null,
) : IllegalStateException(
    failures.firstOrNull()?.message ?: "Twitch EventSub primary chat bootstrap failed",
    cause,
)

/**
 * Establishes the minimum usable EventSub session first, then exposes the remaining
 * subscription work separately so optional moderation/interactive events cannot block chat.
 */
internal class TwitchEventSubBootstrapCoordinator(
    private val createSubscriptionAction: suspend (
        StoredAuthentication,
        String,
        TwitchEventSubSubscriptionSpec,
    ) -> Unit,
) {
    constructor() : this(TwitchEventSubSubscriptionCoordinator())

    constructor(coordinator: TwitchEventSubSubscriptionCoordinator) : this(
        createSubscriptionAction = { authentication, sessionId, spec ->
            coordinator.createSubscription(authentication, sessionId, spec)
            Unit
        },
    )

    suspend fun bootstrap(
        authentication: StoredAuthentication,
        sessionId: String,
        channels: List<ChatChannel>,
        moderatedChannelIds: Set<String>,
    ): TwitchEventSubBootstrapResult {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        require(sessionId.isNotBlank()) { "EventSub session id must not be blank" }
        require(channels.isNotEmpty()) { "EventSub bootstrap requires at least one channel" }

        val normalizedChannels = channels.distinctBy(ChatChannel::id)
        val session = requireNotNull(authentication.accessLease).session
        val allSubscriptions = normalizedChannels
            .flatMap { channel ->
                TwitchEventSubSubscriptionPolicy.subscriptionsFor(
                    session = session,
                    channel = channel,
                    moderatedChannelIds = moderatedChannelIds,
                )
            }
            .distinct()

        val created = linkedSetOf<TwitchEventSubSubscriptionSpec>()
        val failures = mutableListOf<TwitchEventSubBootstrapFailure>()
        var connectedChannel: ChatChannel? = null
        var firstPrimaryError: Throwable? = null

        for (channel in normalizedChannels) {
            val primary = TwitchEventSubSubscriptionPolicy.subscription(
                broadcasterId = channel.id,
                type = TwitchEventSubSubscriptionPolicy.PRIMARY_EVENT_TYPE,
            )
            val primaryError = createCatching(authentication, sessionId, primary)
            if (primaryError != null) {
                if (firstPrimaryError == null) firstPrimaryError = primaryError
                failures += primaryError.toFailure(channel, primary.type)
                if (
                    primaryError.isTwitchAuthenticationFailure() ||
                    TwitchEventSubConnectionPolicy.isWebSocketTransportLimit(primaryError)
                ) {
                    throw TwitchEventSubBootstrapException(
                        failures = failures.toList(),
                        cause = primaryError,
                    )
                }
                continue
            }

            created += primary
            connectedChannel = channel

            val notice = TwitchEventSubSubscriptionPolicy.subscription(
                broadcasterId = channel.id,
                type = TwitchEventSubSubscriptionPolicy.NOTICE_EVENT_TYPE,
            )
            val noticeError = createCatching(authentication, sessionId, notice)
            if (noticeError == null) {
                created += notice
            } else {
                failures += noticeError.toFailure(channel, notice.type)
                if (noticeError.isTwitchAuthenticationFailure()) {
                    throw TwitchEventSubBootstrapException(
                        failures = failures.toList(),
                        cause = noticeError,
                    )
                }
            }
            break
        }

        val usableChannel = connectedChannel ?: throw TwitchEventSubBootstrapException(
            failures = failures,
            cause = firstPrimaryError,
        )
        return TwitchEventSubBootstrapResult(
            channels = normalizedChannels,
            connectedChannel = usableChannel,
            createdSubscriptions = created.toList(),
            remainingSubscriptions = allSubscriptions.filterNot(created::contains),
            failures = failures.toList(),
        )
    }

    suspend fun createRemaining(
        authentication: StoredAuthentication,
        sessionId: String,
        bootstrap: TwitchEventSubBootstrapResult,
    ): List<TwitchEventSubBootstrapFailure> {
        val channelById = bootstrap.channels.associateBy(ChatChannel::id)
        val failures = mutableListOf<TwitchEventSubBootstrapFailure>()
        val remaining = bootstrap.remainingSubscriptions
        if (remaining.isEmpty()) return failures

        // Preserve the pre-optimization fail-fast contract for a stale/invalid token. If auth is
        // broken, every supplemental request would fail the same way; probing one request before
        // starting a batch avoids needless traffic and keeps reauthentication deterministic.
        val probe = remaining.first()
        val probeError = createCatching(authentication, sessionId, probe)
        if (probeError != null) {
            failures += probeError.toFailure(
                channel = channelById.getValue(probe.broadcasterId),
                type = probe.type,
            )
            if (
                probeError.isTwitchAuthenticationFailure() ||
                TwitchEventSubConnectionPolicy.isWebSocketTransportLimit(probeError)
            ) {
                return failures
            }
        }

        for (
            batch in remaining
                .drop(1)
                .chunked(REMAINING_SUBSCRIPTION_CONCURRENCY)
        ) {
            val batchResults = coroutineScope {
                batch.map { spec ->
                    async {
                        spec to createCatching(authentication, sessionId, spec)
                    }
                }.awaitAll()
            }
            var stopAfterBatch = false
            for ((spec, error) in batchResults) {
                if (error == null) continue
                val channel = channelById.getValue(spec.broadcasterId)
                failures += error.toFailure(channel, spec.type)
                if (
                    error.isTwitchAuthenticationFailure() ||
                    TwitchEventSubConnectionPolicy.isWebSocketTransportLimit(error)
                ) {
                    stopAfterBatch = true
                }
            }
            if (stopAfterBatch) break
        }
        return failures
    }

    private suspend fun createCatching(
        authentication: StoredAuthentication,
        sessionId: String,
        spec: TwitchEventSubSubscriptionSpec,
    ): Throwable? = try {
        createSubscriptionAction(authentication, sessionId, spec)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        error
    }

    private companion object {
        const val REMAINING_SUBSCRIPTION_CONCURRENCY = 4
    }

    private fun Throwable.toFailure(
        channel: ChatChannel,
        type: String,
    ): TwitchEventSubBootstrapFailure = TwitchEventSubBootstrapFailure(
        channel = channel,
        type = type,
        message = message?.takeIf(String::isNotBlank) ?: "EventSub subscription failed",
        cause = this,
    )
}
