package io.ferventio.app.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyListTransformsTest {
    private data class Item(
        val id: String,
        val value: Int,
    )

    @Test
    fun noOpTransformDoesNotAllocateList() {
        val source = listOf(Item("one", 1), Item("two", 2))

        assertNull(
            mapLegacyListIfChanged(source) { it },
        )
    }

    @Test
    fun partialTransformCopiesLazilyAndKeepsUnchangedReferences() {
        val first = Item("one", 1)
        val second = Item("two", 2)
        val source = listOf(first, second)

        val updated = requireNotNull(
            mapLegacyListIfChanged(source) { item ->
                if (item.id == "two") item.copy(value = 3) else item
            },
        )

        assertTrue(updated[0] === first)
        assertEquals(3, updated[1].value)
    }

    @Test
    fun identicalTailUpsertReusesListButMiddleItemKeepsMoveToEndSemantics() {
        val one = Item("one", 1)
        val two = Item("two", 2)
        val source = listOf(one, two)

        val sameTail = upsertLegacyListAtEnd(source, two, maxSize = 5, key = Item::id)
        val moved = upsertLegacyListAtEnd(source, one, maxSize = 5, key = Item::id)

        assertTrue(sameTail === source)
        assertEquals(listOf("two", "one"), moved.map(Item::id))
    }

    @Test
    fun fullListRejectsNewItemWithoutAllocatingAndMissingDeleteIsNoOp() {
        val source = listOf(Item("one", 1), Item("two", 2))

        val overflow = upsertLegacyListAtEnd(
            source = source,
            value = Item("three", 3),
            maxSize = 2,
            key = Item::id,
        )
        val deleted = removeLegacyListByKey(source, "missing", Item::id)

        assertTrue(overflow === source)
        assertTrue(deleted === source)
    }
}
