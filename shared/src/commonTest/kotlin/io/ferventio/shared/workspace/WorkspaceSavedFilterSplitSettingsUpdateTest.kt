package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkspaceSavedFilterSplitSettingsUpdateTest {
    @Test
    fun conflictRetryRejectsSavedFilterDeletedOnAnotherDevice() = runTest {
        val initialPayload = backupPayload(includeFilter = true)
        val conflictPayload = backupPayload(includeFilter = false)
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(100L, initialPayload)),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    check(putCount == 1) { "A dangling saved-filter split must not be retried" }
                    respond(
                        ByteReadChannel(
                            """{"snapshot":{"revision":101,"payload":$conflictPayload}}""",
                        ),
                        HttpStatusCode.Conflict,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val failure = runCatching {
            client.addSavedFilterWorkspaceSplit(
                identity = identity(),
                authentication = authentication(),
                filterId = "filter-1",
                fallbackChannelId = "channel-1",
            )
        }.exceptionOrNull()

        assertIs<IllegalArgumentException>(failure)
        assertEquals("Saved message filter was not found", failure.message)
        assertEquals(1, putCount)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(includeFilter: Boolean): String {
        val filters = if (includeFilter) {
            """{"schemaVersion":1,"filters":[{"id":"filter-1","name":"Moderator messages","expression":"badge.mod == true"}]}"""
        } else {
            """{"schemaVersion":1,"filters":[]}"""
        }
        return """
            {
              "format":"ferventio-settings-backup",
              "formatVersion":2,
              "createdAt":"2026-08-22T00:00:00Z",
              "appVersion":"0.0.5",
              "contentHash":"fixture",
              "content":{
                "settings":{},
                "channels":{"logins":["alpha"],"selectedLogin":"alpha","favouriteChannelIds":[],"pinnedChannelIds":[],"recentChannelIds":[],"tabTitles":{}},
                "workspaces":{
                  "schemaVersion":2,
                  "activeWorkspaceId":"workspace-1",
                  "workspaces":[{
                    "id":"workspace-1",
                    "name":"Main",
                    "activeTabId":"tab-1",
                    "tabs":[{
                      "id":"tab-1",
                      "title":"Chat",
                      "activeSplitId":"split-1",
                      "primaryFraction":0.5,
                      "splits":[{
                        "type":"chat",
                        "id":"split-1",
                        "channelId":"channel-1",
                        "filterQuery":""
                      }]
                    }]
                  }]
                },
                "filters":$filters,
                "highlights":[],
                "ignoreRules":[],
                "commands":{},
                "favouriteEmotes":[]
              }
            }
        """.trimIndent()
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
                scopes = setOf("user:read:chat"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
