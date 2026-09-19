package io.ferventio.shared.history

import io.ferventio.app.domain.ChatHistoryStore

/**
 * Keeps external destructive history operations ordered with the active persistence queue.
 *
 * The shared runtime exposes this wrapper as a normal [ChatHistoryStore]. While live chat owns a
 * [ChatHistoryPersistenceRuntime], [clearAll] becomes a queue barrier. Outside an active chat
 * session it falls back to the platform store directly.
 */
internal class RuntimeBoundChatHistoryStore(
    internal val delegate: ChatHistoryStore,
) : ChatHistoryStore by delegate {
    private var persistenceRuntime: ChatHistoryPersistenceRuntime? = null

    override suspend fun clearAll() {
        val runtime = persistenceRuntime
        if (runtime != null) {
            runtime.clearAll()
        } else {
            delegate.clearAll()
        }
    }

    internal fun bind(runtime: ChatHistoryPersistenceRuntime) {
        check(persistenceRuntime == null || persistenceRuntime === runtime) {
            "A chat history persistence runtime is already active"
        }
        persistenceRuntime = runtime
    }

    internal fun unbind(runtime: ChatHistoryPersistenceRuntime) {
        if (persistenceRuntime === runtime) {
            persistenceRuntime = null
        }
    }
}
