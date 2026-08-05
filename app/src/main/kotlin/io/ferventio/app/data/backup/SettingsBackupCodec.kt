package io.ferventio.app.data

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.CustomCommandCodec
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.MessageRuleCodec
import io.ferventio.app.domain.WorkspaceLayoutCodec
import io.ferventio.app.security.JsonInputGuard
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class SettingsBackupDocument(
    val format: String = BACKUP_FORMAT,
    val formatVersion: Int = BACKUP_FORMAT_VERSION,
    val createdAt: String,
    val appVersion: String,
    val contentHash: String,
    val content: SettingsBackupContent,
)

@Serializable
data class SettingsBackupContent(
    val settings: BackupSettings,
    val channels: BackupChannels,
    val workspaces: JsonElement = JsonNull,
    val filters: JsonElement = JsonObject(emptyMap()),
    val highlights: JsonElement = JsonArray(emptyList()),
    val ignoreRules: JsonElement = JsonArray(emptyList()),
    val commands: JsonElement = JsonObject(emptyMap()),
    val favouriteEmotes: List<String> = emptyList(),
)

@Serializable
data class BackupSettings(
    val appLanguage: String = AppLanguage.RUSSIAN.name,
    val themeMode: String,
    val fontScalePercent: Int,
    val messageDensity: String,
    val showAvatars: Boolean,
    val showBadges: Boolean,
    val showTimestamps: Boolean,
    val nameStyle: String,
    val wrapMessageLines: Boolean,
    val showDeletedMessageContent: Boolean,
    val showSystemMessages: Boolean = true,
    val mentionColorArgb: Long,
    val autoScrollEnabled: Boolean,
    val animateEmotes: Boolean,
    val emoteScalePercent: Int,
    val betterTtvEnabled: Boolean,
    val frankerFaceZEnabled: Boolean,
    val sevenTvEnabled: Boolean,
    val sendOnEnter: Boolean,
    val showComposerEmoteImages: Boolean,
    val replyNotificationsEnabled: Boolean,
    val autoModNotificationsEnabled: Boolean,
    val recentMessagesEnabled: Boolean = false,
    val localHistoryEnabled: Boolean,
    val localHistoryLimit: Int,
    val localHistoryRetentionDays: Int,
    val localHistoryMaxSizeMb: Int,
    val userCardTimeoutPresetsSeconds: List<Int>,
    val userCardShowBanAction: Boolean,
    val userCardModerationActionOrder: List<String>,
)

@Serializable
data class BackupChannels(
    val logins: List<String>,
    val selectedLogin: String? = null,
    val favouriteChannelIds: List<String> = emptyList(),
    val pinnedChannelIds: List<String> = emptyList(),
    val recentChannelIds: List<String> = emptyList(),
    val tabTitles: Map<String, String> = emptyMap(),
)

data class SettingsBackupImportResult(
    val channelCount: Int,
    val workspaceCount: Int,
    val filterCount: Int,
    val highlightCount: Int,
    val ignoreCount: Int,
    val commandCount: Int,
    val favouriteEmoteCount: Int,
)

