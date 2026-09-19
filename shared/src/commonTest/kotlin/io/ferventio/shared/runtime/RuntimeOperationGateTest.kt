package io.ferventio.shared.runtime

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeOperationGateTest {
    @Test
    fun pauseWaitsForActiveOperationsAndBlocksNewEntries() = runTest {
        val gate = RuntimeOperationGate()
        assertTrue(gate.tryEnter())

        var pauseCompleted = false
        val pauseJob = launch {
            gate.pauseAndAwaitIdle()
            pauseCompleted = true
        }
        yield()

        assertTrue(gate.isPaused)
        assertFalse(gate.tryEnter())
        assertFalse(pauseCompleted)

        gate.leave()
        pauseJob.join()

        assertTrue(pauseCompleted)
        assertFalse(gate.tryEnter())
        gate.resume()
        assertTrue(gate.tryEnter())
        gate.leave()
    }

    @Test
    fun nestedPausesRequireMatchingResumes() = runTest {
        val gate = RuntimeOperationGate()

        gate.pauseAndAwaitIdle()
        gate.pauseAndAwaitIdle()
        assertTrue(gate.isPaused)
        assertFalse(gate.tryEnter())

        gate.resume()
        assertTrue(gate.isPaused)
        assertFalse(gate.tryEnter())

        gate.resume()
        assertFalse(gate.isPaused)
        assertTrue(gate.tryEnter())
        gate.leave()
    }
}
