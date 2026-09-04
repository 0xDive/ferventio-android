package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AuthenticationPersistenceValidationTest {
    @Test
    fun `valid backend session and access lease are accepted`() {
        val credential = credential()

        AuthenticationPersistenceValidation.requireValid(
            backendCredential = credential,
            accessLease = lease(credential.expiresAtEpochMillis),
        )
    }

    @Test
    fun `backend session expiry must match cached access lease`() {
        assertFailsWith<IllegalArgumentException> {
            AuthenticationPersistenceValidation.requireValid(
                backendCredential = credential(),
                accessLease = lease(backendSessionExpiresAt = 600_000L),
            )
        }
    }

    @Test
    fun `validation timestamp after Twitch expiry is rejected`() {
        val credential = credential()
        assertFailsWith<IllegalArgumentException> {
            AuthenticationPersistenceValidation.requireValid(
                backendCredential = credential,
                accessLease = lease(credential.expiresAtEpochMillis).copy(
                    twitchValidatedAtEpochMillis = 400_001L,
                ),
            )
        }
    }

    @Test
    fun `blank OAuth scope is rejected`() {
        val credential = credential()
        assertFailsWith<IllegalArgumentException> {
            AuthenticationPersistenceValidation.requireValid(
                backendCredential = credential,
                accessLease = lease(credential.expiresAtEpochMillis).copy(
                    session = session(scopes = setOf("user:read:chat", " ")),
                ),
            )
        }
    }

    @Test
    fun `scope count is bounded`() {
        val credential = credential()
        assertFailsWith<IllegalArgumentException> {
            AuthenticationPersistenceValidation.requireValid(
                backendCredential = credential,
                accessLease = lease(credential.expiresAtEpochMillis).copy(
                    session = session(
                        scopes = (0..AuthenticationPersistenceValidation.MAX_SCOPES)
                            .map { "scope:$it" }
                            .toSet(),
                    ),
                ),
            )
        }
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
        session = session(),
    )

    private fun session(
        scopes: Set<String> = setOf("user:read:chat", "user:write:chat"),
    ) = TwitchSession(
        clientId = "client-id",
        userId = "user-id",
        login = "viewer",
        scopes = scopes,
        expiresInSeconds = 300L,
    )
}
