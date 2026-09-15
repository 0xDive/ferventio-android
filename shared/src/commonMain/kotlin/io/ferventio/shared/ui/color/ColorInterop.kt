package io.ferventio.shared.ui.color

import androidx.compose.ui.graphics.Color

/** Converts a persisted 32-bit ARGB value (0xAARRGGBB) to a Compose sRGB [Color]. */
fun colorFromArgb(argb: Long): Color {
    val packed = (argb and 0xFFFF_FFFFL).toInt()
    return Color(
        red = (packed ushr 16) and 0xFF,
        green = (packed ushr 8) and 0xFF,
        blue = packed and 0xFF,
        alpha = (packed ushr 24) and 0xFF,
    )
}

/**
 * Repairs a raw 32-bit ARGB value that was accidentally wrapped by the public Color(ULong)
 * value-class constructor. It also protects text rendering from malformed packed colors.
 */
fun Color.sanitizedForText(fallback: Color): Color {
    if (value == Color.Unspecified.value) return fallback
    if (value <= 0xFFFF_FFFFuL) return colorFromArgb(value.toLong())
    return runCatching {
        colorSpace
        this
    }.getOrElse { fallback }
}
