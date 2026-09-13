package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.history.ChatHistoryPersistenceRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

            // Historical rows are intentionally inserted through the history overlay. Live rows
            // win duplicate Twitch message IDs and append() later evicts a matching overlay row.
            state.prependHistory(channel.id, result.messages)
            result.messages.forEach { message ->
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
