package io.ferventio.app.push

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PushRegistrationUpsertGateTest {
    private val request = PushRegistrationRequest(
        installationId = "installation",
        deviceSecret = "secret",
        provider = "embedded_socket",
        appVersion = "1.0",
        platform = "android",
        userId = "viewer",
        channelIds = listOf("channel"),
    )

    @Test
    fun identicalSuccessfulRegistrationIsSkippedUntilCleared() = runBlocking {
        val gate = PushRegistrationUpsertGate()
        var calls = 0

        assertTrue(
            gate.submit("https://example.test/", request) {
                calls += 1
                true
            },
        )
        assertFalse(
            gate.submit("https://example.test", request) {
                calls += 1
                true
            },
        )

        gate.clear()

        assertTrue(
            gate.submit("https://example.test", request) {
                calls += 1
                true
            },
        )
        assertEquals(2, calls)
    }

    @Test
    fun failedOrCancelledRegistrationRemainsRetryable() = runBlocking {
        val gate = PushRegistrationUpsertGate()
        var calls = 0

        assertFalse(
            gate.submit("https://example.test", request) {
                calls += 1
                false
            },
        )
        assertTrue(
            gate.submit("https://example.test", request) {
                calls += 1
                true
            },
        )

        assertEquals(2, calls)
    }

    @Test
    fun samePayloadOnDifferentServerIsNotDeduplicated() = runBlocking {
        val gate = PushRegistrationUpsertGate()
        var calls = 0

        gate.submit("https://one.test", request) {
            calls += 1
            true
        }
        gate.submit("https://two.test", request) {
            calls += 1
            true
        }

        assertEquals(2, calls)
    }
}
