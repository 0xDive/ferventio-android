package io.ferventio.shared.chat

import kotlin.time.Clock

internal class ChatMetadataRefreshGate(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private var globalBadgesLoadedAtMillis: Long? = null
    private val channelBadgesLoadedAtMillis = mutableMapOf<String, Long>()
    private val cheermotesLoadedAtMillis = mutableMapOf<String, Long>()

    init {
        require(ttlMillis > 0L) { "Chat metadata TTL must be positive" }
    }

    fun shouldRefreshGlobalBadges(): Boolean =
        isExpired(globalBadgesLoadedAtMillis)

    fun shouldRefreshChannelBadges(channelId: String): Boolean =
        isExpired(channelBadgesLoadedAtMillis[channelId.normalizedChannelId()])

    fun shouldRefreshCheermotes(channelId: String): Boolean =
        isExpired(cheermotesLoadedAtMillis[channelId.normalizedChannelId()])

    fun markGlobalBadgesLoaded() {
        globalBadgesLoadedAtMillis = nowEpochMillis()
    }

    fun markChannelBadgesLoaded(channelId: String) {
        channelBadgesLoadedAtMillis[channelId.normalizedChannelId()] = nowEpochMillis()
    }

    fun markCheermotesLoaded(channelId: String) {
        cheermotesLoadedAtMillis[channelId.normalizedChannelId()] = nowEpochMillis()
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val retained = channelIds
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        channelBadgesLoadedAtMillis.keys.retainAll(retained)
        cheermotesLoadedAtMillis.keys.retainAll(retained)
    }

    private fun isExpired(loadedAtMillis: Long?): Boolean {
        val loadedAt = loadedAtMillis ?: return true
        return nowEpochMillis() - loadedAt >= ttlMillis
    }

    private fun String.normalizedChannelId(): String =
        trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat metadata channel id must not be blank")

    private companion object {
        const val DEFAULT_TTL_MILLIS = 10 * 60 * 1_000L
    }
}
