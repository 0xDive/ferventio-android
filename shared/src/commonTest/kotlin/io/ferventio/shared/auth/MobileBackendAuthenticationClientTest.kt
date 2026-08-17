package io.ferventio.shared.auth

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MobileBackendAuthenticationClientTest {
    @Test
    fun startAuthorizationUsesServerRelativeExpiryAndValidatesOrigin() = runTest {
        val client = clientResponding(
            """
            {
              "serverTime":"2026-08-16T12:00:00Z",
              "expiresAt":"2026-08-16T12:01:00Z",
              "authorizationUrl":"https://example.test/v1/auth/twitch?state=expected",
              "state":"expected"
            }
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v1/auth/mobile/start", request.url.encodedPath)
        }
        val api = MobileBackendAuthenticationClient(client) { 1_000L }

        val start = api.startAuthorization(
            serverUrl = "https://example.test/",
            installationId = "installation",
            deviceSecret = "secret",
            appCallbackUri = "io.ferventio.app://oauth/callback",
        )

        assertEquals("expected", start.state)
        assertEquals(61_000L, start.expiresAtEpochMillis)
        assertEquals(
            "https://example.test/v1/auth/twitch?state=expected",
            start.authorizationUrl,
        )
    }

    @Test
    fun startAuthorizationRejectsCrossOriginAuthorizationUrl() = runTest {
        val api = MobileBackendAuthenticationClient(
            clientResponding(
                """
                {
                  "serverTime":"2026-08-16T12:00:00Z",
                  "expiresAt":"2026-08-16T12:01:00Z",
                  "authorizationUrl":"https://evil.example/oauth",
                  "state":"expected"
                }
                """.trimIndent(),
            ),
        ) { 1_000L }

        try {
            api.startAuthorization(
                serverUrl = "https://example.test",
                installationId = "installation",
                deviceSecret = "secret",
                appCallbackUri = "io.ferventio.app://oauth/callback",
            )
            fail("Cross-origin authorization URL must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    @Test
    fun completeAuthorizationBuildsValidatedStoredAuthentication() = runTest {
        val client = clientResponding(
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
                "scopes":["chat:read","chat:edit"]
              }
            }
            """.trimIndent(),
        ) { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v1/auth/mobile/complete", request.url.encodedPath)
        }
        val api = MobileBackendAuthenticationClient(client) { 1_000_000L }

        val stored = api.completeAuthorization(
            serverUrl = "https://example.test",
            installationId = "installation",
            deviceSecret = "secret",
            code = "code",
            state = "state",
        )

        assertStoredAuthentication(stored)
    }

    @Test
    fun revokeDeviceUsesBoundSessionCredentials() = runTest {
        val client = clientResponding(
            body = "",
            status = HttpStatusCode.NoContent,
        ) { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/v1/auth/device", request.url.encodedPath)
            assertEquals("Bearer backend-session", request.headers[HttpHeaders.Authorization])
            assertEquals("installation", request.headers["X-Installation-ID"])
            assertEquals("secret", request.headers["X-Device-Secret"])
        }
        val api = MobileBackendAuthenticationClient(client) { 1_000L }

        api.revokeDevice(
            storedAuthentication = StoredAuthentication(
                backendCredential = BackendSessionCredential(
                    serverUrl = "https://example.test",
                    token = "backend-session",
                    expiresAtEpochMillis = 4_600_000L,
                ),
                accessLease = null,
            ),
            installationId = "installation",
            deviceSecret = "secret",
        )
    }

    @Test
    fun backendErrorPreservesStatusAndMessage() = runTest {
        val api = MobileBackendAuthenticationClient(
            clientResponding(
                body = """{"error":"authorization expired"}""",
                status = HttpStatusCode.Unauthorized,
            ),
        ) { 1_000L }

        try {
            api.startAuthorization(
                serverUrl = "https://example.test",
                installationId = "installation",
                deviceSecret = "secret",
                appCallbackUri = "io.ferventio.app://oauth/callback",
            )
            fail("Backend error must be surfaced")
        } catch (error: MobileBackendAuthenticationException) {
            assertEquals(401, error.statusCode)
            assertEquals("authorization expired", error.backendMessage)
        }
    }

    private fun assertStoredAuthentication(stored: StoredAuthentication) {
        assertEquals("https://example.test", stored.backendCredential.serverUrl)
        assertEquals("backend-session", stored.backendCredential.token)
        assertEquals(4_600_000L, stored.backendCredential.expiresAtEpochMillis)
        val lease = assertNotNull(stored.accessLease)
        assertEquals("access-token", lease.accessToken)
        assertEquals(1_300_000L, lease.leaseExpiresAtEpochMillis)
        assertEquals(8_200_000L, lease.twitchExpiresAtEpochMillis)
        assertEquals(4_600_000L, lease.backendSessionExpiresAtEpochMillis)
        assertEquals(setOf("chat:read", "chat:edit"), lease.session.scopes)
        assertEquals(7_200L, lease.session.expiresInSeconds)
        assertTrue(lease.twitchValidatedAtEpochMillis > 0L)
    }

    private fun clientResponding(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        verifyRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): HttpClient {
        val engine = MockEngine { request ->
            verifyRequest(request)
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        return HttpClient(engine) {
            expectSuccess = false
        }
    }
}
