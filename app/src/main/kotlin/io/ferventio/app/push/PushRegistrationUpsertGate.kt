package io.ferventio.app.push

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PushRegistrationUpsertGate {
    private val mutex = Mutex()
    private var lastSuccessfulKey: RegistrationKey? = null

    suspend fun submit(
        serverUrl: String,
        request: PushRegistrationRequest,
        register: suspend () -> Boolean,
    ): Boolean = mutex.withLock {
        val key = RegistrationKey(serverUrl.trimEnd('/'), request)
        if (lastSuccessfulKey == key) return@withLock false
        if (!register()) return@withLock false
        lastSuccessfulKey = key
        true
    }

    suspend fun clear(
        unregister: suspend () -> Unit = {},
    ) {
        mutex.withLock {
            lastSuccessfulKey = null
            unregister()
        }
    }

    private data class RegistrationKey(
        val serverUrl: String,
        val request: PushRegistrationRequest,
    )
}
