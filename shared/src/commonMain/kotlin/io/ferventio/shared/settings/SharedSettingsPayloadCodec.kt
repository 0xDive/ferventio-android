package io.ferventio.shared.settings

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.UserCardModerationLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object SharedSettingsPayloadCodec {
    private const val MAX_PAYLOAD_CHARS = 2 * 1024 * 1024
    private const val BACKUP_FORMAT = "ferventio-settings-backup"
    private const val CURRENT_FORMAT_VERSION = 2
    private val json = Json { ignoreUnknownKeys = true }

    fun parsePreferences(payload: String): SharedAppPreferences {
        val root = parseRoot(payload)
        val settings = root["content"]
            ?.runCatching { jsonObject }
            ?.getOrNull()
            ?.get("settings")
            ?.runCatching { jsonObject }
            ?.getOrNull()
            ?: return SharedAppPreferences()
        return settings.toPreferences().normalized()
    }

    /**
     * Replaces only the settings projection while preserving the rest of the Android backup
     * document and recomputing the same SHA-256 contentHash used by SettingsBackupCodec.
     */
    fun replacePreferences(
        payload: String,
        preferences: SharedAppPreferences,
    ): String {
        val root = parseRoot(payload)
        require(root.string("format") == BACKUP_FORMAT) {
            "Unsupported Ferventio settings payload format"
        }
        val createdAt = root.string("createdAt")
            ?: error("Settings payload does not contain createdAt")
        val appVersion = root.string("appVersion")
            ?: error("Settings payload does not contain appVersion")
        val content = root["content"]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Settings payload does not contain content")
        val canonicalContent = canonicalContent(
            original = content,
            settings = settingsJson(preferences.normalized()),
        )
        val contentHash = sha256Hex(canonicalContent.toString())

        return buildJsonObject {
            put("format", JsonPrimitive(BACKUP_FORMAT))
            put("formatVersion", JsonPrimitive(CURRENT_FORMAT_VERSION))
            put("createdAt", JsonPrimitive(createdAt))
            put("appVersion", JsonPrimitive(appVersion))
            put("contentHash", JsonPrimitive(contentHash))
            put("content", canonicalContent)
            root.forEach { (name, value) ->
                if (name !in DOCUMENT_FIELDS) put(name, value)
            }
        }.toString()
    }

    internal fun contentHashForTesting(
        payload: String,
        preferences: SharedAppPreferences = parsePreferences(payload),
    ): String {
        val root = parseRoot(payload)
        val content = root["content"]?.jsonObject ?: error("Settings payload does not contain content")
        return sha256Hex(
            canonicalContent(content, settingsJson(preferences.normalized())).toString(),
        )
    }

    private fun parseRoot(payload: String): JsonObject {
        require(payload.length <= MAX_PAYLOAD_CHARS) { "Settings payload is too large" }
        return runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { throw IllegalArgumentException("Invalid settings payload JSON", it) }
    }

    private fun JsonObject.toPreferences(): SharedAppPreferences {
        val defaults = SharedAppPreferences()
        val timeouts = intList("userCardTimeoutPresetsSeconds")
            .ifEmpty { defaults.userCardTimeoutPresetsSeconds }
        return SharedAppPreferences(
            appLanguage = enumValue<AppLanguage>("appLanguage")
                ?: string("appLanguage")?.let(AppLanguage::fromStorageValue)
                ?: defaults.appLanguage,
            themeMode = enumValue<AppThemeMode>("themeMode") ?: defaults.themeMode,
            fontScalePercent = int("fontScalePercent") ?: defaults.fontScalePercent,
            messageDensity = enumValue<MessageDensity>("messageDensity") ?: defaults.messageDensity,
            showAvatars = boolean("showAvatars") ?: defaults.showAvatars,
            showBadges = boolean("showBadges") ?: defaults.showBadges,
            showTimestamps = boolean("showTimestamps") ?: defaults.showTimestamps,
            nameStyle = enumValue<ChatNameStyle>("nameStyle") ?: defaults.nameStyle,
            wrapMessageLines = boolean("wrapMessageLines") ?: defaults.wrapMessageLines,
            showDeletedMessageContent = boolean("showDeletedMessageContent")
                ?: defaults.showDeletedMessageContent,
            showSystemMessages = boolean("showSystemMessages") ?: defaults.showSystemMessages,
            mentionColorArgb = long("mentionColorArgb") ?: defaults.mentionColorArgb,
            autoScrollEnabled = boolean("autoScrollEnabled") ?: defaults.autoScrollEnabled,
            repeatCollapseEnabled = boolean("repeatCollapseEnabled") ?: defaults.repeatCollapseEnabled,
            animateEmotes = boolean("animateEmotes") ?: defaults.animateEmotes,
            emoteScalePercent = int("emoteScalePercent") ?: defaults.emoteScalePercent,
            betterTtvEnabled = boolean("betterTtvEnabled") ?: defaults.betterTtvEnabled,
            frankerFaceZEnabled = boolean("frankerFaceZEnabled") ?: defaults.frankerFaceZEnabled,
            sevenTvEnabled = boolean("sevenTvEnabled") ?: defaults.sevenTvEnabled,
            sendOnEnter = boolean("sendOnEnter") ?: defaults.sendOnEnter,
            showComposerEmoteImages = boolean("showComposerEmoteImages")
                ?: defaults.showComposerEmoteImages,
            replyNotificationsEnabled = boolean("replyNotificationsEnabled")
                ?: defaults.replyNotificationsEnabled,
            autoModNotificationsEnabled = boolean("autoModNotificationsEnabled")
                ?: defaults.autoModNotificationsEnabled,
            recentMessagesEnabled = boolean("recentMessagesEnabled") ?: defaults.recentMessagesEnabled,
            localHistoryEnabled = boolean("localHistoryEnabled") ?: defaults.localHistoryEnabled,
            localHistoryLimit = int("localHistoryLimit") ?: defaults.localHistoryLimit,
            localHistoryRetentionDays = int("localHistoryRetentionDays")
                ?: defaults.localHistoryRetentionDays,
            localHistoryMaxSizeMb = int("localHistoryMaxSizeMb") ?: defaults.localHistoryMaxSizeMb,
            userCardTimeoutPresetsSeconds = timeouts,
            userCardShowBanAction = boolean("userCardShowBanAction") ?: defaults.userCardShowBanAction,
            userCardModerationActionOrder = stringList("userCardModerationActionOrder")
                .ifEmpty { UserCardModerationLayout.defaultOrder(timeouts) },
        )
    }

    private fun settingsJson(preferences: SharedAppPreferences): JsonObject = buildJsonObject {
        put("appLanguage", JsonPrimitive(preferences.appLanguage.name))
        put("themeMode", JsonPrimitive(preferences.themeMode.name))
        put("fontScalePercent", JsonPrimitive(preferences.fontScalePercent))
        put("messageDensity", JsonPrimitive(preferences.messageDensity.name))
        put("showAvatars", JsonPrimitive(preferences.showAvatars))
        put("showBadges", JsonPrimitive(preferences.showBadges))
        put("showTimestamps", JsonPrimitive(preferences.showTimestamps))
        put("nameStyle", JsonPrimitive(preferences.nameStyle.name))
        put("wrapMessageLines", JsonPrimitive(preferences.wrapMessageLines))
        put("showDeletedMessageContent", JsonPrimitive(preferences.showDeletedMessageContent))
        put("showSystemMessages", JsonPrimitive(preferences.showSystemMessages))
        put("mentionColorArgb", JsonPrimitive(preferences.mentionColorArgb))
        put("autoScrollEnabled", JsonPrimitive(preferences.autoScrollEnabled))
        put("repeatCollapseEnabled", JsonPrimitive(preferences.repeatCollapseEnabled))
        put("animateEmotes", JsonPrimitive(preferences.animateEmotes))
        put("emoteScalePercent", JsonPrimitive(preferences.emoteScalePercent))
        put("betterTtvEnabled", JsonPrimitive(preferences.betterTtvEnabled))
        put("frankerFaceZEnabled", JsonPrimitive(preferences.frankerFaceZEnabled))
        put("sevenTvEnabled", JsonPrimitive(preferences.sevenTvEnabled))
        put("sendOnEnter", JsonPrimitive(preferences.sendOnEnter))
        put("showComposerEmoteImages", JsonPrimitive(preferences.showComposerEmoteImages))
        put("replyNotificationsEnabled", JsonPrimitive(preferences.replyNotificationsEnabled))
        put("autoModNotificationsEnabled", JsonPrimitive(preferences.autoModNotificationsEnabled))
        put("recentMessagesEnabled", JsonPrimitive(preferences.recentMessagesEnabled))
        put("localHistoryEnabled", JsonPrimitive(preferences.localHistoryEnabled))
        put("localHistoryLimit", JsonPrimitive(preferences.localHistoryLimit))
        put("localHistoryRetentionDays", JsonPrimitive(preferences.localHistoryRetentionDays))
        put("localHistoryMaxSizeMb", JsonPrimitive(preferences.localHistoryMaxSizeMb))
        put(
            "userCardTimeoutPresetsSeconds",
            JsonArray(preferences.userCardTimeoutPresetsSeconds.map(::JsonPrimitive)),
        )
        put("userCardShowBanAction", JsonPrimitive(preferences.userCardShowBanAction))
        put(
            "userCardModerationActionOrder",
            JsonArray(preferences.userCardModerationActionOrder.map(::JsonPrimitive)),
        )
    }

    private fun canonicalContent(original: JsonObject, settings: JsonObject): JsonObject = buildJsonObject {
        put("settings", settings)
        put("channels", canonicalChannels(original["channels"] as? JsonObject))
        put("workspaces", original["workspaces"] ?: JsonNull)
        put("filters", original["filters"] ?: JsonObject(emptyMap()))
        put("highlights", original["highlights"] ?: JsonArray(emptyList()))
        put("ignoreRules", original["ignoreRules"] ?: JsonArray(emptyList()))
        put("commands", original["commands"] ?: JsonObject(emptyMap()))
        put("favouriteEmotes", canonicalStringArray(original["favouriteEmotes"]))
    }

    private fun canonicalChannels(original: JsonObject?): JsonObject = buildJsonObject {
        val channels = original ?: JsonObject(emptyMap())
        put("logins", canonicalStringArray(channels["logins"]))
        channels["selectedLogin"]
            ?.takeUnless { it is JsonNull }
            ?.let { put("selectedLogin", it) }
        put("favouriteChannelIds", canonicalStringArray(channels["favouriteChannelIds"]))
        put("pinnedChannelIds", canonicalStringArray(channels["pinnedChannelIds"]))
        put("recentChannelIds", canonicalStringArray(channels["recentChannelIds"]))
        put("tabTitles", channels["tabTitles"] as? JsonObject ?: JsonObject(emptyMap()))
    }

    private fun canonicalStringArray(element: JsonElement?): JsonArray =
        (element as? JsonArray)?.let { array ->
            JsonArray(array.mapNotNull { item ->
                item.runCatching { jsonPrimitive.contentOrNull }
                    .getOrNull()
                    ?.let(::JsonPrimitive)
            })
        } ?: JsonArray(emptyList())

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String): T? =
        string(name)?.let { raw -> enumValues<T>().firstOrNull { it.name == raw } }

    private fun JsonObject.string(name: String): String? = this[name]
        ?.runCatching { jsonPrimitive.contentOrNull }
        ?.getOrNull()

    private fun JsonObject.boolean(name: String): Boolean? = this[name]
        ?.runCatching { jsonPrimitive.booleanOrNull }
        ?.getOrNull()

    private fun JsonObject.int(name: String): Int? = this[name]
        ?.runCatching { jsonPrimitive.intOrNull }
        ?.getOrNull()

    private fun JsonObject.long(name: String): Long? = this[name]
        ?.runCatching { jsonPrimitive.longOrNull }
        ?.getOrNull()

    private fun JsonObject.stringList(name: String): List<String> = this[name]
        ?.runCatching { jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }
        ?.getOrNull()
        .orEmpty()

    private fun JsonObject.intList(name: String): List<Int> = this[name]
        ?.runCatching { jsonArray.mapNotNull { it.jsonPrimitive.intOrNull } }
        ?.getOrNull()
        .orEmpty()

    private val DOCUMENT_FIELDS = setOf(
        "format",
        "formatVersion",
        "createdAt",
        "appVersion",
        "contentHash",
        "content",
    )
}
