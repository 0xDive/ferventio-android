package io.ferventio.app.performance

import java.util.concurrent.atomic.AtomicBoolean

/** Process-local flag enabled only after a validated benchmark intent. */
object PerformanceRuntimeState {
    private val enabled = AtomicBoolean(false)

    val isEnabled: Boolean
        get() = enabled.get()

    fun enable() {
        enabled.set(true)
    }
}
