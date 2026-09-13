package io.ferventio.shared.settings

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors

/** Device-local visual/read-only chat preferences used while there is no authenticated settings scope. */
data class AnonymousDisplayPreferences(
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
) {
    fun normalized(): AnonymousDisplayPreferences = copy(
        fontScalePercent = fontScalePercent.coerceIn(80, 150),
        mentionColorArgb = mentionColorArgb.coerceIn(0L, 0xFFFF_FFFFL),
        emoteScalePercent = emoteScalePercent.coerceIn(75, 200),
    )

    fun applyTo(preferences: SharedAppPreferences): SharedAppPreferences {
        val normalized = normalized()
        return preferences.copy(
            appLanguage = normalized.appLanguage,
            themeMode = normalized.themeMode,
            fontScalePercent = normalized.fontScalePercent,
            messageDensity = normalized.messageDensity,
            showAvatars = normalized.showAvatars,
            showBadges = normalized.showBadges,
            showTimestamps = normalized.showTimestamps,
            nameStyle = normalized.nameStyle,
            wrapMessageLines = normalized.wrapMessageLines,
            showDeletedMessageContent = normalized.showDeletedMessageContent,
            showSystemMessages = normalized.showSystemMessages,
            mentionColorArgb = normalized.mentionColorArgb,
            autoScrollEnabled = normalized.autoScrollEnabled,
            repeatCollapseEnabled = normalized.repeatCollapseEnabled,
            animateEmotes = normalized.animateEmotes,
            emoteScalePercent = normalized.emoteScalePercent,
            betterTtvEnabled = normalized.betterTtvEnabled,
            frankerFaceZEnabled = normalized.frankerFaceZEnabled,
            sevenTvEnabled = normalized.sevenTvEnabled,
        ).normalized()
    }

    companion object {
        fun from(preferences: SharedAppPreferences): AnonymousDisplayPreferences =
            AnonymousDisplayPreferences(
                appLanguage = preferences.appLanguage,
                themeMode = preferences.themeMode,
                fontScalePercent = preferences.fontScalePercent,
                messageDensity = preferences.messageDensity,
                showAvatars = preferences.showAvatars,
                showBadges = preferences.showBadges,
                showTimestamps = preferences.showTimestamps,
                nameStyle = preferences.nameStyle,
                wrapMessageLines = preferences.wrapMessageLines,
                showDeletedMessageContent = preferences.showDeletedMessageContent,
                showSystemMessages = preferences.showSystemMessages,
                mentionColorArgb = preferences.mentionColorArgb,
                autoScrollEnabled = preferences.autoScrollEnabled,
                repeatCollapseEnabled = preferences.repeatCollapseEnabled,
                animateEmotes = preferences.animateEmotes,
                emoteScalePercent = preferences.emoteScalePercent,
                betterTtvEnabled = preferences.betterTtvEnabled,
                frankerFaceZEnabled = preferences.frankerFaceZEnabled,
                sevenTvEnabled = preferences.sevenTvEnabled,
            ).normalized()
    }
}

interface AnonymousDisplayPreferencesStore {
    fun load(): AnonymousDisplayPreferences

    fun save(preferences: AnonymousDisplayPreferences)
}

/** Projects only signed-out-safe display/chat fields into the shared settings holder. */
class AnonymousDisplayPreferencesCoordinator(
    private val store: AnonymousDisplayPreferencesStore = InMemoryAnonymousDisplayPreferencesStore(),
) {
    fun restore(state: SharedAppSettingsStateHolder): AnonymousDisplayPreferences {
        val restored = store.load().normalized()
        state.updateLocally { current -> restored.applyTo(current) }
        return restored
    }

    fun save(preferences: SharedAppPreferences): AnonymousDisplayPreferences {
        val snapshot = AnonymousDisplayPreferences.from(preferences)
        store.save(snapshot)
        return snapshot
    }
}

internal const val ANONYMOUS_APP_LANGUAGE_KEY = "app_language"
internal const val ANONYMOUS_THEME_MODE_KEY = "theme_mode"
internal const val ANONYMOUS_FONT_SCALE_PERCENT_KEY = "font_scale_percent"
internal const val ANONYMOUS_MESSAGE_DENSITY_KEY = "message_density"
internal const val ANONYMOUS_CHAT_NAME_STYLE_KEY = "chat_name_style"
internal const val ANONYMOUS_WRAP_MESSAGE_LINES_KEY = "wrap_message_lines"
internal const val ANONYMOUS_MENTION_COLOR_ARGB_KEY = "mention_color_argb"
internal const val ANONYMOUS_AUTO_SCROLL_ENABLED_KEY = "auto_scroll_enabled"
internal const val ANONYMOUS_REPEAT_COLLAPSE_ENABLED_KEY = "repeat_collapse_enabled"
internal const val ANONYMOUS_SHOW_AVATARS_KEY = "show_avatars"
internal const val ANONYMOUS_SHOW_BADGES_KEY = "show_badges"
internal const val ANONYMOUS_SHOW_TIMESTAMPS_KEY = "show_timestamps"
internal const val ANONYMOUS_SHOW_DELETED_CONTENT_KEY = "show_deleted_message_content"
internal const val ANONYMOUS_SHOW_SYSTEM_MESSAGES_KEY = "show_system_messages"
internal const val ANONYMOUS_ANIMATE_EMOTES_KEY = "animate_emotes"
internal const val ANONYMOUS_EMOTE_SCALE_PERCENT_KEY = "emote_scale_percent"
internal const val ANONYMOUS_BETTER_TTV_ENABLED_KEY = "better_ttv_enabled"
internal const val ANONYMOUS_FRANKER_FACE_Z_ENABLED_KEY = "franker_face_z_enabled"
internal const val ANONYMOUS_SEVEN_TV_ENABLED_KEY = "seven_tv_enabled"

private class InMemoryAnonymousDisplayPreferencesStore : AnonymousDisplayPreferencesStore {
    private var preferences = AnonymousDisplayPreferences()

    override fun load(): AnonymousDisplayPreferences = preferences

    override fun save(preferences: AnonymousDisplayPreferences) {
        this.preferences = preferences.normalized()
    }
}
