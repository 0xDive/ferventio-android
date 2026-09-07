package io.ferventio.shared.ui.chat

internal data class BttvEmoteVisualState(
    val widthMultiplier: Float = 1f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val zeroSpaceOffsetDp: Float = 0f,
    val cursed: Boolean = false,
    val party: Boolean = false,
    val shake: Boolean = false,
)

internal fun BttvEmoteVisualState(
    modifiers: Set<BttvEmoteModifier>,
): BttvEmoteVisualState {
    val transform = BttvEmoteTransform(modifiers)
    return BttvEmoteVisualState(
        widthMultiplier = if (transform.wide) 4f else 1f,
        scaleX = if (transform.flipHorizontal) -1f else 1f,
        scaleY = if (transform.flipVertical) -1f else 1f,
        rotationDegrees = transform.rotationDegrees,
        zeroSpaceOffsetDp = if (transform.zeroSpace) -4f else 0f,
        cursed = transform.cursed,
        party = transform.party,
        shake = transform.shake,
    )
}
