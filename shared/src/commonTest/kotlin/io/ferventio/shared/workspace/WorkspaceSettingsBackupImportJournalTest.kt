package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WorkspaceSettingsBackupImportJournalTest {
    @Test
    fun beginAndLoadRoundTripRollbackAndPendingImportAtomically() {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        val preImport = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        val imported = workspaceSettingsBackupTestPayload(themeMode = "LIGHT")

        val started = journal.begin(
            preImportPayload = preImport,
            importedPayload = imported,
        )
        val persistedRecord = assertNotNull(storage.value)
        val loaded = journal.load()

        assertEquals(preImport, started.preImportPayload)
        assertEquals(AppThemeMode.LIGHT, started.pendingImport?.preferences?.themeMode)
        assertEquals(preImport, loaded.preImportPayload)
        assertEquals(AppThemeMode.LIGHT, loaded.pendingImport?.preferences?.themeMode)
        assertEquals(1, storage.writeCount)
        assertEquals(persistedRecord, storage.value)
    }

    @Test
    fun invalidImportNeverOverwritesExistingJournal() {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(
            preImportPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK"),
            importedPayload = workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )
        val previous = assertNotNull(storage.value)
        val previousWriteCount = storage.writeCount

        assertFailsWith<IllegalArgumentException> {
            journal.begin(
                preImportPayload = workspaceSettingsBackupTestPayload(themeMode = "DARK"),
                importedPayload = "{\"format\":\"not-ferventio\"}",
            )
        }

        assertEquals(previous, storage.value)
        assertEquals(previousWriteCount, storage.writeCount)
    }

    @Test
    fun settledImportDropsPendingPayloadButKeepsRollbackSource() {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        val preImport = workspaceSettingsBackupTestPayload(themeMode = "DARK")
        journal.begin(
            preImportPayload = preImport,
            importedPayload = workspaceSettingsBackupTestPayload(themeMode = "LIGHT"),
        )

        val settled = journal.markSettled()
        val reloaded = journal.load()

        assertEquals(preImport, settled.preImportPayload)
        assertNull(settled.pendingImport)
        assertEquals(preImport, reloaded.preImportPayload)
        assertNull(reloaded.pendingImport)
    }

    @Test
    fun settledImportWithoutRollbackSourceClearsJournal() {
        val storage = MemoryStorage()
        val journal = WorkspaceSettingsBackupImportJournal(storage)
        journal.begin(
            preImportPayload = null,
            importedPayload = workspaceSettingsBackupTestPayload(),
        )

        journal.markSettled()

        assertNull(storage.value)
        assertEquals(1, storage.clearCount)
    }

    private class MemoryStorage : WorkspaceSettingsBackupImportJournalStorage {
        var value: String? = null
        var writeCount: Int = 0
        var clearCount: Int = 0

        override fun read(): String? = value

        override fun write(value: String) {
            this.value = value
            writeCount += 1
        }

        override fun clear() {
            value = null
            clearCount += 1
        }
    }
}
