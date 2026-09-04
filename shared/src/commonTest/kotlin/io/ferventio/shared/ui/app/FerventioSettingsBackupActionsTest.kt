package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FerventioSettingsBackupActionsTest {
    @Test
    fun fileTransferAvailabilityRequiresAtLeastOnePlatformAction() {
        assertFalse(FerventioSettingsBackupActions().fileTransferAvailable)
        assertTrue(FerventioSettingsBackupActions(onExport = {}).fileTransferAvailable)
        assertTrue(FerventioSettingsBackupActions(onImport = {}).fileTransferAvailable)
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
