package io.ferventio.benchmark

import android.os.Build
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingGfxInfoMetric
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.Metric
import androidx.test.platform.app.InstrumentationRegistry

private const val DRY_RUN_ARGUMENT = "androidx.benchmark.dryRunMode.enable"
private const val FRAME_TIMING_BACKEND_ARGUMENT =
    "io.ferventio.benchmark.frameTimingBackend"
private const val FRAME_TIMING_BACKEND_GFXINFO = "gfxinfo"
private const val FRAME_TIMING_BACKEND_PERFETTO = "perfetto"
private const val ANDROID_16_API_LEVEL = 36

private fun instrumentationArgument(name: String): String? =
    InstrumentationRegistry.getArguments()
        .getString(name)
        ?.trim()
        ?.takeIf(String::isNotEmpty)

internal fun isBenchmarkDryRun(): Boolean =
    instrumentationArgument(DRY_RUN_ARGUMENT)
        ?.equals("true", ignoreCase = true) == true

/**
 * Selects the frame metric before Macrobenchmark starts its measurement phase.
 *
 * AndroidX 1.5.0-alpha07 still identifies RenderThread work through Perfetto slices named
 * `DrawFrame*` on a thread whose name is exactly `RenderThread`. On Android 16 devices the trace
 * can contain UI `Choreographer#doFrame` slices without that matching RenderThread slice, which
 * makes [FrameTimingMetric] abort the entire benchmark after the iteration has completed.
 *
 * `gfxinfo` is therefore the default for dry-runs and API 36+, while older devices keep the richer
 * Perfetto-backed metric. The instrumentation argument is an explicit escape hatch for validating
 * a future AndroidX or platform fix without changing source code:
 *
 * `io.ferventio.benchmark.frameTimingBackend=gfxinfo|perfetto`
 */
@OptIn(ExperimentalMetricApi::class)
internal fun frameTimingMetricForCurrentRun(): Metric {
    val forcedBackend = instrumentationArgument(FRAME_TIMING_BACKEND_ARGUMENT)?.lowercase()
    return when (forcedBackend) {
        FRAME_TIMING_BACKEND_GFXINFO -> FrameTimingGfxInfoMetric()
        FRAME_TIMING_BACKEND_PERFETTO -> FrameTimingMetric()
        null -> if (isBenchmarkDryRun() || Build.VERSION.SDK_INT >= ANDROID_16_API_LEVEL) {
            FrameTimingGfxInfoMetric()
        } else {
            FrameTimingMetric()
        }
        else -> error(
            "Unsupported $FRAME_TIMING_BACKEND_ARGUMENT=$forcedBackend; " +
                "expected $FRAME_TIMING_BACKEND_GFXINFO or $FRAME_TIMING_BACKEND_PERFETTO",
        )
    }
}
