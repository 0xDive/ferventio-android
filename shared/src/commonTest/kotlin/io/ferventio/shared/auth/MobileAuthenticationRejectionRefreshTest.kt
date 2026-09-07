package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileAuthenticationRejectionRefreshTest {
    private val identity = MobileDeviceIdentity(
        installationId = "installation",
        deviceSecret = "s".repeat(32),
    )

    @Test
    fun rejectedTwitchCredentialAlwaysForcesBackendRefresh() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/v1/auth/token", request.url.encodedPath)
            assertEquals("true", request.url.parameters["force_refresh"])
            respond(
                content = ByteReadChannel(REFRESHED_LEASE_JSON),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val coordinator = coordinator(engine)

        val result = coordinator.refreshAuthenticationAfterRejection(
            identity = identity,
            authentication = authenticationWithFreshLease(),
        )

        assertEquals("refreshed-token", result.authentication?.accessLease?.accessToken)
        assertTrue(result.shouldPersist)
        assertFalse(result.shouldSignOut)
    }

    @Test
    fun rejectedTwitchCredentialDoesNotUseStaleFallbackOnBackendOutage() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"temporarily unavailable"}"""),
                status = HttpStatusCode.ServiceUnavailable,
                headers = jsonHeaders(),
            )
        }
        val coordinator = coordinator(engine)

        val result = coordinator.refreshAuthenticationAfterRejection(
            identity = identity,
            authentication = authenticationWithFreshLease(),
        )

        assertNull(result.authentication)
        assertFalse(result.shouldPersist)
        assertFalse(result.shouldSignOut)
    }

    @Test
    fun rejectedBackendSessionSignsOutDuringForcedRefresh() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"authentication record not found"}"""),
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders(),
            )
        }
        val coordinator = coordinator(engine)

        val result = coordinator.refreshAuthenticationAfterRejection(
            identity = identity,
            authentication = authenticationWithFreshLease(),
        )

        assertNull(result.authentication)
        assertFalse(result.shouldPersist)
        assertTrue(result.shouldSignOut)
    }

    private fun coordinator(engine: MockEngine): MobileAuthenticationCoordinator {
        val backend = MobileBackendAuthenticationClient(
            client = HttpClient(engine) { expectSuccess = false },
            nowEpochMillis = { 1_000_000L },
        )
        return MobileAuthenticationCoordinator(
            backend = backend,
            nowEpochMillis = { 1_000_000L },
        )
    }

    private fun authenticationWithFreshLease() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "rejected-token",
            leaseExpiresAtEpochMillis = 1_300_000L,
            twitchExpiresAtEpochMillis = 8_200_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 4_600_000L,
            session = TwitchSession(
                clientId = "client",
                userId = "user",
                login = "login",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    )

    private companion object {
        val REFRESHED_LEASE_JSON = """
            {
              "serverTime":"2026-08-16T12:00:00Z",
              "accessToken":"refreshed-token",
              "leaseExpiresAt":"2026-08-16T12:05:00Z",
              "twitchExpiresAt":"2026-08-16T14:00:00Z",
              "twitchValidatedAt":"2026-08-16T12:00:00Z",
              "sessionExpiresAt":"2026-08-16T13:00:00Z",
              "clientId":"client",
              "userId":"user",
              "login":"login",
              "scopes":["chat:read"]
            }
        """.trimIndent()
    }
}
