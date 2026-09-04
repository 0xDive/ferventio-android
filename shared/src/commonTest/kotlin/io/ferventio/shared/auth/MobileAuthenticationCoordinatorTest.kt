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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileAuthenticationCoordinatorTest {
    private val identity = MobileDeviceIdentity(
        installationId = "installation",
        deviceSecret = "s".repeat(32),
    )

    @Test
    fun startBuildsBrowserRequestFromBackendHandshake() = runTest {
        val coordinator = coordinatorResponding(
            """
            {
              "serverTime":"2026-08-16T12:00:00Z",
              "expiresAt":"2026-08-16T12:01:00Z",
              "authorizationUrl":"https://example.test/oauth/start",
              "state":"expected-state"
            }
            """.trimIndent(),
        )

        val request = coordinator.startAuthorization(
            serverUrl = "https://example.test/",
            identity = identity,
            callbackScheme = "io.ferventio.app",
        )

        assertEquals("https://example.test/oauth/start", request.authorizationUrl)
        assertEquals("io.ferventio.app", request.callbackScheme)
        assertEquals("expected-state", request.state)
        assertEquals("https://example.test", request.serverUrl)
    }

    @Test
    fun startRejectsMalformedCallbackSchemeBeforeNetwork() = runTest {
        val coordinator = coordinatorResponding("{}")

        assertFailsWith<IllegalArgumentException> {
            coordinator.startAuthorization(
                serverUrl = "https://example.test",
                identity = identity,
                callbackScheme = "not a scheme",
            )
        }
    }

    @Test
    fun restoreReusesFreshCachedLease() = runTest {
        val coordinator = coordinatorResponding("{}")
        val authentication = authenticationWithFreshLease()

        val restored = coordinator.restoreAuthentication(identity, authentication)

        assertEquals(authentication, restored)
    }

    @Test
    fun restoreRejectsExpiredBackendSessionBeforeNetwork() = runTest {
        val coordinator = coordinatorResponding("{}")
        val expired = StoredAuthentication(
            backendCredential = BackendSessionCredential(
                serverUrl = "https://example.test",
                token = "backend-session",
                expiresAtEpochMillis = 999_999L,
            ),
            accessLease = null,
        )

        val error = assertFailsWith<MobileAuthenticationFlowException> {
            coordinator.restoreAuthentication(identity, expired)
        }

        assertEquals(MobileAuthenticationFailureReason.EXPIRED, error.reason)
    }

    @Test
    fun restoreRefreshesMissingCachedLease() = runTest {
        val coordinator = coordinatorResponding(
            """
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
            """.trimIndent(),
        )
        val authentication = StoredAuthentication(
            backendCredential = BackendSessionCredential(
                serverUrl = "https://example.test",
                token = "backend-session",
                expiresAtEpochMillis = 4_600_000L,
            ),
            accessLease = null,
        )

        val restored = coordinator.restoreAuthentication(identity, authentication)

        assertEquals("refreshed-token", restored.accessLease?.accessToken)
        assertEquals(authentication.backendCredential, restored.backendCredential)
    }

    @Test
    fun foregroundRefreshReusesFreshLeaseWithoutPersisting() = runTest {
        val authentication = authenticationWithFreshLease()

        val result = coordinatorResponding("{}").refreshAuthenticationForForeground(
            identity = identity,
            authentication = authentication,
        )

        assertEquals(authentication, result.authentication)
        assertFalse(result.shouldPersist)
        assertFalse(result.shouldSignOut)
    }

    @Test
    fun foregroundRefreshUsesCachedTwitchCredentialDuringBackendOutage() = runTest {
        val authentication = authenticationWithStaleLease(twitchExpiresAtEpochMillis = 8_200_000L)
        val coordinator = coordinatorResponding(
            body = """{"error":"temporarily unavailable"}""",
            status = HttpStatusCode.ServiceUnavailable,
        )

        val result = coordinator.refreshAuthenticationForForeground(identity, authentication)

        assertEquals(authentication, result.authentication)
        assertFalse(result.shouldPersist)
        assertFalse(result.shouldSignOut)
    }

    @Test
    fun foregroundRefreshReturnsUnavailableWhenOutageFallbackIsTooCloseToExpiry() = runTest {
        val authentication = authenticationWithStaleLease(twitchExpiresAtEpochMillis = 1_020_000L)
        val coordinator = coordinatorResponding(
            body = """{"error":"temporarily unavailable"}""",
            status = HttpStatusCode.ServiceUnavailable,
        )

        val result = coordinator.refreshAuthenticationForForeground(identity, authentication)

        assertNull(result.authentication)
        assertFalse(result.shouldPersist)
        assertFalse(result.shouldSignOut)
    }

    @Test
    fun foregroundRefreshSignsOutRejectedBackendSession() = runTest {
        val authentication = authenticationWithStaleLease(twitchExpiresAtEpochMillis = 8_200_000L)
        val coordinator = coordinatorResponding(
            body = """{"error":"authentication record not found"}""",
            status = HttpStatusCode.Unauthorized,
        )

        val result = coordinator.refreshAuthenticationForForeground(identity, authentication)

        assertNull(result.authentication)
        assertFalse(result.shouldPersist)
        assertTrue(result.shouldSignOut)
    }

    @Test
    fun completeTurnsAcceptedCallbackIntoStoredAuthentication() = runTest {
        val coordinator = coordinatorResponding(
            """
            {
              "sessionToken":"backend-session",
              "sessionExpiresAt":"2026-08-16T13:00:00Z",
              "lease":{
                "serverTime":"2026-08-16T12:00:00Z",
                "accessToken":"access-token",
                "leaseExpiresAt":"2026-08-16T12:05:00Z",
                "twitchExpiresAt":"2026-08-16T14:00:00Z",
                "twitchValidatedAt":"2026-08-16T12:00:00Z",
                "sessionExpiresAt":"2026-08-16T13:00:00Z",
                "clientId":"client",
                "userId":"user",
                "login":"login",
                "scopes":["chat:read"]
              }
            }
            """.trimIndent(),
        )

        val stored = coordinator.completeAuthorization(
            identity = identity,
            callback = BackendAuthorizationCallbackResult(
                status = BackendAuthorizationCallbackStatus.COMPLETE,
                serverUrl = "https://example.test",
                code = "code",
                state = "state",
            ),
        )

        assertEquals("backend-session", stored.backendCredential.token)
        assertEquals("access-token", stored.accessLease?.accessToken)
    }

    @Test
    fun completeDoesNotCallBackendForRejectedCallback() = runTest {
        val coordinator = coordinatorResponding("{}")

        val error = assertFailsWith<MobileAuthenticationFlowException> {
            coordinator.completeAuthorization(
                identity = identity,
                callback = BackendAuthorizationCallbackResult(
                    status = BackendAuthorizationCallbackStatus.OAUTH_ERROR,
                    errorCode = "access_denied",
                ),
            )
        }

        assertEquals(MobileAuthenticationFailureReason.OAUTH_ERROR, error.reason)
        assertEquals("access_denied", error.errorCode)
    }

    @Test
    fun revokeAllSessionsDelegatesThroughValidatedIdentity() = runTest {
        val coordinator = coordinatorResponding(
            body = "",
            status = HttpStatusCode.NoContent,
        )

        coordinator.revokeAllSessions(
            identity = identity,
            authentication = authenticationWithFreshLease(),
        )
    }

    private fun authenticationWithFreshLease() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "access-token",
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

    private fun authenticationWithStaleLease(
        twitchExpiresAtEpochMillis: Long,
    ) = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "cached-token",
            leaseExpiresAtEpochMillis = 990_000L,
            twitchExpiresAtEpochMillis = twitchExpiresAtEpochMillis,
            twitchValidatedAtEpochMillis = 900_000L,
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

    private fun coordinatorResponding(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): MobileAuthenticationCoordinator {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        val backend = MobileBackendAuthenticationClient(
            client = HttpClient(engine) { expectSuccess = false },
            nowEpochMillis = { 1_000_000L },
        )
        return MobileAuthenticationCoordinator(
            backend = backend,
            nowEpochMillis = { 1_000_000L },
        )
    }
}
