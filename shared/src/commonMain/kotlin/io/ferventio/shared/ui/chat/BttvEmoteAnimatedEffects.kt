package io.ferventio.shared.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class BttvAnimatedEffects(
    val shakeOffsetDp: Float = 0f,
    val colorFilter: ColorFilter? = null,
)

@Composable
internal fun rememberBttvAnimatedEffects(
    state: BttvEmoteVisualState,
): BttvAnimatedEffects {
    if (!state.party && !state.shake) {
        return BttvAnimatedEffects(
            colorFilter = bttvCursedColorFilter(state),
        )
    }

    val transition = rememberInfiniteTransition(label = "bttv-emote-modifiers")
    val hueDegrees by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = BTTV_PARTY_DURATION_MILLIS,
                easing = LinearEasing,
            ),
        ),
        label = "bttv-party-hue",
    )
    val shakeOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = BTTV_SHAKE_DURATION_MILLIS
                0f at 0
                -1f at 50
                2f at 100
                -4f at 150
                4f at 200
                -4f at 250
                4f at 300
                -4f at 350
                2f at 400
                -1f at 450
                0f at 500
            },
        ),
        label = "bttv-shake-offset",
    )

    val partyFilter = if (state.party) {
        remember(hueDegrees) {
            ColorFilter.colorMatrix(cssHueRotateColorMatrix(hueDegrees))
        }
    } else {
        bttvCursedColorFilter(state)
    }
    return BttvAnimatedEffects(
        shakeOffsetDp = if (state.shake) shakeOffset else 0f,
        colorFilter = partyFilter,
    )
}

internal fun cssHueRotateColorMatrix(degrees: Float): ColorMatrix {
    val radians = degrees.toDouble() * PI / 180.0
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return ColorMatrix(
        floatArrayOf(
            0.213f + cosine * 0.787f - sine * 0.213f,
            0.715f - cosine * 0.715f - sine * 0.715f,
            0.072f - cosine * 0.072f + sine * 0.928f,
            0f,
            0f,
            0.213f - cosine * 0.213f + sine * 0.143f,
            0.715f + cosine * 0.285f + sine * 0.140f,
            0.072f - cosine * 0.072f - sine * 0.283f,
            0f,
            0f,
            0.213f - cosine * 0.213f - sine * 0.787f,
            0.715f - cosine * 0.715f + sine * 0.715f,
            0.072f + cosine * 0.928f + sine * 0.072f,
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )
}

private const val BTTV_PARTY_DURATION_MILLIS = 3_000
private const val BTTV_SHAKE_DURATION_MILLIS = 500
