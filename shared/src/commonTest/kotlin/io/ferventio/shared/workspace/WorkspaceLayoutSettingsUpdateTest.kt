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

class WorkspaceLayoutSettingsUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun conflictReappliesLayoutMutationToFreshRemoteSnapshot() = runTest {
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(70L, backupPayload(listOf("base")))),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val requestBody = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    if (putCount == 1) {
                        assertEquals(70L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        respond(
                            ByteReadChannel(
                                """{"snapshot":${snapshotBody(71L, backupPayload(listOf("base", "android-extra")))}}""",
                            ),
                            HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals(71L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        val payload = requestBody.getValue("payload")
                        respond(
                            ByteReadChannel("""{"revision":72,"payload":$payload}"""),
                            HttpStatusCode.OK,
                        )
                    }
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })

        val result = client.updateWorkspaceLayout(identity(), authentication()) { remote ->
            remote.copy(
                workspaces = remote.workspaces.map { workspace ->
                    if (workspace.id == "base") workspace.copy(name = "iOS") else workspace
                },
            )
        }
        val layout = SharedWorkspaceLayoutPayloadCodec.parse(result.payload)

        assertEquals(2, putCount)
        assertEquals(72L, result.revision)
        assertEquals(listOf("base", "android-extra"), layout.workspaces.map { it.id })
        assertEquals("iOS", layout.workspaces.first { it.id == "base" }.name)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(workspaceIds: List<String>): String {
        val workspaces = workspaceIds.joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":"$id","name":"$id","activeTabId":"","tabs":[]}"""
        }
        return """
            {
              "format":"ferventio-settings-backup",
              "formatVersion":2,
              "createdAt":"2026-08-21T00:00:00Z",
              "appVersion":"0.0.5",
              "contentHash":"fixture",
              "content":{
                "settings":{},
                "channels":{"logins":[],"favouriteChannelIds":[],"pinnedChannelIds":[],"recentChannelIds":[],"tabTitles":{}},
                "workspaces":{"schemaVersion":2,"activeWorkspaceId":"base","workspaces":$workspaces},
                "filters":{"schemaVersion":1,"filters":[]},
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
