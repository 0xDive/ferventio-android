package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceChannelSettingsUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conflictReappliesChannelOperationToFreshRemoteSnapshot() = runTest {
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(20L, backupPayload(listOf("alpha")))),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val requestBody = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    if (putCount == 1) {
                        assertEquals(20L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        respond(
                            ByteReadChannel(
                                """{"snapshot":${snapshotBody(21L, backupPayload(listOf("alpha", "gamma")))}}""",
                            ),
                            HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals(21L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        val payload = requestBody.getValue("payload")
                        respond(
                            ByteReadChannel("""{"revision":22,"payload":$payload}"""),
                            HttpStatusCode.OK,
                        )
                    }
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })

        val result = client.updateChannels(identity(), authentication()) { remote ->
            remote.copy(
                logins = if ("beta" in remote.logins) remote.logins else remote.logins + "beta",
                selectedLogin = "beta",
            )
        }

        assertEquals(2, putCount)
        assertEquals(22L, result.revision)
        assertEquals(listOf("alpha", "gamma", "beta"), result.channels.logins)
        assertEquals("beta", result.channels.selectedLogin)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(logins: List<String>): String {
        val loginsJson = logins.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
        val selected = logins.firstOrNull()?.let { "\"$it\"" } ?: "null"
        return """
            {
              "format":"ferventio-settings-backup",
              "formatVersion":2,
              "createdAt":"2026-08-18T00:00:00Z",
              "appVersion":"0.0.5",
              "contentHash":"fixture",
              "content":{
                "settings":{
                  "appLanguage":"RUSSIAN",
                  "themeMode":"DARK",
                  "fontScalePercent":100,
                  "messageDensity":"NORMAL",
                  "showAvatars":false,
                  "showBadges":true,
                  "showTimestamps":true,
                  "nameStyle":"DISPLAY_NAME",
                  "wrapMessageLines":true,
                  "showDeletedMessageContent":false,
                  "showSystemMessages":true,
                  "mentionColorArgb":4294953047,
                  "autoScrollEnabled":true,
                  "repeatCollapseEnabled":true,
                  "animateEmotes":true,
                  "emoteScalePercent":100,
                  "betterTtvEnabled":true,
                  "frankerFaceZEnabled":true,
                  "sevenTvEnabled":true,
                  "sendOnEnter":true,
                  "showComposerEmoteImages":true,
                  "replyNotificationsEnabled":true,
                  "autoModNotificationsEnabled":true,
                  "recentMessagesEnabled":false,
                  "localHistoryEnabled":true,
                  "localHistoryLimit":500,
                  "localHistoryRetentionDays":7,
                  "localHistoryMaxSizeMb":0,
                  "userCardTimeoutPresetsSeconds":[10,60,600,3600,86400],
                  "userCardShowBanAction":true,
                  "userCardModerationActionOrder":["timeout:10","timeout:60","timeout:600","timeout:3600","timeout:86400","warn","ban","unban"]
                },
                "channels":{
                  "logins":$loginsJson,
                  "selectedLogin":$selected,
                  "favouriteChannelIds":[],
                  "pinnedChannelIds":[],
                  "recentChannelIds":[],
                  "tabTitles":{}
                },
                "workspaces":null,
                "filters":{},
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
