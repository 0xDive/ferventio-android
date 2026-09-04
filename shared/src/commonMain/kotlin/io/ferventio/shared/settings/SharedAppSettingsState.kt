package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.UserCardModerationLayout

data class SharedAppPreferences(
    val appLanguage: AppLanguage = AppLanguage.RUSSIAN,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val fontScalePercent: Int = 100,
    val messageDensity: MessageDensity = MessageDensity.NORMAL,
    val showAvatars: Boolean = false,
    val showBadges: Boolean = true,
    val showTimestamps: Boolean = true,
    val nameStyle: ChatNameStyle = ChatNameStyle.DISPLAY_NAME,
    val wrapMessageLines: Boolean = true,
    val showDeletedMessageContent: Boolean = false,
    val showSystemMessages: Boolean = true,
    val mentionColorArgb: Long = MentionColors.GOLD,
    val autoScrollEnabled: Boolean = true,
    val repeatCollapseEnabled: Boolean = true,
    val animateEmotes: Boolean = true,
    val emoteScalePercent: Int = 100,
    val betterTtvEnabled: Boolean = true,
    val frankerFaceZEnabled: Boolean = true,
    val sevenTvEnabled: Boolean = true,
    val sendOnEnter: Boolean = true,
    val showComposerEmoteImages: Boolean = true,
    val replyNotificationsEnabled: Boolean = true,
    val autoModNotificationsEnabled: Boolean = true,
    val recentMessagesEnabled: Boolean = false,
    val localHistoryEnabled: Boolean = true,
    val localHistoryLimit: Int = 500,
    val localHistoryRetentionDays: Int = 7,
    val localHistoryMaxSizeMb: Int = 0,
    val userCardTimeoutPresetsSeconds: List<Int> = DEFAULT_TIMEOUT_PRESETS,
    val userCardShowBanAction: Boolean = true,
    val userCardModerationActionOrder: List<String> =
        UserCardModerationLayout.defaultOrder(DEFAULT_TIMEOUT_PRESETS),
) {
    fun normalized(): SharedAppPreferences {
        val timeouts = userCardTimeoutPresetsSeconds
            .filter { it in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS }
            .distinct()
            .take(MAX_TIMEOUT_PRESETS)
            .ifEmpty { DEFAULT_TIMEOUT_PRESETS }
        return copy(
            fontScalePercent = fontScalePercent.coerceIn(80, 150),
            mentionColorArgb = mentionColorArgb.coerceIn(0L, 0xFFFF_FFFFL),
            emoteScalePercent = emoteScalePercent.coerceIn(75, 200),
            localHistoryLimit = localHistoryLimit.coerceIn(100, 5_000),
            localHistoryRetentionDays = localHistoryRetentionDays.coerceIn(0, 365),
            localHistoryMaxSizeMb = localHistoryMaxSizeMb.coerceIn(0, 1_024),
            userCardTimeoutPresetsSeconds = timeouts,
            userCardModerationActionOrder = UserCardModerationLayout.normalize(
                storedOrder = userCardModerationActionOrder,
                timeoutPresetsSeconds = timeouts,
            ),
        )
    }

    companion object {
        val DEFAULT_TIMEOUT_PRESETS = listOf(10, 60, 600, 3_600, 86_400)
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 14 * 24 * 60 * 60
        const val MAX_TIMEOUT_PRESETS = 10
    }
}

enum class SharedSettingsSaveStatus {
    IDLE,
    SAVING,
    FAILED,
}

class SharedAppSettingsStateHolder(
    initialPreferences: SharedAppPreferences = SharedAppPreferences(),
) {
    var preferences by mutableStateOf(initialPreferences.normalized())
        private set

    var syncRevision by mutableStateOf(0L)
        private set

    var saveStatus by mutableStateOf(SharedSettingsSaveStatus.IDLE)
        private set

    var saveErrorMessage by mutableStateOf<String?>(null)
        private set

    fun restore(preferences: SharedAppPreferences, revision: Long) {
        this.preferences = preferences.normalized()
        syncRevision = revision.coerceAtLeast(0L)
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }

    fun updateLocally(transform: (SharedAppPreferences) -> SharedAppPreferences): SharedAppPreferences {
        preferences = transform(preferences).normalized()
        saveErrorMessage = null
        return preferences
    }

    fun markSaveStarted() {
        saveStatus = SharedSettingsSaveStatus.SAVING
        saveErrorMessage = null
    }

    fun markSaveSucceeded(preferences: SharedAppPreferences, revision: Long) {
        this.preferences = preferences.normalized()
        syncRevision = revision.coerceAtLeast(0L)
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }

    fun markSaveFailed(message: String?) {
        saveStatus = SharedSettingsSaveStatus.FAILED
        saveErrorMessage = message?.trim()?.takeIf(String::isNotEmpty)
            ?: "Failed to save settings"
    }

    fun clear() {
        preferences = SharedAppPreferences()
        syncRevision = 0L
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }
}
