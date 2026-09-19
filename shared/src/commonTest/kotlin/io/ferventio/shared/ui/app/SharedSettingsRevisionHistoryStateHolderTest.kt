package io.ferventio.shared.ui.app

import io.ferventio.shared.workspace.WorkspaceSettingsHistoryEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SharedSettingsRevisionHistoryStateHolderTest {
    @Test
    fun loadedHistoryIsDeduplicatedAndSortedNewestFirst() {
        val holder = SharedSettingsRevisionHistoryStateHolder()

        holder.markLoading()
        holder.markLoaded(
            listOf(
                entry(2L),
                entry(5L),
                entry(2L, appVersion = "duplicate"),
                entry(3L),
            ),
        )

        assertEquals(SharedSettingsRevisionHistoryStatus.IDLE, holder.status)
        assertEquals(listOf(5L, 3L, 2L), holder.entries.map { it.revision })
        assertNull(holder.errorMessage)
        assertNull(holder.restoringRevision)
    }

    @Test
    fun restoreProgressAndFailureDoNotDiscardLoadedHistory() {
        val holder = SharedSettingsRevisionHistoryStateHolder()
        holder.markLoaded(listOf(entry(8L), entry(7L)))

        holder.markRestoring(7L)
        assertEquals(SharedSettingsRevisionHistoryStatus.RESTORING, holder.status)
        assertEquals(7L, holder.restoringRevision)

        holder.markFailed("  restore failed  ")

        assertEquals(SharedSettingsRevisionHistoryStatus.FAILED, holder.status)
        assertEquals(listOf(8L, 7L), holder.entries.map { it.revision })
        assertEquals("restore failed", holder.errorMessage)
        assertNull(holder.restoringRevision)
    }

    @Test
    fun successfulRestoreTracksRestoredRevision() {
        val holder = SharedSettingsRevisionHistoryStateHolder()
        holder.markRestoring(11L)

        holder.markRestored(12L)

        assertEquals(SharedSettingsRevisionHistoryStatus.IDLE, holder.status)
        assertEquals(12L, holder.lastRestoredRevision)
        assertNull(holder.restoringRevision)
        assertNull(holder.errorMessage)
    }

    @Test
    fun invalidRestoreRevisionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SharedSettingsRevisionHistoryStateHolder().markRestoring(0L)
        }
    }

    private fun entry(
        revision: Long,
        appVersion: String? = "1.0",
    ) = WorkspaceSettingsHistoryEntry(
        revision = revision,
        updatedAt = "2026-09-14T00:00:00Z",
        updatedByInstallationId = "device-$revision",
        appVersion = appVersion,
        contentHash = "hash-$revision",
    )
}
