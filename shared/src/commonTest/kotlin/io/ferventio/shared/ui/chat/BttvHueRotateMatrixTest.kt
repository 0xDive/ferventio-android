package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class BttvHueRotateMatrixTest {
    @Test
    fun zeroDegreesIsIdentityWithinCssCoefficientPrecision() {
        val matrix = cssHueRotateColorMatrix(0f)
        val values = matrix.values

        assertEquals(1f, values[0], absoluteTolerance = 0.001f)
        assertEquals(0f, values[1], absoluteTolerance = 0.001f)
        assertEquals(0f, values[2], absoluteTolerance = 0.001f)
        assertEquals(0f, values[5], absoluteTolerance = 0.001f)
        assertEquals(1f, values[6], absoluteTolerance = 0.001f)
        assertEquals(0f, values[7], absoluteTolerance = 0.001f)
        assertEquals(0f, values[10], absoluteTolerance = 0.001f)
        assertEquals(0f, values[11], absoluteTolerance = 0.001f)
        assertEquals(1f, values[12], absoluteTolerance = 0.001f)
        assertEquals(1f, values[18], absoluteTolerance = 0.001f)
    }
}
