package io.ferventio.shared.push

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PushBackendRegistrationClientTest {
    private val identity = MobileDeviceIdentity(
        installationId = "installation-id",
        deviceSecret = "s".repeat(32),
    )

    @Test
    fun apnsCoordinatorRegistersAgainstExpectedEndpoint() = runTest {
        var requestedUrl: String? = null
        val engine = MockEngine { request ->
            requestedUrl = request.url.toString()
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
            )
        }
        val backend = PushBackendRegistrationClient(
            client = HttpClient(engine) { expectSuccess = false },
        )
        val coordinator = ApnsPushRegistrationCoordinator(backend = backend)

        val request = coordinator.register(
            serverUrl = "https://example.test/",
            identity = identity,
            apnsDeviceToken = " token ",
            appVersion = "1.0",
        )

        assertEquals(
            "https://example.test/v1/push/registrations/installation-id",
            requestedUrl,
        )
        assertEquals("ios", request.platform)
        assertEquals("apns", request.provider)
        assertEquals("token", request.apnsDeviceToken)
    }

    @Test
    fun apnsCoordinatorUnregistersWithDeviceSecret() = runTest {
        var requestedMethod: HttpMethod? = null
        var requestedUrl: String? = null
        var requestedSecret: String? = null
        val engine = MockEngine { request ->
            requestedMethod = request.method
            requestedUrl = request.url.toString()
            requestedSecret = request.headers["X-Device-Secret"]
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
            )
        }
        val coordinator = ApnsPushRegistrationCoordinator(
            backend = PushBackendRegistrationClient(
                client = HttpClient(engine) { expectSuccess = false },
            ),
        )

        coordinator.unregister(
            serverUrl = "https://example.test/",
            identity = identity,
        )

        assertEquals(HttpMethod.Delete, requestedMethod)
        assertEquals(
            "https://example.test/v1/push/registrations/installation-id",
            requestedUrl,
        )
        assertEquals(identity.deviceSecret, requestedSecret)
    }

    @Test
    fun unregisterTreatsMissingRegistrationAsAlreadyClean() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("{\"error\":\"registration not found\"}"),
                status = HttpStatusCode.NotFound,
            )
        }
        val backend = PushBackendRegistrationClient(
            client = HttpClient(engine) { expectSuccess = false },
        )

        backend.unregister(
            serverUrl = "https://example.test",
            identity = identity,
        )
    }

    @Test
    fun authenticatedApnsRegistrationIncludesTwitchIdentity() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
            )
        }
        val coordinator = ApnsPushRegistrationCoordinator(
            backend = PushBackendRegistrationClient(
                client = HttpClient(engine) { expectSuccess = false },
            ),
        )

        val request = coordinator.registerAuthenticated(
            serverUrl = "https://example.test",
            identity = identity,
            apnsDeviceToken = "token",
            appVersion = "1.0",
            authentication = authenticatedSession(),
        )

        assertEquals("viewer-id", request.userId)
        assertEquals("viewer", request.userLogin)
    }

    @Test
    fun authenticatedWorkspaceRegistrationIncludesChannelRoutingContext() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("{}"),
                status = HttpStatusCode.OK,
            )
        }
        val coordinator = ApnsPushRegistrationCoordinator(
            backend = PushBackendRegistrationClient(
                client = HttpClient(engine) { expectSuccess = false },
            ),
        )
        val workspace = WorkspaceRuntimeSnapshot(
            channels = listOf(
                ChatChannel("1", "alpha", "Alpha"),
                ChatChannel("2", "beta", "Beta"),
                ChatChannel("3", "gamma", "Gamma"),
            ),
            moderatorChannelIds = linkedSetOf("3", "missing", "1"),
            pushContextRevision = 4L,
        )

        val request = coordinator.registerAuthenticatedWorkspace(
            serverUrl = "https://example.test",
            identity = identity,
            apnsDeviceToken = "token",
            appVersion = "1.0",
            authentication = authenticatedSession(),
            workspace = workspace,
        )

        assertEquals("viewer-id", request.userId)
        assertEquals("viewer", request.userLogin)
        assertEquals(listOf("1", "2", "3"), request.channelIds)
        assertEquals(listOf("1", "3"), request.moderatorChannelIds)
    }

    @Test
    fun authenticatedApnsRegistrationRejectsMissingAccessLease() = runTest {
        val engine = MockEngine {
            error("network must not be reached")
        }
        val coordinator = ApnsPushRegistrationCoordinator(
            backend = PushBackendRegistrationClient(
                client = HttpClient(engine) { expectSuccess = false },
            ),
        )
        val authentication = StoredAuthentication(
            backendCredential = BackendSessionCredential(
                serverUrl = "https://example.test",
                token = "backend-session",
                expiresAtEpochMillis = 4_600_000L,
            ),
            accessLease = null,
        )

        assertFailsWith<IllegalStateException> {
            coordinator.registerAuthenticated(
                serverUrl = "https://example.test",
                identity = identity,
                apnsDeviceToken = "token",
                appVersion = "1.0",
                authentication = authentication,
            )
        }
    }

    @Test
    fun backendErrorIsSurfacedWithStatusAndMessage() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("{\"error\":\"invalid registration\"}"),
                status = HttpStatusCode.BadRequest,
            )
        }
        val backend = PushBackendRegistrationClient(
            client = HttpClient(engine) { expectSuccess = false },
        )

        val error = assertFailsWith<PushBackendRegistrationException> {
            backend.register(
                serverUrl = "https://example.test",
                request = PushRegistrationRequestFactory().apns(
                    identity = identity,
                    apnsDeviceToken = "token",
                    appVersion = "1.0",
                ),
            )
        }

        assertEquals(400, error.statusCode)
        assertEquals("invalid registration", error.backendMessage)
    }

    @Test
    fun unregisterSurfacesDeviceSecretMismatch() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("{\"error\":\"device secret mismatch\"}"),
                status = HttpStatusCode.Forbidden,
            )
        }
        val backend = PushBackendRegistrationClient(
            client = HttpClient(engine) { expectSuccess = false },
        )

        val error = assertFailsWith<PushBackendRegistrationException> {
            backend.unregister(
                serverUrl = "https://example.test",
                identity = identity,
            )
        }

        assertEquals(403, error.statusCode)
        assertEquals("device secret mismatch", error.backendMessage)
    }

    @Test
    fun rejectsCleartextServerBeforeNetwork() = runTest {
        val engine = MockEngine {
            error("network must not be reached")
        }
        val backend = PushBackendRegistrationClient(
            client = HttpClient(engine) { expectSuccess = false },
        )

        assertFailsWith<IllegalArgumentException> {
            backend.register(
                serverUrl = "http://example.test",
                request = PushRegistrationRequestFactory().apns(
                    identity = identity,
                    apnsDeviceToken = "token",
                    appVersion = "1.0",
                ),
            )
        }
    }

    private fun authenticatedSession() = StoredAuthentication(
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
                userId = "viewer-id",
                login = "viewer",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
