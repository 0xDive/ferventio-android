package io.ferventio.shared.workspace

import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder

internal data class WorkspaceSettingsBackupLocalApplyOutcome(
    val resolvedChannelCount: Int,
    val unresolvedLogins: List<String>,
)

/**
 * Applies a validated backup projection to shared runtime state without discarding unresolved
 * Twitch channel logins from the durable import transaction.
 *
 * Visible workspace channels remain real Twitch entities. Fresh directory metadata wins over
 * cached metadata, cached channels keep the UI usable during transient Helix failures, and any
 * login that cannot be resolved is reported to the import flow instead of being reinterpreted as
 * a user deletion.
 */
internal class WorkspaceSettingsBackupLocalApplyCoordinator(
    private val directory: TwitchChannelDirectoryClient = TwitchChannelDirectoryClient(),
) {
    suspend fun apply(
        prepared: WorkspaceSettingsPreparedImport,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder,
        rulesState: SharedMessageRulesStateHolder,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsBackupLocalApplyOutcome {
        val session = authentication.accessLease?.session
            ?: error("Settings import requires a Twitch access lease")
        val currentRevision = maxOf(state.settingsRevision, settingsState.syncRevision)
        val refreshedChannels = runCatching {
            directory.resolveByLogins(authentication, prepared.channels.logins)
        }.getOrDefault(emptyList())
        val resolved = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = prepared.channels.logins,
            cachedChannels = state.channels,
            refreshedChannels = refreshedChannels,
            selectedLogin = prepared.channels.selectedLogin,
        )

        settingsState.restore(prepared.preferences, currentRevision)
        rulesState.restore(prepared.messageRules)
        filtersState.restore(prepared.savedFilters)
        state.replaceChannels(resolved.channels)
        resolved.selectedChannelId?.let(state::selectChannel)
        state.updatePinnedChannelIds(prepared.channels.pinnedChannelIds)
        state.updateChannelTabTitles(prepared.channels.tabTitles)
        state.restoreWorkspaceLayout(prepared.workspaceLayout)

        val moderatedChannelIds = runCatching {
            directory.resolveModeratedChannelIds(authentication)
        }.getOrDefault(state.moderatorChannelIds) + session.userId
        state.updateModeratorChannelIds(moderatedChannelIds)
        state.markLoadReady(currentRevision)

        val resolvedLogins = resolved.channels
            .mapTo(hashSetOf()) { channel -> channel.login.trim().lowercase() }
        val unresolved = prepared.channels.logins.filterNot(resolvedLogins::contains)
        return WorkspaceSettingsBackupLocalApplyOutcome(
            resolvedChannelCount = resolved.channels.size,
            unresolvedLogins = unresolved,
        )
    }
}
