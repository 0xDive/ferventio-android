package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FerventioSettingsBackupActionsTest {
    @Test
    fun fileTransferAvailabilityRequiresPlatformActionWhileIdle() {
        assertFalse(FerventioSettingsBackupActions().fileTransferAvailable)
        assertTrue(FerventioSettingsBackupActions(onExport = {}).fileTransferAvailable)
        assertTrue(FerventioSettingsBackupActions(onImport = {}).fileTransferAvailable)
    }

    @Test
    fun revisionHistoryAvailabilityRequiresRefreshAndRestoreActions() {
        assertFalse(FerventioSettingsBackupActions().revisionHistoryAvailable)
        assertFalse(
            FerventioSettingsBackupActions(onRefreshRevisionHistory = {}).revisionHistoryAvailable,
        )
        assertFalse(
            FerventioSettingsBackupActions(onRestoreRevision = {}).revisionHistoryAvailable,
        )
        assertTrue(
            FerventioSettingsBackupActions(
                onRefreshRevisionHistory = {},
                onRestoreRevision = {},
            ).revisionHistoryAvailable,
        )
    }

    @Test
    fun activeFileTransactionKeepsTransferSupportVisibleWhileActionsAreLocked() {
        val state = SharedSettingsBackupStateHolder()
        val actions = FerventioSettingsBackupActions(state = state)

        state.markExporting()
        assertTrue(actions.fileTransferAvailable)

        state.markImporting()
        assertTrue(actions.fileTransferAvailable)

        state.markConflict(revision = 4L)
        assertTrue(actions.fileTransferAvailable)

        state.markResolving()
        assertTrue(actions.fileTransferAvailable)

        state.markIdle()
        assertFalse(actions.fileTransferAvailable)
    }

    @Test
    fun conflictResolutionAvailabilityRequiresBothChoices() {
        assertFalse(FerventioSettingsBackupActions(onKeepLocal = {}).conflictResolutionAvailable)
        assertFalse(FerventioSettingsBackupActions(onUseServer = {}).conflictResolutionAvailable)
        assertTrue(
            FerventioSettingsBackupActions(
                onKeepLocal = {},
                onUseServer = {},
            ).conflictResolutionAvailable,
        )
    }

    @Test
    fun conflictStateNormalizesRevisionAndUnresolvedLogins() {
        val state = SharedSettingsBackupStateHolder()

        state.markConflict(
            revision = 8L,
            unresolvedLogins = listOf(" #Example ", "example", "Second"),
        )

        assertEquals(SharedSettingsBackupStatus.CONFLICT, state.status)
        assertEquals(8L, state.conflictRevision)
        assertEquals(listOf("example", "second"), state.unresolvedLogins)
        assertNull(state.errorMessage)
    }

    @Test
    fun startingAnotherOperationClearsOldFeedback() {
        val state = SharedSettingsBackupStateHolder()
        state.markFailed("boom")

        state.markImporting()

        assertEquals(SharedSettingsBackupStatus.IMPORTING, state.status)
        assertNull(state.errorMessage)
        assertNull(state.conflictRevision)
        assertTrue(state.unresolvedLogins.isEmpty())
    }
}
