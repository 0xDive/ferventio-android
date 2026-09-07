package io.ferventio.app.ui

import androidx.compose.ui.graphics.Color
import io.ferventio.shared.ui.color.colorFromArgb as sharedColorFromArgb
import io.ferventio.shared.ui.color.sanitizedForText as sharedSanitizedForText

internal fun colorFromArgb(argb: Long): Color = sharedColorFromArgb(argb)

internal fun Color.sanitizedForText(fallback: Color): Color =
    this.sharedSanitizedForText(fallback)
