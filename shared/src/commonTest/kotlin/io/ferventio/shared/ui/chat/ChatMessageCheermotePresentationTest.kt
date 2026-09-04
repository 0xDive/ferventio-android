package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CheermoteAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatMessageCheermotePresentationTest {
    @Test
    fun resolvesHighestEligibleTierAndPlatformAnimation() {
        val message = message(bits = 150)
        val assets = mapOf(
            "cheer" to listOf(
                asset(1),
                asset(100),
                asset(500),
            ),
        )

        val staticPresentation = projectChatMessage(
            message = message,
            deletedPlaceholder = "[deleted]",
            cheermoteAssetsByPrefix = assets,
            animatedMediaSupported = false,
        )
        val animatedPresentation = projectChatMessage(
            message = message,
            deletedPlaceholder = "[deleted]",
            cheermoteAssetsByPrefix = assets,
            animatedMediaSupported = true,
        )

        assertEquals(ChatMessageSegmentKind.CHEERMOTE, staticPresentation.segments.single().kind)
        assertEquals("https://cdn.test/100-static.png", staticPresentation.segments.single().imageUrl)
        assertEquals("https://cdn.test/100-animated.gif", animatedPresentation.segments.single().imageUrl)
    }

    @Test
    fun keepsTextFallbackWhenCatalogHasNoMatchingCheermote() {
        val presentation = projectChatMessage(
            message = message(bits = 100),
            deletedPlaceholder = "[deleted]",
            cheermoteAssetsByPrefix = emptyMap(),
            animatedMediaSupported = true,
        )

        assertEquals("Cheer100", presentation.segments.single().text)
        assertNull(presentation.segments.single().imageUrl)
    }

    private fun message(bits: Int) = ChatMessage(
        id = "message",
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(id = "user", login = "user", displayName = "User"),
        text = "Cheer$bits",
        fragments = listOf(
            ChatFragment.Cheermote(
                text = "Cheer$bits",
                prefix = "Cheer",
                bits = bits,
                tier = bits,
            ),
        ),
        timestamp = "2026-08-17T00:00:00Z",
        timestampMillis = 0L,
    )

    private fun asset(minBits: Int) = CheermoteAsset(
        prefix = "Cheer",
        minBits = minBits,
        tier = minBits,
        color = "#ffffff",
        animatedImageUrl = "https://cdn.test/$minBits-animated.gif",
        staticImageUrl = "https://cdn.test/$minBits-static.png",
    )
}
