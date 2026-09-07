package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
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
import kotlin.test.assertTrue

class WorkspaceMessageRulesSettingsUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conflictReappliesRuleOperationToFreshRemoteSnapshot() = runTest {
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(30L, backupPayload(emptyList()))),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val requestBody = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    if (putCount == 1) {
                        assertEquals(30L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        respond(
                            ByteReadChannel(
                                """{"snapshot":${snapshotBody(31L, backupPayload(listOf("android-rule")))}}""",
                            ),
                            HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals(31L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        val payload = requestBody.getValue("payload")
                        respond(
                            ByteReadChannel("""{"revision":32,"payload":$payload}"""),
                            HttpStatusCode.OK,
                        )
                    }
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })
        val iosRule = HighlightRule(
            id = "ios-rule",
            type = HighlightRuleType.WORD,
            pattern = "urgent",
        )

        val result = client.updateMessageRules(identity(), authentication()) { remote ->
            remote.copy(
                highlightRules = remote.highlightRules
                    .filterNot { it.id == iosRule.id } + iosRule,
            )
        }

        assertEquals(2, putCount)
        assertEquals(32L, result.revision)
        assertEquals(listOf("android-rule", "ios-rule"), result.messageRules.highlightRules.map { it.id })
        assertTrue(result.messageRules.highlightRules.any { it.pattern == "urgent" })
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(highlightIds: List<String>): String {
        val highlights = highlightIds.joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":"$id","type":"WORD","pattern":"$id","enabled":true,"caseSensitive":false,"colorArgb":4294953047,"playSound":false,"push":false,"addToMentions":true,"filteredSplit":false}"""
        }
        return """
            {
              "format":"ferventio-settings-backup",
              "formatVersion":2,
              "createdAt":"2026-08-20T00:00:00Z",
              "appVersion":"0.0.5",
              "contentHash":"fixture",
              "content":{
                "settings":{},
                "channels":{"logins":[],"favouriteChannelIds":[],"pinnedChannelIds":[],"recentChannelIds":[],"tabTitles":{}},
                "workspaces":null,
                "filters":{},
                "highlights":$highlights,
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
