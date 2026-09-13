package io.ferventio.shared.settings

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors
import platform.Foundation.NSUserDefaults

/** iOS device-local display preferences for signed-out chat. */
class IosAnonymousDisplayPreferencesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnonymousDisplayPreferencesStore {
    override fun load(): AnonymousDisplayPreferences = AnonymousDisplayPreferences(
        appLanguage = AppLanguage.fromStorageValue(
            defaults.stringForKey(ANONYMOUS_APP_LANGUAGE_KEY),
        ),
        themeMode = defaults.enumValue(ANONYMOUS_THEME_MODE_KEY, AppThemeMode.DARK),
        fontScalePercent = defaults.intOrDefault(ANONYMOUS_FONT_SCALE_PERCENT_KEY, 100),
        messageDensity = defaults.enumValue(
            ANONYMOUS_MESSAGE_DENSITY_KEY,
            MessageDensity.NORMAL,
        ),
        showAvatars = defaults.boolOrDefault(ANONYMOUS_SHOW_AVATARS_KEY, false),
        showBadges = defaults.boolOrDefault(ANONYMOUS_SHOW_BADGES_KEY, true),
        showTimestamps = defaults.boolOrDefault(ANONYMOUS_SHOW_TIMESTAMPS_KEY, true),
        nameStyle = defaults.enumValue(
            ANONYMOUS_CHAT_NAME_STYLE_KEY,
            ChatNameStyle.DISPLAY_NAME,
        ),
        wrapMessageLines = defaults.boolOrDefault(ANONYMOUS_WRAP_MESSAGE_LINES_KEY, true),
        showDeletedMessageContent = defaults.boolOrDefault(
            ANONYMOUS_SHOW_DELETED_CONTENT_KEY,
            false,
        ),
        showSystemMessages = defaults.boolOrDefault(ANONYMOUS_SHOW_SYSTEM_MESSAGES_KEY, true),
        mentionColorArgb = defaults.longOrDefault(
            ANONYMOUS_MENTION_COLOR_ARGB_KEY,
            MentionColors.GOLD,
        ),
        autoScrollEnabled = defaults.boolOrDefault(ANONYMOUS_AUTO_SCROLL_ENABLED_KEY, true),
        repeatCollapseEnabled = defaults.boolOrDefault(
            ANONYMOUS_REPEAT_COLLAPSE_ENABLED_KEY,
            true,
        ),
        animateEmotes = defaults.boolOrDefault(ANONYMOUS_ANIMATE_EMOTES_KEY, true),
        emoteScalePercent = defaults.intOrDefault(ANONYMOUS_EMOTE_SCALE_PERCENT_KEY, 100),
        betterTtvEnabled = defaults.boolOrDefault(ANONYMOUS_BETTER_TTV_ENABLED_KEY, true),
        frankerFaceZEnabled = defaults.boolOrDefault(
            ANONYMOUS_FRANKER_FACE_Z_ENABLED_KEY,
            true,
        ),
        sevenTvEnabled = defaults.boolOrDefault(ANONYMOUS_SEVEN_TV_ENABLED_KEY, true),
    ).normalized()

    override fun save(preferences: AnonymousDisplayPreferences) {
        val normalized = preferences.normalized()
        defaults.setObject(normalized.appLanguage.storageValue, forKey = ANONYMOUS_APP_LANGUAGE_KEY)
        defaults.setObject(normalized.themeMode.name, forKey = ANONYMOUS_THEME_MODE_KEY)
        defaults.setInteger(
            normalized.fontScalePercent.toLong(),
            forKey = ANONYMOUS_FONT_SCALE_PERCENT_KEY,
        )
        defaults.setObject(normalized.messageDensity.name, forKey = ANONYMOUS_MESSAGE_DENSITY_KEY)
        defaults.setBool(normalized.showAvatars, forKey = ANONYMOUS_SHOW_AVATARS_KEY)
        defaults.setBool(normalized.showBadges, forKey = ANONYMOUS_SHOW_BADGES_KEY)
        defaults.setBool(normalized.showTimestamps, forKey = ANONYMOUS_SHOW_TIMESTAMPS_KEY)
        defaults.setObject(normalized.nameStyle.name, forKey = ANONYMOUS_CHAT_NAME_STYLE_KEY)
        defaults.setBool(normalized.wrapMessageLines, forKey = ANONYMOUS_WRAP_MESSAGE_LINES_KEY)
        defaults.setBool(
            normalized.showDeletedMessageContent,
            forKey = ANONYMOUS_SHOW_DELETED_CONTENT_KEY,
        )
        defaults.setBool(
            normalized.showSystemMessages,
            forKey = ANONYMOUS_SHOW_SYSTEM_MESSAGES_KEY,
        )
        defaults.setInteger(
            normalized.mentionColorArgb,
            forKey = ANONYMOUS_MENTION_COLOR_ARGB_KEY,
        )
        defaults.setBool(
            normalized.autoScrollEnabled,
            forKey = ANONYMOUS_AUTO_SCROLL_ENABLED_KEY,
        )
        defaults.setBool(
            normalized.repeatCollapseEnabled,
            forKey = ANONYMOUS_REPEAT_COLLAPSE_ENABLED_KEY,
        )
        defaults.setBool(normalized.animateEmotes, forKey = ANONYMOUS_ANIMATE_EMOTES_KEY)
        defaults.setInteger(
            normalized.emoteScalePercent.toLong(),
            forKey = ANONYMOUS_EMOTE_SCALE_PERCENT_KEY,
        )
        defaults.setBool(
            normalized.betterTtvEnabled,
            forKey = ANONYMOUS_BETTER_TTV_ENABLED_KEY,
        )
        defaults.setBool(
            normalized.frankerFaceZEnabled,
            forKey = ANONYMOUS_FRANKER_FACE_Z_ENABLED_KEY,
        )
        defaults.setBool(normalized.sevenTvEnabled, forKey = ANONYMOUS_SEVEN_TV_ENABLED_KEY)
    }

    private fun NSUserDefaults.boolOrDefault(key: String, fallback: Boolean): Boolean =
        objectForKey(key)?.let { boolForKey(key) } ?: fallback

    private fun NSUserDefaults.intOrDefault(key: String, fallback: Int): Int =
        objectForKey(key)?.let { integerForKey(key).toInt() } ?: fallback

    private fun NSUserDefaults.longOrDefault(key: String, fallback: Long): Long =
        objectForKey(key)?.let { integerForKey(key) } ?: fallback

    private inline fun <reified T : Enum<T>> NSUserDefaults.enumValue(key: String, fallback: T): T =
        stringForKey(key)
            ?.let { value -> runCatching { enumValueOf<T>(value) }.getOrNull() }
            ?: fallback
}
