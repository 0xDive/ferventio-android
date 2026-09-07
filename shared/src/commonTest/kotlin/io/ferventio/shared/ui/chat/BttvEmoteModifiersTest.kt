package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BttvEmoteModifiersTest {
    @Test
    fun consumesPrefixModifiersBeforeAdjacentEmote() {
        val result = applyBttvEmoteModifiers(
            listOf(
                text("hello w! h! "),
                emote("Kappa"),
                text(" world"),
            ),
        )

        assertEquals("hello ", result[0].text)
        assertEquals(
            setOf(BttvEmoteModifier.WIDE, BttvEmoteModifier.FLIP_HORIZONTAL),
            result[1].bttvModifiers,
        )
        assertEquals(" world", result[2].text)
    }

    @Test
    fun consumesLegacySuffixModifiersAfterAdjacentEmote() {
        val result = applyBttvEmoteModifiers(
            listOf(
                emote("Kappa"),
                text(" ffzX ffzCursed trailing"),
            ),
        )

        assertEquals(
            setOf(BttvEmoteModifier.FLIP_HORIZONTAL, BttvEmoteModifier.CURSED),
            result[0].bttvModifiers,
        )
        assertEquals(" trailing", result[1].text)
    }

    @Test
    fun orphanModifierTokensRemainVisibleText() {
        val original = listOf(text("hello w! h! world ffzX"))

        assertEquals(original, applyBttvEmoteModifiers(original))
    }

    @Test
    fun prefixAndSuffixModifiersCombineOnSameEmote() {
        val result = applyBttvEmoteModifiers(
            listOf(
                text("p! s! "),
                emote("Kappa"),
                text(" ffzW"),
            ),
        )

        assertEquals(
            setOf(
                BttvEmoteModifier.PARTY,
                BttvEmoteModifier.SHAKE,
                BttvEmoteModifier.WIDE,
            ),
            result.single().bttvModifiers,
        )
    }

    @Test
    fun transformMatchesBetterTtvCssCascade() {
        val rotated = BttvEmoteTransform(
            setOf(
                BttvEmoteModifier.WIDE,
                BttvEmoteModifier.FLIP_HORIZONTAL,
                BttvEmoteModifier.ROTATE_LEFT,
                BttvEmoteModifier.ROTATE_RIGHT,
                BttvEmoteModifier.CURSED,
                BttvEmoteModifier.PARTY,
                BttvEmoteModifier.SHAKE,
                BttvEmoteModifier.ZERO_SPACE,
            ),
        )

        assertEquals(90f, rotated.rotationDegrees)
        assertFalse(rotated.wide)
        assertFalse(rotated.flipHorizontal)
        assertFalse(rotated.flipVertical)
        assertTrue(rotated.zeroSpace)
        assertTrue(rotated.cursed)
        assertTrue(rotated.party)
        assertTrue(rotated.shake)
    }

    @Test
    fun thirdPartyAndCheermoteSegmentsCanReceiveModifiers() {
        val thirdParty = ChatMessageSegment(
            text = "OMEGALUL",
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = "https://cdn.test/emote.webp",
        )
        val cheer = ChatMessageSegment(
            text = "Cheer100",
            kind = ChatMessageSegmentKind.CHEERMOTE,
            imageUrl = "https://cdn.test/cheer.gif",
        )

        val result = applyBttvEmoteModifiers(
            listOf(text("v! "), thirdParty, text(" r! "), cheer),
        )

        assertEquals(setOf(BttvEmoteModifier.FLIP_VERTICAL), result[0].bttvModifiers)
        assertEquals(setOf(BttvEmoteModifier.ROTATE_RIGHT), result[1].bttvModifiers)
    }

    private fun text(value: String) = ChatMessageSegment(
        text = value,
        kind = ChatMessageSegmentKind.TEXT,
    )

    private fun emote(code: String) = ChatMessageSegment(
        text = code,
        kind = ChatMessageSegmentKind.TWITCH_EMOTE,
        imageUrl = "https://cdn.test/$code.png",
    )
}
