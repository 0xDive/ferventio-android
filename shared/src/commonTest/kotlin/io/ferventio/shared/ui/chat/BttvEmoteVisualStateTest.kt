package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BttvEmoteVisualStateTest {
    @Test
    fun wideUsesBetterTtvFourTimesBaseWidth() {
        val state = BttvEmoteVisualState(setOf(BttvEmoteModifier.WIDE))

        assertEquals(4f, state.widthMultiplier)
    }

    @Test
    fun flipAndRotationFollowBetterTtvTransformCascade() {
        val horizontal = BttvEmoteVisualState(setOf(BttvEmoteModifier.FLIP_HORIZONTAL))
        val vertical = BttvEmoteVisualState(
            setOf(BttvEmoteModifier.FLIP_HORIZONTAL, BttvEmoteModifier.FLIP_VERTICAL),
        )
        val rotated = BttvEmoteVisualState(
            setOf(
                BttvEmoteModifier.WIDE,
                BttvEmoteModifier.FLIP_VERTICAL,
                BttvEmoteModifier.ROTATE_LEFT,
            ),
        )

        assertEquals(-1f, horizontal.scaleX)
        assertEquals(1f, horizontal.scaleY)
        assertEquals(1f, vertical.scaleX)
        assertEquals(-1f, vertical.scaleY)
        assertEquals(-90f, rotated.rotationDegrees)
        assertEquals(1f, rotated.widthMultiplier)
    }

    @Test
    fun zeroSpaceCursedPartyAndShakeRemainIndependent() {
        val state = BttvEmoteVisualState(
            setOf(
                BttvEmoteModifier.ZERO_SPACE,
                BttvEmoteModifier.CURSED,
                BttvEmoteModifier.PARTY,
                BttvEmoteModifier.SHAKE,
            ),
        )

        assertEquals(-4f, state.zeroSpaceOffsetDp)
        assertTrue(state.cursed)
        assertTrue(state.party)
        assertTrue(state.shake)
        assertFalse(state.rotationDegrees != 0f)
    }
}
