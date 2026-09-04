package io.ferventio.shared.chat

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSessionRunGateTest {
    @Test
    fun waitsForActiveSessionBeforeStartingNextOne() = runTest {
        val gate = ChatSessionRunGate()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()

        val first = launch {
            gate.run {
                firstEntered.complete(Unit)
                releaseFirst.await()
            }
        }
        firstEntered.await()

        val second = launch {
            gate.run {
                secondEntered.complete(Unit)
            }
        }
        runCurrent()
        assertFalse(secondEntered.isCompleted)

        releaseFirst.complete(Unit)
        first.join()
        second.join()

        assertTrue(secondEntered.isCompleted)
    }
}
