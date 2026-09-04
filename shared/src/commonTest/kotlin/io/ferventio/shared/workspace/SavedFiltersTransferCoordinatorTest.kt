package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersTransferCodec
import io.ferventio.shared.settings.SharedSettingsSaveStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class SavedFiltersTransferCoordinatorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun importRebasesMergeAfterConflictAndUpdatesState() = runTest {
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel(snapshotBody(50L, backupPayload(emptyList()))),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val requestBody = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    if (putCount == 1) {
                        assertEquals(50L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        respond(
                            ByteReadChannel(
                                """{"snapshot":${snapshotBody(51L, backupPayload(listOf("android-filter")))}}""",
                            ),
                            HttpStatusCode.Conflict,
                        )
                    } else {
                        assertEquals(51L, requestBody.getValue("baseRevision").jsonPrimitive.long)
                        val payload = requestBody.getValue("payload")
                        respond(
                            ByteReadChannel("""{"revision":52,"payload":$payload}"""),
                            HttpStatusCode.OK,
                        )
                    }
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val sync = WorkspaceSettingsSyncClient(HttpClient(engine) { expectSuccess = false })
        val coordinator = SavedFiltersTransferCoordinator(sync)
        val state = SharedSavedFiltersStateHolder()
        val raw = SharedSavedFiltersTransferCodec.export(
            listOf(
                SavedMessageFilter(
                    id = "ios-filter",
                    name = "iOS filter",
                    expression = "message.content contains \"ios\"",
                ),
            ),
        )

        val result = coordinator.importFilters(identity(), authentication(), raw, state)

        assertEquals(2, putCount)
        assertEquals(52L, result.revision)
        assertEquals(
            listOf("android-filter", "ios-filter"),
            result.savedFilters.filters.map { it.id },
        )
        assertEquals(result.savedFilters.filters, state.filters)
        assertEquals(SharedSettingsSaveStatus.IDLE, state.saveStatus)
    }

    private fun snapshotBody(revision: Long, payload: String): String =
        """{"revision":$revision,"payload":$payload}"""

    private fun backupPayload(filterIds: List<String>): String {
        val filters = filterIds.joinToString(prefix = "[", postfix = "]") { id ->
            """{"id":"$id","name":"$id","expression":"message.content contains \"$id\""}"""
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
                "workspaces":null,
                "filters":{"schemaVersion":1,"filters":$filters},
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
