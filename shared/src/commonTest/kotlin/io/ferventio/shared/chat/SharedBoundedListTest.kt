package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedBoundedListTest {
    @Test
    fun retainsNewestItemsAcrossManyChunkRotations() {
        var values: List<Int> = emptyList()
        repeat(6_000) { value ->
            values = appendSharedBounded(values, value, limit = 5_000)
        }

        assertTrue(values is SharedBoundedList<*>)
        assertEquals((1_000 until 6_000).toList(), values.toList())
    }

    @Test
    fun supportsSmallLimitsAndLimitChanges() {
        var values: List<Int> = emptyList()
        repeat(130) { value ->
            values = appendSharedBounded(values, value, limit = 65)
        }
        assertEquals((65 until 130).toList(), values.toList())

        values = appendSharedBounded(values, 130, limit = 1)
        assertEquals(listOf(130), values)
    }
}
