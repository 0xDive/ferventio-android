package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterCodecTest {
    @Test
    fun `round trip preserves filters`() {
        val filters = listOf(
            SavedMessageFilter(id = "one", name = "Mods", expression = "author.badges contains \"moderator\""),
            SavedMessageFilter(id = "two", name = "Long", expression = "message.length > 80"),
        )

        val decoded = MessageFilterCodec.decode(MessageFilterCodec.encode(filters)).getOrThrow()

        assertEquals(filters, decoded)
    }

    @Test
    fun `merge keeps existing and renames collisions`() {
        val existing = listOf(SavedMessageFilter(id = "same", name = "Filter", expression = "message.length > 1"))
        val imported = listOf(SavedMessageFilter(id = "same", name = "Filter", expression = "message.length > 2"))

        val merged = MessageFilterCodec.merge(existing, imported)

        assertEquals(2, merged.size)
        assertEquals(2, merged.map(SavedMessageFilter::id).distinct().size)
        assertEquals(2, merged.map { it.name.lowercase() }.distinct().size)
        assertTrue(merged.any { it.name == "Filter (2)" })
    }
}
