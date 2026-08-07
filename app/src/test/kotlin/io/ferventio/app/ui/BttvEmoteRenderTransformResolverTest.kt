package io.ferventio.app.ui

import io.ferventio.app.domain.BttvModifierEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BttvEmoteRenderTransformResolverTest {
    @Test
    fun `wide expands layout without scaling bitmap`() {
        val transform = BttvEmoteRenderTransformResolver.resolve(setOf(BttvModifierEffect.WIDE))

        assertEquals(1.5f, transform.widthMultiplier)
        assertEquals(1f, transform.scaleX)
        assertFalse(transform.hasStaticGraphicsTransform)
    }

    @Test
    fun `horizontal and vertical flips compose`() {
        val transform = BttvEmoteRenderTransformResolver.resolve(
            setOf(BttvModifierEffect.FLIP_X, BttvModifierEffect.FLIP_Y),
        )

        assertEquals(-1f, transform.scaleX)
        assertEquals(-1f, transform.scaleY)
        assertTrue(transform.hasStaticGraphicsTransform)
    }

    @Test
    fun `opposite rotations cancel`() {
        val transform = BttvEmoteRenderTransformResolver.resolve(
            setOf(BttvModifierEffect.ROTATE_LEFT, BttvModifierEffect.ROTATE_RIGHT),
        )

        assertEquals(0f, transform.rotationDegrees)
    }

    @Test
    fun `no space is a layout semantic`() {
        val transform = BttvEmoteRenderTransformResolver.resolve(setOf(BttvModifierEffect.NO_SPACE))

        assertTrue(transform.suppressTrailingSpace)
        assertFalse(transform.hasStaticGraphicsTransform)
    }

    @Test
    fun `dynamic modifier effects remain explicit render semantics`() {
        val effects = setOf(
            BttvModifierEffect.CURSED,
            BttvModifierEffect.PARTY,
            BttvModifierEffect.SHAKE,
        )

        val transform = BttvEmoteRenderTransformResolver.resolve(effects)
        val profile = BttvDynamicEffectProfileResolver.resolve(transform.dynamicEffects)

        assertEquals(effects, transform.dynamicEffects)
        assertTrue(profile.cursed)
        assertTrue(profile.party)
        assertTrue(profile.shake)
        assertTrue(profile.needsAnimation)
    }

    @Test
    fun `static modifier set does not request dynamic animation`() {
        val transform = BttvEmoteRenderTransformResolver.resolve(
            setOf(BttvModifierEffect.WIDE, BttvModifierEffect.FLIP_X),
        )

        assertFalse(BttvDynamicEffectProfileResolver.resolve(transform.dynamicEffects).needsAnimation)
    }
}
