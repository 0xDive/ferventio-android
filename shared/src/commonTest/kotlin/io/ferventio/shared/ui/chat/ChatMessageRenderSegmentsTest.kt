package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMessageRenderSegmentsTest {
    @Test
    fun zeroWidthOverlayAttachesToPreviousTwitchEmote() {
        val base = segment(
            text = "Kappa",
            kind = ChatMessageSegmentKind.TWITCH_EMOTE,
            imageUrl = "https://cdn.test/kappa.png",
        )
        val overlay = segment(
            text = "hat",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/hat.png",
            zeroWidth = true,
        )

        val grouped = groupChatMessageSegments(listOf(base, overlay))

        assertEquals(1, grouped.size)
        assertEquals(base, grouped.single().base)
        assertEquals(listOf(overlay), grouped.single().overlays)
    }

    @Test
    fun multipleOverlaysAttachToSameBase() {
        val base = segment(
            text = "base",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/base.png",
        )
        val first = segment(
            text = "first",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/first.png",
            zeroWidth = true,
        )
        val second = segment(
            text = "second",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/second.png",
            zeroWidth = true,
        )

        val grouped = groupChatMessageSegments(listOf(base, first, second))

        assertEquals(1, grouped.size)
        assertEquals(listOf(first, second), grouped.single().overlays)
    }

    @Test
    fun textBreaksOverlayAdjacency() {
        val base = segment(
            text = "Kappa",
            kind = ChatMessageSegmentKind.TWITCH_EMOTE,
            imageUrl = "https://cdn.test/kappa.png",
        )
        val space = segment(" ", ChatMessageSegmentKind.TEXT)
        val overlay = segment(
            text = "hat",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/hat.png",
            zeroWidth = true,
        )

        val grouped = groupChatMessageSegments(listOf(base, space, overlay))

        assertEquals(3, grouped.size)
        assertTrue(grouped.all { it.overlays.isEmpty() })
        assertEquals(overlay, grouped.last().base)
    }

    @Test
    fun orphanOverlayRemainsStandaloneAndVisible() {
        val overlay = segment(
            text = "hat",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/hat.png",
            zeroWidth = true,
        )

        val grouped = groupChatMessageSegments(listOf(overlay))

        assertEquals(1, grouped.size)
        assertEquals(overlay, grouped.single().base)
        assertTrue(grouped.single().overlays.isEmpty())
    }

    @Test
    fun invalidOverlayImageDoesNotDisappearIntoPreviousBase() {
        val base = segment(
            text = "Kappa",
            kind = ChatMessageSegmentKind.TWITCH_EMOTE,
            imageUrl = "https://cdn.test/kappa.png",
        )
        val overlay = segment(
            text = "broken",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = null,
            zeroWidth = true,
        )

        val grouped = groupChatMessageSegments(listOf(base, overlay))

        assertEquals(2, grouped.size)
        assertFalse(grouped.first().overlays.isNotEmpty())
        assertEquals(overlay, grouped.last().base)
    }

    private fun segment(
        text: String,
        kind: ChatMessageSegmentKind,
        imageUrl: String? = null,
        zeroWidth: Boolean = false,
    ) = ChatMessageSegment(
        text = text,
        kind = kind,
        imageUrl = imageUrl,
        zeroWidth = zeroWidth,
    )
}
