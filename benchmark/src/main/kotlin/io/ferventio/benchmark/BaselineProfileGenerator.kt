package io.ferventio.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndLongChatJourney() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        prepareDeviceForBenchmark()
        startActivityAndWait(performanceIntent(initialMessages = 10_000))
        swipeChatList(device, Direction.UP, repetitions = 4)
        swipeChatList(device, Direction.DOWN, repetitions = 4)
    }
}
