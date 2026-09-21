package io.ferventio.shared.history

import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.settings.SharedAppPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    store: ChatHistoryStore,
    private val configProvider: () -> ChatHistoryConfig,
    private val batchWindowAction: suspend () -> Unit = {
        delay(SAVE_BATCH_WINDOW_MILLIS)
    },
) {
    private val runtimeBoundStore = store as? RuntimeBoundChatHistoryStore
    private val mutationStore = runtimeBoundStore?.delegate ?: store
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = Channel<Mutation>(Channel.UNLIMITED)
    private val worker: Job

    init {
        runtimeBoundStore?.bind(this)
        worker = scope.launch {
            var pendingMutation: Mutation? = null
            while (true) {
                val mutation = pendingMutation
                    ?: queue.receiveCatching().getOrNull()
                    ?: break
                pendingMutation = null

                if (mutation is Mutation.SaveMessage) {
                    val batch = ArrayList<Mutation.SaveMessage>(SAVE_BATCH_SIZE).apply {
                        add(mutation)
                    }
                    batchWindowAction()
                    while (batch.size < SAVE_BATCH_SIZE) {
                        val next = queue.tryReceive().getOrNull() ?: break
                        if (next is Mutation.SaveMessage && next.config == mutation.config) {
                            batch += next
                        } else {
                            pendingMutation = next
                            break
                        }
                    }
                    applySaveBatch(batch)
                } else {
                    applyMutation(mutation)
                }
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
            mutationStore.loadRecentMessages(
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

    /**
     * Inserts a barrier into the durable mutation queue and waits until all older writes and this
     * clear operation have completed. New messages accepted after the barrier are persisted again.
     */
    suspend fun clearAll() {
        val completion = CompletableDeferred<Result<Unit>>()
        check(queue.trySend(Mutation.AllCleared(completion)).isSuccess) {
            "Chat history persistence queue is closed"
        }
        completion.await().getOrThrow()
    }

    fun close() {
        queue.close()
    }

    suspend fun flushAndClose() {
        close()
        try {
            worker.join()
        } finally {
            runtimeBoundStore?.unbind(this)
            scope.cancel()
        }
    }

    private fun enqueue(mutation: Mutation) {
        queue.trySend(mutation)
    }

    private suspend fun applySaveBatch(batch: List<Mutation.SaveMessage>) {
        if (batch.isEmpty()) return
        try {
            mutationStore.saveMessages(
                messages = batch.map(Mutation.SaveMessage::message),
                config = batch.first().config,
            )
            batch.forEach { it.complete(Result.success(Unit)) }
        } catch (error: CancellationException) {
            batch.forEach { it.complete(Result.failure(error)) }
            throw error
        } catch (error: Exception) {
            batch.forEach { it.complete(Result.failure(error)) }
            // Local history must never take down live chat. Search can still use the last
            // successfully persisted snapshot and a later accepted event retries naturally.
        }
    }

    private suspend fun applyMutation(mutation: Mutation) {
        try {
            mutation.apply(mutationStore)
            mutation.complete(Result.success(Unit))
        } catch (error: CancellationException) {
            mutation.complete(Result.failure(error))
            throw error
        } catch (error: Exception) {
            mutation.complete(Result.failure(error))
            // Local history must never take down live chat. Search can still use the last
            // successfully persisted snapshot and a later accepted event retries naturally.
        }
    }

    private sealed interface Mutation {
        suspend fun apply(store: ChatHistoryStore)

        fun complete(result: Result<Unit>) = Unit

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

        data class AllCleared(
            val completion: CompletableDeferred<Result<Unit>>,
        ) : Mutation {
            override suspend fun apply(store: ChatHistoryStore) {
                store.clearAll()
            }

            override fun complete(result: Result<Unit>) {
                completion.complete(result)
            }
        }
    }

    private companion object {
        const val SAVE_BATCH_WINDOW_MILLIS = 24L
        const val SAVE_BATCH_SIZE = 64
    }
}
