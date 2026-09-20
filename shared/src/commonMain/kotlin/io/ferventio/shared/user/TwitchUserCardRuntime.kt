package io.ferventio.shared.user

import io.ferventio.app.domain.PublicChannelRelationship
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchUser
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runtime-level cache for remote user-card enrichment.
 *
 * User profiles are channel-independent, while relationship data is channel-specific. Keeping
 * separate caches avoids reloading /users when the same chatter is inspected in another channel
 * and deduplicates repeated sheet opens for the same target.
 */
class TwitchUserCardRuntime internal constructor(
    private val client: TwitchUserCardClient,
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor() : this(TwitchUserCardClient())

    private val slotsMutex = Mutex()
    private val profileSlots = mutableMapOf<String, CacheSlot<TwitchUser>>()
    private val relationshipSlots = mutableMapOf<String, CacheSlot<PublicChannelRelationship>>()
    private val banStateSlots = mutableMapOf<String, CacheSlot<Boolean>>()

    init {
        require(ttlMillis > 0L) { "User-card cache TTL must be positive" }
    }

    suspend fun loadUser(
        authentication: StoredAuthentication,
        userId: String,
        userLogin: String,
    ): TwitchUser = loadUserCached(
        authentication = authentication,
        userId = userId,
        userLogin = userLogin,
    )

    internal suspend fun enrich(
        authentication: StoredAuthentication,
        userId: String,
        userLogin: String,
        channelLogin: String,
    ): UserCardRemoteEnrichment {
        val user = bestEffort {
            loadUserCached(
                authentication = authentication,
                userId = userId,
                userLogin = userLogin,
            )
        }
        val effectiveLogin = user?.login
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: userLogin.trim()
        val relationship = if (effectiveLogin.isBlank() || channelLogin.isBlank()) {
            null
        } else {
            bestEffort {
                loadRelationshipCached(
                    userLogin = effectiveLogin,
                    channelLogin = channelLogin,
                )
            }
        }
        return UserCardRemoteEnrichment(
            user = user,
            relationship = relationship,
        )
    }

    suspend fun loadPermanentBanState(
        authentication: StoredAuthentication,
        broadcasterId: String,
        targetUserId: String,
    ): Boolean {
        val normalizedBroadcasterId = broadcasterId.trim()
        val normalizedTargetUserId = targetUserId.trim()
        require(normalizedBroadcasterId.isNotEmpty()) { "Broadcaster id is required" }
        require(normalizedTargetUserId.isNotEmpty()) { "Target user id is required" }
        val slot = banStateSlot(banStateKey(normalizedBroadcasterId, normalizedTargetUserId))
        return slot.mutex.withLock {
            val now = nowEpochMillis()
            slot.cache?.takeIf { it.isFresh(now, ttlMillis) }?.value?.let {
                return@withLock it
            }
            client.loadPermanentBanState(
                authentication = authentication,
                broadcasterId = normalizedBroadcasterId,
                targetUserId = normalizedTargetUserId,
            ).also { banned ->
                slot.cache = TimedValue(banned, nowEpochMillis())
            }
        }
    }

    suspend fun updatePermanentBanState(
        broadcasterId: String,
        targetUserId: String,
        isPermanentlyBanned: Boolean,
    ) {
        val normalizedBroadcasterId = broadcasterId.trim()
        val normalizedTargetUserId = targetUserId.trim()
        if (normalizedBroadcasterId.isEmpty() || normalizedTargetUserId.isEmpty()) return
        val slot = banStateSlot(banStateKey(normalizedBroadcasterId, normalizedTargetUserId))
        slot.mutex.withLock {
            slot.cache = TimedValue(isPermanentlyBanned, nowEpochMillis())
        }
    }

    internal suspend fun clear() {
        slotsMutex.withLock {
            profileSlots.clear()
            relationshipSlots.clear()
            banStateSlots.clear()
        }
    }

    private suspend fun loadUserCached(
        authentication: StoredAuthentication,
        userId: String,
        userLogin: String,
    ): TwitchUser {
        val key = profileKey(userId, userLogin)
        val slot = profileSlot(key)
        return slot.mutex.withLock {
            val now = nowEpochMillis()
            slot.cache?.takeIf { it.isFresh(now, ttlMillis) }?.value?.let {
                return@withLock it
            }
            client.loadUser(authentication, userId, userLogin).also { user ->
                slot.cache = TimedValue(user, nowEpochMillis())
            }
        }
    }

    private suspend fun loadRelationshipCached(
        userLogin: String,
        channelLogin: String,
    ): PublicChannelRelationship {
        val key = relationshipKey(userLogin, channelLogin)
        val slot = relationshipSlot(key)
        return slot.mutex.withLock {
            val now = nowEpochMillis()
            slot.cache?.takeIf { it.isFresh(now, ttlMillis) }?.value?.let {
                return@withLock it
            }
            client.loadPublicRelationship(userLogin, channelLogin).also { relationship ->
                slot.cache = TimedValue(relationship, nowEpochMillis())
            }
        }
    }

    private suspend fun profileSlot(key: String): CacheSlot<TwitchUser> =
        slotsMutex.withLock {
            profileSlots.getOrPut(key) { CacheSlot() }
        }

    private suspend fun relationshipSlot(key: String): CacheSlot<PublicChannelRelationship> =
        slotsMutex.withLock {
            relationshipSlots.getOrPut(key) { CacheSlot() }
        }

    private suspend fun banStateSlot(key: String): CacheSlot<Boolean> =
        slotsMutex.withLock {
            banStateSlots.getOrPut(key) { CacheSlot() }
        }

    private fun profileKey(userId: String, userLogin: String): String {
        val normalizedId = userId.trim()
        if (normalizedId.isNotEmpty()) return "id:$normalizedId"
        val normalizedLogin = userLogin.trim().removePrefix("@").lowercase()
        require(normalizedLogin.isNotEmpty()) { "User-card cache requires user id or login" }
        return "login:$normalizedLogin"
    }

    private fun relationshipKey(userLogin: String, channelLogin: String): String =
        userLogin.trim().removePrefix("@").lowercase() +
            "@" +
            channelLogin.trim().removePrefix("#").lowercase()

    private fun banStateKey(broadcasterId: String, targetUserId: String): String =
        broadcasterId + ":" + targetUserId

    private suspend fun <T> bestEffort(block: suspend () -> T): T? =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }

    private data class CacheSlot<T>(
        val mutex: Mutex = Mutex(),
        var cache: TimedValue<T>? = null,
    )

    private data class TimedValue<T>(
        val value: T,
        val loadedAtMillis: Long,
    ) {
        fun isFresh(nowMillis: Long, ttlMillis: Long): Boolean =
            nowMillis >= loadedAtMillis && nowMillis - loadedAtMillis < ttlMillis
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 2 * 60 * 1_000L
    }
}
