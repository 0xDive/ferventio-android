package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceBootstrapCoordinatorTest {
    @Test
    fun syncedSettingsAndTwitchMetadataPopulateSharedWorkspace() = runTest {
        val settingsEngine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "revision": 9,
                      "payload": {
                        "content": {
                          "channels": {
                            "logins": ["beta", "alpha"],
                            "selectedLogin": "alpha",
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
        val twitchEngine = MockEngine {
            respond(
                content = ByteReadChannel(
                    """
                    {
                      "data": [
                        {"id":"1","login":"alpha","display_name":"Alpha live"}
                      ]
                    }
                    """.trimIndent(),
                ),
                status = HttpStatusCode.OK,
            )
        }
        val state = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(
                    ChatChannel(
                        id = "2",
                        login = "beta",
                        displayName = "Beta cached",
                    ),
                ),
            ),
        )
        val coordinator = WorkspaceBootstrapCoordinator(
            settings = WorkspaceSettingsSyncClient(
                HttpClient(settingsEngine) { expectSuccess = false },
            ),
            directory = TwitchChannelDirectoryClient(
                HttpClient(twitchEngine) { expectSuccess = false },
            ),
        )

        val outcome = coordinator.bootstrap(
            identity = identity(),
            authentication = authentication(),
            state = state,
        )

        assertTrue(outcome.remoteSettingsAvailable)
        assertEquals(9L, outcome.settingsRevision)
        assertEquals(2, outcome.channelCount)
        assertEquals(listOf("2", "1"), state.channelIds)
        assertEquals("1", state.selectedChannelId)
        assertEquals(listOf("2"), state.pinnedChannelIds)
        assertEquals("Alpha live", state.channels[1].displayName)
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
