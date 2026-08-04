package io.ferventio.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubRecoveryPolicyTest {
    @Test
    fun reconnectsAfterNetworkFailureWhenJobStopped() {
        assertTrue(
            EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = true,
                channelCount = 2,
                hasActiveJob = false,
                hasSession = true,
                hasAccessToken = true,
                networkAvailable = true,
            ),
        )
    }

    @Test
    fun doesNotCreateParallelConnection() {
        assertFalse(
            EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = true,
                channelCount = 2,
                hasActiveJob = true,
                hasSession = true,
                hasAccessToken = true,
                networkAvailable = true,
            ),
        )
    }

    @Test
    fun requiresAuthenticatedSessionAndAtLeastOneChannel() {
        assertFalse(
            EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = false,
                channelCount = 2,
                hasActiveJob = false,
                hasSession = true,
                hasAccessToken = true,
                networkAvailable = true,
            ),
        )
        assertFalse(
            EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = true,
                channelCount = 0,
                hasActiveJob = false,
                hasSession = true,
                hasAccessToken = true,
                networkAvailable = true,
            ),
        )
    }

    @Test
    fun `does not reconnect without validated network`() {
        assertFalse(
            EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = true,
                channelCount = 1,
                hasActiveJob = false,
                hasSession = true,
                hasAccessToken = true,
                networkAvailable = false,
            ),
        )
    }
}
