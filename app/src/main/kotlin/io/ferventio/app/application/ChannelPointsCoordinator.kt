package io.ferventio.app.application

import io.ferventio.app.twitch.TwitchChannelPointsGqlClient
import io.ferventio.app.twitch.TwitchChannelPointsReward
import java.io.Closeable
import java.util.UUID
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
    val errorMessage: String? = null,
    val lastRedemptionId: String? = null,
)

data class ChannelPointsUiState(
    val byChannel: Map<String, ChannelPointsChannelState> = emptyMap(),
) {
    fun channel(channelId: String): ChannelPointsChannelState? = byChannel[channelId]
}

/** Separate state holder for private-GQL viewer Channel Points functionality. */
class ChannelPointsCoordinator internal constructor(
    private val api: TwitchChannelPointsGqlClient = TwitchChannelPointsGqlClient(),
) : Closeable {
    private val mutableState = MutableStateFlow(ChannelPointsUiState())
    val state: StateFlow<ChannelPointsUiState> = mutableState.asStateFlow()
    private val mutex = Mutex()

    suspend fun refresh(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
    ) = mutex.withLock {
        updateChannel(channelId, channelLogin) { it.copy(loading = true, errorMessage = null) }
        runCatching {
            api.getContext(auth.clientId, auth.accessToken, channelLogin)
        }.onSuccess { context ->
            updateChannel(channelId, channelLogin) {
                it.copy(
                    balance = context.balance,
                    rewards = context.rewards.sortedWith(compareBy<TwitchChannelPointsReward> { reward -> !reward.enabled }.thenBy { reward -> reward.cost }),
                    loading = false,
                    errorMessage = null,
                )
            }
        }.onFailure { error ->
            updateChannel(channelId, channelLogin) {
                it.copy(loading = false, errorMessage = error.message ?: error::class.simpleName)
            }
        }.getOrThrow()
    }

    suspend fun redeem(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
        reward: TwitchChannelPointsReward,
        textInput: String?,
    ) = mutex.withLock {
        updateChannel(channelId, channelLogin) {
            it.copy(redeemingRewardId = reward.id, errorMessage = null, lastRedemptionId = null)
        }
        runCatching {
            api.redeem(
                clientId = auth.clientId,
                accessToken = auth.accessToken,
                channelId = channelId,
                reward = reward,
                transactionId = UUID.randomUUID().toString(),
                textInput = textInput,
            )
        }.onSuccess { redemption ->
            val refreshed = runCatching {
                api.getContext(auth.clientId, auth.accessToken, channelLogin)
            }.getOrNull()
            updateChannel(channelId, channelLogin) { current ->
                current.copy(
                    balance = refreshed?.balance ?: current.balance?.let { balance -> (balance - reward.cost).coerceAtLeast(0) },
                    rewards = refreshed?.rewards ?: current.rewards,
                    redeemingRewardId = null,
                    errorMessage = null,
                    lastRedemptionId = redemption.id,
                )
            }
        }.onFailure { error ->
            updateChannel(channelId, channelLogin) {
                it.copy(redeemingRewardId = null, errorMessage = error.message ?: error::class.simpleName)
            }
        }.getOrThrow()
    }

    fun clearError(channelId: String) {
        mutableState.update { state ->
            val current = state.byChannel[channelId] ?: return@update state
            state.copy(byChannel = state.byChannel + (channelId to current.copy(errorMessage = null)))
        }
    }

    private fun updateChannel(
        channelId: String,
        channelLogin: String,
        transform: (ChannelPointsChannelState) -> ChannelPointsChannelState,
    ) {
        mutableState.update { state ->
            val current = state.byChannel[channelId]
                ?: ChannelPointsChannelState(channelId = channelId, channelLogin = channelLogin)
            state.copy(byChannel = state.byChannel + (channelId to transform(current)))
        }
    }

    override fun close() = api.close()
}
