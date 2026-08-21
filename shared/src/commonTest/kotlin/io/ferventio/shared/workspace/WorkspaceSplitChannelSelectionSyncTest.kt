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
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceSplitChannelSelectionSyncTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun splitChannelAndLegacySelectionArePersistedTogether() = runTest {
        var persistedPayload: String? = null
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(90L, backupPayload())),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    persistedPayload = body.getValue("payload").toString()
                    respond(
                        ByteReadChannel("""{"revision":91,"payload":${body.getValue("payload")}}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val state = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(
                    ChatChannel("channel-1", "alpha", "Alpha"),
                    ChatChannel("channel-2", "beta", "Beta"),
                ),
                selectedChannelId = "channel-1",
                workspaceLayout = SharedWorkspaceLayoutPayloadCodec.parse(
                    backupPayload(),
                    fallbackChannelId = "channel-1",
                ),
            ),
        )
        state.markLoadReady(90L)
        val coordinator = WorkspaceLayoutMutationCoordinator(
            WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false }),
        )

        coordinator.setSplitChannel(
            identity = identity(),
            authentication = authentication(),
            state = state,
            splitId = "split-1",
            channelId = "channel-2",
        )

        val payload = requireNotNull(persistedPayload)
        assertEquals("beta", WorkspaceSettingsPayloadParser.parse(payload).selectedLogin)
        assertEquals(
            "channel-2",
            SharedWorkspaceLayoutPayloadCodec.parse(payload).activeTab?.activeSplit?.channelId,
        )
        assertEquals("channel-2", state.selectedChannelId)
        assertEquals(91L, state.settingsRevision)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(): String = """
        {
          "format":"ferventio-settings-backup",
          "formatVersion":2,
          "createdAt":"2026-08-21T00:00:00Z",
          "appVersion":"0.0.5",
          "contentHash":"fixture",
          "content":{
            "settings":{},
            "channels":{
              "logins":["alpha","beta"],
              "selectedLogin":"alpha",
              "favouriteChannelIds":[],
              "pinnedChannelIds":[],
              "recentChannelIds":[],
              "tabTitles":{}
            },
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
            "filters":{"schemaVersion":1,"filters":[]},
            "highlights":[],
            "ignoreRules":[],
            "commands":{},
            "favouriteEmotes":[]
          }
        }
    """.trimIndent()

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
