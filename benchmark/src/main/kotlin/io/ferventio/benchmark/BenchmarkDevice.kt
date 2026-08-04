package io.ferventio.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope

private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
private const val DEVICE_UNLOCK_WAIT_STEPS = 20
private const val DEVICE_UNLOCK_WAIT_MILLIS = 250L

/**
 * Makes a connected physical device ready before an unmeasured benchmark iteration.
 *
 * `wm dismiss-keyguard` can dismiss an insecure keyguard, but Android intentionally does not let
 * instrumentation bypass a PIN, pattern, password, or biometric challenge. In that case we fail
 * immediately with a useful error instead of waiting one minute for a Compose node hidden behind
 * SystemUI.
 */
internal fun MacrobenchmarkScope.prepareDeviceForBenchmark() {
    device.wakeUp()
    device.executeShellCommand("wm dismiss-keyguard")
    device.pressHome()
    device.waitForIdle()

    repeat(DEVICE_UNLOCK_WAIT_STEPS) {
        if (device.currentPackageName != SYSTEM_UI_PACKAGE) return
        Thread.sleep(DEVICE_UNLOCK_WAIT_MILLIS)
    }

    error(
        "Benchmark device is still locked by SystemUI. " +
            "Unlock the device manually and disable automatic screen locking for the test run.",
    )
}
