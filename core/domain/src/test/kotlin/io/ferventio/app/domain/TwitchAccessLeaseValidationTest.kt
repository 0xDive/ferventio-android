package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TwitchAccessLeaseValidationTest {
    @Test
    fun `direct validation updates actual expiry and timestamp`() {
        val updated = TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = lease(),
            validatedSession = session(expiresInSeconds = 7_200L),
            requiredScopes = REQUIRED_SCOPES,
            nowEpochMillis = 1_000_000L,
        )

        assertEquals(8_200_000L, updated.twitchExpiresAtEpochMillis)
        assertEquals(1_000_000L, updated.twitchValidatedAtEpochMillis)
        assertEquals(7_200L, updated.session.expiresInSeconds)
    }

    @Test(expected = IllegalStateException::class)
    fun `different Twitch user is rejected`() {
        TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = lease(),
            validatedSession = session().copy(userId = "attacker"),
            requiredScopes = REQUIRED_SCOPES,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `missing required scope is rejected`() {
        TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = lease(),
            validatedSession = session().copy(scopes = setOf("user:read:chat")),
            requiredScopes = REQUIRED_SCOPES,
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `expired validation response is rejected`() {
        TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = lease(),
            validatedSession = session(expiresInSeconds = 0L),
            requiredScopes = REQUIRED_SCOPES,
        )
    }

    private fun lease() = TwitchAccessLease(
        accessToken = "access",
        leaseExpiresAtEpochMillis = 500_000L,
        twitchExpiresAtEpochMillis = 10_000_000L,
        twitchValidatedAtEpochMillis = 100_000L,
        backendSessionExpiresAtEpochMillis = 20_000_000L,
        session = session(),
    )

    private fun session(expiresInSeconds: Long = 3_600L) = TwitchSession(
        clientId = "client",
        userId = "user",
        login = "viewer",
        scopes = REQUIRED_SCOPES,
        expiresInSeconds = expiresInSeconds,
    )

    private companion object {
        val REQUIRED_SCOPES = setOf(
            "user:read:chat",
            "user:write:chat",
            "moderator:manage:chat_messages",
        )
    }
}
