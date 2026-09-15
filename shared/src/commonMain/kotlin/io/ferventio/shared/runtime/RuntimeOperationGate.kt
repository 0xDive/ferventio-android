package io.ferventio.shared.runtime

import kotlin.Throws
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Small cross-platform barrier for host operations that must drain before authentication changes.
 * Pauses are reference-counted so overlapping auth transitions cannot resume work prematurely.
 */
class RuntimeOperationGate {
    private data class State(
        val pauseCount: Int = 0,
        val activeOperations: Int = 0,
    )

    private val state = MutableStateFlow(State())

    val isPaused: Boolean
        get() = state.value.pauseCount > 0

    fun tryEnter(): Boolean {
        while (true) {
            val current = state.value
            if (current.pauseCount > 0) return false
            if (
                state.compareAndSet(
                    expect = current,
                    update = current.copy(activeOperations = current.activeOperations + 1),
                )
            ) {
                return true
            }
        }
    }

    fun leave() {
        while (true) {
            val current = state.value
            check(current.activeOperations > 0) { "Runtime operation gate is not active" }
            if (
                state.compareAndSet(
                    expect = current,
                    update = current.copy(activeOperations = current.activeOperations - 1),
                )
            ) {
                return
            }
        }
    }

    @Throws(Exception::class)
    suspend fun pauseAndAwaitIdle() {
        while (true) {
            val current = state.value
            if (
                state.compareAndSet(
                    expect = current,
                    update = current.copy(pauseCount = current.pauseCount + 1),
                )
            ) {
                break
            }
        }
        withContext(NonCancellable) {
            state.first { current -> current.activeOperations == 0 }
        }
    }

    fun resume() {
        while (true) {
            val current = state.value
            check(current.pauseCount > 0) { "Runtime operation gate is not paused" }
            if (
                state.compareAndSet(
                    expect = current,
                    update = current.copy(pauseCount = current.pauseCount - 1),
                )
            ) {
                return
            }
        }
    }
}
