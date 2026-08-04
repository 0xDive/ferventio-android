package io.ferventio.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupWithTenThousandMessages() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric(), frameTimingMetricForCurrentRun()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { prepareDeviceForBenchmark() },
        measureBlock = {
            startActivityAndWait(performanceIntent(initialMessages = 10_000))
            swipeChatList(device, Direction.UP, repetitions = 3)
            swipeChatList(device, Direction.DOWN, repetitions = 3)
        },
    )

    @Test
    fun incomingFiftyMessagesPerSecond() = measureIncomingBurst(messagesPerSecond = 50)

    @Test
    fun incomingHundredMessagesPerSecond() = measureIncomingBurst(messagesPerSecond = 100)

    private fun measureIncomingBurst(messagesPerSecond: Int) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(frameTimingMetricForCurrentRun()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { prepareDeviceForBenchmark() },
        measureBlock = {
            startActivityAndWait(
                performanceIntent(
                    initialMessages = 500,
                    messagesPerSecond = messagesPerSecond,
                    durationSeconds = 10,
                ),
            )
            requireChatList(device)
            Thread.sleep(PERFORMANCE_BURST_WAIT_MILLIS)
        },
    )

    private companion object {
        const val PERFORMANCE_BURST_WAIT_MILLIS = 11_000L
    }
}
