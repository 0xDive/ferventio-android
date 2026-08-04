package io.ferventio.app.emote

import io.ferventio.app.domain.BoundedLruCache
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.EmoteProviderCatalog
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.domain.usageKey
import io.ferventio.app.twitch.TwitchApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Context shared by all providers which need authenticated Twitch API calls. */
data class EmoteProviderContext(
    val twitchClientId: String,
    val twitchAccessToken: String,
    val twitchUserId: String,
)

/**
 * A provider only knows how to load its own global and channel catalog.
 * Provider precedence, text parsing and live refreshes stay inside [EmoteRepository].
 */
interface EmoteProvider {
    val id: String
    val textResolvable: Boolean

    suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog

    suspend fun loadChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
    ): EmoteProviderCatalog
}

data class EmoteRepositorySnapshot(
    val providerEmotesByChannel: Map<String, Map<String, Map<String, ThirdPartyEmoteAsset>>> = emptyMap(),
    val catalogByChannel: Map<String, List<ThirdPartyEmoteAsset>> = emptyMap(),
    val sevenTvSetIdsByChannel: Map<String, Set<String>> = emptyMap(),
    val errorsByProvider: Map<String, String> = emptyMap(),
    val liveProviders: Set<String> = emptySet(),
) {
    fun emotes(providerId: String, channelId: String): Map<String, ThirdPartyEmoteAsset> =
        providerEmotesByChannel[providerId]?.get(channelId).orEmpty()

    val errorMessage: String?
        get() = errorsByProvider.takeIf { it.isNotEmpty() }
            ?.entries
            ?.joinToString(" · ") { (provider, message) -> "$provider: $message" }
}

