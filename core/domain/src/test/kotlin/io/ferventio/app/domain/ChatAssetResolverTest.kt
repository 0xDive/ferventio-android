package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAssetResolverTest {
    @Test
    fun buildsAnimatedTwitchEmoteUrlWhenAvailable() {
        val fragment = ChatFragment.TwitchEmote(
            text = "Kappa",
            emoteId = "25",
            formats = setOf("static", "animated"),
        )

        assertEquals(
            "https://static-cdn.jtvnw.net/emoticons/v2/25/animated/dark/2.0",
            ChatAssetResolver.twitchEmoteUrl(fragment, animate = true),
        )
    }

    @Test
    fun fallsBackToStaticTwitchEmoteUrl() {
        val fragment = ChatFragment.TwitchEmote(
            text = "Kappa",
            emoteId = "25",
            formats = setOf("static", "animated"),
        )

        assertEquals(
            "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0",
            ChatAssetResolver.twitchEmoteUrl(fragment, animate = false),
        )
    }

    @Test
    fun rejectsBlankEmoteId() {
        assertNull(
            ChatAssetResolver.twitchEmoteUrl(
                ChatFragment.TwitchEmote(text = "", emoteId = ""),
                animate = true,
            ),
        )
    }

    @Test
    fun buildsBetterTtvCdnUrl() {
        assertEquals(
            "https://cdn.betterttv.net/emote/abc123/2x",
            ChatAssetResolver.betterTtvEmoteUrl("abc123"),
        )
    }

    @Test
    fun buildsSevenTvFallbackCdnUrl() {
        assertEquals(
            "https://cdn.7tv.app/emote/abc123/2x.webp",
            ChatAssetResolver.sevenTvEmoteUrl("abc123"),
        )
    }

    @Test
    fun resolvesBadgeBySetAndVersion() {
        val badge = ChatBadge(setId = "moderator", id = "1")
        val asset = ChatBadgeAsset(
            setId = "moderator",
            id = "1",
            imageUrl1x = "1x",
            imageUrl2x = "2x",
            imageUrl4x = "4x",
            title = "Moderator",
            description = "Moderator",
        )

        assertEquals(asset, ChatAssetResolver.badgeAsset(badge, mapOf(asset.key to asset)))
    }

    @Test
    fun normalizesProtocolRelativeThirdPartyUrl() {
        assertEquals(
            "https://cdn.frankerfacez.com/emote/1/2",
            ChatAssetResolver.absoluteImageUrl("//cdn.frankerfacez.com/emote/1/2"),
        )
    }

    @Test
    fun rejectsRelativeImageUrlWithoutHost() {
        assertNull(ChatAssetResolver.absoluteImageUrl("/emote/1/2"))
    }
}
