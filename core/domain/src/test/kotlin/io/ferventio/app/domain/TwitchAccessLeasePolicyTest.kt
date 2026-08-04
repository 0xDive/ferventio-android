package io.ferventio.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchAccessLeasePolicyTest {
    @Test
    fun `fresh short lease is reused normally`() {
        assertTrue(
            TwitchAccessLeasePolicy.canReuseWithoutBackendCall(
                lease = lease(leaseExpiry = 106_000L, twitchExpiry = 500_000L),
                nowEpochMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `expired short lease remains usable only as transient outage fallback`() {
        val lease = lease(leaseExpiry = 99_000L, twitchExpiry = 500_000L)

        assertFalse(TwitchAccessLeasePolicy.canReuseWithoutBackendCall(lease, 100_000L))
        assertTrue(TwitchAccessLeasePolicy.canUseDuringBackendOutage(lease, 100_000L))
    }

    @Test
    fun `token near Twitch expiry is never used as outage fallback`() {
        assertFalse(
            TwitchAccessLeasePolicy.canUseDuringBackendOutage(
                lease = lease(leaseExpiry = 99_000L, twitchExpiry = 129_999L),
                nowEpochMillis = 100_000L,
            ),
        )
    }

    @Test
    fun `blank token fails closed even with future timestamps`() {
        assertFalse(
            TwitchAccessLeasePolicy.canUseDuringBackendOutage(
                lease = lease(leaseExpiry = 500_000L, twitchExpiry = 500_000L).copy(accessToken = ""),
                nowEpochMillis = 100_000L,
            ),
        )
    }


    @Test
    fun `startup trusts only a just validated server lease`() {
        val lease = lease(leaseExpiry = 500_000L, twitchExpiry = 8_000_000L).copy(
            twitchValidatedAtEpochMillis = 100_000L,
        )

        assertFalse(TwitchAccessLeasePolicy.needsDirectValidationAtStartup(lease, 104_999L))
        assertTrue(TwitchAccessLeasePolicy.needsDirectValidationAtStartup(lease, 105_000L))
    }

    @Test
    fun `outage validation becomes due before the hourly Twitch deadline`() {
        val lease = lease(leaseExpiry = 99_000L, twitchExpiry = 8_000_000L).copy(
            twitchValidatedAtEpochMillis = 100_000L,
        )

        assertFalse(TwitchAccessLeasePolicy.needsDirectValidationDuringOutage(lease, 3_399_999L))
        assertTrue(TwitchAccessLeasePolicy.needsDirectValidationDuringOutage(lease, 3_400_000L))
    }

    @Test
    fun `rolling short lease and derived expires in do not require another disk write`() {
        val original = lease(leaseExpiry = 120_000L, twitchExpiry = 500_000L)
        val renewed = original.copy(
            leaseExpiresAtEpochMillis = 180_000L,
            session = original.session.copy(expiresInSeconds = 250L),
        )

        assertTrue(TwitchAccessLeasePolicy.representsSameCachedCredential(original, renewed))
    }

    @Test
    fun `rotated access token is a material cached credential change`() {
        val original = lease(leaseExpiry = 120_000L, twitchExpiry = 500_000L)

        assertFalse(
            TwitchAccessLeasePolicy.representsSameCachedCredential(
                original,
                original.copy(accessToken = "rotated-access"),
            ),
        )
    }

    @Test
    fun `new Twitch validation timestamp is persisted`() {
        val original = lease(leaseExpiry = 120_000L, twitchExpiry = 500_000L)

        assertFalse(
            TwitchAccessLeasePolicy.representsSameCachedCredential(
                original,
                original.copy(twitchValidatedAtEpochMillis = 101_000L),
            ),
        )
    }

    private fun lease(leaseExpiry: Long, twitchExpiry: Long) = TwitchAccessLease(
        accessToken = "access",
        leaseExpiresAtEpochMillis = leaseExpiry,
        twitchExpiresAtEpochMillis = twitchExpiry,
        twitchValidatedAtEpochMillis = 100_000L,
        backendSessionExpiresAtEpochMillis = 900_000L,
        session = TwitchSession(
            clientId = "client",
            userId = "user",
            login = "viewer",
            scopes = setOf("user:read:chat"),
            expiresInSeconds = 300L,
        ),
    )
}
