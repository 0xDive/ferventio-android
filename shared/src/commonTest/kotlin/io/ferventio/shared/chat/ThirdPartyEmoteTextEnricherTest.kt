package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThirdPartyEmoteTextEnricherTest {
    @Test
    fun resolvesExactCodeAndPreservesZeroWidth() {
        val result = enrichThirdPartyEmotes(
            listOf(ChatFragment.Text("hello base overlay world")),
            mapOf("base" to asset("base"), "overlay" to asset("overlay", true)),
        )
        assertEquals("hello ", (result[0] as ChatFragment.Text).text)
        assertEquals("base", (result[1] as ChatFragment.ThirdPartyEmote).text)
        assertEquals(" ", (result[2] as ChatFragment.Text).text)
        assertTrue((result[3] as ChatFragment.ThirdPartyEmote).zeroWidth)
        assertEquals(" world", (result[4] as ChatFragment.Text).text)
    }

    @Test
    fun punctuationDoesNotMatchWholeCode() {
        val result = enrichThirdPartyEmotes(
            listOf(ChatFragment.Text("Kappa, Kappa")),
            mapOf("Kappa" to asset("Kappa")),
        )
        assertEquals("Kappa, ", (result[0] as ChatFragment.Text).text)
        assertEquals("Kappa", (result[1] as ChatFragment.ThirdPartyEmote).text)
    }

    private fun asset(code: String, zeroWidth: Boolean = false) = ThirdPartyEmoteAsset(
        id = "id-$code",
        code = code,
        provider = "betterttv",
        imageType = "png",
        animated = false,
        imageUrl1x = "https://cdn.test/$code-1x.png",
        imageUrl2x = "https://cdn.test/$code-2x.png",
        imageUrl3x = "https://cdn.test/$code-3x.png",
        scope = EmoteScope.GLOBAL,
        zeroWidth = zeroWidth,
    )
}
