package io.ferventio.shared.settings

import kotlin.math.abs

internal object AppearanceSettingsPresets {
    val FONT_SCALE_PERCENT = listOf(85, 100, 115, 130)
    val EMOTE_SCALE_PERCENT = listOf(90, 100, 125, 150)

    fun snapFontScalePercent(value: Int): Int = snap(value, FONT_SCALE_PERCENT)

    fun snapEmoteScalePercent(value: Int): Int = snap(value, EMOTE_SCALE_PERCENT)

    private fun snap(value: Int, presets: List<Int>): Int =
        presets.minWithOrNull(
            compareBy<Int> { preset -> abs(preset - value) }
                .thenByDescending { preset -> preset },
        ) ?: value
}
