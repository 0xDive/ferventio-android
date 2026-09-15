package io.ferventio.shared.ui.chat

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset

internal fun Modifier.applyBttvStaticEffects(
    state: BttvEmoteVisualState,
): Modifier = this
    .offset(x = state.zeroSpaceOffsetDp.dp)
    .graphicsLayer {
        scaleX = state.scaleX
        scaleY = state.scaleY
        rotationZ = state.rotationDegrees
    }

internal fun bttvCursedColorFilter(
    state: BttvEmoteVisualState,
): ColorFilter? = if (state.cursed) {
    ColorFilter.colorMatrix(BTTV_CURSED_COLOR_MATRIX)
} else {
    null
}

/**
 * CSS-equivalent composition of grayscale(1) -> brightness(0.7) -> contrast(2.5).
 * ColorMatrix RGB translations are expressed in the 0..255 channel space.
 */
private val BTTV_CURSED_COLOR_MATRIX = ColorMatrix(
    floatArrayOf(
        0.37205f, 1.25160f, 0.12635f, 0f, -191.25f,
        0.37205f, 1.25160f, 0.12635f, 0f, -191.25f,
        0.37205f, 1.25160f, 0.12635f, 0f, -191.25f,
        0f, 0f, 0f, 1f, 0f,
    ),
)
