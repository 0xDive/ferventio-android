package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

class WorkspaceSettingsBackupImportCoordinatorTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val syncTime = Instant.parse("2026-08-30T12:00:00Z")

    @Test
    fun beginCapturesRemoteRollbackBeforePersistingPendingImport() = runTest {
        val rollback = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            respond(
                ByteReadChannel("""{"revision":5,"payload":$rollback}"""),
                HttpStatusCode.OK,
            )
        }
        val client = HttpClient(engine) { expectSuccess = false }
        val storage = MemoryStorage()
        val coordinator = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(client),
            uploads = WorkspaceSettingsBackupUploadClient(client),
            journal = WorkspaceSettingsBackupImportJournal(storage),
        )

        val started = coordinator.begin(
            identity = identity(),
            authentication = authentication(),
            importedPayload = imported,
        )
        val restored = coordinator.restore()

        assertEquals(AppThemeMode.DARK, WorkspaceSettingsBackupImportPreparation.prepare(
            requireNotNull(started.preImportPayload),
        ).preferences.themeMode)
        assertEquals(AppThemeMode.LIGHT, started.pendingImport?.preferences?.themeMode)
        assertEquals(AppThemeMode.LIGHT, restored.pendingImport?.preferences?.themeMode)
        assertNull(restored.conflict)
    }

    @Test
    fun syncPendingPersistsConflictWithoutRetryingOrForcing() = runTest {
        val rollback = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val server = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel("""{"revision":7,"payload":$server}"""),
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
                            """{"error":"settings revision conflict","snapshot":{"revision":8,"payload":$server}}""",
                        ),
                        HttpStatusCode.Conflict,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = HttpClient(engine) { expectSuccess = false }
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(preImportPayload = rollback, importedPayload = imported)
        val coordinator = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(client),
            uploads = WorkspaceSettingsBackupUploadClient(client),
            journal = journal,
        )

        val result = assertIs<WorkspaceSettingsBackupImportSyncResult.Conflict>(
            coordinator.syncPending(
                identity = identity(),
                authentication = authentication(),
                currentAppVersion = "0.0.6",
                createdAt = syncTime,
            ),
        )
        val restored = coordinator.restore()

        assertEquals(1, putCount)
        assertEquals(8L, result.serverRevision)
        assertEquals(AppThemeMode.DARK, result.serverImport.preferences.themeMode)
        assertEquals(AppThemeMode.LIGHT, restored.pendingImport?.preferences?.themeMode)
        assertEquals(8L, restored.conflict?.serverRevision)
        assertEquals(AppThemeMode.DARK, restored.conflict?.serverImport?.preferences?.themeMode)
    }

    @Test
    fun keepLocalUsesExplicitForceAndSettlesDurableConflict() = runTest {
        val rollback = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val server = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel("""{"revision":8,"payload":$server}"""),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertEquals(8L, body.getValue("baseRevision").jsonPrimitive.long)
                    assertTrue(body.getValue("force").jsonPrimitive.boolean)
                    val uploaded = body.getValue("payload")
                    respond(
                        ByteReadChannel("""{"revision":9,"payload":$uploaded}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected request ${request.method}")
            }
        }
        val client = HttpClient(engine) { expectSuccess = false }
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(preImportPayload = rollback, importedPayload = imported)
        journal.markConflict(serverRevision = 8L, serverPayload = server)
        val coordinator = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(client),
            uploads = WorkspaceSettingsBackupUploadClient(client),
            journal = journal,
        )

        val result = coordinator.keepLocal(
            identity = identity(),
            authentication = authentication(),
            currentAppVersion = "0.0.6",
            createdAt = syncTime,
        )
        val restored = coordinator.restore()

        assertEquals(1, putCount)
        assertEquals(9L, result.revision)
        assertNull(restored.pendingImport)
        assertNull(restored.conflict)
        assertEquals(AppThemeMode.DARK, WorkspaceSettingsBackupImportPreparation.prepare(
            requireNotNull(restored.preImportPayload),
        ).preferences.themeMode)
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

    private class MemoryStorage : WorkspaceSettingsBackupImportJournalStorage {
        private var value: String? = null

        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
        override fun clear() {
            value = null
        }
    }
}
