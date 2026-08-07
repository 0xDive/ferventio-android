package io.ferventio.app.domain

enum class BttvModifierEffect {
    WIDE,
    FLIP_X,
    FLIP_Y,
    ROTATE_LEFT,
    ROTATE_RIGHT,
    CURSED,
    PARTY,
    SHAKE,
    NO_SPACE,
}

data class BttvModifier(
    val code: String,
    val effects: Set<BttvModifierEffect>,
)

data class BttvEmoteGroup(
    val baseFragmentIndex: Int,
    val overlayFragmentIndices: List<Int> = emptyList(),
    val effects: Set<BttvModifierEffect> = emptySet(),
)

data class BttvEmoteCompositionPlan(
    val groups: List<BttvEmoteGroup>,
    val hiddenFragmentIndices: Set<Int>,
) {
    fun groupForBase(fragmentIndex: Int): BttvEmoteGroup? =
        groups.firstOrNull { it.baseFragmentIndex == fragmentIndex }

    companion object {
        val Empty = BttvEmoteCompositionPlan(emptyList(), emptySet())
    }
}

object BttvEmoteSemantics {
    private val modifiers = mapOf(
        "w!" to BttvModifier("w!", setOf(BttvModifierEffect.WIDE)),
        "h!" to BttvModifier("h!", setOf(BttvModifierEffect.FLIP_X)),
        "v!" to BttvModifier("v!", setOf(BttvModifierEffect.FLIP_Y)),
        "l!" to BttvModifier("l!", setOf(BttvModifierEffect.ROTATE_LEFT)),
        "r!" to BttvModifier("r!", setOf(BttvModifierEffect.ROTATE_RIGHT)),
        "c!" to BttvModifier("c!", setOf(BttvModifierEffect.CURSED)),
        "p!" to BttvModifier("p!", setOf(BttvModifierEffect.PARTY)),
        "s!" to BttvModifier("s!", setOf(BttvModifierEffect.SHAKE)),
        "z!" to BttvModifier("z!", setOf(BttvModifierEffect.NO_SPACE)),
    )

    private val overlayIds = setOf(
        "5e76d338d6581c3724c0f0b2",
        "5e76d399d6581c3724c0f0b8",
        "5849c9a4f52be01a7ee5f79d",
        "567b5b520e984428652809b6",
        "58487cc6f52be01a7ee5f205",
        "5849c9c8f52be01a7ee5f79e",
        "567b5c080e984428652809ba",
        "567b5dc00e984428652809bd",
    )

    fun modifier(fragment: ChatFragment): BttvModifier? {
        val emote = fragment as? ChatFragment.ThirdPartyEmote ?: return null
        if (!emote.provider.equals("bttv", ignoreCase = true)) return null
        return modifiers[emote.text]
    }

    fun isOverlay(fragment: ChatFragment): Boolean {
        val emote = fragment as? ChatFragment.ThirdPartyEmote ?: return false
        if (!emote.provider.equals("bttv", ignoreCase = true)) return emote.zeroWidth
        return emote.zeroWidth || emote.emoteId in overlayIds
    }
}

/**
 * Produces a renderer-friendly composition plan without mutating canonical
 * message fragments. Prefix modifiers are hidden and applied to the next emote;
 * zero-width overlays attach to the previous visible emote group.
 */
object BttvEmoteCompositionPlanner {
    fun build(fragments: List<ChatFragment>): BttvEmoteCompositionPlan {
        if (fragments.isEmpty()) return BttvEmoteCompositionPlan.Empty

        val groups = mutableListOf<BttvEmoteGroup>()
        val hidden = linkedSetOf<Int>()
        val pendingEffects = linkedSetOf<BttvModifierEffect>()
        val pendingWhitespace = mutableListOf<Int>()

        fragments.forEachIndexed { index, fragment ->
            val modifier = BttvEmoteSemantics.modifier(fragment)
            if (modifier != null) {
                hidden += index
                pendingEffects += modifier.effects
                return@forEachIndexed
            }

            if (fragment is ChatFragment.Text && fragment.text.isBlank() && pendingEffects.isNotEmpty()) {
                pendingWhitespace += index
                return@forEachIndexed
            }

            if (BttvEmoteSemantics.isOverlay(fragment)) {
                val previousIndex = groups.lastIndex
                if (previousIndex >= 0) {
                    val previous = groups[previousIndex]
                    groups[previousIndex] = previous.copy(
                        overlayFragmentIndices = previous.overlayFragmentIndices + index,
                    )
                    hidden += index
                    return@forEachIndexed
                }
            }

            if (fragment.isRenderableEmote()) {
                if (pendingEffects.isNotEmpty()) {
                    hidden += pendingWhitespace
                }
                groups += BttvEmoteGroup(
                    baseFragmentIndex = index,
                    effects = pendingEffects.toSet(),
                )
                pendingEffects.clear()
                pendingWhitespace.clear()
                return@forEachIndexed
            }

            // Non-whitespace text breaks an unfinished modifier prefix. The
            // modifier stays hidden but spacing remains untouched.
            pendingEffects.clear()
            pendingWhitespace.clear()
        }

        return BttvEmoteCompositionPlan(
            groups = groups,
            hiddenFragmentIndices = hidden,
        )
    }

    private fun ChatFragment.isRenderableEmote(): Boolean = when (this) {
        is ChatFragment.TwitchEmote,
        is ChatFragment.ThirdPartyEmote,
        is ChatFragment.Gif,
        is ChatFragment.Cheermote -> true
        else -> false
    }
}
