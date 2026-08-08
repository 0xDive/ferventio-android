package io.ferventio.app.twitch

/**
 * Legacy model types kept only while controller call sites are being removed.
 *
 * Ferventio no longer contains a Channel Points network transport or viewer redemption flow.
 */
data class TwitchChannelPointsReward(
    val id: String,
    val title: String,
    val prompt: String,
    val cost: Int,
    val enabled: Boolean,
    val userInputRequired: Boolean,
    val imageUrl: String?,
)

data class TwitchChannelPointsRedemption(val id: String)
