package io.ferventio.shared.settings

import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.SavedMessageFilter
import kotlin.Throws

/** Device-local saved-filter persistence used before a Twitch account is authorized. */
interface AnonymousSavedFiltersStore {
    fun load(): String?

    fun save(raw: String)
}

/**
 * Keeps guest saved filters compatible with Android's established message_filters_json contract.
 * Mutations are durable-first so a storage failure never leaves Compose state ahead of disk.
 */
class AnonymousSavedFiltersCoordinator(
    private val store: AnonymousSavedFiltersStore = InMemoryAnonymousSavedFiltersStore(),
) {
    @Throws(Exception::class)
    fun restore(state: SharedSavedFiltersStateHolder): SharedSavedFiltersSnapshot {
        val raw = store.load()
        val decoded = MessageFilterCodec.decode(raw).getOrDefault(emptyList())
        val snapshot = SharedSavedFiltersStateHolder(
            SharedSavedFiltersSnapshot(filters = decoded),
        ).snapshot
        state.restore(snapshot)
        return snapshot
    }

    @Throws(Exception::class)
    fun upsert(
        filter: SavedMessageFilter,
        state: SharedSavedFiltersStateHolder,
    ): SavedMessageFilter {
        state.markSaveStarted()
        return try {
            val next = SharedSavedFiltersStateHolder(state.snapshot)
            val normalized = next.upsert(filter)
            persist(next.snapshot)
            state.markSaveSucceeded(next.snapshot)
            normalized
        } catch (error: Exception) {
            state.markSaveFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    fun delete(
        filterId: String,
        state: SharedSavedFiltersStateHolder,
    ): SharedSavedFiltersSnapshot {
        state.markSaveStarted()
        return try {
            val next = SharedSavedFiltersStateHolder(state.snapshot)
            next.delete(filterId)
            persist(next.snapshot)
            state.markSaveSucceeded(next.snapshot)
            next.snapshot
        } catch (error: Exception) {
            state.markSaveFailed(error.message)
            throw error
        }
    }

    @Throws(Exception::class)
    fun importAndMerge(
        raw: String,
        state: SharedSavedFiltersStateHolder,
    ): SharedSavedFiltersSnapshot {
        state.markSaveStarted()
        return try {
            val next = SharedSavedFiltersTransferCodec.importAndMerge(
                raw = raw,
                existing = state.filters,
            )
            persist(next)
            state.markSaveSucceeded(next)
            next
        } catch (error: Exception) {
            state.markSaveFailed(error.message)
            throw error
        }
    }

    private fun persist(snapshot: SharedSavedFiltersSnapshot) {
        store.save(SharedSavedFiltersTransferCodec.export(snapshot.filters))
    }
}

internal const val ANONYMOUS_SAVED_FILTERS_KEY = "message_filters_json"

private class InMemoryAnonymousSavedFiltersStore : AnonymousSavedFiltersStore {
    private var raw: String? = null

    override fun load(): String? = raw

    override fun save(raw: String) {
        this.raw = raw
    }
}
