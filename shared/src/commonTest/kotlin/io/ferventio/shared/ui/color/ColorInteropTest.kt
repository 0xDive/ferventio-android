package io.ferventio.shared.ui.color

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

class ColorInteropTest {
    @Test
    fun argbConversionPreservesChannels() {
        assertEquals(
            Color(red = 0xAB, green = 0x12, blue = 0xCD, alpha = 0x7F),
            colorFromArgb(0x7FAB12CDL),
        )
    }

    @Test
    fun argbConversionUsesLow32Bits() {
        assertEquals(
            colorFromArgb(0x7FAB12CDL),
            colorFromArgb(0x123456787FAB12CDL),
        )
    }

    @Test
    fun unspecifiedTextColorUsesFallback() {
        val fallback = Color(red = 0x12, green = 0x34, blue = 0x56, alpha = 0xFF)
        assertEquals(fallback, Color.Unspecified.sanitizedForText(fallback))
    }
}
