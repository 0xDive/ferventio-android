package io.ferventio.shared.workspace

import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedMessageRulesPayloadCodec
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedSavedFiltersPayloadCodec
import io.ferventio.shared.settings.SharedSavedFiltersSnapshot
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ferventio.shared.settings.SharedSettingsPayloadCodec

data class WorkspaceSettingsBackupImportPreview(
    val formatVersion: Int,
    val createdAt: String,
    val appVersion: String,
    val contentHash: String,
    val channelCount: Int,
    val workspaceCount: Int,
    val filterCount: Int,
    val highlightCount: Int,
    val ignoreCount: Int,
    val commandCount: Int,
    val favouriteEmoteCount: Int,
)

internal data class WorkspaceSettingsPreparedImport(
    val sourcePayload: String,
    val preview: WorkspaceSettingsBackupImportPreview,
    val channels: PersistedWorkspaceChannels,
    val preferences: SharedAppPreferences,
    val messageRules: SharedMessageRulesSnapshot,
    val savedFilters: SharedSavedFiltersSnapshot,
    val workspaceLayout: WorkspaceLayout,
)

/**
 * Validates an Android-compatible settings backup once, then projects it onto the same shared
 * runtime models used by normal backend bootstrap. No local or remote state is mutated here.
 */
internal object WorkspaceSettingsBackupImportPreparation {
    fun prepare(
        raw: String,
        fallbackChannelId: String? = null,
    ): WorkspaceSettingsPreparedImport {
        val decoded = SharedSettingsBackupCodec.decode(raw)
        val migratedRepeatCollapse = decoded.document.content.settings.repeatCollapseEnabled
        val preferences = SharedSettingsPayloadCodec.parsePreferences(raw)
            .copy(repeatCollapseEnabled = migratedRepeatCollapse)
            .normalized()
        val summary = decoded.summary

        return WorkspaceSettingsPreparedImport(
            sourcePayload = raw,
            preview = WorkspaceSettingsBackupImportPreview(
                formatVersion = decoded.document.formatVersion,
                createdAt = decoded.document.createdAt,
                appVersion = decoded.document.appVersion,
                contentHash = decoded.document.contentHash,
                channelCount = summary.channelCount,
                workspaceCount = summary.workspaceCount,
                filterCount = summary.filterCount,
                highlightCount = summary.highlightCount,
                ignoreCount = summary.ignoreCount,
                commandCount = summary.commandCount,
                favouriteEmoteCount = summary.favouriteEmoteCount,
            ),
            channels = WorkspaceSettingsPayloadParser.parse(raw),
            preferences = preferences,
            messageRules = SharedMessageRulesPayloadCodec.parse(raw),
            savedFilters = SharedSavedFiltersPayloadCodec.parse(raw),
            workspaceLayout = SharedWorkspaceLayoutPayloadCodec.parse(
                payload = raw,
                fallbackChannelId = fallbackChannelId,
            ),
        )
    }
}