class EmoteRepository(
    api: TwitchApiClient,
    private val providers: List<EmoteProvider> = listOf(
        TwitchEmoteProvider(api),
        BetterTtvEmoteProvider(api),
        FrankerFaceZEmoteProvider(api),
        SevenTvEmoteProvider(api),
    ),
    private val liveUpdateClient: EmoteLiveUpdateClient = EmoteLiveUpdateClient(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var liveUpdateJob: Job? = null
    private val twitchCacheMutex = Mutex()
    private var twitchCacheContextKey: String? = null
    private var twitchSharedCatalog = EmoteProviderCatalog()
    private var twitchSharedLoadedAtMillis = 0L
    private val twitchChannelCatalogs = BoundedLruCache<String, CachedTwitchChannelCatalog>(MAX_TWITCH_CHANNEL_CACHES)

    suspend fun refresh(
        context: EmoteProviderContext,
        channels: List<ChatChannel>,
        enabledProviders: Set<String>,
        includeTwitch: Boolean = true,
    ): EmoteRepositorySnapshot = coroutineScope {
        if (channels.isEmpty()) return@coroutineScope EmoteRepositorySnapshot()
        if (includeTwitch) ensureTwitchContext(context)

        val activeProviders = providers.filter { provider ->
            (includeTwitch && provider.id == TWITCH) || provider.id in enabledProviders
        }
        val errors = ConcurrentHashMap<String, String>()
        val globalDeferred = activeProviders.associateWith { provider ->
            async {
                runCatching {
                    if (provider.id == TWITCH) {
                        cachedTwitchSharedCatalog(context)
                            ?: provider.loadGlobal(context).normalizedFor(provider)
                    } else {
                        provider.loadGlobal(context).normalizedFor(provider)
                    }
                }
                    .onFailure { error -> errors[provider.id] = error.userMessage() }
                    .getOrDefault(EmoteProviderCatalog())
            }
        }
        val globalCatalogs = globalDeferred.mapValues { (_, deferred) -> deferred.await() }
        if (errors[TWITCH] == null) {
            activeProviders.firstOrNull { it.id == TWITCH }?.let { twitchProvider ->
                globalCatalogs[twitchProvider]?.let { catalog ->
                    cacheTwitchSharedCatalog(context, catalog)
                }
            }
        }

        val channelResults = channels.map { channel ->
            async {
                val providerCatalogs = activeProviders.map { provider ->
                    async {
                        val catalog = if (provider.id == TWITCH) {
                            cachedTwitchChannelCatalog(context, channel.id)
                        } else {
                            runCatching { provider.loadChannel(context, channel).normalizedFor(provider) }
                                .onFailure { error -> errors.putIfAbsent(provider.id, error.userMessage()) }
                                .getOrDefault(EmoteProviderCatalog())
                        }
                        provider.id to catalog
                    }
                }.awaitAll().toMap()
                channel.id to providerCatalogs
            }
        }.awaitAll().toMap()

        val byProvider = activeProviders.associate { provider ->
            provider.id to channels.associate { channel ->
                val global = globalCatalogs[provider].orEmptyCatalog().emotes
                val channelCatalog = channelResults[channel.id]?.get(provider.id).orEmptyCatalog().emotes
                channel.id to (global + channelCatalog)
            }
        }

        val activeProviderIds = activeProviders.map(EmoteProvider::id).distinct()
        val pickerProviderOrder = PICKER_MERGE_ORDER.filter { it in activeProviderIds } +
            activeProviderIds.filterNot { it in PICKER_MERGE_ORDER }.sorted()
        val catalogByChannel = channels.associate { channel ->
            val merged = buildMap<String, ThirdPartyEmoteAsset> {
                // Known-provider precedence stays stable. Additional providers are appended
                // deterministically so custom/test providers are not silently dropped.
                pickerProviderOrder.forEach { providerId ->
                    putAll(byProvider[providerId]?.get(channel.id).orEmpty())
                }
            }
            channel.id to merged.values.sortedWith(PICKER_COMPARATOR)
        }

        val sevenTvSetIdsByChannel = channels.associate { channel ->
            val globalIds = activeProviders.firstOrNull { it.id == SEVEN_TV }
                ?.let(globalCatalogs::get)
                ?.subscriptionIds
                .orEmpty()
            val channelIds = channelResults[channel.id]?.get(SEVEN_TV)?.subscriptionIds.orEmpty()
            channel.id to (globalIds + channelIds)
        }

        EmoteRepositorySnapshot(
            providerEmotesByChannel = byProvider,
            catalogByChannel = catalogByChannel,
            sevenTvSetIdsByChannel = sevenTvSetIdsByChannel,
            errorsByProvider = errors.toMap(),
            liveProviders = buildSet {
                if (BETTER_TTV in enabledProviders) add(BETTER_TTV)
                if (SEVEN_TV in enabledProviders && sevenTvSetIdsByChannel.values.any { it.isNotEmpty() }) {
                    add(SEVEN_TV)
                }
            },
        )
    }

    /**
     * Loads Twitch emotes which are valid only in [channel], most importantly follower emotes.
     * The shared user catalog is reused, while the channel addition is cached for quick tab switches.
     */
    suspend fun refreshTwitchChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
        currentCatalog: List<ThirdPartyEmoteAsset>,
        force: Boolean = false,
    ): Result<List<ThirdPartyEmoteAsset>> = runCatching {
        ensureTwitchContext(context)
        val provider = providers.firstOrNull { it.id == TWITCH }
            ?: return@runCatching currentCatalog
        val now = nowMillis()
        val shared = twitchCacheMutex.withLock {
            twitchSharedCatalog.takeIf {
                twitchSharedLoadedAtMillis > 0L && now - twitchSharedLoadedAtMillis < TWITCH_CACHE_TTL_MILLIS
            }
        } ?: provider.loadGlobal(context).normalizedFor(provider).also {
            cacheTwitchSharedCatalog(context, it)
        }
        val channelCatalog = twitchCacheMutex.withLock {
            twitchChannelCatalogs[channel.id]?.takeIf { cached ->
                !force && now - cached.loadedAtMillis < TWITCH_CACHE_TTL_MILLIS
            }?.catalog
        } ?: provider.loadChannel(context, channel).normalizedFor(provider).also { loaded ->
            twitchCacheMutex.withLock {
                twitchChannelCatalogs[channel.id] = CachedTwitchChannelCatalog(loaded, nowMillis())
            }
        }

        mergePickerCatalog(
            twitch = shared.emotes + channelCatalog.emotes,
            nonTwitch = currentCatalog.filterNot { it.provider == TWITCH },
        )
    }

    suspend fun invalidateTwitchCache() {
        twitchCacheMutex.withLock {
            twitchSharedCatalog = EmoteProviderCatalog()
            twitchSharedLoadedAtMillis = 0L
            twitchChannelCatalogs.clear()
        }
    }

    fun startLiveUpdates(
        scope: CoroutineScope,
        channels: List<ChatChannel>,
        snapshot: EmoteRepositorySnapshot,
        enabledProviders: Set<String>,
        onCatalogInvalidated: (providerId: String, channelId: String?) -> Unit,
    ) {
        liveUpdateJob?.cancel()
        val channelIds = channels.map(ChatChannel::id).filter(String::isNotBlank).toSet()
        val setOwners = linkedMapOf<String, String?>()
        snapshot.sevenTvSetIdsByChannel.forEach { (channelId, setIds) ->
            setIds.forEach { setId ->
                if (setId !in setOwners) {
                    setOwners[setId] = channelId
                } else if (setOwners[setId] != channelId) {
                    // A set used by more than one channel is global/shared.
                    setOwners[setId] = null
                }
            }
        }
        val sevenTvSets = setOwners.toMap()
        if (channelIds.isEmpty()) return
        liveUpdateJob = scope.launch {
            liveUpdateClient.run(
                betterTtvChannelIds = channelIds.takeIf { BETTER_TTV in enabledProviders }.orEmpty(),
                sevenTvSetIds = sevenTvSets.takeIf { SEVEN_TV in enabledProviders }.orEmpty(),
                onInvalidated = onCatalogInvalidated,
            )
        }
    }

    suspend fun stopLiveUpdates() {
        liveUpdateJob?.cancelAndJoin()
        liveUpdateJob = null
    }

    private suspend fun ensureTwitchContext(context: EmoteProviderContext) {
        val key = "${context.twitchClientId}:${context.twitchUserId}"
        twitchCacheMutex.withLock {
            if (twitchCacheContextKey != key) {
                twitchCacheContextKey = key
                twitchSharedCatalog = EmoteProviderCatalog()
                twitchSharedLoadedAtMillis = 0L
                twitchChannelCatalogs.clear()
            }
        }
    }

    private suspend fun cacheTwitchSharedCatalog(
        context: EmoteProviderContext,
        catalog: EmoteProviderCatalog,
    ) {
        ensureTwitchContext(context)
        twitchCacheMutex.withLock {
            twitchSharedCatalog = catalog
            twitchSharedLoadedAtMillis = nowMillis()
        }
    }

    private suspend fun cachedTwitchSharedCatalog(
        context: EmoteProviderContext,
    ): EmoteProviderCatalog? {
        ensureTwitchContext(context)
        val now = nowMillis()
        return twitchCacheMutex.withLock {
            twitchSharedCatalog.takeIf {
                twitchSharedLoadedAtMillis > 0L && now - twitchSharedLoadedAtMillis < TWITCH_CACHE_TTL_MILLIS
            }
        }
    }

    private suspend fun cachedTwitchChannelCatalog(
        context: EmoteProviderContext,
        channelId: String,
    ): EmoteProviderCatalog {
        ensureTwitchContext(context)
        val now = nowMillis()
        return twitchCacheMutex.withLock {
            twitchChannelCatalogs[channelId]?.takeIf { cached ->
                now - cached.loadedAtMillis < TWITCH_CACHE_TTL_MILLIS
            }?.catalog ?: EmoteProviderCatalog()
        }
    }

    private fun mergePickerCatalog(
        twitch: Map<String, ThirdPartyEmoteAsset>,
        nonTwitch: List<ThirdPartyEmoteAsset>,
    ): List<ThirdPartyEmoteAsset> = buildMap<String, ThirdPartyEmoteAsset> {
        twitch.values.forEach { asset -> put(asset.usageKey, asset) }
        nonTwitch.forEach { asset -> put(asset.usageKey, asset) }
    }.values.sortedWith(PICKER_COMPARATOR)

    private fun EmoteProviderCatalog.normalizedFor(provider: EmoteProvider): EmoteProviderCatalog =
        copy(
            emotes = emotes.mapValues { (_, asset) ->
                asset.copy(
                    provider = provider.id,
                    textResolvable = provider.textResolvable,
                )
            },
        )

    private fun EmoteProviderCatalog?.orEmptyCatalog(): EmoteProviderCatalog =
        this ?: EmoteProviderCatalog()

    private fun Throwable.userMessage(): String =
        message?.takeIf(String::isNotBlank) ?: this::class.simpleName.orEmpty()

    companion object {
        const val TWITCH = "twitch"
        const val BETTER_TTV = "betterttv"
        const val FRANKER_FACE_Z = "frankerfacez"
        const val SEVEN_TV = "7tv"

        private const val TWITCH_CACHE_TTL_MILLIS = 10 * 60 * 1_000L
        private const val MAX_TWITCH_CHANNEL_CACHES = 16

        private val PICKER_MERGE_ORDER = listOf(
            TWITCH,
            SEVEN_TV,
            FRANKER_FACE_Z,
            BETTER_TTV,
        )

        private val PROVIDER_SORT_ORDER = mapOf(
            TWITCH to 0,
            BETTER_TTV to 1,
            FRANKER_FACE_Z to 2,
            SEVEN_TV to 3,
        )

        private val PICKER_COMPARATOR =
            compareByDescending<ThirdPartyEmoteAsset> { it.scope == EmoteScope.CHANNEL }
                .thenBy { PROVIDER_SORT_ORDER[it.provider] ?: Int.MAX_VALUE }
                .thenBy { it.code.lowercase() }
    }
}

