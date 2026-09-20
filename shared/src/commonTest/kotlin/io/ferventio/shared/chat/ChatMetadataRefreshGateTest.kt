package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMetadataRefreshGateTest {
    @Test
    fun loadedMetadataStaysFreshUntilTtlExpires() {
        var now = 1_000L
        val gate = ChatMetadataRefreshGate(
            ttlMillis = 5_000L,
            nowEpochMillis = { now },
        )

        assertTrue(gate.shouldRefreshGlobalBadges())
        assertTrue(gate.shouldRefreshChannelBadges("channel"))
        assertTrue(gate.shouldRefreshCheermotes("channel"))

        gate.markGlobalBadgesLoaded()
        gate.markChannelBadgesLoaded("channel")
        gate.markCheermotesLoaded("channel")

        now = 5_999L
        assertFalse(gate.shouldRefreshGlobalBadges())
        assertFalse(gate.shouldRefreshChannelBadges("channel"))
        assertFalse(gate.shouldRefreshCheermotes("channel"))

        now = 6_000L
        assertTrue(gate.shouldRefreshGlobalBadges())
        assertTrue(gate.shouldRefreshChannelBadges("channel"))
        assertTrue(gate.shouldRefreshCheermotes("channel"))
    }

    @Test
    fun retainChannelsDropsRemovedChannelTimestamps() {
        var now = 1_000L
        val gate = ChatMetadataRefreshGate(
            ttlMillis = 10_000L,
            nowEpochMillis = { now },
        )
        gate.markChannelBadgesLoaded("one")
        gate.markCheermotesLoaded("one")
        gate.markChannelBadgesLoaded("two")
        gate.markCheermotesLoaded("two")

        gate.retainChannels(listOf("two"))

        assertTrue(gate.shouldRefreshChannelBadges("one"))
        assertTrue(gate.shouldRefreshCheermotes("one"))
        assertFalse(gate.shouldRefreshChannelBadges("two"))
        assertFalse(gate.shouldRefreshCheermotes("two"))
    }
}