object SettingsBackupCodec {
    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    @OptIn(ExperimentalSerializationApi::class)
    private val prettyJson = Json(compactJson) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun capture(store: SettingsStore, appVersion: String, createdAt: Instant = Instant.now()): SettingsBackupDocument {
        val content = SettingsBackupContent(
            settings = BackupSettings(
                appLanguage = store.appLanguage.name,
                themeMode = store.themeMode.name,
                fontScalePercent = store.fontScalePercent,
                messageDensity = store.messageDensity.name,
                showAvatars = store.showAvatars,
                showBadges = store.showBadges,
                showTimestamps = store.showTimestamps,
                nameStyle = store.chatNameStyle.name,
                wrapMessageLines = store.wrapMessageLines,
                showDeletedMessageContent = store.showDeletedMessageContent,
                showSystemMessages = store.showSystemMessages,
                mentionColorArgb = store.mentionColorArgb,
                autoScrollEnabled = store.autoScrollEnabled,
                animateEmotes = store.animateEmotes,
                emoteScalePercent = store.emoteScalePercent,
                betterTtvEnabled = store.betterTtvEnabled,
                frankerFaceZEnabled = store.frankerFaceZEnabled,
                sevenTvEnabled = store.sevenTvEnabled,
                sendOnEnter = store.sendOnEnter,
                showComposerEmoteImages = store.showComposerEmoteImages,
                replyNotificationsEnabled = store.replyNotificationsEnabled,
                autoModNotificationsEnabled = store.autoModNotificationsEnabled,
                localHistoryEnabled = store.localHistoryEnabled,
                localHistoryLimit = store.localHistoryLimit,
                localHistoryRetentionDays = store.localHistoryRetentionDays,
                localHistoryMaxSizeMb = store.localHistoryMaxSizeMb,
                userCardTimeoutPresetsSeconds = store.userCardTimeoutPresetsSeconds,
                userCardShowBanAction = store.userCardShowBanAction,
                userCardModerationActionOrder = store.userCardModerationActionOrder,
                recentMessagesEnabled = store.recentMessagesEnabled,
            ),
            channels = BackupChannels(
                logins = store.channelLogins,
                selectedLogin = store.selectedChannelLogin,
                favouriteChannelIds = store.favoriteChannelIds.sorted(),
                pinnedChannelIds = store.pinnedChannelIds,
                recentChannelIds = store.recentChannelIds,
                tabTitles = store.channelTabTitles,
            ),
            workspaces = parseOrNull(store.workspaceLayoutJson),
            filters = parseOrEmptyObject(io.ferventio.app.domain.MessageFilterCodec.encode(store.savedMessageFilters)),
            highlights = parseOrEmptyArray(MessageRuleCodec.encodeHighlights(store.highlightRules)),
            ignoreRules = parseOrEmptyArray(MessageRuleCodec.encodeIgnores(store.ignoreRules)),
            commands = parseOrEmptyObject(store.customCommandsJson),
            favouriteEmotes = store.favoriteEmoteKeys.sorted(),
        )
        return SettingsBackupDocument(
            createdAt = createdAt.toString(),
            appVersion = appVersion,
            contentHash = contentHash(content),
            content = content,
        )
    }

    fun encode(document: SettingsBackupDocument, pretty: Boolean = true): String =
        (if (pretty) prettyJson else compactJson).encodeToString(document)

    fun decode(raw: String): SettingsBackupDocument {
        require(raw.toByteArray(Charsets.UTF_8).size <= BackupFileIo.MAX_BACKUP_FILE_BYTES) {
            "Файл настроек больше ${BackupFileIo.MAX_BACKUP_FILE_BYTES / 1024} КБ"
        }
        JsonInputGuard.requireWithinLimits(
            raw = raw,
            maxChars = BackupFileIo.MAX_BACKUP_FILE_BYTES,
            maxNestingDepth = MAX_BACKUP_JSON_DEPTH,
            inputName = "Файл настроек",
        )
        val document = compactJson.decodeFromString<SettingsBackupDocument>(raw)
        validateDocument(document)
        return document
    }

