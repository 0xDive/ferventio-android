package io.ferventio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageRenderMetricsTest {
    @Test
    fun `snapshot reports percentiles`() {
        repeat(100) { index -> MessageRenderMetrics.record("m$index", (index + 1L) * 1_000L) }
        val snapshot = MessageRenderMetrics.snapshot()
        assertEquals(100, snapshot.samples)
        assertEquals(50_000L, snapshot.p50Nanos)
        assertEquals(95_000L, snapshot.p95Nanos)
        assertEquals(100_000L, snapshot.maxNanos)
    }
}
