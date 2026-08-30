package io.ferventio.shared.workspace

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkspaceSettingsBackupUploadClientTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val uploadTime = Instant.parse("2026-08-30T10:00:00Z")

    @Test
    fun createsFirstSnapshotWithCurrentEnvelopeBaseRevisionZeroAndNoForce() = runTest {
        var putCount = 0
        val importedPayload = workspaceSettingsBackupTestPayload(
            formatVersion = 1,
            repeatCollapseEnabled = false,
        )
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(ByteReadChannel(""), HttpStatusCode.NoContent)
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertEquals(0L, body.getValue("baseRevision").jsonPrimitive.long)
                    assertFalse(body.getValue("force").jsonPrimitive.boolean)
                    assertEquals("Bearer backend-session", request.headers[HttpHeaders.Authorization])
                    assertEquals("installation-id", request.headers["X-Installation-ID"])
                    assertEquals("s".repeat(32), request.headers["X-Device-Secret"])
                    val uploaded = body.getValue("payload").toString()
                    val decoded = SharedSettingsBackupCodec.decode(uploaded)
                    assertEquals(2, decoded.document.formatVersion)
                    assertEquals("0.0.6", decoded.document.appVersion)
                    assertEquals(uploadTime.toString(), decoded.document.createdAt)
                    assertTrue(decoded.document.content.settings.repeatCollapseEnabled)
                    respond(
                        ByteReadChannel("""{"revision":1,"payload":${body.getValue("payload")}}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsBackupUploadClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val result = client.upload(
            identity = identity(),
            authentication = authentication(),
            importedPayload = importedPayload,
            currentAppVersion = "0.0.6",
            createdAt = uploadTime,
        )

        assertEquals(1, putCount)
        assertEquals(1L, assertIs<WorkspaceSettingsBackupUploadResult.Success>(result).revision)
    }

    @Test
    fun returnsConflictWithoutRetryingOrForcingImportedBackup() = runTest {
        var putCount = 0
        val localPayload = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val serverPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel("""{"revision":7,"payload":$serverPayload}"""),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertEquals(7L, body.getValue("baseRevision").jsonPrimitive.long)
                    assertFalse(body.getValue("force").jsonPrimitive.boolean)
                    respond(
                        ByteReadChannel(
                            """{"error":"settings revision conflict","snapshot":{"revision":8,"payload":$serverPayload}}""",
                        ),
                        HttpStatusCode.Conflict,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = WorkspaceSettingsBackupUploadClient(
            HttpClient(engine) { expectSuccess = false },
        )

        val result = assertIs<WorkspaceSettingsBackupUploadResult.Conflict>(
            client.upload(
                identity = identity(),
                authentication = authentication(),
                importedPayload = localPayload,
                currentAppVersion = "0.0.6",
                createdAt = uploadTime,
            ),
        )

        assertEquals(1, putCount)
        assertEquals(8L, result.serverRevision)
        assertEquals(
            "DARK",
            WorkspaceSettingsBackupImportPreparation.prepare(result.serverPayload).preferences.themeMode.name,
        )
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
