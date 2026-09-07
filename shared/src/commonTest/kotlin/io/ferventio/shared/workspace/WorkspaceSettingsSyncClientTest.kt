package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceSettingsSyncClientTest {
    @Test
    fun fetchesWorkspaceProjectionWithDeviceAuthenticationHeaders() = runTest {
        var authorization: String? = null
        var installationId: String? = null
        var deviceSecret: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            installationId = request.headers["X-Installation-ID"]
            deviceSecret = request.headers["X-Device-Secret"]
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "revision": 12,
                      "payload": {
                        "content": {
                          "channels": {
                            "logins": ["alpha", "beta"],
                            "selectedLogin": "beta",
                            "pinnedChannelIds": ["2"]
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = WorkspaceSettingsSyncClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val snapshot = client.fetch(identity(), authentication())

        assertEquals(12L, snapshot?.revision)
        assertEquals(listOf("alpha", "beta"), snapshot?.channels?.logins)
        assertEquals("beta", snapshot?.channels?.selectedLogin)
        assertEquals(listOf("2"), snapshot?.channels?.pinnedChannelIds)
        assertEquals("Bearer backend-session", authorization)
        assertEquals("installation-id", installationId)
        assertEquals("s".repeat(32), deviceSecret)
    }

    @Test
    fun noRemoteSettingsReturnsNull() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.toString().endsWith("/v1/sync/settings"))
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.NoContent,
            )
        }
        val client = WorkspaceSettingsSyncClient(
            HttpClient(engine) { expectSuccess = false },
        )

        assertNull(client.fetch(identity(), authentication()))
    }

    private fun identity() = MobileDeviceIdentity(
        installationId = "installation-id",
        deviceSecret = "s".repeat(32),
    )

    private fun authentication() = StoredAuthentication(
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
                clientId = "client-id",
                userId = "viewer-id",
                login = "viewer",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
