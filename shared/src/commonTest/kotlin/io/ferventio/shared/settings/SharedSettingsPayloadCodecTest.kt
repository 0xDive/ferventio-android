package io.ferventio.shared.settings

import io.ferventio.app.domain.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedSettingsPayloadCodecTest {
    @Test
    fun sha256MatchesStandardVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc"),
        )
    }

    @Test
    fun readsAndroidBackupSettingsAndNormalizesRanges() {
        val payload = backupPayload(
            settingsOverride = """
                "themeMode":"AMOLED",
                "fontScalePercent":999,
                "betterTtvEnabled":false,
                "animateEmotes":false,
                "emoteScalePercent":20
            """.trimIndent(),
        )

        val preferences = SharedSettingsPayloadCodec.parsePreferences(payload)

        assertEquals(AppThemeMode.AMOLED, preferences.themeMode)
        assertEquals(150, preferences.fontScalePercent)
        assertEquals(75, preferences.emoteScalePercent)
        assertFalse(preferences.betterTtvEnabled)
        assertFalse(preferences.animateEmotes)
        assertTrue(preferences.showBadges)
    }

    @Test
    fun replacingSettingsKeepsWorkspaceAndProducesAndroidCompatibleHash() {
        val preferences = SharedAppPreferences().copy(
            themeMode = AppThemeMode.LIGHT,
            fontScalePercent = 125,
            betterTtvEnabled = false,
        )

        val updated = SharedSettingsPayloadCodec.replacePreferences(
            payload = backupPayload(),
            preferences = preferences,
        )
        val root = Json.parseToJsonElement(updated).jsonObject
        val content = root.getValue("content").jsonObject
        val channels = content.getValue("channels").jsonObject

        assertEquals("2", root.getValue("formatVersion").jsonPrimitive.content)
        assertEquals(
            "e2fe5cee606756ff7540b5d292799450f01e5c6bdac1d4a81c88c808269ff614",
            root.getValue("contentHash").jsonPrimitive.content,
        )
        assertEquals("beta", channels.getValue("selectedLogin").jsonPrimitive.content)
        assertEquals("future-value", root.getValue("futureDocumentField").jsonPrimitive.content)
        assertEquals(
            preferences.normalized(),
            SharedSettingsPayloadCodec.parsePreferences(updated),
        )
    }

    @Test
    fun replacingChannelsPreservesSettingsAndOtherBackupSections() {
        val original = backupPayload(
            channelsOverride = """
                "logins":["alpha","beta"],
                "selectedLogin":"beta",
                "favouriteChannelIds":["fav-id"],
                "pinnedChannelIds":["beta-id"],
                "recentChannelIds":["recent-id"],
                "tabTitles":{"alpha-id":"Alpha local","beta-id":"Beta local"}
            """.trimIndent(),
        )
        val originalPreferences = SharedSettingsPayloadCodec.parsePreferences(original)

        val updated = SharedSettingsPayloadCodec.replaceChannels(
            payload = original,
            logins = listOf(" #Gamma ", "alpha", "gamma"),
            selectedLogin = "gamma",
            pinnedChannelIds = listOf("gamma-id", "gamma-id"),
        )

        val root = Json.parseToJsonElement(updated).jsonObject
        val content = root.getValue("content").jsonObject
        val channels = content.getValue("channels").jsonObject
        assertEquals(
            listOf("gamma", "alpha"),
            channels.getValue("logins").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("gamma", channels.getValue("selectedLogin").jsonPrimitive.content)
        assertEquals(
            listOf("fav-id"),
            channels.getValue("favouriteChannelIds").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("recent-id"),
            channels.getValue("recentChannelIds").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("gamma-id"),
            channels.getValue("pinnedChannelIds").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            "Alpha local",
            channels.getValue("tabTitles").jsonObject.getValue("alpha-id").jsonPrimitive.content,
        )
        assertEquals(originalPreferences, SharedSettingsPayloadCodec.parsePreferences(updated))
        assertEquals("future-value", root.getValue("futureDocumentField").jsonPrimitive.content)
        assertEquals(
            sha256Hex(content.toString()),
            root.getValue("contentHash").jsonPrimitive.content,
        )
    }

    @Test
    fun replacingChannelsDropsInvalidSelectionAndSupportsRenaming() {
        val updated = SharedSettingsPayloadCodec.replaceChannels(
            payload = backupPayload(),
            logins = listOf("alpha"),
            selectedLogin = "beta",
            pinnedChannelIds = emptyList(),
            tabTitles = mapOf("1" to "  Custom Alpha  ", "2" to "   "),
        )
        val channels = Json.parseToJsonElement(updated)
            .jsonObject.getValue("content").jsonObject
            .getValue("channels").jsonObject

        assertNull(channels["selectedLogin"])
        assertEquals(
            mapOf("1" to "Custom Alpha"),
            channels.getValue("tabTitles").jsonObject.mapValues { it.value.jsonPrimitive.content },
        )
    }

    private fun backupPayload(
        settingsOverride: String? = null,
        channelsOverride: String? = null,
    ): String {
        val settings = settingsOverride ?: """
            "appLanguage":"RUSSIAN",
            "themeMode":"DARK",
            "fontScalePercent":100,
            "messageDensity":"NORMAL",
            "showAvatars":false,
            "showBadges":true,
            "showTimestamps":true,
            "nameStyle":"DISPLAY_NAME",
            "wrapMessageLines":true,
            "showDeletedMessageContent":false,
            "showSystemMessages":true,
            "mentionColorArgb":4294953047,
            "autoScrollEnabled":true,
            "repeatCollapseEnabled":true,
            "animateEmotes":true,
            "emoteScalePercent":100,
            "betterTtvEnabled":true,
            "frankerFaceZEnabled":true,
            "sevenTvEnabled":true,
            "sendOnEnter":true,
            "showComposerEmoteImages":true,
            "replyNotificationsEnabled":true,
            "autoModNotificationsEnabled":true,
            "recentMessagesEnabled":false,
            "localHistoryEnabled":true,
            "localHistoryLimit":500,
            "localHistoryRetentionDays":7,
            "localHistoryMaxSizeMb":0,
            "userCardTimeoutPresetsSeconds":[10,60,600,3600,86400],
            "userCardShowBanAction":true,
            "userCardModerationActionOrder":["timeout:10","timeout:60","timeout:600","timeout:3600","timeout:86400","warn","ban","unban"]
        """.trimIndent()
        val channels = channelsOverride ?: """
            "logins":["alpha","beta"],
            "selectedLogin":"beta",
            "favouriteChannelIds":[],
            "pinnedChannelIds":["2"],
            "recentChannelIds":[],
            "tabTitles":{}
        """.trimIndent()
        return """
            {
              "format":"ferventio-settings-backup",
              "formatVersion":2,
              "createdAt":"2026-08-18T00:00:00Z",
              "appVersion":"0.0.5",
              "contentHash":"ignored-for-update",
              "content":{
                "settings":{$settings},
                "channels":{$channels},
                "workspaces":null,
                "filters":{},
                "highlights":[],
                "ignoreRules":[],
                "commands":{},
                "favouriteEmotes":[]
              },
              "futureDocumentField":"future-value"
            }
        """.trimIndent()
    }
}
