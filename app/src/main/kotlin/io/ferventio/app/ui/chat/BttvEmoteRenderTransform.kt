package io.ferventio.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.BttvModifierEffect

internal data class BttvDynamicEffectProfile(
    val cursed: Boolean = false,
    val party: Boolean = false,
    val shake: Boolean = false,
) {
    val needsAnimation: Boolean
        get() = cursed || party || shake
}

internal object BttvDynamicEffectProfileResolver {
    fun resolve(effects: Set<BttvModifierEffect>) = BttvDynamicEffectProfile(
        cursed = BttvModifierEffect.CURSED in effects,
        party = BttvModifierEffect.PARTY in effects,
        shake = BttvModifierEffect.SHAKE in effects,
    )
}

internal data class BttvEmoteRenderTransform(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val widthMultiplier: Float = 1f,
    val suppressTrailingSpace: Boolean = false,
    val dynamicEffects: Set<BttvModifierEffect> = emptySet(),
) {
    val hasStaticGraphicsTransform: Boolean
        get() = scaleX != 1f || scaleY != 1f || rotationDegrees != 0f
}

internal object BttvEmoteRenderTransformResolver {
    fun resolve(effects: Set<BttvModifierEffect>): BttvEmoteRenderTransform {
        var scaleX = 1f
        var scaleY = 1f
        var rotation = 0f
        var widthMultiplier = 1f
        var suppressTrailingSpace = false
        val dynamic = linkedSetOf<BttvModifierEffect>()

        effects.forEach { effect ->
            when (effect) {
                BttvModifierEffect.WIDE -> widthMultiplier = maxOf(widthMultiplier, 1.5f)
                BttvModifierEffect.FLIP_X -> scaleX *= -1f
                BttvModifierEffect.FLIP_Y -> scaleY *= -1f
                BttvModifierEffect.ROTATE_LEFT -> rotation -= 90f
                BttvModifierEffect.ROTATE_RIGHT -> rotation += 90f
                BttvModifierEffect.NO_SPACE -> suppressTrailingSpace = true
                BttvModifierEffect.CURSED,
                BttvModifierEffect.PARTY,
                BttvModifierEffect.SHAKE -> dynamic += effect
            }
        }

        return BttvEmoteRenderTransform(
            scaleX = scaleX,
            scaleY = scaleY,
            rotationDegrees = normalizeRotation(rotation),
            widthMultiplier = widthMultiplier,
            suppressTrailingSpace = suppressTrailingSpace,
            dynamicEffects = dynamic,
        )
    }

    private fun normalizeRotation(value: Float): Float {
        var normalized = value % 360f
        if (normalized > 180f) normalized -= 360f
        if (normalized < -180f) normalized += 360f
        return normalized
    }
}

/**
 * Applies bitmap-space BTTV transforms. Dynamic effects share the chat's existing
 * `animateEmotes` visibility/scroll gate, so off-screen rows never keep infinite
 * transitions alive. `widthMultiplier` remains a placeholder-layout concern.
 */
@Composable
internal fun Modifier.bttvEmoteTransform(
    transform: BttvEmoteRenderTransform,
    animateDynamicEffects: Boolean,
): Modifier {
    val profile = BttvDynamicEffectProfileResolver.resolve(transform.dynamicEffects)
    if (!animateDynamicEffects || !profile.needsAnimation) {
        return if (!transform.hasStaticGraphicsTransform) this else graphicsLayer {
            scaleX = transform.scaleX
            scaleY = transform.scaleY
            rotationZ = transform.rotationDegrees
        }
    }

    val transition = rememberInfiniteTransition(label = "bttv-modifier")
    val partyPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 420),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bttv-party-pulse",
    )
    val partyRotation by transition.animateFloat(
        initialValue = -7f,
        targetValue = 7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bttv-party-rotation",
    )
    val shakePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 240
                0f at 0
                -1f at 40
                1f at 80
                -0.8f at 120
                0.8f at 160
                -0.4f at 200
                0f at 240
            },
        ),
        label = "bttv-shake",
    )
    val cursedPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 310
                0f at 0
                1f at 55
                -0.7f at 110
                0.55f at 165
                -1f at 220
                0.35f at 270
                0f at 310
            },
        ),
        label = "bttv-cursed",
    )
    val density = LocalDensity.current
    val shakeDistancePx = with(density) { 1.6.dp.toPx() }
    val cursedDistancePx = with(density) { 1.1.dp.toPx() }

    val dynamicScale =
        (if (profile.party) partyPulse else 1f) *
            (if (profile.cursed) 1f + cursedPhase * 0.08f else 1f)
    val dynamicRotation =
        (if (profile.party) partyRotation else 0f) +
            (if (profile.cursed) cursedPhase * 11f else 0f)
    val translationX =
        (if (profile.shake) shakePhase * shakeDistancePx else 0f) +
            (if (profile.cursed) cursedPhase * cursedDistancePx else 0f)
    val translationY =
        (if (profile.shake) -shakePhase * shakeDistancePx * 0.65f else 0f) +
            (if (profile.cursed) -cursedPhase * cursedDistancePx * 0.45f else 0f)

    return graphicsLayer {
        scaleX = transform.scaleX * dynamicScale
        scaleY = transform.scaleY * dynamicScale
        rotationZ = transform.rotationDegrees + dynamicRotation
        this.translationX = translationX
        this.translationY = translationY
    }
}
