package io.ferventio.shared.history

import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.settings.SharedAppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal fun SharedAppPreferences.toChatHistoryConfig(): ChatHistoryConfig = ChatHistoryConfig(
    enabled = localHistoryEnabled,
    limitPerChannel = localHistoryLimit,
    retentionDays = localHistoryRetentionDays,
    maxDatabaseSizeMb = localHistoryMaxSizeMb,
)

/**
 * Serializes durable history mutations without making EventSub delivery callbacks suspend.
 *
 * The worker owns an independent supervisor so a transport cancellation can close the queue and
 * still drain accepted events before the session finishes. [flushAndClose] is awaited from the
 * coordinator's non-cancellable cleanup path.
 */
internal class ChatHistoryPersistenceRuntime(
    private val store: ChatHistoryStore,
    private val configProvider: () -> ChatHistoryConfig,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Mutation>(Channel.UNLIMITED)
    private val worker: Job = scope.launch {
        for (mutation in queue) {
            try {
                mutation.apply(store)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Local history must never take down live chat. Search can still use the last
                // successfully persisted snapshot and a later accepted event retries naturally.
            }
        }
    }

    suspend fun restoreRecent(
        state: ChatRuntimeStateHolder,
        channelIds: List<String>,
    ) {
        val config = configProvider()
        if (!config.enabled || channelIds.isEmpty()) return
        val restored = runCatching {
            store.loadRecentMessages(
                channelIds = channelIds,
                config = config,
            )
        }.getOrDefault(emptyMap())
        channelIds.distinct().forEach { channelId ->
            restored[channelId]?.takeIf { it.isNotEmpty() }?.let { messages ->
                state.prependHistory(channelId, messages)
            }
        }
    }

    fun saveMessage(message: ChatMessage) {
        enqueue(Mutation.SaveMessage(message, configProvider()))
    }

    fun markMessageDeleted(channelId: String, messageId: String) {
        enqueue(Mutation.MessageDeleted(channelId, messageId))
    }

    fun markUserMessagesDeleted(channelId: String, userId: String) {
        enqueue(Mutation.UserMessagesDeleted(channelId, userId))
    }

    fun clearChannel(channelId: String) {
        enqueue(Mutation.ChannelCleared(channelId))
    }

    fun close() {
        queue.close()
    }

    suspend fun flushAndClose() {
        close()
        worker.join()
        scope.cancel()
    }

    private fun enqueue(mutation: Mutation) {
        queue.trySend(mutation)
    }

    private sealed interface Mutation {
        suspend fun apply(store: ChatHistoryStore)

        data class SaveMessage(
            val message: ChatMessage,
            val config: ChatHistoryConfig,
        ) : Mutation {
            override suspend fun apply(store: ChatHistoryStore) {
                store.saveMessage(message, config)
            }
        }

        data class MessageDeleted(
            val channelId: String,
            val messageId: String,
        ) : Mutation {
            override suspend fun apply(store: ChatHistoryStore) {
                store.markMessageDeleted(channelId, messageId)
            }
        }

        data class UserMessagesDeleted(
            val channelId: String,
            val userId: String,
        ) : Mutation {
            override suspend fun apply(store: ChatHistoryStore) {
                store.markUserMessagesDeleted(channelId, userId)
            }
        }

        data class ChannelCleared(
            val channelId: String,
        ) : Mutation {
            override suspend fun apply(store: ChatHistoryStore) {
                store.clearChannel(channelId)
            }
        }
    }
}
