package io.ferventio.shared.settings

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.CustomCommandCodec
import io.ferventio.app.domain.MessageDensity
import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.MessageRuleCodec
import io.ferventio.app.domain.WorkspaceLayoutCodec
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
internal data class SharedSettingsBackupDocument(
    val format: String = SharedSettingsBackupCodec.BACKUP_FORMAT,
    val formatVersion: Int = SharedSettingsBackupCodec.BACKUP_FORMAT_VERSION,
    val createdAt: String,
    val appVersion: String,
    val contentHash: String,
    val content: SharedSettingsBackupContent,
)

@Serializable
internal data class SharedSettingsBackupContent(
    val settings: SharedSettingsBackupSettings,
    val channels: SharedSettingsBackupChannels,
    val workspaces: JsonElement = JsonNull,
    val filters: JsonElement = JsonObject(emptyMap()),
    val highlights: JsonElement = JsonArray(emptyList()),
    val ignoreRules: JsonElement = JsonArray(emptyList()),
    val commands: JsonElement = JsonObject(emptyMap()),
    val favouriteEmotes: List<String> = emptyList(),
)

@Serializable
internal data class SharedSettingsBackupSettings(
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
    val repeatCollapseEnabled: Boolean = true,
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
internal data class SharedSettingsBackupChannels(
    val logins: List<String>,
    val selectedLogin: String? = null,
    val favouriteChannelIds: List<String> = emptyList(),
    val pinnedChannelIds: List<String> = emptyList(),
    val recentChannelIds: List<String> = emptyList(),
    val tabTitles: Map<String, String> = emptyMap(),
)

internal data class SharedSettingsBackupImportSummary(
    val channelCount: Int,
    val workspaceCount: Int,
    val filterCount: Int,
    val highlightCount: Int,
    val ignoreCount: Int,
    val commandCount: Int,
    val favouriteEmoteCount: Int,
)

internal data class SharedSettingsBackupDecodeResult(
    val document: SharedSettingsBackupDocument,
    val summary: SharedSettingsBackupImportSummary,
)

internal object SharedSettingsBackupCodec {
    const val BACKUP_FORMAT = "ferventio-settings-backup"
    const val BACKUP_FORMAT_VERSION = 2

    @OptIn(ExperimentalSerializationApi::class)
    private val compactJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val channelLoginPattern = Regex("[A-Za-z0-9_]{1,25}")
    private val v1RepeatCollapseField = Regex(",\"repeatCollapseEnabled\":(?:true|false)")

    fun decode(raw: String): SharedSettingsBackupDecodeResult {
        SharedSettingsBackupInputGuard.requireWithinLimits(raw)
        val decoded = runCatching {
            compactJson.decodeFromString<SharedSettingsBackupDocument>(raw)
        }.getOrElse { error ->
            throw IllegalArgumentException("Invalid Ferventio settings backup", error)
        }
        val document = if (decoded.formatVersion == 1) {
            decoded.copy(
                content = decoded.content.copy(
                    settings = decoded.content.settings.copy(repeatCollapseEnabled = true),
                ),
            )
        } else {
            decoded
        }
        val summary = validateDocument(document)
        return SharedSettingsBackupDecodeResult(document = document, summary = summary)
    }

    internal fun encodeForTesting(document: SharedSettingsBackupDocument): String =
        compactJson.encodeToString(document)

    internal fun contentHashForTesting(
        content: SharedSettingsBackupContent,
        formatVersion: Int = BACKUP_FORMAT_VERSION,
    ): String = contentHashForVersion(content, formatVersion)

    private fun validateDocument(document: SharedSettingsBackupDocument): SharedSettingsBackupImportSummary {
        require(document.format == BACKUP_FORMAT) { "Unsupported Ferventio settings backup format" }
        require(document.formatVersion in 1..BACKUP_FORMAT_VERSION) {
            "Unsupported Ferventio settings backup version: ${document.formatVersion}"
        }
        val summary = validateContent(document.content)
        require(document.contentHash == contentHashForVersion(document.content, document.formatVersion)) {
            "Settings backup checksum does not match"
        }
        runCatching { Instant.parse(document.createdAt) }
            .getOrElse { throw IllegalArgumentException("Invalid settings backup date", it) }
        return summary
    }

    private fun contentHashForVersion(
        content: SharedSettingsBackupContent,
        formatVersion: Int,
    ): String {
        require(formatVersion in 1..BACKUP_FORMAT_VERSION) {
            "Unsupported Ferventio settings backup version: $formatVersion"
        }
        val canonicalText = compactJson.encodeToString(content).let { encoded ->
            if (formatVersion == 1) {
                encoded.replace(v1RepeatCollapseField, "")
            } else {
                encoded
            }
        }
        return sha256Hex(canonicalText)
    }

    private fun validateContent(content: SharedSettingsBackupContent): SharedSettingsBackupImportSummary {
        val settings = content.settings
        require(runCatching { AppLanguage.valueOf(settings.appLanguage) }.isSuccess) {
            "Unknown app language"
        }
        require(runCatching { AppThemeMode.valueOf(settings.themeMode) }.isSuccess) {
            "Unknown app theme"
        }
        require(settings.fontScalePercent in 80..150) { "Font size is outside the supported range" }
        require(runCatching { MessageDensity.valueOf(settings.messageDensity) }.isSuccess) {
            "Unknown message density"
        }
        require(runCatching { ChatNameStyle.valueOf(settings.nameStyle) }.isSuccess) {
            "Unknown chat name style"
        }
        require(settings.mentionColorArgb in 0L..0xFFFF_FFFFL) { "Invalid mention color" }
        require(settings.emoteScalePercent in 75..200) { "Emote size is outside the supported range" }
        require(settings.localHistoryLimit in 100..5_000) { "Invalid history limit" }
        require(settings.localHistoryRetentionDays in 0..365) { "Invalid history retention" }
        require(settings.localHistoryMaxSizeMb in 0..1_024) { "Invalid history size" }
        require(settings.userCardTimeoutPresetsSeconds.size <= 10) { "Too many timeout presets" }
        require(settings.userCardModerationActionOrder.size <= 32) { "Moderation action order is too long" }

        val channels = content.channels
        require(channels.logins.size <= 20) { "Backup contains more than 20 channels" }
        require(channels.logins.all(channelLoginPattern::matches)) { "Invalid Twitch channel login" }
        require(channels.logins.map { it.lowercase() }.distinct().size == channels.logins.size) {
            "Backup contains duplicate channels"
        }
        require(
            channels.favouriteChannelIds.size <= 20 &&
                channels.pinnedChannelIds.size <= 20 &&
                channels.recentChannelIds.size <= 20,
        ) { "Too many channel references" }
        require(channels.tabTitles.size <= 20 && channels.tabTitles.values.all { it.length <= 32 }) {
            "Invalid channel tab titles"
        }

        if (content.workspaces !is JsonNull) {
            require(content.workspaces is JsonObject) { "Workspaces must be a JSON object" }
            val root = content.workspaces.jsonObject
            val schema = root["schemaVersion"]?.toString()?.trim('"')?.toIntOrNull() ?: 1
            require(schema in 1..2) { "Unsupported Workspaces version: $schema" }
            require(WorkspaceLayoutCodec.decodeOrDefault(content.workspaces.toString()).workspaces.size <= 10) {
                "Too many workspaces"
            }
        }

        val filters = MessageFilterCodec.decode(content.filters.toString()).getOrThrow()
        require(filters.size <= 100) { "Too many saved message filters" }
        val highlights = MessageRuleCodec.decodeHighlights(content.highlights.toString())
        require(highlights.size <= 100) { "Too many highlight rules" }
        val ignores = MessageRuleCodec.decodeIgnores(content.ignoreRules.toString())
        require(ignores.size <= 100) { "Too many ignore rules" }
        val commands = CustomCommandCodec.decode(content.commands.toString()).getOrThrow()
        require(commands.size <= 100) { "Too many custom commands" }
        require(content.favouriteEmotes.size <= 2_000 && content.favouriteEmotes.all { it.length in 1..160 }) {
            "Invalid favourite emote list"
        }

        return SharedSettingsBackupImportSummary(
            channelCount = channels.logins.size,
            workspaceCount = WorkspaceLayoutCodec.decodeOrDefault(content.workspaces.toString()).workspaces.size,
            filterCount = filters.size,
            highlightCount = highlights.size,
            ignoreCount = ignores.size,
            commandCount = commands.size,
            favouriteEmoteCount = content.favouriteEmotes.size,
        )
    }
}
