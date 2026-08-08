package io.ferventio.app.application

import io.ferventio.app.twitch.TwitchApiException
import io.ferventio.app.twitch.TwitchChannelPointsContext
import io.ferventio.app.twitch.TwitchChannelPointsGqlClient
import io.ferventio.app.twitch.TwitchChannelPointsRedemption
import io.ferventio.app.twitch.TwitchChannelPointsRedeemException
import io.ferventio.app.twitch.TwitchChannelPointsReward
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ChannelPointsAuth(
    val clientId: String,
    val accessToken: String,
)

data class ChannelPointsChannelState(
    val channelId: String,
    val channelLogin: String,
    val balance: Int? = null,
    val rewards: List<TwitchChannelPointsReward> = emptyList(),
    val loading: Boolean = false,
    val redeemingRewardId: String? = null,
    val redemptionOutcomeUncertain: Boolean = false,
    val errorMessage: String? = null,
    val lastRedemptionId: String? = null,
)

data class ChannelPointsUiState(
    val byChannel: Map<String, ChannelPointsChannelState> = emptyMap(),
) {
    fun channel(channelId: String): ChannelPointsChannelState? = byChannel[channelId]
}

internal interface ChannelPointsGateway : Closeable {
    suspend fun getContext(
        clientId: String,
        accessToken: String,
        channelLogin: String,
    ): TwitchChannelPointsContext

    suspend fun redeem(
        clientId: String,
        accessToken: String,
        channelId: String,
        reward: TwitchChannelPointsReward,
        transactionId: String,
        textInput: String?,
    ): TwitchChannelPointsRedemption
}

private class TwitchChannelPointsGateway(
    private val client: TwitchChannelPointsGqlClient = TwitchChannelPointsGqlClient(),
) : ChannelPointsGateway {
    override suspend fun getContext(
        clientId: String,
        accessToken: String,
        channelLogin: String,
    ): TwitchChannelPointsContext = client.getContext(clientId, accessToken, channelLogin)

    override suspend fun redeem(
        clientId: String,
        accessToken: String,
        channelId: String,
        reward: TwitchChannelPointsReward,
        transactionId: String,
        textInput: String?,
    ): TwitchChannelPointsRedemption = client.redeem(
        clientId = clientId,
        accessToken = accessToken,
        channelId = channelId,
        reward = reward,
        transactionId = transactionId,
        textInput = textInput,
    )

    override fun close() = client.close()
}

