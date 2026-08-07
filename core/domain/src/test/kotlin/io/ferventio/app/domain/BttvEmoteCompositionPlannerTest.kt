package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BttvEmoteCompositionPlannerTest {
    @Test
    fun `prefix modifier applies to following emote and stays hidden`() {
        val fragments = listOf(
            bttv("modifier", "w!"),
            ChatFragment.Text(" "),
            bttv("base", "OMEGALUL"),
        )

        val plan = BttvEmoteCompositionPlanner.build(fragments)

        assertEquals(1, plan.groups.size)
        assertEquals(2, plan.groups.single().baseFragmentIndex)
        assertEquals(setOf(BttvModifierEffect.WIDE), plan.groups.single().effects)
        assertTrue(0 in plan.hiddenFragmentIndices)
        assertTrue(1 in plan.hiddenFragmentIndices)
    }

    @Test
    fun `multiple prefix modifiers compose onto one emote`() {
        val fragments = listOf(
            bttv("flip", "h!"),
            ChatFragment.Text(" "),
            bttv("shake", "s!"),
            ChatFragment.Text(" "),
            ChatFragment.TwitchEmote(text = "Kappa", emoteId = "25"),
        )

        val plan = BttvEmoteCompositionPlanner.build(fragments)

        assertEquals(
            setOf(BttvModifierEffect.FLIP_X, BttvModifierEffect.SHAKE),
            plan.groups.single().effects,
        )
    }

    @Test
    fun `known BTTV overlay id attaches to previous emote`() {
        val fragments = listOf(
            bttv("base", "OMEGALUL"),
            ChatFragment.Text(" "),
            bttv("5e76d399d6581c3724c0f0b8", "cvMask"),
        )

        val plan = BttvEmoteCompositionPlanner.build(fragments)

        assertEquals(1, plan.groups.size)
        assertEquals(listOf(2), plan.groups.single().overlayFragmentIndices)
        assertTrue(2 in plan.hiddenFragmentIndices)
    }

    @Test
    fun `zero width emote from another provider also overlays previous emote`() {
        val fragments = listOf(
            ChatFragment.TwitchEmote(text = "Kappa", emoteId = "25"),
            ChatFragment.ThirdPartyEmote(
                text = "overlay",
                emoteId = "overlay-id",
                provider = "7tv",
                zeroWidth = true,
            ),
        )

        val plan = BttvEmoteCompositionPlanner.build(fragments)

        assertEquals(listOf(1), plan.groups.single().overlayFragmentIndices)
    }

    @Test
    fun `ordinary BTTV emote creates normal group`() {
        val plan = BttvEmoteCompositionPlanner.build(listOf(bttv("base", "OMEGALUL")))

        assertEquals(0, plan.groups.single().baseFragmentIndex)
        assertTrue(plan.groups.single().effects.isEmpty())
        assertTrue(plan.hiddenFragmentIndices.isEmpty())
    }

    @Test
    fun `non whitespace text breaks pending modifier target`() {
        val fragments = listOf(
            bttv("modifier", "r!"),
            ChatFragment.Text(" hello "),
            bttv("base", "OMEGALUL"),
        )

        val plan = BttvEmoteCompositionPlanner.build(fragments)

        assertTrue(plan.groups.single().effects.isEmpty())
        assertTrue(0 in plan.hiddenFragmentIndices)
    }

    private fun bttv(id: String, code: String): ChatFragment.ThirdPartyEmote =
        ChatFragment.ThirdPartyEmote(
            text = code,
            emoteId = id,
            provider = "bttv",
        )
}
