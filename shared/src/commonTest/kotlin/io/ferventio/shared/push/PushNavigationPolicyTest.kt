package io.ferventio.shared.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushNavigationPolicyTest {
    @Test
    fun resolvesPushSettingsWithoutChannel() {
        assertEquals(
            PushNavigationTarget.PushSettings,
            PushNavigationPolicy.resolve(
                PushNavigationInput(destination = " push_settings "),
            ),
        )
    }

    @Test
    fun normalizesChannelLoginForIosRoutes() {
        assertEquals(
            PushNavigationTarget.Channel(
                PushChannelReference(login = "channel"),
            ),
            PushNavigationPolicy.resolve(
                PushNavigationInput(channelLogin = " Channel "),
            ),
        )
    }

    @Test
    fun preservesAndroidChannelIdAndMessageTarget() {
        assertEquals(
            PushNavigationTarget.Message(
                channel = PushChannelReference(id = "123", login = "channel"),
                messageId = "message",
            ),
            PushNavigationPolicy.resolve(
                PushNavigationInput(
                    channelId = " 123 ",
                    channelLogin = " CHANNEL ",
                    messageId = " message ",
                ),
            ),
        )
    }

    @Test
    fun resolvesTypedDestinationsBeforeMessageFallback() {
        val channel = PushChannelReference(id = "123")

        assertEquals(
            PushNavigationTarget.Mentions(channel),
            PushNavigationPolicy.resolve(
                PushNavigationInput(
                    channelId = "123",
                    messageId = "message",
                    destination = "mentions",
                ),
            ),
        )
        assertEquals(
            PushNavigationTarget.Moderation(channel),
            PushNavigationPolicy.resolve(
                PushNavigationInput(
                    channelId = "123",
                    messageId = "message",
                    destination = "moderation",
                ),
            ),
        )
    }

    @Test
    fun ignoresRoutesWithoutChannelOutsideSettings() {
        assertNull(PushNavigationPolicy.resolve(PushNavigationInput()))
        assertNull(
            PushNavigationPolicy.resolve(
                PushNavigationInput(messageId = "message", destination = "mentions"),
            ),
        )
    }
}