/** Separate state holder for private-GQL viewer Channel Points functionality. */
class ChannelPointsCoordinator internal constructor(
    private val gateway: ChannelPointsGateway = TwitchChannelPointsGateway(),
) : Closeable {
    private val mutableState = MutableStateFlow(ChannelPointsUiState())
    val state: StateFlow<ChannelPointsUiState> = mutableState.asStateFlow()
    private val channelMutexes = ConcurrentHashMap<String, Mutex>()
    private val sessionEpoch = AtomicLong(0L)

    suspend fun refresh(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
    ) {
        val epoch = sessionEpoch.get()
        channelMutex(channelId).withLock {
            if (!isCurrentEpoch(epoch)) return@withLock
            updateChannel(channelId, channelLogin, epoch) { it.copy(loading = true, errorMessage = null) }
            try {
                val context = gateway.getContext(auth.clientId, auth.accessToken, channelLogin)
                updateChannel(channelId, channelLogin, epoch) {
                    it.copy(
                        balance = context.balance,
                        rewards = sortedRewards(context.rewards),
                        loading = false,
                        redemptionOutcomeUncertain = false,
                        errorMessage = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                updateChannel(channelId, channelLogin, epoch) { it.copy(loading = false) }
                throw cancelled
            } catch (error: Throwable) {
                updateChannel(channelId, channelLogin, epoch) {
                    it.copy(loading = false, errorMessage = error.message ?: error::class.simpleName)
                }
                throw error
            }
        }
    }

    suspend fun redeem(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
        reward: TwitchChannelPointsReward,
        textInput: String?,
    ): TwitchChannelPointsRedemption {
        val epoch = sessionEpoch.get()
        return channelMutex(channelId).withLock {
            check(isCurrentEpoch(epoch)) { "Channel Points session changed; refresh before redeeming" }
            check(mutableState.value.channel(channelId)?.redemptionOutcomeUncertain != true) {
                "Refresh Channel Points before another redemption"
            }
            updateChannel(channelId, channelLogin, epoch) {
                it.copy(
                    redeemingRewardId = reward.id,
                    redemptionOutcomeUncertain = false,
                    errorMessage = null,
                    lastRedemptionId = null,
                )
            }

            val redemption = try {
                gateway.redeem(
                    clientId = auth.clientId,
                    accessToken = auth.accessToken,
                    channelId = channelId,
                    reward = reward,
                    transactionId = UUID.randomUUID().toString(),
                    textInput = textInput,
                )
            } catch (cancelled: CancellationException) {
                markRedemptionOutcomeUncertain(channelId, channelLogin, epoch)
                throw cancelled
            } catch (error: TwitchApiException) {
                if (error.statusCode == 401) {
                    // An authorization rejection is definitive: Twitch rejected the request before
                    // executing the mutation. Leave it retryable so the controller can refresh the
                    // OAuth lease and invoke the same user action once with the fresh token.
                    updateChannel(channelId, channelLogin, epoch) {
                        it.copy(
                            redeemingRewardId = null,
                            redemptionOutcomeUncertain = false,
                            errorMessage = error.message,
                        )
                    }
                } else {
                    markRedemptionOutcomeUncertain(channelId, channelLogin, epoch)
                }
                throw error
            } catch (error: Throwable) {
                if (error is TwitchChannelPointsRedeemException) {
                    updateChannel(channelId, channelLogin, epoch) {
                        it.copy(
                            redeemingRewardId = null,
                            redemptionOutcomeUncertain = false,
                            errorMessage = error.message ?: error.code,
                        )
                    }
                } else {
                    markRedemptionOutcomeUncertain(channelId, channelLogin, epoch)
                }
                throw error
            }

            updateChannel(channelId, channelLogin, epoch) { current ->
                current.copy(
                    balance = current.balance?.let { balance -> (balance - reward.cost).coerceAtLeast(0) },
                    redeemingRewardId = null,
                    redemptionOutcomeUncertain = false,
                    errorMessage = null,
                    lastRedemptionId = redemption.id,
                )
            }

            if (!isCurrentEpoch(epoch)) return@withLock redemption
            try {
                val refreshed = gateway.getContext(auth.clientId, auth.accessToken, channelLogin)
                updateChannel(channelId, channelLogin, epoch) { current ->
                    current.copy(
                        balance = refreshed.balance,
                        rewards = sortedRewards(refreshed.rewards),
                    )
                }
            } catch (cancelled: CancellationException) {
                // Twitch already returned a redemption id, so the write is confirmed even if the
                // best-effort follow-up balance refresh is cancelled.
                throw cancelled
            } catch (_: Throwable) {
                // Keep the confirmed redemption and optimistic balance. A later refresh will reconcile.
            }
            redemption
        }
    }

    fun clearError(channelId: String) {
        mutableState.update { state ->
            val current = state.byChannel[channelId] ?: return@update state
            state.copy(byChannel = state.byChannel + (channelId to current.copy(errorMessage = null)))
        }
    }

    internal fun resetSession() {
        sessionEpoch.incrementAndGet()
        mutableState.value = ChannelPointsUiState()
    }

    private fun markRedemptionOutcomeUncertain(
        channelId: String,
        channelLogin: String,
        expectedEpoch: Long,
    ) {
        updateChannel(channelId, channelLogin, expectedEpoch) {
            it.copy(
                redeemingRewardId = null,
                redemptionOutcomeUncertain = true,
                errorMessage = null,
                lastRedemptionId = null,
            )
        }
    }

    private fun channelMutex(channelId: String): Mutex =
        channelMutexes.computeIfAbsent(channelId) { Mutex() }

    private fun isCurrentEpoch(expectedEpoch: Long): Boolean = sessionEpoch.get() == expectedEpoch

    private fun updateChannel(
        channelId: String,
        channelLogin: String,
        expectedEpoch: Long,
        transform: (ChannelPointsChannelState) -> ChannelPointsChannelState,
    ) {
        mutableState.update { state ->
            if (!isCurrentEpoch(expectedEpoch)) return@update state
            val current = state.byChannel[channelId]
                ?: ChannelPointsChannelState(channelId = channelId, channelLogin = channelLogin)
            state.copy(byChannel = state.byChannel + (channelId to transform(current)))
        }
    }

    override fun close() {
        sessionEpoch.incrementAndGet()
        mutableState.value = ChannelPointsUiState()
        channelMutexes.clear()
        gateway.close()
    }

    private companion object {
        fun sortedRewards(rewards: List<TwitchChannelPointsReward>): List<TwitchChannelPointsReward> =
            rewards.sortedWith(
                compareBy<TwitchChannelPointsReward> { reward -> !reward.enabled }
                    .thenBy { reward -> reward.cost },
            )
    }
}
