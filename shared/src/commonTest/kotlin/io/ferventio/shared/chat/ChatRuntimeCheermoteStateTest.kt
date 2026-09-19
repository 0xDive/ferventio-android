package io.ferventio.shared.chat

import io.ferventio.app.domain.CheermoteAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatRuntimeCheermoteStateTest {
    @Test
    fun normalizesCatalogAndDropsRemovedChannels() {
        val state = ChatRuntimeStateHolder()
        state.replaceChannelCheermoteAssets(
            channelId = " channel-1 ",
            value = mapOf(
                "ignored-key" to listOf(
                    asset(prefix = " Cheer ", minBits = 100),
                    asset(prefix = "Cheer", minBits = 1),
                ),
            ),
        )
        state.replaceChannelCheermoteAssets(
            channelId = "channel-2",
            value = mapOf("custom" to listOf(asset(prefix = "Custom", minBits = 1))),
        )

        assertEquals(listOf(1, 100), state.cheermoteAssets("channel-1").getValue("cheer").map { it.minBits })
        assertEquals(setOf("channel-1", "channel-2"), state.cheermoteAssetsByChannel.keys)

        state.retainChannels(listOf("channel-2"))

        assertTrue(state.cheermoteAssets("channel-1").isEmpty())
        assertEquals(setOf("channel-2"), state.snapshot.cheermoteAssetsByChannel.keys)
    }

    @Test
    fun clearRemovesCheermoteCatalogs() {
        val state = ChatRuntimeStateHolder()
        state.replaceChannelCheermoteAssets(
            channelId = "channel-1",
            value = mapOf("cheer" to listOf(asset(prefix = "Cheer", minBits = 1))),
        )

        state.clear()

        assertTrue(state.cheermoteAssetsByChannel.isEmpty())
    }

    private fun asset(prefix: String, minBits: Int) = CheermoteAsset(
        prefix = prefix,
        minBits = minBits,
        tier = minBits,
        color = "#ffffff",
        animatedImageUrl = "https://cdn.test/$minBits-animated.gif",
        staticImageUrl = "https://cdn.test/$minBits-static.png",
    )
}
