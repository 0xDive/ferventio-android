package io.ferventio.app.ui

import io.ferventio.app.security.SafeLog
import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import io.ferventio.app.BuildConfig
import io.ferventio.app.performance.PerformanceRuntimeState
import java.util.ArrayDeque

internal fun Modifier.measureMessageRender(messageId: String): Modifier {
    if (!BuildConfig.DEBUG && !PerformanceRuntimeState.isEnabled) return this
    return layout { measurable, constraints ->
        val started = SystemClock.elapsedRealtimeNanos()
        val placeable = measurable.measure(constraints)
        val elapsed = SystemClock.elapsedRealtimeNanos() - started
        MessageRenderMetrics.record(messageId, elapsed)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative(0, 0)
        }
    }
}

internal object MessageRenderMetrics {
    private const val MAX_SAMPLES = 512
    private const val REPORT_EVERY = 250
    private const val SLOW_MEASURE_NANOS = 8_000_000L
    private const val TAG = "FerventioRender"

    private val samples = ArrayDeque<Long>(MAX_SAMPLES)
    private var totalSamples = 0L

    @Synchronized
    fun record(messageId: String, durationNanos: Long) {
        if (samples.size == MAX_SAMPLES) samples.removeFirst()
        samples.addLast(durationNanos)
        totalSamples += 1
        if (durationNanos >= SLOW_MEASURE_NANOS) {
            SafeLog.w(TAG, "Slow message measure id=$messageId durationMs=${durationNanos / 1_000_000.0}")
        }
        if (totalSamples % REPORT_EVERY == 0L) {
            val snapshot = snapshotLocked()
            SafeLog.i(
                TAG,
                "message measure samples=${snapshot.samples} p50Ms=${snapshot.p50Nanos / 1_000_000.0} " +
                    "p95Ms=${snapshot.p95Nanos / 1_000_000.0} maxMs=${snapshot.maxNanos / 1_000_000.0}",
            )
        }
    }

    @Synchronized
    fun snapshot(): MessageRenderSnapshot = snapshotLocked()

    private fun snapshotLocked(): MessageRenderSnapshot {
        if (samples.isEmpty()) return MessageRenderSnapshot()
        val sorted = samples.sorted()
        fun percentile(percent: Double): Long {
            val index = ((sorted.lastIndex) * percent).toInt().coerceIn(0, sorted.lastIndex)
            return sorted[index]
        }
        return MessageRenderSnapshot(
            samples = sorted.size,
            p50Nanos = percentile(0.50),
            p95Nanos = percentile(0.95),
            maxNanos = sorted.last(),
        )
    }
}

internal data class MessageRenderSnapshot(
    val samples: Int = 0,
    val p50Nanos: Long = 0L,
    val p95Nanos: Long = 0L,
    val maxNanos: Long = 0L,
)
