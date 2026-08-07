package io.ferventio.app.application

import io.ferventio.app.domain.InteractiveMutationFailureKind
import io.ferventio.app.domain.InteractiveMutationRecovery
import io.ferventio.app.twitch.TwitchInteractiveApiException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class InteractiveMutationFailureClassifierTest {
    @Test
    fun `classifies authentication and rate limits as explicit retries`() {
        val unauthorized = InteractiveMutationFailureClassifier.classify(
            TwitchInteractiveApiException(401, "expired"),
        )
        val rateLimited = InteractiveMutationFailureClassifier.classify(
            TwitchInteractiveApiException(429, "slow down"),
        )

        assertEquals(InteractiveMutationFailureKind.AUTHENTICATION, unauthorized.kind)
        assertEquals(InteractiveMutationRecovery.RETRY, unauthorized.recovery)
        assertEquals(InteractiveMutationFailureKind.RATE_LIMITED, rateLimited.kind)
        assertEquals(InteractiveMutationRecovery.RETRY, rateLimited.recovery)
    }

    @Test
    fun `classifies ambiguous transport failures as refresh only`() {
        val network = InteractiveMutationFailureClassifier.classify(IOException("offline"))
        val server = InteractiveMutationFailureClassifier.classify(
            TwitchInteractiveApiException(503, "unavailable"),
        )

        assertEquals(InteractiveMutationFailureKind.NETWORK, network.kind)
        assertEquals(InteractiveMutationRecovery.REFRESH, network.recovery)
        assertEquals(InteractiveMutationFailureKind.SERVER, server.kind)
        assertEquals(InteractiveMutationRecovery.REFRESH, server.recovery)
    }

    @Test
    fun `permission and conflict failures are not replayed`() {
        val forbidden = InteractiveMutationFailureClassifier.classify(
            TwitchInteractiveApiException(403, "forbidden"),
        )
        val conflict = InteractiveMutationFailureClassifier.classify(
            TwitchInteractiveApiException(409, "conflict"),
        )

        assertEquals(InteractiveMutationFailureKind.PERMISSION, forbidden.kind)
        assertEquals(InteractiveMutationRecovery.NONE, forbidden.recovery)
        assertEquals(InteractiveMutationFailureKind.CONFLICT, conflict.kind)
        assertEquals(InteractiveMutationRecovery.NONE, conflict.recovery)
    }
}
