package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
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
}
