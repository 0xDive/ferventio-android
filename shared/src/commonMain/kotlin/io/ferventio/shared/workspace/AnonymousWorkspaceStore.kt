package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.WorkspaceLayout
import kotlin.Throws

data class AnonymousWorkspaceSnapshot(
    val channelLogins: List<String> = emptyList(),
    val selectedChannelLogin: String? = null,
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

    private fun applySnapshot(
        state: WorkspaceRuntimeStateHolder,
        snapshot: AnonymousWorkspaceSnapshot,
    ) {
        val channels = snapshot.channelLogins.map(::anonymousChannel)
        state.replaceChannels(channels)
        val selectedChannel = snapshot.selectedChannelLogin?.let { selectedLogin ->
            channels.firstOrNull { channel -> channel.login == selectedLogin }
        }
        selectedChannel?.let { state.selectChannel(it.id) }
        state.restoreWorkspaceLayout(WorkspaceLayout.default(state.selectedChannelId))
    }

    private fun persist(state: WorkspaceRuntimeStateHolder): AnonymousWorkspaceSnapshot {
        val selectedLogin = state.selectedChannelId?.let { selectedId ->
            state.channels.firstOrNull { channel -> channel.id == selectedId }?.login
        }
        return normalizeSnapshot(
            AnonymousWorkspaceSnapshot(
                channelLogins = state.channels.map(ChatChannel::login),
                selectedChannelLogin = selectedLogin,
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
        return AnonymousWorkspaceSnapshot(
            channelLogins = logins,
            selectedChannelLogin = selected,
        )
    }

    private fun requireLogin(value: String): String = normalizeStoredLogin(value)
        ?: throw AnonymousWorkspaceMutationException("Enter a valid Twitch channel login")

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
    }
}

internal fun anonymousWorkspaceChannelId(login: String): String =
    "anonymous:${login.trim().lowercase()}"

internal const val ANONYMOUS_WORKSPACE_CHANNELS_KEY = "channels"
internal const val ANONYMOUS_WORKSPACE_SELECTED_CHANNEL_KEY = "selected_channel"
internal const val ANONYMOUS_WORKSPACE_EXPLICITLY_EMPTY_KEY = "channels_explicitly_empty"

private class InMemoryAnonymousWorkspaceStore : AnonymousWorkspaceStore {
    private var snapshot = AnonymousWorkspaceSnapshot()

    override fun load(): AnonymousWorkspaceSnapshot = snapshot

    override fun save(snapshot: AnonymousWorkspaceSnapshot) {
        this.snapshot = snapshot
    }
}
