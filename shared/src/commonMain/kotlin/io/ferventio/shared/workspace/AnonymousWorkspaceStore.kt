package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceLayoutCodec
import kotlin.Throws

data class AnonymousWorkspaceSnapshot(
    val channelLogins: List<String> = emptyList(),
    val selectedChannelLogin: String? = null,
    val pinnedChannelLogins: List<String> = emptyList(),
    val channelTitlesByLogin: Map<String, String> = emptyMap(),
    val workspaceLayoutJson: String? = null,
)

/** Device-local channel storage used before a Twitch account is authorized. */
interface AnonymousWorkspaceStore {
    fun load(): AnonymousWorkspaceSnapshot

    fun save(snapshot: AnonymousWorkspaceSnapshot)
}

class AnonymousWorkspaceMutationException(message: String) : IllegalStateException(message)

/**
 * Owns the signed-out workspace without depending on backend settings or an OAuth lease.
 *
 * Anonymous IRC initially addresses channels by login. The workspace therefore uses a stable
 * temporary id until Twitch reports the real room id; [onRoomResolved] remaps only runtime
 * identity while persistence intentionally remains login-based.
 */
class AnonymousWorkspaceCoordinator(
    private val store: AnonymousWorkspaceStore = InMemoryAnonymousWorkspaceStore(),
) {
    @Throws(Exception::class)
    fun restore(state: WorkspaceRuntimeStateHolder): AnonymousWorkspaceSnapshot {
        state.markLoadStarted()
        return try {
            val loaded = store.load()
            val normalized = normalizeSnapshot(loaded)
            if (normalized != loaded) store.save(normalized)
            applySnapshot(state, normalized)
            state.markLoadReady(settingsRevision = 0L)
            normalized
        } catch (error: Exception) {
            state.markLoadFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    fun addChannel(
        loginInput: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        val login = requireLogin(loginInput)
        val existing = state.channels.firstOrNull { channel ->
            channel.login.equals(login, ignoreCase = true)
        }
        if (existing != null) {
            state.selectChannel(existing.id)
            return@mutate persist(state)
        }
        if (state.channels.size >= MAX_CHANNELS) {
            throw AnonymousWorkspaceMutationException(
                "A workspace can contain at most $MAX_CHANNELS channels",
            )
        }
        val channel = anonymousChannel(login)
        state.addOrReplaceChannel(channel)
        state.selectChannel(channel.id)
        if (state.channels.size == 1) {
            state.restoreWorkspaceLayout(WorkspaceLayout.default(channel.id))
        }
        persist(state)
    }

    @Throws(Exception::class)
    fun removeChannel(
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        state.removeChannel(channelId)
        persist(state)
    }

    @Throws(Exception::class)
    fun selectChannel(
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        state.selectChannel(channelId)
        persist(state)
    }

    @Throws(Exception::class)
    fun moveChannel(
        channelId: String,
        targetIndex: Int,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        state.moveChannel(channelId, targetIndex)
        persist(state)
    }

    @Throws(Exception::class)
    fun setChannelPinned(
        channelId: String,
        pinned: Boolean,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        val normalizedId = requireWorkspaceChannelId(channelId, state)
        state.updatePinnedChannelIds(
            if (pinned) {
                state.pinnedChannelIds + normalizedId
            } else {
                state.pinnedChannelIds - normalizedId
            },
        )
        persist(state)
    }

    @Throws(Exception::class)
    fun renameChannel(
        channelId: String,
        title: String?,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        val normalizedId = requireWorkspaceChannelId(channelId, state)
        state.setChannelTabTitle(normalizedId, title)
        persist(state)
    }

    @Throws(Exception::class)
    fun setSplitFilterQuery(
        splitId: String,
        filterQuery: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutateLayout(state) { layout ->
        updateWorkspaceSplitFilterQuery(layout, splitId, filterQuery)
    }

    @Throws(Exception::class)
    fun setSplitChannel(
        splitId: String,
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot {
        val normalizedChannelId = requireWorkspaceChannelId(channelId, state)
        return mutateLayout(state) { layout ->
            updateWorkspaceSplitChannel(layout, splitId, normalizedChannelId)
        }.also {
            state.selectChannel(normalizedChannelId)
            persist(state)
        }
    }

    @Throws(Exception::class)
    fun focusSplit(
        splitId: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutateLayout(state) { layout ->
        focusWorkspaceSplit(layout, splitId)
    }

    @Throws(Exception::class)
    fun addSplit(state: WorkspaceRuntimeStateHolder): AnonymousWorkspaceSnapshot =
        mutateLayout(state) { layout ->
            addWorkspaceChatSplit(layout, state.selectedChannelId)
        }

    @Throws(Exception::class)
    fun removeSplit(
        splitId: String,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutateLayout(state) { layout ->
        removeWorkspaceSplit(layout, splitId)
    }

    @Throws(Exception::class)
    fun setPrimaryFraction(
        fraction: Float,
        state: WorkspaceRuntimeStateHolder,
    ): AnonymousWorkspaceSnapshot = mutateLayout(state) { layout ->
        updateWorkspaceSplitPrimaryFraction(layout, fraction)
    }

    /** Replaces a temporary anonymous id with Twitch's canonical room id without changing storage. */
    @Throws(IllegalArgumentException::class)
    fun onRoomResolved(
        channelLogin: String,
        roomId: String,
        state: WorkspaceRuntimeStateHolder,
    ): Boolean {
        val login = normalizeStoredLogin(channelLogin) ?: return false
        val normalizedRoomId = roomId.trim().takeIf(String::isNotEmpty) ?: return false
        val channel = state.channels.firstOrNull { candidate ->
            candidate.login.equals(login, ignoreCase = true)
        } ?: return false
        return state.remapChannelId(channel.id, normalizedRoomId)
    }

    private inline fun mutate(
        state: WorkspaceRuntimeStateHolder,
        block: () -> AnonymousWorkspaceSnapshot,
    ): AnonymousWorkspaceSnapshot {
        state.markMutationStarted()
        return try {
            block().also { state.markMutationSucceeded() }
        } catch (error: Exception) {
            state.markMutationFailed(error.message)
            throw error
        }
    }

    private inline fun mutateLayout(
        state: WorkspaceRuntimeStateHolder,
        transform: (WorkspaceLayout) -> WorkspaceLayout,
    ): AnonymousWorkspaceSnapshot = mutate(state) {
        state.restoreWorkspaceLayout(
            transform(state.workspaceLayout).normalized(state.channelIds.toSet()),
        )
        persist(state)
    }

    private fun applySnapshot(
        state: WorkspaceRuntimeStateHolder,
        snapshot: AnonymousWorkspaceSnapshot,
    ) {
        val channels = snapshot.channelLogins.map(::anonymousChannel)
        val channelIdByLogin = channels.associate { channel -> channel.login to channel.id }
        state.replaceChannels(channels)
        val selectedChannel = snapshot.selectedChannelLogin?.let { selectedLogin ->
            channels.firstOrNull { channel -> channel.login == selectedLogin }
        }
        selectedChannel?.let { state.selectChannel(it.id) }
        state.updatePinnedChannelIds(
            snapshot.pinnedChannelLogins.mapNotNull(channelIdByLogin::get),
        )
        state.updateChannelTabTitles(
            snapshot.channelTitlesByLogin.mapNotNull { (login, title) ->
                channelIdByLogin[login]?.let { channelId -> channelId to title }
            }.toMap(),
        )
        state.restoreWorkspaceLayout(
            WorkspaceLayoutCodec.decodeOrDefault(
                raw = snapshot.workspaceLayoutJson,
                fallbackChannelId = state.selectedChannelId,
            ).normalized(state.channelIds.toSet()),
        )
    }

    private fun persist(state: WorkspaceRuntimeStateHolder): AnonymousWorkspaceSnapshot {
        val loginById = state.channels.associate { channel -> channel.id to channel.login.lowercase() }
        val stableIdById = state.channels.associate { channel ->
            channel.id to anonymousWorkspaceChannelId(channel.login)
        }
        val selectedLogin = state.selectedChannelId?.let(loginById::get)
        val stableLayout = remapWorkspaceLayoutChannelIds(
            layout = state.workspaceLayout,
            replacementIdByCurrentId = stableIdById,
        ).normalized(stableIdById.values.toSet())
        return normalizeSnapshot(
            AnonymousWorkspaceSnapshot(
                channelLogins = state.channels.map(ChatChannel::login),
                selectedChannelLogin = selectedLogin,
                pinnedChannelLogins = state.pinnedChannelIds.mapNotNull(loginById::get),
                channelTitlesByLogin = state.channelTabTitles.mapNotNull { (channelId, title) ->
                    loginById[channelId]?.let { login -> login to title }
                }.toMap(),
                workspaceLayoutJson = WorkspaceLayoutCodec.encode(stableLayout),
            ),
        ).also(store::save)
    }

    private fun normalizeSnapshot(value: AnonymousWorkspaceSnapshot): AnonymousWorkspaceSnapshot {
        val logins = value.channelLogins
            .mapNotNull(::normalizeStoredLogin)
            .distinct()
            .take(MAX_CHANNELS)
        val selected = normalizeStoredLogin(value.selectedChannelLogin.orEmpty())
            ?.takeIf(logins::contains)
            ?: logins.firstOrNull()
        val pinned = value.pinnedChannelLogins
            .mapNotNull(::normalizeStoredLogin)
            .filter(logins::contains)
            .distinct()
        val titles = buildMap {
            value.channelTitlesByLogin.forEach { (rawLogin, rawTitle) ->
                val login = normalizeStoredLogin(rawLogin) ?: return@forEach
                if (login !in logins) return@forEach
                val title = rawTitle.trim().take(MAX_TAB_TITLE_LENGTH)
                if (title.isNotEmpty()) put(login, title)
            }
        }
        val stableChannelIds = logins.mapTo(linkedSetOf(), ::anonymousWorkspaceChannelId)
        val selectedStableId = selected?.let(::anonymousWorkspaceChannelId)
        val layoutJson = value.workspaceLayoutJson?.let { raw ->
            WorkspaceLayoutCodec.encode(
                WorkspaceLayoutCodec.decodeOrDefault(raw, selectedStableId)
                    .normalized(stableChannelIds),
            )
        }
        return AnonymousWorkspaceSnapshot(
            channelLogins = logins,
            selectedChannelLogin = selected,
            pinnedChannelLogins = pinned,
            channelTitlesByLogin = titles,
            workspaceLayoutJson = layoutJson,
        )
    }

    private fun requireLogin(value: String): String = normalizeStoredLogin(value)
        ?: throw AnonymousWorkspaceMutationException("Enter a valid Twitch channel login")

    private fun requireWorkspaceChannelId(
        channelId: String,
        state: WorkspaceRuntimeStateHolder,
    ): String {
        val normalized = channelId.trim()
        return normalized.takeIf { id -> state.channels.any { channel -> channel.id == id } }
            ?: throw AnonymousWorkspaceMutationException("Channel is not in the workspace")
    }

    private fun normalizeStoredLogin(value: String): String? = value
        .trim()
        .removePrefix("#")
        .lowercase()
        .takeIf(CHANNEL_LOGIN_PATTERN::matches)

    private fun anonymousChannel(login: String): ChatChannel = ChatChannel(
        id = anonymousWorkspaceChannelId(login),
        login = login,
        displayName = login,
    )

    private companion object {
        val CHANNEL_LOGIN_PATTERN = Regex("[a-z0-9_]{1,25}")
        const val MAX_CHANNELS = 20
        const val MAX_TAB_TITLE_LENGTH = 32
    }
}

internal fun remapWorkspaceLayoutChannelIds(
    layout: WorkspaceLayout,
    replacementIdByCurrentId: Map<String, String>,
): WorkspaceLayout = layout.copy(
    workspaces = layout.workspaces.map { workspace ->
        workspace.copy(
            tabs = workspace.tabs.map { tab ->
                tab.copy(
                    splits = tab.splits.map { split ->
                        val currentId = split.channelId
                        val replacementId = currentId?.let(replacementIdByCurrentId::get) ?: currentId
                        if (replacementId != currentId) split.withChannelId(replacementId) else split
                    },
                )
            },
        )
    },
)

internal fun anonymousWorkspaceChannelId(login: String): String =
    "anonymous:${login.trim().lowercase()}"

internal const val ANONYMOUS_WORKSPACE_CHANNELS_KEY = "channels"
internal const val ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY = "selected_channel"
internal const val ANONYMOUS_WORKSPACE_EXPLICITLY_EMPTY_KEY = "channels_explicitly_empty"
internal const val ANONYMOUS_WORKSPACE_PINNED_LOGINS_KEY = "anonymous_pinned_channel_logins"
internal const val ANONYMOUS_WORKSPACE_TITLES_KEY = "anonymous_channel_titles"
internal const val ANONYMOUS_WORKSPACE_LAYOUT_KEY = "workspace_layout_json"

private class InMemoryAnonymousWorkspaceStore : AnonymousWorkspaceStore {
    private var snapshot = AnonymousWorkspaceSnapshot()

    override fun load(): AnonymousWorkspaceSnapshot = snapshot

    override fun save(snapshot: AnonymousWorkspaceSnapshot) {
        this.snapshot = snapshot
    }
}
