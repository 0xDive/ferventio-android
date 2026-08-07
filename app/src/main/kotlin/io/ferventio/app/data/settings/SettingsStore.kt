package io.ferventio.app.data

import io.ferventio.app.domain.UserCardModerationLayout
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MentionColors
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageRuleCodec
import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.SavedMessageFilter

import android.content.Context
import android.util.Base64
import io.ferventio.app.BuildConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom
import java.util.UUID

internal const val FERVENTIO_SETTINGS_FILE_NAME = "ferventio_settings"
internal const val FERVENTIO_REPEAT_COLLAPSE_KEY = "repeat_collapse_enabled"

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val deviceSecretStore = DeviceSecretStore(appContext)
    private var syncNotificationsSuppressed = 0
    private var syncRelevantChangeListener: (() -> Unit)? = null
    private val preferenceChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (syncNotificationsSuppressed == 0 && key != null && key in SYNC_RELEVANT_KEYS) {
            syncRelevantChangeListener?.invoke()
        }
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        // Backend routing is part of the signed build configuration. Discard the
        // legacy user-editable override so an old preference cannot redirect OAuth,
        // push registration, settings sync or the anonymous badge proxy.
        if (preferences.contains(KEY_PUSH_SERVER_URL)) {
            preferences.edit().remove(KEY_PUSH_SERVER_URL).apply()
        }
    }

    fun setSyncRelevantChangeListener(listener: (() -> Unit)?) {
        syncRelevantChangeListener = listener
    }

    fun <T> withSyncNotificationsSuppressed(block: () -> T): T {
        syncNotificationsSuppressed++
        return try {
            block()
        } finally {
            syncNotificationsSuppressed--
        }
    }

    val channelsExplicitlyEmpty: Boolean
        get() = preferences.getBoolean(KEY_CHANNELS_EXPLICITLY_EMPTY, false)

    fun markChannelsExplicitlyEmpty(value: Boolean) {
        check(
            preferences.edit()
                .putBoolean(KEY_CHANNELS_EXPLICITLY_EMPTY, value)
                .commit(),
        ) { "Не удалось сохранить состояние списка каналов" }
    }

    var channelLogins: List<String>
        get() = preferences.getString(KEY_CHANNELS, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        set(value) {
            val normalized = value.map { it.lowercase().trim() }
                .filter(String::isNotBlank)
                .distinct()
            val editor = preferences.edit()
                .putString(KEY_CHANNELS, normalized.joinToString("|"))
            if (normalized.isNotEmpty()) {
                editor.putBoolean(KEY_CHANNELS_EXPLICITLY_EMPTY, false)
            }
            check(editor.commit()) { "Не удалось сохранить список каналов" }
        }

    var selectedChannelLogin: String?
        get() = preferences.getString(KEY_SELECTED_CHANNEL, null)
        set(value) {
            check(
                preferences.edit()
                    .putString(KEY_SELECTED_CHANNEL, value?.trim()?.takeIf(String::isNotEmpty))
                    .commit(),
            ) { "Не удалось сохранить выбранный канал" }
        }


    var favoriteChannelIds: Set<String>
        get() = preferences.getString(KEY_FAVORITE_CHANNEL_IDS, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        set(value) = preferences.edit()
            .putString(KEY_FAVORITE_CHANNEL_IDS, value.filter(String::isNotBlank).distinct().joinToString("|"))
            .apply()

    var pinnedChannelIds: List<String>
        get() = preferences.getString(KEY_PINNED_CHANNEL_IDS, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        set(value) = preferences.edit()
            .putString(KEY_PINNED_CHANNEL_IDS, value.filter(String::isNotBlank).distinct().joinToString("|"))
            .apply()

    var recentChannelIds: List<String>
        get() = preferences.getString(KEY_RECENT_CHANNEL_IDS, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        set(value) = preferences.edit()
            .putString(KEY_RECENT_CHANNEL_IDS, value.filter(String::isNotBlank).distinct().joinToString("|"))
            .apply()

    var channelTabTitles: Map<String, String>
        get() {
            val raw = preferences.getString(KEY_CHANNEL_TAB_TITLES, null) ?: return emptyMap()
            return runCatching {
                Json.parseToJsonElement(raw).jsonObject.mapNotNull { (channelId, value) ->
                    value.jsonPrimitive.contentOrNull
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
                        ?.let { title -> channelId to title }
                }.toMap()
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val normalized = value.mapNotNull { (channelId, title) ->
                title.trim().takeIf(String::isNotEmpty)?.let { channelId to it }
            }.toMap()
            preferences.edit()
                .putString(
                    KEY_CHANNEL_TAB_TITLES,
                    JsonObject(normalized.mapValues { (_, title) -> JsonPrimitive(title) }).toString(),
                )
                .apply()
        }

    var workspaceLayoutJson: String?
        get() = preferences.getString(KEY_WORKSPACE_LAYOUT_JSON, null)
        set(value) = preferences.edit().putString(KEY_WORKSPACE_LAYOUT_JSON, value).apply()


    var draftsByChannel: Map<String, String>
        get() = readStringMap(KEY_DRAFTS_BY_CHANNEL)
        set(value) = writeStringMap(
            KEY_DRAFTS_BY_CHANNEL,
            value.mapValues { (_, draft) -> draft.take(MAX_DRAFT_LENGTH) }
                .filterValues(String::isNotEmpty),
        )

    var sentMessageHistoryByChannel: Map<String, List<String>>
        get() {
            val raw = preferences.getString(KEY_SENT_MESSAGE_HISTORY, null) ?: return emptyMap()
            return runCatching {
                Json.parseToJsonElement(raw).jsonObject.mapValues { (_, value) ->
                    value.jsonArray.mapNotNull { element ->
                        element.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                    }.take(MAX_SENT_HISTORY_PER_CHANNEL)
                }.filterValues { it.isNotEmpty() }
            }.getOrDefault(emptyMap())
        }
        set(value) {
            val normalized = value.mapValues { (_, messages) ->
                messages.map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .take(MAX_SENT_HISTORY_PER_CHANNEL)
            }.filterValues { it.isNotEmpty() }
            preferences.edit().putString(
                KEY_SENT_MESSAGE_HISTORY,
                JsonObject(
                    normalized.mapValues { (_, messages) ->
                        JsonArray(messages.map(::JsonPrimitive))
                    },
                ).toString(),
            ).apply()
        }

    var sendOnEnter: Boolean
        get() = preferences.getBoolean(KEY_SEND_ON_ENTER, true)
        set(value) = preferences.edit().putBoolean(KEY_SEND_ON_ENTER, value).apply()

    var showComposerEmoteImages: Boolean
        get() = preferences.getBoolean(KEY_SHOW_COMPOSER_EMOTE_IMAGES, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_COMPOSER_EMOTE_IMAGES, value).apply()

    var userCardTimeoutPresetsSeconds: List<Int>
        get() = preferences.getString(KEY_USER_CARD_TIMEOUT_PRESETS, null)
            ?.split('|')
            ?.mapNotNull(String::toIntOrNull)
            ?.filter { it in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS }
            ?.distinct()
            ?.take(MAX_TIMEOUT_PRESETS)
            ?.takeIf(List<Int>::isNotEmpty)
            ?: DEFAULT_TIMEOUT_PRESETS
        set(value) {
            val normalized = value
                .filter { it in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS }
                .distinct()
                .take(MAX_TIMEOUT_PRESETS)
                .ifEmpty { DEFAULT_TIMEOUT_PRESETS }
            preferences.edit()
                .putString(KEY_USER_CARD_TIMEOUT_PRESETS, normalized.joinToString("|"))
                .apply()
        }

    var userCardShowBanAction: Boolean
        get() = preferences.getBoolean(KEY_USER_CARD_SHOW_BAN_ACTION, true)
        set(value) = preferences.edit().putBoolean(KEY_USER_CARD_SHOW_BAN_ACTION, value).apply()

    var userCardModerationActionOrder: List<String>
        get() {
            val stored = preferences.getString(KEY_USER_CARD_MODERATION_ACTION_ORDER, null)
                ?.split('|')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty()
            return UserCardModerationLayout.normalize(stored, userCardTimeoutPresetsSeconds)
        }
        set(value) {
            val normalized = UserCardModerationLayout.normalize(value, userCardTimeoutPresetsSeconds)
            preferences.edit()
                .putString(KEY_USER_CARD_MODERATION_ACTION_ORDER, normalized.joinToString("|"))
                .apply()
        }

    var replyNotificationsEnabled: Boolean
        get() = preferences.getBoolean(KEY_REPLY_NOTIFICATIONS_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_REPLY_NOTIFICATIONS_ENABLED, value).apply()

    var autoModNotificationsEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTOMOD_NOTIFICATIONS_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_AUTOMOD_NOTIFICATIONS_ENABLED, value).apply()

    var highlightRules: List<HighlightRule>
        get() = MessageRuleCodec.decodeHighlights(preferences.getString(KEY_HIGHLIGHT_RULES_JSON, null))
        set(value) = preferences.edit()
            .putString(KEY_HIGHLIGHT_RULES_JSON, MessageRuleCodec.encodeHighlights(value))
            .apply()

    var ignoreRules: List<IgnoreRule>
        get() = MessageRuleCodec.decodeIgnores(preferences.getString(KEY_IGNORE_RULES_JSON, null))
        set(value) = preferences.edit()
            .putString(KEY_IGNORE_RULES_JSON, MessageRuleCodec.encodeIgnores(value))
            .apply()

    var savedMessageFilters: List<SavedMessageFilter>
        get() = MessageFilterCodec.decode(preferences.getString(KEY_MESSAGE_FILTERS_JSON, null))
            .getOrDefault(emptyList())
        set(value) = preferences.edit()
            .putString(KEY_MESSAGE_FILTERS_JSON, MessageFilterCodec.encode(value))
            .apply()

    var customCommandsJson: String?
        get() = preferences.getString(KEY_CUSTOM_COMMANDS_JSON, null)
        set(value) = preferences.edit().putString(KEY_CUSTOM_COMMANDS_JSON, value).apply()

    var favoriteEmoteKeys: Set<String>
        get() = preferences.getString(KEY_FAVORITE_EMOTE_KEYS, "")
            .orEmpty()
            .split('|')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        set(value) = preferences.edit()
            .putString(KEY_FAVORITE_EMOTE_KEYS, value.filter(String::isNotBlank).sorted().joinToString("|"))
            .apply()

    var recentEmoteKeys: List<String>
        get() {
            val raw = preferences.getString(KEY_RECENT_EMOTE_KEYS, null) ?: return emptyList()
            return runCatching {
                Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                    element.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank)
                }
            }.getOrDefault(emptyList())
        }
        set(value) {
            preferences.edit()
                .putString(
                    KEY_RECENT_EMOTE_KEYS,
                    JsonArray(value.filter(String::isNotBlank).take(MAX_RECENT_EMOTE_USES).map { JsonPrimitive(it) }).toString(),
                )
                .apply()
        }

    val pushServerUrl: String
        get() = BuildConfig.FERVENTIO_SERVER_URL.trim().removeSuffix("/")

    var pushEnabled: Boolean
        get() = preferences.getBoolean(KEY_PUSH_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_PUSH_ENABLED, value).apply()

    var notificationPermissionRequested: Boolean
        get() = preferences.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = preferences.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, value).apply()

    var pushLastEventId: String?
        get() = preferences.getString(KEY_PUSH_LAST_EVENT_ID, null)?.takeIf(String::isNotBlank)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_PUSH_LAST_EVENT_ID)
            else putString(KEY_PUSH_LAST_EVENT_ID, value)
        }.apply()

    var pushRecentEventIds: List<String>
        get() = preferences.getString(KEY_PUSH_RECENT_EVENT_IDS, null)
            ?.split('|')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.takeLast(MAX_RECENT_PUSH_EVENT_IDS)
            .orEmpty()
        set(value) = preferences.edit()
            .putString(
                KEY_PUSH_RECENT_EVENT_IDS,
                value.filter(String::isNotBlank).distinct().takeLast(MAX_RECENT_PUSH_EVENT_IDS).joinToString("|"),
            )
            .apply()

    var pushLastConnectedAtMillis: Long
        get() = preferences.getLong(KEY_PUSH_LAST_CONNECTED_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_PUSH_LAST_CONNECTED_AT, value.coerceAtLeast(0L)).apply()

    var pushLastHeartbeatAtMillis: Long
        get() = preferences.getLong(KEY_PUSH_LAST_HEARTBEAT_AT, 0L)
        set(value) = preferences.edit().putLong(KEY_PUSH_LAST_HEARTBEAT_AT, value.coerceAtLeast(0L)).apply()

    val pendingAuthState: String?
        get() = preferences.getString(KEY_PENDING_AUTH_STATE, null)?.takeIf(String::isNotBlank)

    val pendingAuthExpiresAtMillis: Long
        get() = preferences.getLong(KEY_PENDING_AUTH_EXPIRES_AT, 0L)

    val pendingAuthServerUrl: String?
        get() = preferences.getString(KEY_PENDING_AUTH_SERVER_URL, null)?.takeIf(String::isNotBlank)

    fun savePendingAuth(state: String, expiresAtMillis: Long, serverUrl: String) {
        require(state.isNotBlank() && expiresAtMillis > 0L && serverUrl.isNotBlank()) {
            "Некорректные данные OAuth-сессии"
        }
        check(
            preferences.edit()
                .putString(KEY_PENDING_AUTH_STATE, state)
                .putLong(KEY_PENDING_AUTH_EXPIRES_AT, expiresAtMillis)
                .putString(KEY_PENDING_AUTH_SERVER_URL, serverUrl.trim())
                .commit(),
        ) { "Не удалось атомарно сохранить OAuth-сессию" }
    }

    fun clearPendingAuth() {
        check(
            preferences.edit()
                .remove(KEY_PENDING_AUTH_STATE)
                .remove(KEY_PENDING_AUTH_EXPIRES_AT)
                .remove(KEY_PENDING_AUTH_SERVER_URL)
                .commit(),
        ) { "Не удалось очистить OAuth state" }
    }

    var themeMode: AppThemeMode
        get() = preferences.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)
            ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
            ?: AppThemeMode.DARK
        set(value) = preferences.edit().putString(KEY_THEME_MODE, value.name).apply()

    var appLanguage: AppLanguage
        get() = AppLanguage.fromStorageValue(preferences.getString(KEY_APP_LANGUAGE, AppLanguage.RUSSIAN.storageValue))
        set(value) = preferences.edit().putString(KEY_APP_LANGUAGE, value.storageValue).apply()

    var fontScalePercent: Int
        get() = preferences.getInt(KEY_FONT_SCALE_PERCENT, 100).coerceIn(80, 150)
        set(value) = preferences.edit().putInt(KEY_FONT_SCALE_PERCENT, value.coerceIn(80, 150)).apply()

    var messageDensity: MessageDensity
        get() = preferences.getString(KEY_MESSAGE_DENSITY, MessageDensity.NORMAL.name)
            ?.let { runCatching { MessageDensity.valueOf(it) }.getOrNull() }
            ?: MessageDensity.NORMAL
        set(value) = preferences.edit().putString(KEY_MESSAGE_DENSITY, value.name).apply()

    var chatNameStyle: ChatNameStyle
        get() = preferences.getString(KEY_CHAT_NAME_STYLE, ChatNameStyle.DISPLAY_NAME.name)
            ?.let { runCatching { ChatNameStyle.valueOf(it) }.getOrNull() }
            ?: ChatNameStyle.DISPLAY_NAME
        set(value) = preferences.edit().putString(KEY_CHAT_NAME_STYLE, value.name).apply()

    var wrapMessageLines: Boolean
        get() = preferences.getBoolean(KEY_WRAP_MESSAGE_LINES, true)
        set(value) = preferences.edit().putBoolean(KEY_WRAP_MESSAGE_LINES, value).apply()

    var mentionColorArgb: Long
        get() = preferences.getLong(KEY_MENTION_COLOR_ARGB, MentionColors.GOLD)
        set(value) = preferences.edit().putLong(KEY_MENTION_COLOR_ARGB, value).apply()

    var autoScrollEnabled: Boolean
        get() = preferences.getBoolean(KEY_AUTO_SCROLL_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_AUTO_SCROLL_ENABLED, value).apply()

    var repeatCollapseEnabled: Boolean
        get() = preferences.getBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, true)
        set(value) = preferences.edit().putBoolean(FERVENTIO_REPEAT_COLLAPSE_KEY, value).apply()

    var settingsSyncEnabled: Boolean
        get() = preferences.getBoolean(KEY_SETTINGS_SYNC_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_SETTINGS_SYNC_ENABLED, value).apply()

    val settingsSyncUserId: String?
        get() = preferences.getString(KEY_SETTINGS_SYNC_USER_ID, null)?.takeIf(String::isNotBlank)

    fun bindSettingsSyncUser(userId: String): Boolean {
        val normalized = userId.trim()
        require(normalized.isNotEmpty()) { "Некорректный Twitch user ID" }
        if (settingsSyncUserId == normalized) return false
        check(
            preferences.edit()
                .putString(KEY_SETTINGS_SYNC_USER_ID, normalized)
                .putLong(KEY_SETTINGS_SYNC_REVISION, 0L)
                .remove(KEY_SETTINGS_SYNC_LAST_CONTENT_HASH)
                .putLong(KEY_SETTINGS_SYNC_LAST_SYNCED_AT, 0L)
                .commit(),
        ) { "Не удалось переключить профиль синхронизации" }
        return true
    }

    var settingsSyncRevision: Long
        get() = preferences.getLong(KEY_SETTINGS_SYNC_REVISION, 0L).coerceAtLeast(0L)
        set(value) = preferences.edit().putLong(KEY_SETTINGS_SYNC_REVISION, value.coerceAtLeast(0L)).apply()

    var settingsSyncLastContentHash: String?
        get() = preferences.getString(KEY_SETTINGS_SYNC_LAST_CONTENT_HASH, null)?.takeIf(String::isNotBlank)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_SETTINGS_SYNC_LAST_CONTENT_HASH)
            else putString(KEY_SETTINGS_SYNC_LAST_CONTENT_HASH, value)
        }.apply()

    var settingsSyncLastSyncedAtMillis: Long
        get() = preferences.getLong(KEY_SETTINGS_SYNC_LAST_SYNCED_AT, 0L).coerceAtLeast(0L)
        set(value) = preferences.edit().putLong(KEY_SETTINGS_SYNC_LAST_SYNCED_AT, value.coerceAtLeast(0L)).apply()

    var lastImportBackupJson: String?
        get() = preferences.getString(KEY_LAST_IMPORT_BACKUP_JSON, null)?.takeIf(String::isNotBlank)
        set(value) = preferences.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_LAST_IMPORT_BACKUP_JSON)
            else putString(KEY_LAST_IMPORT_BACKUP_JSON, value)
        }.apply()

    var recentMessagesEnabled: Boolean
        get() = preferences.getBoolean(KEY_RECENT_MESSAGES_ENABLED, false)
        set(value) = preferences.edit().putBoolean(KEY_RECENT_MESSAGES_ENABLED, value).apply()

    var localHistoryEnabled: Boolean
        get() = preferences.getBoolean(KEY_LOCAL_HISTORY_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_LOCAL_HISTORY_ENABLED, value).apply()

    var localHistoryLimit: Int
        get() = preferences.getInt(KEY_LOCAL_HISTORY_LIMIT, 500).coerceIn(100, 5_000)
        set(value) = preferences.edit()
            .putInt(KEY_LOCAL_HISTORY_LIMIT, value.coerceIn(100, 5_000))
            .apply()

    var localHistoryRetentionDays: Int
        get() = preferences.getInt(KEY_LOCAL_HISTORY_RETENTION_DAYS, 7).coerceIn(0, 365)
        set(value) = preferences.edit()
            .putInt(KEY_LOCAL_HISTORY_RETENTION_DAYS, value.coerceIn(0, 365))
            .apply()

    var localHistoryMaxSizeMb: Int
        get() = preferences.getInt(KEY_LOCAL_HISTORY_MAX_SIZE_MB, 0).coerceIn(0, 1_024)
        set(value) = preferences.edit()
            .putInt(KEY_LOCAL_HISTORY_MAX_SIZE_MB, value.coerceIn(0, 1_024))
            .apply()

    var showAvatars: Boolean
        get() = preferences.getBoolean(KEY_SHOW_AVATARS, false)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_AVATARS, value).apply()

    var showBadges: Boolean
        get() = preferences.getBoolean(KEY_SHOW_BADGES, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_BADGES, value).apply()

    var showTimestamps: Boolean
        get() = preferences.getBoolean(KEY_SHOW_TIMESTAMPS, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_TIMESTAMPS, value).apply()

    var showDeletedMessageContent: Boolean
        get() = preferences.getBoolean(KEY_SHOW_DELETED_MESSAGE_CONTENT, false)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_DELETED_MESSAGE_CONTENT, value).apply()

    var showSystemMessages: Boolean
        get() = preferences.getBoolean(KEY_SHOW_SYSTEM_MESSAGES, true)
        set(value) = preferences.edit().putBoolean(KEY_SHOW_SYSTEM_MESSAGES, value).apply()

    var animateEmotes: Boolean
        get() = preferences.getBoolean(KEY_ANIMATE_EMOTES, true)
        set(value) = preferences.edit().putBoolean(KEY_ANIMATE_EMOTES, value).apply()

    var emoteScalePercent: Int
        get() = preferences.getInt(KEY_EMOTE_SCALE_PERCENT, 100).coerceIn(75, 200)
        set(value) = preferences.edit()
            .putInt(KEY_EMOTE_SCALE_PERCENT, value.coerceIn(75, 200))
            .apply()

    var betterTtvEnabled: Boolean
        get() = preferences.getBoolean(KEY_BETTER_TTV_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_BETTER_TTV_ENABLED, value).apply()

    var frankerFaceZEnabled: Boolean
        get() = preferences.getBoolean(KEY_FRANKER_FACE_Z_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_FRANKER_FACE_Z_ENABLED, value).apply()

    var sevenTvEnabled: Boolean
        get() = preferences.getBoolean(KEY_SEVEN_TV_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_SEVEN_TV_ENABLED, value).apply()

    val installationId: String
        get() = getOrCreateValue(KEY_INSTALLATION_ID) { UUID.randomUUID().toString() }

    val installationSecret: String
        get() {
            val legacy = preferences.getString(KEY_INSTALLATION_SECRET, null)?.takeIf(String::isNotBlank)
            val secret = deviceSecretStore.getOrCreate {
                legacy ?: ByteArray(32).also(SecureRandom()::nextBytes).let { bytes ->
                    Base64.encodeToString(
                        bytes,
                        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
                    )
                }
            }
            if (legacy != null) {
                preferences.edit().remove(KEY_INSTALLATION_SECRET).apply()
            }
            return secret
        }

    fun clearAccountData() {
        preferences.edit()
            .remove(KEY_CHANNELS)
            .remove(KEY_CHANNELS_EXPLICITLY_EMPTY)
            .remove(KEY_SELECTED_CHANNEL)
            .remove(KEY_FAVORITE_CHANNEL_IDS)
            .remove(KEY_PINNED_CHANNEL_IDS)
            .remove(KEY_RECENT_CHANNEL_IDS)
            .remove(KEY_CHANNEL_TAB_TITLES)
            .remove(KEY_WORKSPACE_LAYOUT_JSON)
            .remove(KEY_RECENT_EMOTE_KEYS)
            .remove(KEY_DRAFTS_BY_CHANNEL)
            .remove(KEY_SENT_MESSAGE_HISTORY)
            .remove(KEY_FAVORITE_EMOTE_KEYS)
            .apply()
    }


    private fun readStringMap(key: String): Map<String, String> {
        val raw = preferences.getString(key, null) ?: return emptyMap()
        return runCatching {
            Json.parseToJsonElement(raw).jsonObject.mapNotNull { (entryKey, value) ->
                value.jsonPrimitive.contentOrNull?.let { entryKey to it }
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeStringMap(key: String, value: Map<String, String>) {
        preferences.edit().putString(
            key,
            JsonObject(value.mapValues { (_, item) -> JsonPrimitive(item) }).toString(),
        ).apply()
    }

    private fun getOrCreateValue(key: String, factory: () -> String): String {
        preferences.getString(key, null)?.takeIf(String::isNotBlank)?.let { return it }
        return factory().also { value ->
            check(preferences.edit().putString(key, value).commit()) {
                "Не удалось сохранить идентификатор установки"
            }
        }
    }

    private companion object {
        const val FILE_NAME = FERVENTIO_SETTINGS_FILE_NAME
        const val KEY_CHANNELS = "channels"
        const val KEY_CHANNELS_EXPLICITLY_EMPTY = "channels_explicitly_empty"
        const val KEY_SELECTED_CHANNEL = "selected_channel"
        const val KEY_FAVORITE_CHANNEL_IDS = "favorite_channel_ids"
        const val KEY_PINNED_CHANNEL_IDS = "pinned_channel_ids"
        const val KEY_RECENT_CHANNEL_IDS = "recent_channel_ids"
        const val KEY_CHANNEL_TAB_TITLES = "channel_tab_titles"
        const val KEY_WORKSPACE_LAYOUT_JSON = "workspace_layout_json"
        const val KEY_RECENT_EMOTE_KEYS = "recent_emote_keys"
        const val KEY_DRAFTS_BY_CHANNEL = "drafts_by_channel"
        const val KEY_SENT_MESSAGE_HISTORY = "sent_message_history_by_channel"
        const val KEY_SEND_ON_ENTER = "send_on_enter"
        const val KEY_SHOW_COMPOSER_EMOTE_IMAGES = "show_composer_emote_images"
        const val KEY_USER_CARD_TIMEOUT_PRESETS = "user_card_timeout_presets"
        const val KEY_USER_CARD_SHOW_BAN_ACTION = "user_card_show_ban_action"
        const val KEY_USER_CARD_MODERATION_ACTION_ORDER = "user_card_moderation_action_order"
        const val KEY_REPLY_NOTIFICATIONS_ENABLED = "reply_notifications_enabled"
        const val KEY_AUTOMOD_NOTIFICATIONS_ENABLED = "automod_notifications_enabled"
        const val KEY_HIGHLIGHT_RULES_JSON = "highlight_rules_json"
        const val KEY_IGNORE_RULES_JSON = "ignore_rules_json"
        const val KEY_MESSAGE_FILTERS_JSON = "message_filters_json"
        const val KEY_CUSTOM_COMMANDS_JSON = "custom_commands_json"
        const val KEY_FAVORITE_EMOTE_KEYS = "favorite_emote_keys"
        const val KEY_PUSH_SERVER_URL = "push_server_url"
        const val KEY_PUSH_ENABLED = "push_enabled"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
        const val KEY_PUSH_LAST_EVENT_ID = "push_last_event_id"
        const val KEY_PUSH_RECENT_EVENT_IDS = "push_recent_event_ids"
        const val KEY_PUSH_LAST_CONNECTED_AT = "push_last_connected_at"
        const val KEY_PUSH_LAST_HEARTBEAT_AT = "push_last_heartbeat_at"
        const val KEY_PENDING_AUTH_STATE = "pending_auth_state"
        const val KEY_PENDING_AUTH_EXPIRES_AT = "pending_auth_expires_at"
        const val KEY_PENDING_AUTH_SERVER_URL = "pending_auth_server_url"
        const val KEY_APP_LANGUAGE = "app_language"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_FONT_SCALE_PERCENT = "font_scale_percent"
        const val KEY_MESSAGE_DENSITY = "message_density"
        const val KEY_CHAT_NAME_STYLE = "chat_name_style"
        const val KEY_WRAP_MESSAGE_LINES = "wrap_message_lines"
        const val KEY_MENTION_COLOR_ARGB = "mention_color_argb"
        const val KEY_AUTO_SCROLL_ENABLED = "auto_scroll_enabled"
        const val KEY_SETTINGS_SYNC_ENABLED = "settings_sync_enabled"
        const val KEY_SETTINGS_SYNC_USER_ID = "settings_sync_user_id"
        const val KEY_SETTINGS_SYNC_REVISION = "settings_sync_revision"
        const val KEY_SETTINGS_SYNC_LAST_CONTENT_HASH = "settings_sync_last_content_hash"
        const val KEY_SETTINGS_SYNC_LAST_SYNCED_AT = "settings_sync_last_synced_at"
        const val KEY_LAST_IMPORT_BACKUP_JSON = "last_import_backup_json"
        const val KEY_RECENT_MESSAGES_ENABLED = "recent_messages_enabled"
        const val KEY_LOCAL_HISTORY_ENABLED = "local_history_enabled"
        const val KEY_LOCAL_HISTORY_LIMIT = "local_history_limit"
        const val KEY_LOCAL_HISTORY_RETENTION_DAYS = "local_history_retention_days"
        const val KEY_LOCAL_HISTORY_MAX_SIZE_MB = "local_history_max_size_mb"
        const val KEY_SHOW_AVATARS = "show_avatars"
        const val KEY_SHOW_BADGES = "show_badges"
        const val KEY_SHOW_TIMESTAMPS = "show_timestamps"
        const val KEY_SHOW_DELETED_MESSAGE_CONTENT = "show_deleted_message_content"
        const val KEY_SHOW_SYSTEM_MESSAGES = "show_system_messages"
        const val KEY_ANIMATE_EMOTES = "animate_emotes"
        const val KEY_EMOTE_SCALE_PERCENT = "emote_scale_percent"
        const val KEY_BETTER_TTV_ENABLED = "better_ttv_enabled"
        const val KEY_FRANKER_FACE_Z_ENABLED = "franker_face_z_enabled"
        const val KEY_SEVEN_TV_ENABLED = "seven_tv_enabled"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_INSTALLATION_SECRET = "installation_secret"
        const val MAX_RECENT_EMOTE_USES = 160
        const val MAX_DRAFT_LENGTH = 500
        const val MAX_SENT_HISTORY_PER_CHANNEL = 50
        const val MIN_TIMEOUT_SECONDS = 1
        const val MAX_TIMEOUT_SECONDS = 14 * 24 * 60 * 60
        const val MAX_TIMEOUT_PRESETS = 10
        val DEFAULT_TIMEOUT_PRESETS = listOf(10, 60, 600, 3_600, 86_400)
        const val MAX_RECENT_PUSH_EVENT_IDS = 128
        val SYNC_RELEVANT_KEYS = setOf(
            KEY_CHANNELS,
            KEY_CHANNELS_EXPLICITLY_EMPTY,
            KEY_SELECTED_CHANNEL,
            KEY_FAVORITE_CHANNEL_IDS,
            KEY_PINNED_CHANNEL_IDS,
            KEY_RECENT_CHANNEL_IDS,
            KEY_CHANNEL_TAB_TITLES,
            KEY_WORKSPACE_LAYOUT_JSON,
            KEY_SEND_ON_ENTER,
            KEY_SHOW_COMPOSER_EMOTE_IMAGES,
            KEY_USER_CARD_TIMEOUT_PRESETS,
            KEY_USER_CARD_SHOW_BAN_ACTION,
            KEY_USER_CARD_MODERATION_ACTION_ORDER,
            KEY_REPLY_NOTIFICATIONS_ENABLED,
            KEY_AUTOMOD_NOTIFICATIONS_ENABLED,
            KEY_HIGHLIGHT_RULES_JSON,
            KEY_IGNORE_RULES_JSON,
            KEY_MESSAGE_FILTERS_JSON,
            KEY_CUSTOM_COMMANDS_JSON,
            KEY_FAVORITE_EMOTE_KEYS,
            KEY_RECENT_MESSAGES_ENABLED,
            KEY_LOCAL_HISTORY_ENABLED,
            KEY_LOCAL_HISTORY_LIMIT,
            KEY_LOCAL_HISTORY_RETENTION_DAYS,
            KEY_LOCAL_HISTORY_MAX_SIZE_MB,
            KEY_SHOW_AVATARS,
            KEY_SHOW_BADGES,
            KEY_SHOW_TIMESTAMPS,
            KEY_SHOW_DELETED_MESSAGE_CONTENT,
            KEY_SHOW_SYSTEM_MESSAGES,
            KEY_ANIMATE_EMOTES,
            KEY_EMOTE_SCALE_PERCENT,
            KEY_BETTER_TTV_ENABLED,
            KEY_FRANKER_FACE_Z_ENABLED,
            KEY_SEVEN_TV_ENABLED,
            KEY_APP_LANGUAGE,
            KEY_THEME_MODE,
            KEY_FONT_SCALE_PERCENT,
            KEY_MESSAGE_DENSITY,
            KEY_CHAT_NAME_STYLE,
            KEY_WRAP_MESSAGE_LINES,
            KEY_MENTION_COLOR_ARGB,
            KEY_AUTO_SCROLL_ENABLED,
            FERVENTIO_REPEAT_COLLAPSE_KEY,
        )
    }
}
