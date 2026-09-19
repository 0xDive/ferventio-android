package io.ferventio.shared.workspace

import io.ferventio.app.domain.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceSettingsBackupImportPreparationTest {
    @Test
    fun preparesValidatedBackupForSharedRuntimeWithoutMutatingIt() {
        val prepared = WorkspaceSettingsBackupImportPreparation.prepare(
            workspaceSettingsBackupTestPayload(),
        )

        assertEquals(2, prepared.preview.formatVersion)
        assertEquals("2026-08-18T00:00:00Z", prepared.preview.createdAt)
        assertEquals("0.0.5", prepared.preview.appVersion)
        assertEquals(2, prepared.preview.channelCount)
        assertEquals(1, prepared.preview.workspaceCount)
        assertEquals(listOf("alpha", "beta"), prepared.channels.logins)
        assertEquals("beta", prepared.channels.selectedLogin)
        assertEquals(AppThemeMode.LIGHT, prepared.preferences.themeMode)
        assertEquals(125, prepared.preferences.fontScalePercent)
        assertTrue(prepared.workspaceLayout.workspaces.isNotEmpty())
    }

    @Test
    fun versionOneProjectionUsesMigratedRepeatCollapseValue() {
        val prepared = WorkspaceSettingsBackupImportPreparation.prepare(
            workspaceSettingsBackupTestPayload(
                formatVersion = 1,
                repeatCollapseEnabled = false,
            ),
        )

        assertEquals(1, prepared.preview.formatVersion)
        assertTrue(prepared.preferences.repeatCollapseEnabled)
    }
}