private data class CachedTwitchChannelCatalog(
    val catalog: EmoteProviderCatalog,
    val loadedAtMillis: Long,
)

private class TwitchEmoteProvider(
    private val api: TwitchApiClient,
) : EmoteProvider {
    override val id: String = EmoteRepository.TWITCH
    override val textResolvable: Boolean = true

    override suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog =
        EmoteProviderCatalog(
            emotes = api.getTwitchUserEmotes(
                clientId = context.twitchClientId,
                token = context.twitchAccessToken,
                userId = context.twitchUserId,
            ).filterValues { asset ->
                // Follower emotes are channel-bound. The unfiltered user catalog is shared by every
                // open chat, so exclude them rather than offering an emote Twitch may reject there.
                asset.emoteType != "follower"
            },
        )

    override suspend fun loadChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
    ): EmoteProviderCatalog = EmoteProviderCatalog(
        emotes = api.getTwitchUserEmotes(
            clientId = context.twitchClientId,
            token = context.twitchAccessToken,
            userId = context.twitchUserId,
            broadcasterId = channel.id,
        ).filterValues { asset ->
            asset.emoteType == "follower" && asset.ownerId == channel.id
        },
    )
}

private class BetterTtvEmoteProvider(
    private val api: TwitchApiClient,
) : EmoteProvider {
    override val id: String = EmoteRepository.BETTER_TTV
    override val textResolvable: Boolean = true

    override suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog =
        EmoteProviderCatalog(emotes = api.getBetterTtvGlobalEmotes())

    override suspend fun loadChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
    ): EmoteProviderCatalog = EmoteProviderCatalog(emotes = api.getBetterTtvChannelEmotes(channel.id))
}

private class FrankerFaceZEmoteProvider(
    private val api: TwitchApiClient,
) : EmoteProvider {
    override val id: String = EmoteRepository.FRANKER_FACE_Z
    override val textResolvable: Boolean = true

    override suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog =
        EmoteProviderCatalog(emotes = api.getFrankerFaceZGlobalEmotes())

    override suspend fun loadChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
    ): EmoteProviderCatalog = EmoteProviderCatalog(emotes = api.getFrankerFaceZChannelEmotes(channel.id))
}

private class SevenTvEmoteProvider(
    private val api: TwitchApiClient,
) : EmoteProvider {
    override val id: String = EmoteRepository.SEVEN_TV
    override val textResolvable: Boolean = true

    override suspend fun loadGlobal(context: EmoteProviderContext): EmoteProviderCatalog =
        api.getSevenTvGlobalCatalog()

    override suspend fun loadChannel(
        context: EmoteProviderContext,
        channel: ChatChannel,
    ): EmoteProviderCatalog = api.getSevenTvChannelCatalog(channel.id)
}
