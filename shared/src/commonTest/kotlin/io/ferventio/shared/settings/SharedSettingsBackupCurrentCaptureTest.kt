package io.ferventio.shared.settings

import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.shared.workspace.workspaceSettingsBackupTestPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class SharedSettingsBackupCurrentCaptureTest {
    private val captureTime = Instant.parse("2026-08-30T18:00:00Z")

    @Test
    fun currentCaptureOverridesMutableStateAndPreservesOpaqueBaseSections() {
        val original = SharedSettingsBackupCodec.decode(workspaceSettingsBackupTestPayload()).document
        val baseContent = original.content.copy(
            channels = original.content.channels.copy(
                favouriteChannelIds = listOf("favorite-id"),
                recentChannelIds = listOf("recent-id"),
            ),
            favouriteEmotes = listOf("7tv:emote-id"),
        )
        val basePayload = SharedSettingsBackupCodec.encodeForTesting(
            original.copy(
                contentHash = SharedSettingsBackupCodec.contentHashForTesting(baseContent),
                content = baseContent,
            ),
        )
        val preferences = SharedAppPreferences(themeMode = AppThemeMode.DARK)

        val captured = SharedSettingsBackupCodec.captureCurrent(
            basePayload = basePayload,
            preferences = preferences,
            channelLogins = listOf("Gamma"),
            selectedChannelLogin = "gamma",
            pinnedChannelIds = listOf("3"),
            channelTabTitles = mapOf("3" to "Gamma tab"),
            workspaceLayout = WorkspaceLayout.default(),
            messageRules = SharedMessageRulesSnapshot(),
            savedFilters = SharedSavedFiltersSnapshot(),
            currentAppVersion = "0.0.6",
            createdAt = captureTime,
        )

        val decoded = SharedSettingsBackupCodec.decode(captured).document
        assertEquals(2, decoded.formatVersion)
        assertEquals("0.0.6", decoded.appVersion)
        assertEquals(captureTime.toString(), decoded.createdAt)
        assertEquals(AppThemeMode.DARK.name, decoded.content.settings.themeMode)
        assertEquals(listOf("gamma"), decoded.content.channels.logins)
        assertEquals("gamma", decoded.content.channels.selectedLogin)
        assertEquals(listOf("3"), decoded.content.channels.pinnedChannelIds)
        assertEquals(mapOf("3" to "Gamma tab"), decoded.content.channels.tabTitles)
        assertEquals(listOf("favorite-id"), decoded.content.channels.favouriteChannelIds)
        assertEquals(listOf("recent-id"), decoded.content.channels.recentChannelIds)
        assertEquals(listOf("7tv:emote-id"), decoded.content.favouriteEmotes)
        assertEquals(0, SharedMessageRulesPayloadCodec.parse(captured).highlightRules.size)
        assertEquals(0, SharedSavedFiltersPayloadCodec.parse(captured).filters.size)
        assertTrue(decoded.content.commands.toString() == original.content.commands.toString())
    }

    @Test
    fun currentCaptureWithoutServerBaseDoesNotBorrowOpaqueImportData() {
        val captured = SharedSettingsBackupCodec.captureCurrent(
            basePayload = null,
            preferences = SharedAppPreferences(),
            channelLogins = emptyList(),
            selectedChannelLogin = null,
            pinnedChannelIds = emptyList(),
            channelTabTitles = emptyMap(),
            workspaceLayout = WorkspaceLayout.default(),
            messageRules = SharedMessageRulesSnapshot(),
            savedFilters = SharedSavedFiltersSnapshot(),
            currentAppVersion = "0.0.6",
            createdAt = captureTime,
        )

        val decoded = SharedSettingsBackupCodec.decode(captured).document
        assertEquals(emptyList(), decoded.content.channels.logins)
        assertEquals(emptyList(), decoded.content.channels.favouriteChannelIds)
        assertEquals(emptyList(), decoded.content.channels.recentChannelIds)
        assertEquals(emptyList(), decoded.content.favouriteEmotes)
        assertEquals("{}", decoded.content.commands.toString())
        assertEquals("0.0.6", decoded.appVersion)
        assertEquals(captureTime.toString(), decoded.createdAt)
    }
}
