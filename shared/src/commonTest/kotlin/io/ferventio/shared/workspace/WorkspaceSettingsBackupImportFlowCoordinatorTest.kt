package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class WorkspaceSettingsBackupImportFlowCoordinatorTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val syncTime = Instant.parse("2026-08-30T18:30:00Z")

    @Test
    fun startCapturesLocalRollbackAppliesImportAndAdvancesSyncedRevision() = runTest {
        val remote = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        var getCount = 0
        var putCount = 0
        val backend = HttpClient(MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    getCount += 1
                    respond(
                        ByteReadChannel("""{"revision":5,"payload":$remote}"""),
                        HttpStatusCode.OK,
                    )
                }
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertEquals(5L, body.getValue("baseRevision").jsonPrimitive.long)
                    assertFalse(body.getValue("force").jsonPrimitive.boolean)
                    val uploaded = body.getValue("payload")
                    respond(
                        ByteReadChannel("""{"revision":6,"payload":$uploaded}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected backend request ${request.method}")
            }
        }) { expectSuccess = false }
        val storage = MemoryStorage()
        val transaction = transaction(backend, storage)
        val flow = WorkspaceSettingsBackupImportFlowCoordinator(
            transactions = transaction,
            localApply = WorkspaceSettingsBackupLocalApplyCoordinator(twitchDirectoryClient()),
        )
        val state = WorkspaceRuntimeStateHolder().apply { markLoadReady(4L) }
        val settings = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(themeMode = AppThemeMode.DARK), 4L)
        }
        val rules = SharedMessageRulesStateHolder()
        val filters = SharedSavedFiltersStateHolder()

        val result = flow.start(
            identity = identity(),
            authentication = authentication(),
            importedPayload = imported,
            currentAppVersion = "0.0.6",
            createdAt = syncTime,
            state = state,
            settingsState = settings,
            rulesState = rules,
            filtersState = filters,
        )
        val synced = assertIs<WorkspaceSettingsBackupImportSyncResult.Synced>(result.syncResult)
        val restored = transaction.restore()
        val rollback = WorkspaceSettingsBackupImportPreparation.prepare(
            requireNotNull(restored.preImportPayload),
        )

        assertEquals(2, getCount)
        assertEquals(1, putCount)
        assertEquals(6L, synced.revision)
        assertEquals(AppThemeMode.LIGHT, settings.preferences.themeMode)
        assertEquals(6L, settings.syncRevision)
        assertEquals(6L, state.settingsRevision)
        assertEquals(listOf("1", "2"), state.channelIds)
        assertEquals("2", state.selectedChannelId)
        assertEquals(AppThemeMode.DARK, rollback.preferences.themeMode)
        assertTrue(rollback.channels.logins.isEmpty())
        assertNull(restored.pendingImport)
        assertNull(restored.conflict)
    }

    @Test
    fun resumeReappliesPendingImportButDoesNotRetryDurableConflict() = runTest {
        val remote = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        var getCount = 0
        var putCount = 0
        val backend = HttpClient(MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> {
                    getCount += 1
                    respond(
                        ByteReadChannel("""{"revision":5,"payload":$remote}"""),
                        HttpStatusCode.OK,
                    )
                }
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertFalse(body.getValue("force").jsonPrimitive.boolean)
                    respond(
                        ByteReadChannel(
                            """{"error":"settings revision conflict","snapshot":{"revision":6,"payload":$remote}}""",
                        ),
                        HttpStatusCode.Conflict,
                    )
                }
                else -> error("Unexpected backend request ${request.method}")
            }
        }) { expectSuccess = false }
        val storage = MemoryStorage()
        val firstTransaction = transaction(backend, storage)
        val firstFlow = WorkspaceSettingsBackupImportFlowCoordinator(
            transactions = firstTransaction,
            localApply = WorkspaceSettingsBackupLocalApplyCoordinator(twitchDirectoryClient()),
        )
        val firstState = WorkspaceRuntimeStateHolder().apply { markLoadReady(4L) }
        val firstSettings = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(themeMode = AppThemeMode.DARK), 4L)
        }

        val initial = firstFlow.start(
            identity = identity(),
            authentication = authentication(),
            importedPayload = imported,
            currentAppVersion = "0.0.6",
            createdAt = syncTime,
            state = firstState,
            settingsState = firstSettings,
            rulesState = SharedMessageRulesStateHolder(),
            filtersState = SharedSavedFiltersStateHolder(),
        )
        assertIs<WorkspaceSettingsBackupImportSyncResult.Conflict>(initial.syncResult)
        assertEquals(2, getCount)
        assertEquals(1, putCount)

        val restartedTransaction = transaction(backend, storage)
        val restartedFlow = WorkspaceSettingsBackupImportFlowCoordinator(
            transactions = restartedTransaction,
            localApply = WorkspaceSettingsBackupLocalApplyCoordinator(twitchDirectoryClient()),
        )
        val restartedState = WorkspaceRuntimeStateHolder().apply { markLoadReady(6L) }
        val restartedSettings = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(themeMode = AppThemeMode.DARK), 6L)
        }
        val restartedRules = SharedMessageRulesStateHolder()
        val restartedFilters = SharedSavedFiltersStateHolder()

        val resumed = restartedFlow.resume(
            identity = identity(),
            authentication = authentication(),
            currentAppVersion = "0.0.6",
            createdAt = syncTime,
            state = restartedState,
            settingsState = restartedSettings,
            rulesState = restartedRules,
            filtersState = restartedFilters,
        )
        val conflict = assertIs<WorkspaceSettingsBackupImportSyncResult.Conflict>(resumed.syncResult)

        assertEquals(2, getCount)
        assertEquals(1, putCount)
        assertEquals(6L, conflict.serverRevision)
        assertEquals(AppThemeMode.LIGHT, restartedSettings.preferences.themeMode)
        assertEquals(listOf("1", "2"), restartedState.channelIds)
        assertEquals(6L, restartedTransaction.restore().conflict?.serverRevision)

        val serverResolution = restartedFlow.useServer(
            authentication = authentication(),
            state = restartedState,
            settingsState = restartedSettings,
            rulesState = restartedRules,
            filtersState = restartedFilters,
        )
        val settled = restartedTransaction.restore()

        assertEquals(6L, serverResolution.revision)
        assertEquals(AppThemeMode.DARK, restartedSettings.preferences.themeMode)
        assertEquals(6L, restartedSettings.syncRevision)
        assertEquals(6L, restartedState.settingsRevision)
        assertNull(settled.pendingImport)
        assertNull(settled.conflict)
        assertEquals(2, getCount)
        assertEquals(1, putCount)
    }

    @Test
    fun keepLocalReappliesPendingImportAfterRestartAndUsesExplicitForce() = runTest {
        val rollback = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val server = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        var putCount = 0
        val backend = HttpClient(MockEngine { request ->
            when (request.method) {
                HttpMethod.Get -> respond(
                    ByteReadChannel("""{"revision":6,"payload":$server}"""),
                    HttpStatusCode.OK,
                )
                HttpMethod.Put -> {
                    putCount += 1
                    val body = json.parseToJsonElement(
                        request.body.toByteArray().decodeToString(),
                    ).jsonObject
                    assertEquals(6L, body.getValue("baseRevision").jsonPrimitive.long)
                    assertTrue(body.getValue("force").jsonPrimitive.boolean)
                    val uploaded = body.getValue("payload")
                    respond(
                        ByteReadChannel("""{"revision":7,"payload":$uploaded}"""),
                        HttpStatusCode.OK,
                    )
                }
                else -> error("Unexpected backend request ${request.method}")
            }
        }) { expectSuccess = false }
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(preImportPayload = rollback, importedPayload = imported)
        journal.markConflict(serverRevision = 6L, serverPayload = server)
        val transaction = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(backend),
            uploads = WorkspaceSettingsBackupUploadClient(backend),
            journal = journal,
        )
        val flow = WorkspaceSettingsBackupImportFlowCoordinator(
            transactions = transaction,
            localApply = WorkspaceSettingsBackupLocalApplyCoordinator(twitchDirectoryClient()),
        )
        val state = WorkspaceRuntimeStateHolder().apply { markLoadReady(6L) }
        val settings = SharedAppSettingsStateHolder().apply {
            restore(SharedAppPreferences(themeMode = AppThemeMode.DARK), 6L)
        }

        val result = flow.keepLocal(
            identity = identity(),
            authentication = authentication(),
            currentAppVersion = "0.0.6",
            createdAt = syncTime,
            state = state,
            settingsState = settings,
            rulesState = SharedMessageRulesStateHolder(),
            filtersState = SharedSavedFiltersStateHolder(),
        )
        val synced = assertIs<WorkspaceSettingsBackupImportSyncResult.Synced>(result.syncResult)
        val settled = transaction.restore()

        assertEquals(1, putCount)
        assertEquals(7L, synced.revision)
        assertEquals(AppThemeMode.LIGHT, settings.preferences.themeMode)
        assertEquals(7L, settings.syncRevision)
        assertEquals(7L, state.settingsRevision)
        assertEquals(listOf("1", "2"), state.channelIds)
        assertNull(settled.pendingImport)
        assertNull(settled.conflict)
    }

    @Test
    fun networkCoordinatorRejectsForcedOverwriteWithoutDurableConflict() = runTest {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(
            preImportPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK"),
            importedPayload = workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )
        var requestCount = 0
        val backend = HttpClient(MockEngine { request ->
            requestCount += 1
            error("Unexpected request ${request.method}")
        }) { expectSuccess = false }
        val transaction = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(backend),
            uploads = WorkspaceSettingsBackupUploadClient(backend),
            journal = journal,
        )

        assertFailsWith<IllegalArgumentException> {
            transaction.keepLocal(
                identity = identity(),
                authentication = authentication(),
                currentAppVersion = "0.0.6",
                createdAt = syncTime,
            )
        }
        assertEquals(0, requestCount)
        assertTrue(transaction.restore().pendingImport != null)
        assertNull(transaction.restore().conflict)
    }

    @Test
    fun networkCoordinatorRejectsAutomaticRetryAfterDurableConflict() = runTest {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")
        val server = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        journal.begin(preImportPayload = server, importedPayload = imported)
        journal.markConflict(serverRevision = 6L, serverPayload = server)
        var requestCount = 0
        val backend = HttpClient(MockEngine { request ->
            requestCount += 1
            error("Unexpected request ${request.method}")
        }) { expectSuccess = false }
        val transaction = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(backend),
            uploads = WorkspaceSettingsBackupUploadClient(backend),
            journal = journal,
        )

        assertFailsWith<IllegalArgumentException> {
            transaction.syncPending(
                identity = identity(),
                authentication = authentication(),
                currentAppVersion = "0.0.6",
                createdAt = syncTime,
            )
        }
        assertEquals(0, requestCount)
        assertEquals(6L, transaction.restore().conflict?.serverRevision)
    }

    @Test
    fun networkCoordinatorRejectsStartingOverExistingPendingImport() = runTest {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(
            preImportPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK"),
            importedPayload = workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )
        var requestCount = 0
        val backend = HttpClient(MockEngine { request ->
            requestCount += 1
            error("Unexpected request ${request.method}")
        }) { expectSuccess = false }
        val transaction = WorkspaceSettingsBackupImportCoordinator(
            snapshots = WorkspaceSettingsSyncClient(backend),
            uploads = WorkspaceSettingsBackupUploadClient(backend),
            journal = journal,
        )

        assertFailsWith<IllegalArgumentException> {
            transaction.begin(
                identity = identity(),
                authentication = authentication(),
                importedPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK"),
            )
        }
        assertEquals(0, requestCount)
        assertEquals(AppThemeMode.LIGHT, transaction.restore().pendingImport?.preferences?.themeMode)
    }

    private fun transaction(
        backend: HttpClient,
        storage: MemoryStorage,
    ): WorkspaceSettingsBackupImportCoordinator = WorkspaceSettingsBackupImportCoordinator(
        snapshots = WorkspaceSettingsSyncClient(backend),
        uploads = WorkspaceSettingsBackupUploadClient(backend),
        journal = WorkspaceSettingsBackupImportJournal(storage),
    )

    private fun twitchDirectoryClient(): TwitchChannelDirectoryClient {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/helix/users" -> respond(
                    ByteReadChannel(
                        """{"data":[{"id":"1","login":"alpha","display_name":"Alpha"},{"id":"2","login":"beta","display_name":"Beta"}]}""",
                    ),
                    HttpStatusCode.OK,
                )
                "/helix/moderation/channels" -> respond(
                    ByteReadChannel("""{"data":[{"broadcaster_id":"2"}]}"""),
                    HttpStatusCode.OK,
                )
                else -> error("Unexpected Twitch request ${request.url}")
            }
        }
        return TwitchChannelDirectoryClient(HttpClient(engine) { expectSuccess = false })
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
