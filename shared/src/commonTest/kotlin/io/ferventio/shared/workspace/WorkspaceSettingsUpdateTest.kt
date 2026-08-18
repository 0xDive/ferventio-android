package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.settings.SharedAppPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class WorkspaceSettingsUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun updatePreferencesUsesCurrentRevisionAndReturnsSavedSnapshot() = runTest {
        var requestCount = 0
        var putBody: String? = null
        val engine = MockEngine { request ->
            requestCount += 1
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(12L, backupPayload(listOf("alpha", "beta")))),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putBody = request.body.toByteArray().decodeToString()
                    val payload = json.parseToJsonElement(requireNotNull(putBody))
                        .jsonObject.getValue("payload")
                    respond(
                        ByteReadChannel("""{"revision":13,"payload":$payload}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected request: ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })

        val result = client.updatePreferences(
            identity = identity(),
            authentication = authentication(),
            preferences = SharedAppPreferences().copy(
                themeMode = AppThemeMode.AMOLED,
                betterTtvEnabled = false,
            ),
        )

        val body = json.parseToJsonElement(requireNotNull(putBody)).jsonObject
        assertEquals(12L, body.getValue("baseRevision").jsonPrimitive.long)
        assertFalse(body.getValue("force").jsonPrimitive.boolean)
        assertEquals(13L, result.revision)
        assertEquals(AppThemeMode.AMOLED, result.preferences.themeMode)
        assertFalse(result.preferences.betterTtvEnabled)
        assertEquals(listOf("alpha", "beta"), result.channels.logins)
        assertEquals(2, requestCount)
    }

    @Test
    fun conflictRebasesPreferenceChangeOntoNewestServerPayloadOnce() = runTest {
        var putCount = 0
        val basePayload = backupPayload(listOf("alpha"))
        val conflictPayload = backupPayload(listOf("alpha", "gamma"))
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(20L, basePayload)),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
                    if (putCount == 1) {
                        assertEquals(20L, body.getValue("baseRevision").jsonPrimitive.long)
                        respond(
                            ByteReadChannel(
                                """{"snapshot":{"revision":21,"payload":$conflictPayload}}""",
                            ),
                            HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals(21L, body.getValue("baseRevision").jsonPrimitive.long)
                        val payload = body.getValue("payload")
                        respond(
                            ByteReadChannel("""{"revision":22,"payload":$payload}"""),
                            HttpStatusCode.OK,
                        )
                    }
                }
                else -> error("Unexpected request: ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })

        val result = client.updatePreferences(
            identity = identity(),
            authentication = authentication(),
            preferences = SharedAppPreferences().copy(fontScalePercent = 125),
        )

        assertEquals(22L, result.revision)
        assertEquals(125, result.preferences.fontScalePercent)
        assertEquals(listOf("alpha", "gamma"), result.channels.logins)
        assertEquals(2, putCount)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(logins: List<String>): String {
        val loginsJson = logins.joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
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
                  "selectedLogin":${logins.firstOrNull()?.let { "\"$it\"" } ?: "null"},
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
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
