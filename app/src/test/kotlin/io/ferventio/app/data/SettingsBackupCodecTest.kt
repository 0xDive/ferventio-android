package io.ferventio.app.data

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SettingsBackupCodecTest {
    @Test
    fun roundTripPreservesVersionAndContentHash() {
        val content = sampleContent()
        val document = SettingsBackupDocument(
            createdAt = Instant.parse("2026-07-25T12:00:00Z").toString(),
            appVersion = "0.0.1-test",
            contentHash = SettingsBackupCodec.contentHash(content),
            content = content,
        )

        val decoded = SettingsBackupCodec.decode(SettingsBackupCodec.encode(document))

        assertEquals(BACKUP_FORMAT, decoded.format)
        assertEquals(BACKUP_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(document.contentHash, decoded.contentHash)
        assertEquals(listOf("channel_a", "channel_b"), decoded.content.channels.logins)
        assertTrue("provider:emote" in decoded.content.favouriteEmotes)
        assertEquals(false, decoded.content.settings.showSystemMessages)
        assertTrue(decoded.content.settings.recentMessagesEnabled)
    }

    @Test
    fun tamperedContentIsRejectedBeforeImport() {
        val content = sampleContent()
        val document = SettingsBackupDocument(
            createdAt = "2026-07-25T12:00:00Z",
            appVersion = "0.0.1-test",
            contentHash = SettingsBackupCodec.contentHash(content),
            content = content,
        )
        val tampered = SettingsBackupCodec.encode(document)
            .replace("channel_a", "channel_x")

        assertFailsWith<IllegalArgumentException> {
            SettingsBackupCodec.decode(tampered)
        }
    }

    @Test
    fun legacyBackupWithoutSystemMessagesFieldUsesEnabledDefault() {
        val baseContent = sampleContent()
        val content = baseContent.copy(
            settings = baseContent.settings.copy(showSystemMessages = true),
        )
        val document = SettingsBackupDocument(
            createdAt = "2026-07-25T12:00:00Z",
            appVersion = "0.0.1-test",
            contentHash = SettingsBackupCodec.contentHash(content),
            content = content,
        )
        val legacyJson = SettingsBackupCodec.encode(document)
            .replace(Regex("""\s*"showSystemMessages"\s*:\s*true\s*,?"""), "")

        val decoded = SettingsBackupCodec.decode(legacyJson)

        assertTrue(decoded.content.settings.showSystemMessages)
    }

    @Test
    fun legacyBackupWithoutRecentMessagesFieldUsesDisabledDefault() {
        val content = sampleContent()
        val document = SettingsBackupDocument(
            createdAt = "2026-07-25T12:00:00Z",
            appVersion = "0.0.1-test",
            contentHash = SettingsBackupCodec.contentHash(content),
            content = content,
        )
        val legacyContent = content.copy(
            settings = content.settings.copy(recentMessagesEnabled = false),
        )
        val legacyDocument = document.copy(
            content = legacyContent,
            contentHash = SettingsBackupCodec.contentHash(legacyContent),
        )
        val legacyJson = SettingsBackupCodec.encode(legacyDocument)
            .replace(Regex("""\s*"recentMessagesEnabled"\s*:\s*false\s*,?"""), "")

        val decoded = SettingsBackupCodec.decode(legacyJson)

        assertEquals(false, decoded.content.settings.recentMessagesEnabled)
    }

    private fun sampleContent(): SettingsBackupContent = SettingsBackupContent(
        settings = BackupSettings(
            themeMode = "AMOLED",
            fontScalePercent = 115,
            messageDensity = "COMPACT",
            showAvatars = false,
            showBadges = true,
            showTimestamps = true,
            nameStyle = "DISPLAY_AND_LOGIN",
            wrapMessageLines = true,
            showDeletedMessageContent = true,
            showSystemMessages = false,
            mentionColorArgb = 0xFFFFC857,
            autoScrollEnabled = true,
            animateEmotes = true,
            emoteScalePercent = 125,
            betterTtvEnabled = true,
            frankerFaceZEnabled = true,
            sevenTvEnabled = true,
            sendOnEnter = true,
            showComposerEmoteImages = true,
            replyNotificationsEnabled = true,
            autoModNotificationsEnabled = true,
            localHistoryEnabled = true,
            localHistoryLimit = 500,
            localHistoryRetentionDays = 30,
            localHistoryMaxSizeMb = 250,
            userCardTimeoutPresetsSeconds = listOf(10, 60, 600),
            userCardShowBanAction = true,
            userCardModerationActionOrder = listOf("timeout:10", "ban", "unban"),
            recentMessagesEnabled = true,
        ),
        channels = BackupChannels(
            logins = listOf("channel_a", "channel_b"),
            selectedLogin = "channel_a",
            favouriteChannelIds = listOf("1"),
            pinnedChannelIds = listOf("1"),
            recentChannelIds = listOf("2"),
        ),
        favouriteEmotes = listOf("provider:emote"),
    )
}
