package io.ferventio.app.push

internal data class HighlightDeliveryDecision(
    val showNotification: Boolean,
    val playStandaloneSound: Boolean,
    val notificationSilent: Boolean,
)

internal fun resolveHighlightDelivery(
    deliveryEnabled: Boolean,
    pushRequested: Boolean,
    playSoundRequested: Boolean,
    soundEnabled: Boolean = true,
): HighlightDeliveryDecision {
    if (!deliveryEnabled) {
        return HighlightDeliveryDecision(
            showNotification = false,
            playStandaloneSound = false,
            notificationSilent = true,
        )
    }
    if (pushRequested) {
        return HighlightDeliveryDecision(
            showNotification = true,
            playStandaloneSound = false,
            notificationSilent = !(playSoundRequested && soundEnabled),
        )
    }
    return HighlightDeliveryDecision(
        showNotification = false,
        playStandaloneSound = playSoundRequested && soundEnabled,
        notificationSilent = true,
    )
}
