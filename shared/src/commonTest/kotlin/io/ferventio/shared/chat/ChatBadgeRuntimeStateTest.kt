package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatBadgeRuntimeStateTest {
    @Test
    fun channelBadgeOverridesGlobalAsset() {
        val state = ChatRuntimeStateHolder()
        val badge = ChatBadge(setId = "subscriber", id = "3")
        state.replaceGlobalBadgeAssets(mapOf("ignored" to asset("global")))
        state.replaceChannelBadgeAssets(
            channelId = "channel-1",
            value = mapOf("ignored" to asset("channel")),
        )

        assertEquals(
            "https://cdn.test/channel-2x.png",
            state.badgeAsset("channel-1", badge)?.imageUrl2x,
        )
        assertEquals(
            "https://cdn.test/global-2x.png",
            state.badgeAsset("channel-2", badge)?.imageUrl2x,
        )
    }

    @Test
    fun retainingChannelsDropsUnusedChannelCatalogs() {
        val state = ChatRuntimeStateHolder()
        val badge = ChatBadge(setId = "subscriber", id = "3")
        state.replaceChannelBadgeAssets("channel-1", mapOf("ignored" to asset("one")))
        state.replaceChannelBadgeAssets("channel-2", mapOf("ignored" to asset("two")))

        state.retainChannels(listOf("channel-2"))

        assertNull(state.badgeAsset("channel-1", badge))
        assertEquals("https://cdn.test/two-2x.png", state.badgeAsset("channel-2", badge)?.imageUrl2x)
    }

    @Test
    fun clearDropsGlobalAndChannelBadgeCatalogs() {
        val state = ChatRuntimeStateHolder()
        val badge = ChatBadge(setId = "subscriber", id = "3")
        state.replaceGlobalBadgeAssets(mapOf("ignored" to asset("global")))
        state.replaceChannelBadgeAssets("channel-1", mapOf("ignored" to asset("channel")))

        state.clear()

        assertNull(state.badgeAsset("channel-1", badge))
        assertEquals(emptyMap(), state.globalBadgeAssets)
        assertEquals(emptyMap(), state.badgeAssetsByChannel)
    }

    private fun asset(prefix: String) = ChatBadgeAsset(
        setId = "subscriber",
        id = "3",
        imageUrl1x = "https://cdn.test/$prefix-1x.png",
        imageUrl2x = "https://cdn.test/$prefix-2x.png",
        imageUrl4x = "https://cdn.test/$prefix-4x.png",
        title = prefix,
        description = prefix,
    )
}
