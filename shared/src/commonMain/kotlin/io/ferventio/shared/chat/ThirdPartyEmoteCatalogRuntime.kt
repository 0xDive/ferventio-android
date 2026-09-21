package io.ferventio.shared.chat

import io.ferventio.app.domain.ThirdPartyEmoteAsset
import kotlin.time.Clock
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-runtime cache for third-party emote catalogs.
 *
 * Multiple chat panes can render the same channel at once. Keeping this cache above Compose avoids
 * constructing one HTTP client per pane and deduplicates identical in-flight channel loads.
 */
class ThirdPartyEmoteCatalogRuntime internal constructor(
    private val client: ThirdPartyEmoteCatalogClient,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor() : this(ThirdPartyEmoteCatalogClient())

    private val globalMutex = Mutex()
    private val slotsMutex = Mutex()
    private var globalCache: TimedValue<ThirdPartyProviderCatalogs>? = null
    private val channelSlots = mutableMapOf<String, ChannelSlot>()

    init {
        require(ttlMillis > 0L) { "Third-party emote cache TTL must be positive" }
    }

    suspend fun load(channelId: String): Map<String, ThirdPartyEmoteAsset> {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty()) return emptyMap()
        val slot = channelSlot(normalizedChannelId)

        return slot.mutex.withLock {
            val now = nowEpochMillis()
            slot.cache?.takeIf { it.isFresh(now, ttlMillis) }?.value?.let { return@withLock it }

            val merged = coroutineScope {
                val globals = async { loadGlobalsCached() }
                val channel = async { client.loadChannel(normalizedChannelId) }
                client.mergeForChannel(globals.await(), channel.await())
            }
            slot.cache = TimedValue(merged, nowEpochMillis())
            merged
        }
    }

    internal suspend fun invalidateChannel(channelId: String) {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty()) return
        slotsMutex.withLock {
            channelSlots[normalizedChannelId]?.cache = null
        }
    }

    internal suspend fun clear() {
        globalMutex.withLock { globalCache = null }
        slotsMutex.withLock { channelSlots.clear() }
    }

    private suspend fun loadGlobalsCached(): ThirdPartyProviderCatalogs =
        globalMutex.withLock {
            val now = nowEpochMillis()
            globalCache?.takeIf { it.isFresh(now, ttlMillis) }?.value?.let {
                return@withLock it
            }
            client.loadGlobals().also { loaded ->
                globalCache = TimedValue(loaded, nowEpochMillis())
            }
        }

    private suspend fun channelSlot(channelId: String): ChannelSlot =
        slotsMutex.withLock {
            channelSlots.getOrPut(channelId) { ChannelSlot() }
        }

    private data class ChannelSlot(
        val mutex: Mutex = Mutex(),
        var cache: TimedValue<Map<String, ThirdPartyEmoteAsset>>? = null,
    )

    private data class TimedValue<T>(
        val value: T,
        val loadedAtMillis: Long,
    ) {
        fun isFresh(nowMillis: Long, ttlMillis: Long): Boolean =
            nowMillis >= loadedAtMillis && nowMillis - loadedAtMillis < ttlMillis
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 5 * 60 * 1_000L
    }
}
