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

    private fun coordinatorResponding(body: String): MobileAuthenticationCoordinator {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
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
