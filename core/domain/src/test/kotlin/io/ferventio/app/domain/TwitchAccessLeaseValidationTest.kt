package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class TwitchAccessLeaseValidationTest {
    @Test
    fun `direct validation updates actual expiry without replacing stable transport session`() {
        val cached = lease()
        val updated = TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = cached,
            validatedSession = session(expiresInSeconds = 7_200L),
            requiredScopes = REQUIRED_SCOPES,
            nowEpochMillis = 1_000_000L,
        )

        assertEquals(8_200_000L, updated.twitchExpiresAtEpochMillis)
        assertEquals(1_000_000L, updated.twitchValidatedAtEpochMillis)
        assertSame(cached.session, updated.session)
        assertEquals(3_600L, updated.session.expiresInSeconds)
    }

    @Test
    fun `transport identity change adopts validated session`() {
        val cached = lease().copy(
            session = session(scopes = REQUIRED_SCOPES + "channel:read:redemptions"),
        )
        val validated = session(expiresInSeconds = 7_200L)

        val updated = TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = cached,
            validatedSession = validated,
            requiredScopes = REQUIRED_SCOPES,
            nowEpochMillis = 1_000_000L,
        )

        assertNotSame(cached.session, updated.session)
        assertSame(validated, updated.session)
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
            validatedSession = session(scopes = setOf("user:read:chat")),
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

    private fun session(
        expiresInSeconds: Long = 3_600L,
        scopes: Set<String> = REQUIRED_SCOPES,
    ) = TwitchSession(
        clientId = "client",
        userId = "user",
        login = "viewer",
        scopes = scopes,
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
