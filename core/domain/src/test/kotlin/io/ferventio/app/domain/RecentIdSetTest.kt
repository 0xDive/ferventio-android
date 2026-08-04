package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentIdSetTest {
    @Test
    fun rejectsDuplicateWithinTtl() {
        val ids = RecentIdSet(maxSize = 10, ttlMillis = 1_000L)

        assertTrue(ids.addIfNew("event-1", 0L))
        assertFalse(ids.addIfNew("event-1", 500L))
        assertEquals(1, ids.size(500L))
    }

    @Test
    fun acceptsIdAgainAfterTtl() {
        val ids = RecentIdSet(maxSize = 10, ttlMillis = 1_000L)

        assertTrue(ids.addIfNew("event-1", 0L))
        assertTrue(ids.addIfNew("event-1", 1_001L))
        assertEquals(1, ids.size(1_001L))
    }

    @Test
    fun limitsStoredIds() {
        val ids = RecentIdSet(maxSize = 2, ttlMillis = 10_000L)

        ids.addIfNew("event-1", 1L)
        ids.addIfNew("event-2", 2L)
        ids.addIfNew("event-3", 3L)

        assertEquals(2, ids.size(3L))
        assertTrue(ids.addIfNew("event-1", 4L))
    }
}
