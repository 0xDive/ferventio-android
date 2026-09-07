package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatMessageRenderSegmentsTest {
    @Test
    fun zeroWidthOverlayAttachesToPreviousTwitchEmote() {
        val base = emote("Kappa", ChatMessageSegmentKind.TWITCH_EMOTE)
        val overlay = emote("hat", ChatMessageSegmentKind.THIRD_PARTY_EMOTE, zeroWidth = true)

        val grouped = groupChatMessageSegments(listOf(base, overlay))

        assertEquals(1, grouped.size)
        assertEquals(listOf(overlay), grouped.single().overlays)
    }

    @Test
    fun whitespaceSeparatorIsConsumedWhenOverlayAttaches() {
        val base = emote("base", ChatMessageSegmentKind.THIRD_PARTY_EMOTE)
        val separator = ChatMessageSegment("  ", ChatMessageSegmentKind.TEXT)
        val overlay = emote("overlay", ChatMessageSegmentKind.THIRD_PARTY_EMOTE, zeroWidth = true)

        val grouped = groupChatMessageSegments(listOf(base, separator, overlay))

        assertEquals(1, grouped.size)
        assertEquals(base, grouped.single().base)
        assertEquals(listOf(overlay), grouped.single().overlays)
    }

    @Test
    fun nonWhitespaceTextStillBreaksOverlayAdjacency() {
        val base = emote("base", ChatMessageSegmentKind.TWITCH_EMOTE)
        val text = ChatMessageSegment(" text ", ChatMessageSegmentKind.TEXT)
        val overlay = emote("overlay", ChatMessageSegmentKind.THIRD_PARTY_EMOTE, zeroWidth = true)

        val grouped = groupChatMessageSegments(listOf(base, text, overlay))

        assertEquals(3, grouped.size)
        assertTrue(grouped.all { it.overlays.isEmpty() })
    }

    @Test
    fun orphanOverlayRemainsStandaloneAndVisible() {
        val overlay = emote("overlay", ChatMessageSegmentKind.THIRD_PARTY_EMOTE, zeroWidth = true)

        val grouped = groupChatMessageSegments(listOf(overlay))

        assertEquals(1, grouped.size)
        assertEquals(overlay, grouped.single().base)
        assertTrue(grouped.single().overlays.isEmpty())
    }

    private fun emote(
        text: String,
        kind: ChatMessageSegmentKind,
        zeroWidth: Boolean = false,
    ) = ChatMessageSegment(
        text = text,
        kind = kind,
        imageUrl = "https://cdn.test/$text.png",
        zeroWidth = zeroWidth,
    )
}
