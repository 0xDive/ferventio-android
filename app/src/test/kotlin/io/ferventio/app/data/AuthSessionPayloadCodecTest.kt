package io.ferventio.app.data

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionPayloadCodecTest {
    @Test
    fun `round trip keeps backend session and current access token`() {
        val credential = credential()
        val lease = lease(credential.expiresAtEpochMillis)

        val decoded = AuthSessionPayloadCodec.decode(
            AuthSessionPayloadCodec.encode(credential, lease),
        )

        assertEquals(credential, decoded.backendCredential)
        assertEquals(lease, decoded.accessLease)
    }

    @Test
    fun `legacy backend-only payload migrates without inventing access token`() {
        val payload = "backend-session-v1\n500000\nhttps://auth.example.test\nopaque-session"
            .toByteArray(Charsets.UTF_8)

        val decoded = AuthSessionPayloadCodec.decode(payload)

        assertEquals(credential(), decoded.backendCredential)
        assertNull(decoded.accessLease)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cache cannot be saved with a different backend session expiry`() {
        AuthSessionPayloadCodec.encode(
            backendCredential = credential(),
            accessLease = lease(backendSessionExpiresAt = 600_000L),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `validation timestamp after Twitch expiry is rejected`() {
        val credential = credential()
        AuthSessionPayloadCodec.encode(
            backendCredential = credential,
            accessLease = lease(credential.expiresAtEpochMillis).copy(
                twitchValidatedAtEpochMillis = 400_001L,
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `trailing data is rejected fail closed`() {
        val credential = credential()
        val encoded = AuthSessionPayloadCodec.encode(credential, lease(credential.expiresAtEpochMillis))
        AuthSessionPayloadCodec.decode(encoded + byteArrayOf(1))
    }

    private fun credential() = BackendSessionCredential(
        serverUrl = "https://auth.example.test",
        token = "opaque-session",
        expiresAtEpochMillis = 500_000L,
    )

    private fun lease(backendSessionExpiresAt: Long) = TwitchAccessLease(
        accessToken = "twitch-access",
        leaseExpiresAtEpochMillis = 110_000L,
        twitchExpiresAtEpochMillis = 400_000L,
        twitchValidatedAtEpochMillis = 100_000L,
        backendSessionExpiresAtEpochMillis = backendSessionExpiresAt,
        session = TwitchSession(
            clientId = "client-id",
            userId = "user-id",
            login = "viewer",
            scopes = setOf("user:read:chat", "user:write:chat"),
            expiresInSeconds = 300L,
        ),
    )
}
