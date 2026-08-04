package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLruCacheTest {
    @Test
    fun `evicts least recently used entry`() {
        val cache = BoundedLruCache<String, Int>(2)
        cache["a"] = 1
        cache["b"] = 2
        assertEquals(1, cache["a"])

        cache["c"] = 3

        assertEquals(1, cache["a"])
        assertNull(cache["b"])
        assertEquals(3, cache["c"])
    }

    @Test
    fun `retain keys removes stale channels`() {
        val cache = BoundedLruCache<String, Int>(4)
        cache.putAll(mapOf("a" to 1, "b" to 2, "c" to 3))

        cache.retainKeys(setOf("a", "c"))

        assertTrue("a" in cache.snapshot())
        assertFalse("b" in cache.snapshot())
        assertTrue("c" in cache.snapshot())
    }
}
