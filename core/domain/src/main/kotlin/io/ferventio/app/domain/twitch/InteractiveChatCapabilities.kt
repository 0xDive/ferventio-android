package io.ferventio.app.domain

import androidx.compose.runtime.Immutable

@Immutable
data class InteractiveChatCapabilities(
    val canReadPolls: Boolean = false,
    val canManagePolls: Boolean = false,
    val canReadPredictions: Boolean = false,
    val canManagePredictions: Boolean = false,
) {
    val canReadAnything: Boolean
        get() = canReadPolls || canReadPredictions
}

fun TwitchSession.interactiveChatCapabilities(channelId: String): InteractiveChatCapabilities {
    if (userId != channelId) return InteractiveChatCapabilities()

    val canManagePolls = "channel:manage:polls" in scopes
    val canManagePredictions = "channel:manage:predictions" in scopes
    return InteractiveChatCapabilities(
        canReadPolls = canManagePolls || "channel:read:polls" in scopes,
        canManagePolls = canManagePolls,
        canReadPredictions = canManagePredictions || "channel:read:predictions" in scopes,
        canManagePredictions = canManagePredictions,
    )
}