    fun apply(
        store: SettingsStore,
        document: SettingsBackupDocument,
        currentAppVersion: String,
        createPreImportBackup: Boolean = true,
    ): SettingsBackupImportResult {
        validateDocument(document)
        if (createPreImportBackup) {
            store.lastImportBackupJson = encode(capture(store, currentAppVersion), pretty = false)
        }
        val settings = document.content.settings
        val channels = document.content.channels
        store.withSyncNotificationsSuppressed {
            store.appLanguage = runCatching { AppLanguage.valueOf(settings.appLanguage) }.getOrDefault(AppLanguage.RUSSIAN)
            store.themeMode = AppThemeMode.valueOf(settings.themeMode)
            store.fontScalePercent = settings.fontScalePercent
            store.messageDensity = MessageDensity.valueOf(settings.messageDensity)
            store.showAvatars = settings.showAvatars
            store.showBadges = settings.showBadges
            store.showTimestamps = settings.showTimestamps
            store.chatNameStyle = ChatNameStyle.valueOf(settings.nameStyle)
            store.wrapMessageLines = settings.wrapMessageLines
            store.showDeletedMessageContent = settings.showDeletedMessageContent
            store.showSystemMessages = settings.showSystemMessages
            store.mentionColorArgb = settings.mentionColorArgb
            store.autoScrollEnabled = settings.autoScrollEnabled
            store.animateEmotes = settings.animateEmotes
            store.emoteScalePercent = settings.emoteScalePercent
            store.betterTtvEnabled = settings.betterTtvEnabled
            store.frankerFaceZEnabled = settings.frankerFaceZEnabled
            store.sevenTvEnabled = settings.sevenTvEnabled
            store.sendOnEnter = settings.sendOnEnter
            store.showComposerEmoteImages = settings.showComposerEmoteImages
            store.replyNotificationsEnabled = settings.replyNotificationsEnabled
            store.autoModNotificationsEnabled = settings.autoModNotificationsEnabled
            store.localHistoryEnabled = settings.localHistoryEnabled
            store.localHistoryLimit = settings.localHistoryLimit
            store.localHistoryRetentionDays = settings.localHistoryRetentionDays
            store.localHistoryMaxSizeMb = settings.localHistoryMaxSizeMb
            store.userCardTimeoutPresetsSeconds = settings.userCardTimeoutPresetsSeconds
            store.userCardShowBanAction = settings.userCardShowBanAction
            store.userCardModerationActionOrder = settings.userCardModerationActionOrder
            store.recentMessagesEnabled = settings.recentMessagesEnabled

            store.channelLogins = channels.logins
            store.markChannelsExplicitlyEmpty(channels.logins.isEmpty())
            store.selectedChannelLogin = channels.selectedLogin?.takeIf { selected ->
                channels.logins.any { it.equals(selected, ignoreCase = true) }
            }
            store.favoriteChannelIds = channels.favouriteChannelIds.toSet()
            store.pinnedChannelIds = channels.pinnedChannelIds
            store.recentChannelIds = channels.recentChannelIds
            store.channelTabTitles = channels.tabTitles
            store.workspaceLayoutJson = document.content.workspaces.takeUnless { it is JsonNull }?.toString()
            store.savedMessageFilters = MessageFilterCodec.decode(document.content.filters.toString()).getOrThrow()
            store.highlightRules = MessageRuleCodec.decodeHighlights(document.content.highlights.toString())
            store.ignoreRules = MessageRuleCodec.decodeIgnores(document.content.ignoreRules.toString())
            store.customCommandsJson = document.content.commands.takeUnless { it is JsonNull }?.toString()
            store.favoriteEmoteKeys = document.content.favouriteEmotes.toSet()
        }
        return SettingsBackupImportResult(
            channelCount = channels.logins.size,
            workspaceCount = WorkspaceLayoutCodec.decodeOrDefault(document.content.workspaces.toString()).workspaces.size,
            filterCount = MessageFilterCodec.decode(document.content.filters.toString()).getOrThrow().size,
            highlightCount = MessageRuleCodec.decodeHighlights(document.content.highlights.toString()).size,
            ignoreCount = MessageRuleCodec.decodeIgnores(document.content.ignoreRules.toString()).size,
            commandCount = CustomCommandCodec.decode(document.content.commands.toString()).getOrThrow().size,
            favouriteEmoteCount = document.content.favouriteEmotes.size,
        )
    }

    private fun validateDocument(document: SettingsBackupDocument) {
        require(document.format == BACKUP_FORMAT) { "Это не резервная копия Ferventio" }
        require(document.formatVersion in 1..BACKUP_FORMAT_VERSION) {
            "Неподдерживаемая версия резервной копии: ${document.formatVersion}"
        }
        validate(document.content)
        require(document.contentHash == contentHash(document.content)) {
            "Контрольная сумма резервной копии не совпадает"
        }
        runCatching { Instant.parse(document.createdAt) }
            .getOrElse { throw IllegalArgumentException("Некорректная дата резервной копии") }
    }

