package io.ferventio.shared.workspace

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersSnapshot
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import kotlin.Throws

data class WorkspaceBootstrapOutcome(
    val remoteSettingsAvailable: Boolean,
    val settingsRevision: Long,
    val channelCount: Int,
    val messageRules: SharedMessageRulesSnapshot = SharedMessageRulesSnapshot(),
    val savedFilters: SharedSavedFiltersSnapshot = SharedSavedFiltersSnapshot(),
)

class WorkspaceChannelMutationException(message: String) : IllegalStateException(message)

/** Restores and mutates shared workspace/settings state from Android-compatible sync snapshots. */
class WorkspaceBootstrapCoordinator(
    private val settings: WorkspaceSettingsSyncClient = WorkspaceSettingsSyncClient(),
    private val directory: TwitchChannelDirectoryClient = TwitchChannelDirectoryClient(),
) {
    constructor() : this(
        settings = WorkspaceSettingsSyncClient(),
        directory = TwitchChannelDirectoryClient(),
    )

    @Throws(Exception::class)
    suspend fun bootstrap(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
    ): WorkspaceBootstrapOutcome = bootstrap(
        identity = identity,
        authentication = authentication,
        state = state,
        settingsState = null,
    )

    @Throws(Exception::class)
    suspend fun bootstrap(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder?,
    ): WorkspaceBootstrapOutcome {
        val snapshot = settings.fetch(identity, authentication)
            ?: return WorkspaceBootstrapOutcome(
                remoteSettingsAvailable = false,
                settingsRevision = 0L,
                channelCount = state.channels.size,
            )

        applySnapshot(
            snapshot = snapshot,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            requireDirectoryRefresh = true,
        )
        return WorkspaceBootstrapOutcome(
            remoteSettingsAvailable = true,
            settingsRevision = snapshot.revision,
            channelCount = state.channels.size,
            messageRules = snapshot.messageRules,
            savedFilters = snapshot.savedFilters,
        )
    }

    @Throws(Exception::class)
    suspend fun savePreferences(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        preferences: SharedAppPreferences,
        settingsState: SharedAppSettingsStateHolder,
    ): WorkspaceSettingsSnapshot {
        settingsState.markSaveStarted()
        return try {
            settings.updatePreferences(
                identity = identity,
                authentication = authentication,
                preferences = preferences,
            ).also { snapshot ->
                settingsState.markSaveSucceeded(snapshot.preferences, snapshot.revision)
            }
        } catch (error: Throwable) {
            settingsState.markSaveFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    suspend fun upsertHighlightRule(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        rule: HighlightRule,
        rulesState: SharedMessageRulesStateHolder,
    ): WorkspaceSettingsSnapshot = mutateMessageRules(rulesState) {
        settings.updateMessageRules(identity, authentication) { remote ->
            val index = remote.highlightRules.indexOfFirst { it.id == rule.id }
            remote.copy(
                highlightRules = if (index < 0) {
                    remote.highlightRules + rule
                } else {
                    remote.highlightRules.toMutableList().apply { this[index] = rule }
                },
            )
        }
    }

    @Throws(Exception::class)
    suspend fun deleteHighlightRule(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        ruleId: String,
        rulesState: SharedMessageRulesStateHolder,
    ): WorkspaceSettingsSnapshot {
        val id = requireRuleId(ruleId)
        return mutateMessageRules(rulesState) {
            settings.updateMessageRules(identity, authentication) { remote ->
                remote.copy(highlightRules = remote.highlightRules.filterNot { it.id == id })
            }
        }
    }

    @Throws(Exception::class)
    suspend fun upsertIgnoreRule(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        rule: IgnoreRule,
        rulesState: SharedMessageRulesStateHolder,
    ): WorkspaceSettingsSnapshot = mutateMessageRules(rulesState) {
        settings.updateMessageRules(identity, authentication) { remote ->
            val index = remote.ignoreRules.indexOfFirst { it.id == rule.id }
            remote.copy(
                ignoreRules = if (index < 0) {
                    remote.ignoreRules + rule
                } else {
                    remote.ignoreRules.toMutableList().apply { this[index] = rule }
                },
            )
        }
    }

    @Throws(Exception::class)
    suspend fun deleteIgnoreRule(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        ruleId: String,
        rulesState: SharedMessageRulesStateHolder,
    ): WorkspaceSettingsSnapshot {
        val id = requireRuleId(ruleId)
        return mutateMessageRules(rulesState) {
            settings.updateMessageRules(identity, authentication) { remote ->
                remote.copy(ignoreRules = remote.ignoreRules.filterNot { it.id == id })
            }
        }
    }

    @Throws(Exception::class)
    suspend fun upsertSavedFilter(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        filter: SavedMessageFilter,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsSnapshot {
        val normalized = SharedSavedFiltersStateHolder().upsert(filter)
        return mutateSavedFilters(filtersState) {
            settings.updateSavedFilters(identity, authentication) { remote ->
                SharedSavedFiltersStateHolder(remote).apply {
                    upsert(normalized)
                }.snapshot
            }
        }
    }

    @Throws(Exception::class)
    suspend fun deleteSavedFilter(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        filterId: String,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsSnapshot {
        val id = filterId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Saved message filter id must not be blank")
        return mutateSavedFilters(filtersState) {
            settings.updateSavedFilters(identity, authentication) { remote ->
                remote.copy(filters = remote.filters.filterNot { it.id == id })
            }
        }
    }

    @Throws(Exception::class)
    suspend fun addChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        loginInput: String,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val login = normalizeLogin(loginInput)
        val resolved = directory.resolveByLogins(authentication, listOf(login)).firstOrNull()
            ?: throw WorkspaceChannelMutationException("Twitch channel #$login was not found")
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            if (login in remote.logins) {
                remote.copy(selectedLogin = login)
            } else {
                if (remote.logins.size >= MAX_CHANNELS) {
                    throw WorkspaceChannelMutationException("A workspace can contain at most $MAX_CHANNELS channels")
                }
                remote.copy(logins = remote.logins + login, selectedLogin = login)
            }
        }
        applySnapshot(
            snapshot = snapshot,
            authentication = authentication,
            state = state,
            settingsState = settingsState,
            cachedChannels = state.channels + resolved,
        )
        return snapshot
    }

    @Throws(Exception::class)
    suspend fun removeChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val channel = requireChannel(state, channelId)
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            val logins = remote.logins.filterNot { it == channel.login.lowercase() }
            remote.copy(
                logins = logins,
                selectedLogin = remote.selectedLogin
                    ?.takeUnless { it == channel.login.lowercase() }
                    ?: logins.firstOrNull(),
                pinnedChannelIds = remote.pinnedChannelIds.filterNot { it == channel.id },
                tabTitles = remote.tabTitles - channel.id,
            )
        }
        applySnapshot(snapshot, authentication, state, settingsState)
        return snapshot
    }

    @Throws(Exception::class)
    suspend fun moveChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        channelId: String,
        targetIndex: Int,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val channel = requireChannel(state, channelId)
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            val currentIndex = remote.logins.indexOf(channel.login.lowercase())
            if (currentIndex < 0 || remote.logins.size < 2) return@updateChannels remote
            val target = targetIndex.coerceIn(0, remote.logins.lastIndex)
            if (target == currentIndex) return@updateChannels remote
            remote.copy(
                logins = remote.logins.toMutableList().apply {
                    add(target, removeAt(currentIndex))
                },
            )
        }
        applySnapshot(snapshot, authentication, state, settingsState)
        return snapshot
    }

    @Throws(Exception::class)
    suspend fun setChannelPinned(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        channelId: String,
        pinned: Boolean,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val channel = requireChannel(state, channelId)
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            val ids = remote.pinnedChannelIds.toMutableList().apply {
                removeAll { it == channel.id }
                if (pinned) add(channel.id)
            }
            remote.copy(pinnedChannelIds = ids.distinct())
        }
        applySnapshot(snapshot, authentication, state, settingsState)
        return snapshot
    }

    @Throws(Exception::class)
    suspend fun renameChannelTab(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        channelId: String,
        title: String?,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val channel = requireChannel(state, channelId)
        val normalizedTitle = title?.trim()?.take(MAX_TAB_TITLE_LENGTH).orEmpty()
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            remote.copy(
                tabTitles = if (normalizedTitle.isEmpty()) {
                    remote.tabTitles - channel.id
                } else {
                    remote.tabTitles + (channel.id to normalizedTitle)
                },
            )
        }
        applySnapshot(snapshot, authentication, state, settingsState)
        return snapshot
    }

    @Throws(Exception::class)
    suspend fun selectChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder? = null,
    ): WorkspaceSettingsSnapshot {
        val channel = requireChannel(state, channelId)
        val snapshot = settings.updateChannels(identity, authentication) { remote ->
            if (channel.login.lowercase() !in remote.logins) remote
            else remote.copy(selectedLogin = channel.login.lowercase())
        }
        applySnapshot(snapshot, authentication, state, settingsState)
        return snapshot
    }

    private suspend fun mutateMessageRules(
        rulesState: SharedMessageRulesStateHolder,
        block: suspend () -> WorkspaceSettingsSnapshot,
    ): WorkspaceSettingsSnapshot {
        rulesState.markSaveStarted()
        return try {
            block().also { snapshot ->
                rulesState.markSaveSucceeded(snapshot.messageRules)
            }
        } catch (error: Throwable) {
            rulesState.markSaveFailed(error.message)
            throw error
        }
    }

    private suspend fun mutateSavedFilters(
        filtersState: SharedSavedFiltersStateHolder,
        block: suspend () -> WorkspaceSettingsSnapshot,
    ): WorkspaceSettingsSnapshot {
        filtersState.markSaveStarted()
        return try {
            block().also { snapshot ->
                filtersState.markSaveSucceeded(snapshot.savedFilters)
            }
        } catch (error: Throwable) {
            filtersState.markSaveFailed(error.message)
            throw error
        }
    }

    private suspend fun applySnapshot(
        snapshot: WorkspaceSettingsSnapshot,
        authentication: StoredAuthentication,
        state: WorkspaceRuntimeStateHolder,
        settingsState: SharedAppSettingsStateHolder?,
        cachedChannels: List<io.ferventio.app.domain.ChatChannel> = state.channels,
        requireDirectoryRefresh: Boolean = false,
    ) {
        settingsState?.restore(snapshot.preferences, snapshot.revision)
        val refreshedResult = runCatching {
            directory.resolveByLogins(authentication, snapshot.channels.logins)
        }
        if (requireDirectoryRefresh) refreshedResult.getOrThrow()
        val resolved = WorkspaceChannelBootstrapPolicy.resolve(
            persistedLogins = snapshot.channels.logins,
            cachedChannels = cachedChannels,
            refreshedChannels = refreshedResult.getOrDefault(emptyList()),
            selectedLogin = snapshot.channels.selectedLogin,
        )
        state.replaceChannels(resolved.channels)
        resolved.selectedChannelId?.let(state::selectChannel)
        state.updatePinnedChannelIds(snapshot.channels.pinnedChannelIds)
        state.updateChannelTabTitles(snapshot.channels.tabTitles)

        val session = authentication.accessLease?.session
            ?: error("Workspace update requires a Twitch access lease")
        val moderatedChannelIds = runCatching {
            directory.resolveModeratedChannelIds(authentication)
        }.getOrDefault(state.moderatorChannelIds) + session.userId
        state.updateModeratorChannelIds(moderatedChannelIds)
        state.markLoadReady(snapshot.revision)
    }

    private fun requireChannel(
        state: WorkspaceRuntimeStateHolder,
        channelId: String,
    ) = state.channels.firstOrNull { it.id == channelId.trim() }
        ?: throw WorkspaceChannelMutationException("Channel is not in the workspace")

    private fun requireRuleId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Message rule id must not be blank")

    private fun normalizeLogin(input: String): String {
        val login = input.trim().removePrefix("#").lowercase()
        if (!CHANNEL_LOGIN_PATTERN.matches(login)) {
            throw WorkspaceChannelMutationException("Enter a valid Twitch channel login")
        }
        return login
    }

    private companion object {
        val CHANNEL_LOGIN_PATTERN = Regex("[a-z0-9_]{1,25}")
        const val MAX_CHANNELS = 20
        const val MAX_TAB_TITLE_LENGTH = 32
    }
}
