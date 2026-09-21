package io.ferventio.app.push

import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightDeliveryPolicyTest {
    @Test
    fun mutedOrDisabledDeliveryNeverFallsBackToStandaloneSound() {
        assertEquals(
            HighlightDeliveryDecision(
                showNotification = false,
                playStandaloneSound = false,
                notificationSilent = true,
            ),
            resolveHighlightDelivery(
                deliveryEnabled = false,
                pushRequested = true,
                playSoundRequested = true,
            ),
        )
    }

    @Test
    fun soundOnlyHighlightStillWorksWhenDeliveryPolicyAllowsIt() {
        assertEquals(
            HighlightDeliveryDecision(
                showNotification = false,
                playStandaloneSound = true,
                notificationSilent = true,
            ),
            resolveHighlightDelivery(
                deliveryEnabled = true,
                pushRequested = false,
                playSoundRequested = true,
            ),
        )
    }

    @Test
    fun pushedHighlightCanBeVisibleWithoutSound() {
        assertEquals(
            HighlightDeliveryDecision(
                showNotification = true,
                playStandaloneSound = false,
                notificationSilent = true,
            ),
            resolveHighlightDelivery(
                deliveryEnabled = true,
                pushRequested = true,
                playSoundRequested = true,
                soundEnabled = false,
            ),
        )
    }
}