    fun contentHash(content: SettingsBackupContent): String {
        val canonical = compactJson.encodeToString(content).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun validate(content: SettingsBackupContent) {
        val settings = content.settings
        require(runCatching { AppLanguage.valueOf(settings.appLanguage) }.isSuccess) { "Неизвестный язык приложения" }
        require(runCatching { AppThemeMode.valueOf(settings.themeMode) }.isSuccess) { "Неизвестная тема" }
        require(settings.fontScalePercent in 80..150) { "Размер шрифта вне допустимого диапазона" }
        require(runCatching { MessageDensity.valueOf(settings.messageDensity) }.isSuccess) { "Неизвестная плотность сообщений" }
        require(runCatching { ChatNameStyle.valueOf(settings.nameStyle) }.isSuccess) { "Неизвестный стиль имени" }
        require(settings.mentionColorArgb in 0L..0xFFFF_FFFFL) { "Некорректный цвет упоминаний" }
        require(settings.emoteScalePercent in 75..200) { "Размер emotes вне допустимого диапазона" }
        require(settings.localHistoryLimit in 100..5_000) { "Некорректный лимит истории" }
        require(settings.localHistoryRetentionDays in 0..365) { "Некорректный срок истории" }
        require(settings.localHistoryMaxSizeMb in 0..1_024) { "Некорректный размер истории" }
        require(settings.userCardTimeoutPresetsSeconds.size <= 10) { "Слишком много timeout-интервалов" }
        require(settings.userCardModerationActionOrder.size <= 32) { "Слишком длинный порядок действий" }

        val channels = content.channels
        require(channels.logins.size <= 20) { "В копии больше 20 каналов" }
        require(channels.logins.all(CHANNEL_LOGIN_PATTERN::matches)) { "Некорректный Twitch login в списке каналов" }
        require(channels.logins.map(String::lowercase).distinct().size == channels.logins.size) { "Каналы в копии повторяются" }
        require(
            channels.favouriteChannelIds.size <= 20 &&
                channels.pinnedChannelIds.size <= 20 &&
                channels.recentChannelIds.size <= 20
        ) { "Слишком много ссылок на каналы" }
        require(channels.tabTitles.size <= 20 && channels.tabTitles.values.all { it.length <= 32 }) { "Некорректные названия вкладок" }

        if (content.workspaces !is JsonNull) {
            require(content.workspaces is JsonObject) { "Workspaces должны быть JSON-объектом" }
            val root = content.workspaces.jsonObject
            val schema = root["schemaVersion"]?.toString()?.trim('"')?.toIntOrNull() ?: 1
            require(schema in 1..2) { "Неподдерживаемая версия Workspaces: $schema" }
            require(WorkspaceLayoutCodec.decodeOrDefault(content.workspaces.toString()).workspaces.size <= 10) {
                "Слишком много workspaces"
            }
        }
        require(MessageFilterCodec.decode(content.filters.toString()).getOrThrow().size <= 100) { "Слишком много фильтров" }
        require(MessageRuleCodec.decodeHighlights(content.highlights.toString()).size <= 100) { "Слишком много highlight-правил" }
        require(MessageRuleCodec.decodeIgnores(content.ignoreRules.toString()).size <= 100) { "Слишком много ignore-правил" }
        require(CustomCommandCodec.decode(content.commands.toString()).getOrThrow().size <= 100) { "Слишком много команд" }
        require(content.favouriteEmotes.size <= 2_000 && content.favouriteEmotes.all { it.length in 1..160 }) {
            "Некорректный список избранных emotes"
        }
    }

    private fun parseOrNull(raw: String?): JsonElement = raw
        ?.takeIf(String::isNotBlank)
        ?.let { value -> runCatching { compactJson.parseToJsonElement(value) }.getOrNull() }
        ?: JsonNull

    private fun parseOrEmptyObject(raw: String?): JsonElement = raw
        ?.takeIf(String::isNotBlank)
        ?.let { value -> runCatching { compactJson.parseToJsonElement(value) }.getOrNull() }
        ?: JsonObject(emptyMap())

    private fun parseOrEmptyArray(raw: String?): JsonElement = raw
        ?.takeIf(String::isNotBlank)
        ?.let { value -> runCatching { compactJson.parseToJsonElement(value) }.getOrNull() }
        ?: JsonArray(emptyList())

    private val CHANNEL_LOGIN_PATTERN = Regex("[A-Za-z0-9_]{1,25}")
    internal const val MAX_BACKUP_JSON_DEPTH = 64
}

const val BACKUP_FORMAT = "ferventio-settings-backup"
const val BACKUP_FORMAT_VERSION = 1
