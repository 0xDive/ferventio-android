package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoredAuthenticationJsonCodecTest {
    private val codec = StoredAuthenticationJsonCodec()

    @Test
    fun roundTripsValidatedAuthentication() {
        val source = authentication()

        val restored = codec.decode(codec.encode(source))

        assertEquals(source, restored)
    }

    @Test
    fun roundTripsCredentialWithoutCachedLease() {
        val source = StoredAuthentication(
            backendCredential = authentication().backendCredential,
            accessLease = null,
        )

        val restored = codec.decode(codec.encode(source))

        assertEquals(source.backendCredential, restored.backendCredential)
        assertNull(restored.accessLease)
    }

    @Test
    fun rejectsUnknownEnvelopeVersion() {
        val payload = codec.encode(authentication()).replace(
            "\"version\":1",
            "\"version\":2",
        )

        assertFailsWith<IllegalArgumentException> { codec.decode(payload) }
    }

    @Test
    fun rejectsLeaseFromDifferentBackendSession() {
        val source = authentication()
        val payload = codec.encode(source).replace(
            "\"backendSessionExpiresAtEpochMillis\":5000",
            "\"backendSessionExpiresAtEpochMillis\":5001",
        )

        assertFailsWith<IllegalArgumentException> { codec.decode(payload) }
    }

    @Test
    fun rejectsBlankScopeEntry() {
        val payload = codec.encode(authentication()).replace(
            "\"scopes\":[\"chat:edit\",\"chat:read\"]",
            "\"scopes\":[\"chat:edit\",\"\"]",
        )

        assertFailsWith<IllegalStateException> { codec.decode(payload) }
    }

    private fun authentication(): StoredAuthentication = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-token",
            expiresAtEpochMillis = 5_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "access-token",
            leaseExpiresAtEpochMillis = 2_000L,
            twitchExpiresAtEpochMillis = 8_000L,
            twitchValidatedAtEpochMillis = 1_000L,
            backendSessionExpiresAtEpochMillis = 5_000L,
            session = TwitchSession(
                clientId = "client",
                userId = "user",
                login = "login",
                scopes = setOf("chat:read", "chat:edit"),
                expiresInSeconds = 7L,
            ),
        ),
    )
}
