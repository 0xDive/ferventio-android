package io.ferventio.shared.workspace

import io.ferventio.shared.settings.SharedSettingsBackupChannels
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ferventio.shared.settings.SharedSettingsBackupContent
import io.ferventio.shared.settings.SharedSettingsBackupDocument
import io.ferventio.shared.settings.SharedSettingsBackupSettings

internal fun workspaceSettingsBackupTestPayload(
    formatVersion: Int = SharedSettingsBackupCodec.BACKUP_FORMAT_VERSION,
    repeatCollapseEnabled: Boolean = true,
    themeMode: String = "LIGHT",
): String {
    val content = SharedSettingsBackupContent(
        settings = SharedSettingsBackupSettings(
            appLanguage = "RUSSIAN",
            themeMode = themeMode,
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
            repeatCollapseEnabled = repeatCollapseEnabled,
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
    val document = SharedSettingsBackupDocument(
        formatVersion = formatVersion,
        createdAt = "2026-08-18T00:00:00Z",
        appVersion = "0.0.5",
        contentHash = SharedSettingsBackupCodec.contentHashForTesting(content, formatVersion),
        content = content,
    )
    return SharedSettingsBackupCodec.encodeForTesting(document)
}
