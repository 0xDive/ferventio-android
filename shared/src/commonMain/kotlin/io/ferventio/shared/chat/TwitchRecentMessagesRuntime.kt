package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.history.ChatHistoryPersistenceRuntime
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Matches Android's Recent Messages target set: visible active-tab splits plus legacy selection. */
internal fun WorkspaceRuntimeSnapshot.activeRecentMessageChannels(): List<ChatChannel> {
    val activeIds = buildSet {
        workspaceLayout?.activeTab?.splits
            ?.mapNotNull { split -> split.channelId?.trim()?.takeIf(String::isNotEmpty) }
            ?.forEach(::add)
        selectedChannelId?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }
    return channels.filter { channel -> channel.id in activeIds }
}

/** Loads Recent Messages without routing historical rows through unread/highlight side effects. */
internal class TwitchRecentMessagesRuntime(
    private val state: ChatRuntimeStateHolder,
    private val history: ChatHistoryPersistenceRuntime?,
    private val loadRecentMessages: suspend (ChatChannel, Int) -> TwitchRecentMessagesResult,
) {
    constructor(
        state: ChatRuntimeStateHolder,
        history: ChatHistoryPersistenceRuntime?,
        client: TwitchRecentMessagesClient = TwitchRecentMessagesClient(),
    ) : this(
        state = state,
        history = history,
        loadRecentMessages = client::load,
    )

    suspend fun loadChannels(channels: List<ChatChannel>) = coroutineScope {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        channels.asSequence()
            .distinctBy { channel -> channel.login.trim().lowercase() }
            .forEach { channel ->
                launch {
                    semaphore.withPermit {
                        loadChannelBestEffort(channel)
                    }
                }
            }
    }

    private suspend fun loadChannelBestEffort(channel: ChatChannel) {
        try {
            val result = loadRecentMessages(channel, TwitchRecentMessagesClient.DEFAULT_LIMIT)
            if (result.messages.isEmpty()) return

            // Android parity: rows already held locally win duplicate Twitch message IDs. This
            // preserves richer local moderation/hydration state and also keeps live rows canonical.
            val existingIds = state.messages(channel.id).mapTo(hashSetOf()) { message -> message.id }
            val novelMessages = result.messages.filterNot { message -> message.id in existingIds }
            if (novelMessages.isEmpty()) return

            // Historical rows are intentionally inserted through the history overlay. They never
            // route through append()/attention and therefore cannot create unread or highlight UI.
            state.prependHistory(channel.id, novelMessages)
            novelMessages.forEach { message ->
                history?.saveMessage(message)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // The third-party snapshot is optional and must never interrupt live EventSub chat.
        }
    }

    private companion object {
        const val MAX_CONCURRENT_REQUESTS = 3
    }
}
