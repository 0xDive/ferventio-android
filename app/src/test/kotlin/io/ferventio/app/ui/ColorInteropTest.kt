package io.ferventio.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import io.ferventio.app.domain.MentionColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorInteropTest {
    @Test
    fun persistedArgbColorsUseValidSrgbColorSpace() {
        MentionColors.presets.forEach { argb ->
            val color = colorFromArgb(argb)
            val luminance = color.luminance()
            assertTrue("luminance must be finite for 0x${argb.toString(16)}", luminance.isFinite())
            assertTrue("luminance must be within sRGB bounds", luminance in 0f..1f)
            assertEquals((argb and 0xFFFF_FFFFL).toInt(), color.toArgb())
        }
    }

    @Test
    fun rawArgbWrappedAsPackedColorIsRepairedBeforeTextRendering() {
        val malformed = Color(MentionColors.GOLD.toULong())
        val repaired = malformed.sanitizedForText(Color.White)

        assertEquals(colorFromArgb(MentionColors.GOLD).toArgb(), repaired.toArgb())
        assertTrue(repaired.luminance() in 0f..1f)
    }
}
