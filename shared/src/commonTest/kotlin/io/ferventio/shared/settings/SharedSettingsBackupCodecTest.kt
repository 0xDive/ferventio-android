package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedSettingsBackupCodecTest {
    @Test
    fun contentHashMatchesExistingAndroidCompatibleVector() {
        assertEquals(
            "e2fe5cee606756ff7540b5d292799450f01e5c6bdac1d4a81c88c808269ff614",
            SharedSettingsBackupCodec.contentHashForTesting(androidCompatibleContent()),
        )
    }

    @Test
    fun decodesCurrentBackupAndReturnsPreviewCounts() {
        val content = androidCompatibleContent()
        val document = document(content = content)

        val decoded = SharedSettingsBackupCodec.decode(
            SharedSettingsBackupCodec.encodeForTesting(document),
        )

        assertEquals(2, decoded.document.formatVersion)
        assertEquals(2, decoded.summary.channelCount)
        assertEquals(1, decoded.summary.workspaceCount)
        assertEquals(0, decoded.summary.filterCount)
        assertEquals(0, decoded.summary.highlightCount)
        assertEquals(0, decoded.summary.ignoreCount)
        assertEquals(0, decoded.summary.commandCount)
        assertEquals(0, decoded.summary.favouriteEmoteCount)
    }

    @Test
    fun migratesVersionOneRepeatCollapseToEnabledBeforeValidation() {
        val content = androidCompatibleContent().copy(
            settings = androidCompatibleContent().settings.copy(repeatCollapseEnabled = false),
        )
        val document = document(content = content, formatVersion = 1)

        val decoded = SharedSettingsBackupCodec.decode(
            SharedSettingsBackupCodec.encodeForTesting(document),
        )

        assertTrue(decoded.document.content.settings.repeatCollapseEnabled)
        assertEquals(1, decoded.document.formatVersion)
    }

    @Test
    fun appliesAndroidDefaultsWhenFormatFieldsAreMissing() {
        val content = androidCompatibleContent()
        val encoded = SharedSettingsBackupCodec.encodeForTesting(document(content = content))
            .replace("\"format\":\"ferventio-settings-backup\",", "")
            .replace("\"formatVersion\":2,", "")

        val decoded = SharedSettingsBackupCodec.decode(encoded)

        assertEquals(SharedSettingsBackupCodec.BACKUP_FORMAT, decoded.document.format)
        assertEquals(2, decoded.document.formatVersion)
    }

    @Test
    fun rejectsChecksumMismatch() {
        val content = androidCompatibleContent()
        val encoded = SharedSettingsBackupCodec.encodeForTesting(document(content = content))
            .replace("\"themeMode\":\"LIGHT\"", "\"themeMode\":\"DARK\"")

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsOutOfRangeSettingEvenWithMatchingChecksum() {
        val content = androidCompatibleContent().copy(
            settings = androidCompatibleContent().settings.copy(fontScalePercent = 151),
        )
        val document = document(content = content)

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupCodec.decode(SharedSettingsBackupCodec.encodeForTesting(document))
        }
    }

    @Test
    fun rejectsStringEncodedInteger() {
        val content = androidCompatibleContent()
        val encoded = SharedSettingsBackupCodec.encodeForTesting(document(content = content))
            .replace("\"fontScalePercent\":125", "\"fontScalePercent\":\"125\"")

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupCodec.decode(encoded)
        }
    }

    @Test
    fun rejectsCaseInsensitiveDuplicateChannelsWithMatchingChecksum() {
        val content = androidCompatibleContent().copy(
            channels = androidCompatibleContent().channels.copy(logins = listOf("alpha", "ALPHA")),
        )
        val document = document(content = content)

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupCodec.decode(SharedSettingsBackupCodec.encodeForTesting(document))
        }
    }

    @Test
    fun rejectsInvalidBackupDate() {
        val content = androidCompatibleContent()
        val document = document(content = content).copy(createdAt = "not-an-instant")

        assertFailsWith<IllegalArgumentException> {
            SharedSettingsBackupCodec.decode(SharedSettingsBackupCodec.encodeForTesting(document))
        }
    }

    private fun document(
        content: SharedSettingsBackupContent,
        formatVersion: Int = SharedSettingsBackupCodec.BACKUP_FORMAT_VERSION,
    ): SharedSettingsBackupDocument = SharedSettingsBackupDocument(
        formatVersion = formatVersion,
        createdAt = "2026-08-18T00:00:00Z",
        appVersion = "0.0.5",
        contentHash = SharedSettingsBackupCodec.contentHashForTesting(content, formatVersion),
        content = content,
    )

    private fun androidCompatibleContent(): SharedSettingsBackupContent = SharedSettingsBackupContent(
        settings = SharedSettingsBackupSettings(
            appLanguage = "RUSSIAN",
            themeMode = "LIGHT",
            fontScalePercent = 125,
            messageDensity = "NORMAL",
            showAvatars = false,
            showBadges = true,
            showTimestamps = true,
            nameStyle = "DISPLAY_NAME",
            wrapMessageLines = true,
            showDeletedMessageContent = false,
            showSystemMessages = true,
            mentionColorArgb = 4_294_953_047L,
            autoScrollEnabled = true,
            repeatCollapseEnabled = true,
            animateEmotes = true,
            emoteScalePercent = 100,
            betterTtvEnabled = false,
            frankerFaceZEnabled = true,
            sevenTvEnabled = true,
            sendOnEnter = true,
            showComposerEmoteImages = true,
            replyNotificationsEnabled = true,
            autoModNotificationsEnabled = true,
            recentMessagesEnabled = false,
            localHistoryEnabled = true,
            localHistoryLimit = 500,
            localHistoryRetentionDays = 7,
            localHistoryMaxSizeMb = 0,
            userCardTimeoutPresetsSeconds = listOf(10, 60, 600, 3_600, 86_400),
            userCardShowBanAction = true,
            userCardModerationActionOrder = listOf(
                "timeout:10",
                "timeout:60",
                "timeout:600",
                "timeout:3600",
                "timeout:86400",
                "warn",
                "ban",
                "unban",
            ),
        ),
        channels = SharedSettingsBackupChannels(
            logins = listOf("alpha", "beta"),
            selectedLogin = "beta",
            pinnedChannelIds = listOf("2"),
        ),
    )
}
