package io.ferventio.app.application

import io.ferventio.app.twitch.TwitchChannelPointsRedemption
import io.ferventio.app.twitch.TwitchChannelPointsReward
import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

/**
 * Compatibility surface for controller call sites left from the removed Channel Points feature.
 *
 * There is intentionally no network implementation. Twitch does not expose viewer balance or
 * viewer redemption through supported Helix APIs, so Ferventio must not attempt those operations.
 */
@Deprecated("Channel Points viewer operations are not supported by Twitch Helix")
class ChannelPointsCoordinator : Closeable {
    private val mutableState = MutableStateFlow(ChannelPointsUiState())
    val state: StateFlow<ChannelPointsUiState> = mutableState.asStateFlow()

    suspend fun refresh(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
    ) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = Triple(auth, channelId, channelLogin)
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }

    suspend fun redeem(
        auth: ChannelPointsAuth,
        channelId: String,
        channelLogin: String,
        reward: TwitchChannelPointsReward,
        textInput: String?,
    ): TwitchChannelPointsRedemption {
        @Suppress("UNUSED_VARIABLE")
        val ignored = listOf(auth, channelId, channelLogin, reward, textInput)
        throw UnsupportedOperationException(UNSUPPORTED_MESSAGE)
    }

    fun clearError(channelId: String) {
        @Suppress("UNUSED_VARIABLE")
        val ignored = channelId
    }

    internal fun resetSession() {
        mutableState.value = ChannelPointsUiState()
    }

    override fun close() {
        mutableState.value = ChannelPointsUiState()
    }

    private companion object {
        const val UNSUPPORTED_MESSAGE = "Channel Points viewer operations are not supported by Twitch Helix"
    }
}
