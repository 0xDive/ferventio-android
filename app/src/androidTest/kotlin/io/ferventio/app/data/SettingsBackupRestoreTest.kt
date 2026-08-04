package io.ferventio.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ferventio.app.BuildConfig
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsBackupRestoreTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearPreferencesBeforeTest() {
        clearPreferences()
    }

    @After
    fun clearPreferencesAfterTest() {
        clearPreferences()
    }

    @Test
    fun exportImportAndPreImportRestorePreserveBackedUpStateOnly() {
        val store = SettingsStore(context)
        applyProfileA(store)
        val installationId = store.installationId
        val installationSecret = store.installationSecret
        val exported = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T12:00:00Z"),
        )
        val raw = SettingsBackupCodec.encode(exported)

        assertFalse(raw.contains(installationId))
        assertFalse(raw.contains(installationSecret))

        applyProfileB(store)
        val builtInServerUrl = store.pushServerUrl
        store.pushEnabled = true
        store.pushLastEventId = "event-sensitive-marker"
        store.savePendingAuth(
            state = "oauth-sensitive-marker",
            expiresAtMillis = 9_999_999L,
            serverUrl = "https://oauth-private.example.test",
        )
        store.settingsSyncEnabled = true
        store.bindSettingsSyncUser("998877")
        store.settingsSyncRevision = 44L
        val stateBeforeImport = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T12:01:00Z"),
        )

        val result = SettingsBackupCodec.apply(
            store = store,
            document = SettingsBackupCodec.decode(raw),
            currentAppVersion = "0.0.1-test",
            createPreImportBackup = true,
        )

        val restored = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T12:02:00Z"),
        )
        assertEquals(exported.content, restored.content)
        assertEquals(2, result.channelCount)
        assertEquals(2, result.favouriteEmoteCount)
        assertFalse(store.channelsExplicitlyEmpty)

        assertEquals(builtInServerUrl, store.pushServerUrl)
        assertTrue(store.pushEnabled)
        assertEquals("event-sensitive-marker", store.pushLastEventId)
        assertEquals("oauth-sensitive-marker", store.pendingAuthState)
        assertEquals("https://oauth-private.example.test", store.pendingAuthServerUrl)
        assertTrue(store.settingsSyncEnabled)
        assertEquals("998877", store.settingsSyncUserId)
        assertEquals(44L, store.settingsSyncRevision)
        assertEquals(installationId, store.installationId)
        assertEquals(installationSecret, store.installationSecret)

        val preImportRaw = store.lastImportBackupJson
        assertNotNull(preImportRaw)
        val preImportDocument = SettingsBackupCodec.decode(requireNotNull(preImportRaw))
        assertEquals(stateBeforeImport.content, preImportDocument.content)

        SettingsBackupCodec.apply(
            store = store,
            document = preImportDocument,
            currentAppVersion = "0.0.1-test",
            createPreImportBackup = false,
        )

        val rolledBack = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T12:03:00Z"),
        )
        assertEquals(stateBeforeImport.content, rolledBack.content)
        assertTrue(store.channelsExplicitlyEmpty)
        assertEquals(preImportRaw, store.lastImportBackupJson)
    }


    @Test
    fun backendRouteComesFromBuildAndLegacyOverrideIsRemoved() {
        val preferences = context.getSharedPreferences("ferventio_settings", Context.MODE_PRIVATE)
        assertTrue(
            preferences.edit()
                .putString("push_server_url", "https://attacker.example.test")
                .commit(),
        )

        val store = SettingsStore(context)

        assertEquals(
            BuildConfig.FERVENTIO_SERVER_URL.trim().removeSuffix("/"),
            store.pushServerUrl,
        )
        assertFalse(preferences.contains("push_server_url"))
    }

    @Test
    fun directApplyRejectsTamperedDocumentWithoutChangingPreferences() {
        val store = SettingsStore(context)
        applyProfileB(store)
        val before = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T13:00:00Z"),
        )
        val tampered = before.copy(contentHash = "0".repeat(64))

        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackupCodec.apply(
                store = store,
                document = tampered,
                currentAppVersion = "0.0.1-test",
                createPreImportBackup = true,
            )
        }

        val after = SettingsBackupCodec.capture(
            store = store,
            appVersion = "0.0.1-test",
            createdAt = Instant.parse("2026-07-29T13:01:00Z"),
        )
        assertEquals(before.content, after.content)
        assertNull(store.lastImportBackupJson)
    }

    private fun applyProfileA(store: SettingsStore) {
        store.themeMode = AppThemeMode.AMOLED
        store.fontScalePercent = 125
        store.messageDensity = MessageDensity.COMPACT
        store.showAvatars = true
        store.showBadges = false
        store.showTimestamps = false
        store.chatNameStyle = ChatNameStyle.DISPLAY_AND_LOGIN
        store.wrapMessageLines = false
        store.showDeletedMessageContent = true
        store.showSystemMessages = false
        store.mentionColorArgb = 0xFF12_3456
        store.autoScrollEnabled = false
        store.animateEmotes = false
        store.emoteScalePercent = 135
        store.betterTtvEnabled = false
        store.frankerFaceZEnabled = true
        store.sevenTvEnabled = false
        store.sendOnEnter = false
        store.showComposerEmoteImages = false
        store.replyNotificationsEnabled = false
        store.autoModNotificationsEnabled = false
        store.localHistoryEnabled = true
        store.localHistoryLimit = 1_500
        store.localHistoryRetentionDays = 45
        store.localHistoryMaxSizeMb = 300
        store.userCardTimeoutPresetsSeconds = listOf(15, 90, 900)
        store.userCardShowBanAction = false
        store.userCardModerationActionOrder = listOf("timeout:15", "unban")
        store.channelLogins = listOf("alpha_channel", "beta_channel")
        store.selectedChannelLogin = "beta_channel"
        store.favoriteChannelIds = setOf("100", "200")
        store.pinnedChannelIds = listOf("200", "100")
        store.recentChannelIds = listOf("300", "200")
        store.channelTabTitles = mapOf("100" to "Alpha", "200" to "Beta")
        store.workspaceLayoutJson = null
        store.savedMessageFilters = emptyList()
        store.highlightRules = emptyList()
        store.ignoreRules = emptyList()
        store.customCommandsJson = null
        store.favoriteEmoteKeys = setOf("7tv:one", "bttv:two")
    }

    private fun applyProfileB(store: SettingsStore) {
        store.themeMode = AppThemeMode.LIGHT
        store.fontScalePercent = 90
        store.messageDensity = MessageDensity.RELAXED
        store.showAvatars = false
        store.showBadges = true
        store.showTimestamps = true
        store.chatNameStyle = ChatNameStyle.LOGIN
        store.wrapMessageLines = true
        store.showDeletedMessageContent = false
        store.showSystemMessages = true
        store.mentionColorArgb = 0xFFAB_CDEF
        store.autoScrollEnabled = true
        store.animateEmotes = true
        store.emoteScalePercent = 80
        store.betterTtvEnabled = true
        store.frankerFaceZEnabled = false
        store.sevenTvEnabled = true
        store.sendOnEnter = true
        store.showComposerEmoteImages = true
        store.replyNotificationsEnabled = true
        store.autoModNotificationsEnabled = true
        store.localHistoryEnabled = false
        store.localHistoryLimit = 200
        store.localHistoryRetentionDays = 0
        store.localHistoryMaxSizeMb = 0
        store.userCardTimeoutPresetsSeconds = listOf(30, 300)
        store.userCardShowBanAction = true
        store.userCardModerationActionOrder = listOf("timeout:30", "ban")
        store.channelLogins = emptyList()
        store.markChannelsExplicitlyEmpty(true)
        store.selectedChannelLogin = null
        store.favoriteChannelIds = emptySet()
        store.pinnedChannelIds = emptyList()
        store.recentChannelIds = emptyList()
        store.channelTabTitles = emptyMap()
        store.workspaceLayoutJson = null
        store.savedMessageFilters = emptyList()
        store.highlightRules = emptyList()
        store.ignoreRules = emptyList()
        store.customCommandsJson = null
        store.favoriteEmoteKeys = setOf("twitch:three")
    }

    private fun clearPreferences() {
        context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(DEVICE_CREDENTIALS_FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val SETTINGS_FILE_NAME = "ferventio_settings"
        const val DEVICE_CREDENTIALS_FILE_NAME = "ferventio_device_credentials"
    }
}
