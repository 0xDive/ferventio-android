package io.ferventio.app.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyBoundedListTest {
    @Test
    fun appendKeepsOnlyNewestWindowAndPreservesRandomAccess() {
        var values: List<Int> = emptyList()

        repeat(6_000) { value ->
            values = appendLegacyBounded(values, value, limit = 5_000)
        }

        assertEquals(5_000, values.size)
        assertEquals(1_000, values.first())
        assertEquals(5_999, values.last())
        assertEquals(3_500, values[2_500])
        assertEquals((1_000 until 6_000).toList(), values.toList())
    }

    @Test
    fun changingLimitRebuildsFromNewestSourceItems() {
        var values: List<Int> = listOf(1, 2, 3)
        values = appendLegacyBounded(values, 4, limit = 3)
        values = appendLegacyBounded(values, 5, limit = 2)

        assertEquals(listOf(4, 5), values)
    }

    @Test
    fun boundedListBehavesLikeOrdinaryListForSearchAndCopyOperations() {
        var values: List<String> = emptyList()
        listOf("a", "b", "c", "d").forEach { value ->
            values = appendLegacyBounded(values, value, limit = 4)
        }

        assertTrue(values is LegacyBoundedList<*>)
        assertEquals(2, values.indexOf("c"))
        assertEquals(listOf("a", "b", "c", "d"), values.toList())
        assertEquals(listOf("b", "c"), values.subList(1, 3))
        assertEquals(listOf("a", "b", "changed", "d"), values.toMutableList().apply {
            this[2] = "changed"
        })
    }

    @Test
    fun chunkBoundaryAndSingleItemWindowStayCorrect() {
        var boundary: List<Int> = emptyList()
        repeat(130) { value ->
            boundary = appendLegacyBounded(boundary, value, limit = 65)
        }
        assertEquals((65 until 130).toList(), boundary.toList())

        var single: List<Int> = emptyList()
        repeat(130) { value ->
            single = appendLegacyBounded(single, value, limit = 1)
        }
        assertEquals(listOf(129), single)
    }

    @Test
    fun zeroLimitReturnsSharedEmptySemantics() {
        assertTrue(appendLegacyBounded(listOf(1, 2), 3, limit = 0).isEmpty())
    }
}
