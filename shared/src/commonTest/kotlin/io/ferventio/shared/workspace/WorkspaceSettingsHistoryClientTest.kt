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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkspaceSettingsHistoryClientTest {
    @Test
    fun fetchHistoryUsesAuthenticatedEndpointAndDecodesEntries() = runTest {
        var method: HttpMethod? = null
        var url: String? = null
        var authorization: String? = null
        var installationId: String? = null
        var deviceSecret: String? = null
        val engine = MockEngine { request ->
            method = request.method
            url = request.url.toString()
            authorization = request.headers[HttpHeaders.Authorization]
            installationId = request.headers["X-Installation-ID"]
            deviceSecret = request.headers["X-Device-Secret"]
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {
                          "revision": 12,
                          "updatedAt": "2026-09-13T17:00:00Z",
                          "updatedByInstallationId": "device-a",
                          "appVersion": "0.0.5",
                          "contentHash": "hash-a"
                        },
                        {
                          "revision": 11,
                          "updatedAt": "2026-09-12T17:00:00Z",
                          "updatedByInstallationId": "device-b",
                          "contentHash": "hash-b"
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = WorkspaceSettingsHistoryClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val history = client.fetchHistory(identity(), authentication())

        assertEquals(HttpMethod.Get, method)
        assertEquals("https://example.test/v1/sync/settings/history", url)
        assertEquals("Bearer backend-session", authorization)
        assertEquals("installation-id", installationId)
        assertEquals("s".repeat(32), deviceSecret)
        assertEquals(2, history.size)
        assertEquals(12L, history[0].revision)
        assertEquals("2026-09-13T17:00:00Z", history[0].updatedAt)
        assertEquals("device-a", history[0].updatedByInstallationId)
        assertEquals("0.0.5", history[0].appVersion)
        assertEquals("hash-a", history[0].contentHash)
        assertEquals(11L, history[1].revision)
        assertNull(history[1].appVersion)
    }

    @Test
    fun restoreRevisionPostsRevisionEndpointAndDecodesWorkspaceSnapshot() = runTest {
        var method: HttpMethod? = null
        var url: String? = null
        val engine = MockEngine { request ->
            method = request.method
            url = request.url.toString()
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "revision": 9,
                      "updatedAt": "2026-09-11T17:00:00Z",
                      "updatedByInstallationId": "device-a",
                      "contentHash": "hash-restored",
                      "payload": {
                        "content": {
                          "channels": {
                            "logins": ["alpha", "beta"],
                            "selectedLogin": "alpha",
                            "pinnedChannelIds": ["1"]
                          },
                          "preferences": {
                            "themeMode": "AMOLED"
                          }
                        }
                      }
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val client = WorkspaceSettingsHistoryClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val snapshot = client.restoreRevision(identity(), authentication(), revision = 9L)

        assertEquals(HttpMethod.Post, method)
        assertEquals("https://example.test/v1/sync/settings/restore/9", url)
        assertEquals(9L, snapshot.revision)
        assertEquals(listOf("alpha", "beta"), snapshot.channels.logins)
        assertEquals("alpha", snapshot.channels.selectedLogin)
        assertEquals(listOf("1"), snapshot.channels.pinnedChannelIds)
    }

    @Test
    fun restoreRevisionRejectsNonPositiveRevisionBeforeNetwork() = runTest {
        val engine = MockEngine { error("network must not be reached") }
        val client = WorkspaceSettingsHistoryClient(
            HttpClient(engine) { expectSuccess = false },
        )

        assertFailsWith<IllegalArgumentException> {
            client.restoreRevision(identity(), authentication(), revision = 0L)
        }
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
