package io.ferventio.shared.settings

import android.content.Context
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors

/** Android guest display persistence compatible with the legacy production preference keys. */
class AndroidAnonymousDisplayPreferencesStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : AnonymousDisplayPreferencesStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): AnonymousDisplayPreferences = AnonymousDisplayPreferences(
        appLanguage = AppLanguage.fromStorageValue(
            preferences.getString(ANONYMOUS_APP_LANGUAGE_KEY, AppLanguage.RUSSIAN.storageValue),
        ),
        themeMode = preferences.enumValue(ANONYMOUS_THEME_MODE_KEY, AppThemeMode.DARK),
        fontScalePercent = preferences.getInt(ANONYMOUS_FONT_SCALE_PERCENT_KEY, 100),
        messageDensity = preferences.enumValue(
            ANONYMOUS_MESSAGE_DENSITY_KEY,
            MessageDensity.NORMAL,
        ),
        showAvatars = preferences.getBoolean(ANONYMOUS_SHOW_AVATARS_KEY, false),
        showBadges = preferences.getBoolean(ANONYMOUS_SHOW_BADGES_KEY, true),
        showTimestamps = preferences.getBoolean(ANONYMOUS_SHOW_TIMESTAMPS_KEY, true),
        nameStyle = preferences.enumValue(
            ANONYMOUS_CHAT_NAME_STYLE_KEY,
            ChatNameStyle.DISPLAY_NAME,
        ),
        wrapMessageLines = preferences.getBoolean(ANONYMOUS_WRAP_MESSAGE_LINES_KEY, true),
        showDeletedMessageContent = preferences.getBoolean(
            ANONYMOUS_SHOW_DELETED_CONTENT_KEY,
            false,
        ),
        showSystemMessages = preferences.getBoolean(ANONYMOUS_SHOW_SYSTEM_MESSAGES_KEY, true),
        mentionColorArgb = preferences.getLong(ANONYMOUS_MENTION_COLOR_ARGB_KEY, MentionColors.GOLD),
        autoScrollEnabled = preferences.getBoolean(ANONYMOUS_AUTO_SCROLL_ENABLED_KEY, true),
        repeatCollapseEnabled = preferences.getBoolean(
            ANONYMOUS_REPEAT_COLLAPSE_ENABLED_KEY,
            true,
        ),
        animateEmotes = preferences.getBoolean(ANONYMOUS_ANIMATE_EMOTES_KEY, true),
        emoteScalePercent = preferences.getInt(ANONYMOUS_EMOTE_SCALE_PERCENT_KEY, 100),
        betterTtvEnabled = preferences.getBoolean(ANONYMOUS_BETTER_TTV_ENABLED_KEY, true),
        frankerFaceZEnabled = preferences.getBoolean(
            ANONYMOUS_FRANKER_FACE_Z_ENABLED_KEY,
            true,
        ),
        sevenTvEnabled = preferences.getBoolean(ANONYMOUS_SEVEN_TV_ENABLED_KEY, true),
    ).normalized()

    override fun save(preferences: AnonymousDisplayPreferences) {
        val normalized = preferences.normalized()
        check(
            this.preferences.edit()
                .putString(ANONYMOUS_APP_LANGUAGE_KEY, normalized.appLanguage.storageValue)
                .putString(ANONYMOUS_THEME_MODE_KEY, normalized.themeMode.name)
                .putInt(ANONYMOUS_FONT_SCALE_PERCENT_KEY, normalized.fontScalePercent)
                .putString(ANONYMOUS_MESSAGE_DENSITY_KEY, normalized.messageDensity.name)
                .putBoolean(ANONYMOUS_SHOW_AVATARS_KEY, normalized.showAvatars)
                .putBoolean(ANONYMOUS_SHOW_BADGES_KEY, normalized.showBadges)
                .putBoolean(ANONYMOUS_SHOW_TIMESTAMPS_KEY, normalized.showTimestamps)
                .putString(ANONYMOUS_CHAT_NAME_STYLE_KEY, normalized.nameStyle.name)
                .putBoolean(ANONYMOUS_WRAP_MESSAGE_LINES_KEY, normalized.wrapMessageLines)
                .putBoolean(
                    ANONYMOUS_SHOW_DELETED_CONTENT_KEY,
                    normalized.showDeletedMessageContent,
                )
                .putBoolean(ANONYMOUS_SHOW_SYSTEM_MESSAGES_KEY, normalized.showSystemMessages)
                .putLong(ANONYMOUS_MENTION_COLOR_ARGB_KEY, normalized.mentionColorArgb)
                .putBoolean(ANONYMOUS_AUTO_SCROLL_ENABLED_KEY, normalized.autoScrollEnabled)
                .putBoolean(
                    ANONYMOUS_REPEAT_COLLAPSE_ENABLED_KEY,
                    normalized.repeatCollapseEnabled,
                )
                .putBoolean(ANONYMOUS_ANIMATE_EMOTES_KEY, normalized.animateEmotes)
                .putInt(ANONYMOUS_EMOTE_SCALE_PERCENT_KEY, normalized.emoteScalePercent)
                .putBoolean(ANONYMOUS_BETTER_TTV_ENABLED_KEY, normalized.betterTtvEnabled)
                .putBoolean(
                    ANONYMOUS_FRANKER_FACE_Z_ENABLED_KEY,
                    normalized.frankerFaceZEnabled,
                )
                .putBoolean(ANONYMOUS_SEVEN_TV_ENABLED_KEY, normalized.sevenTvEnabled)
                .commit(),
        ) { "Failed to persist anonymous display preferences" }
    }

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        fallback: T,
    ): T = getString(key, fallback.name)
        ?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
        ?: fallback
}
