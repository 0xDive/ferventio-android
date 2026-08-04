package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyEmoteParserTest {
    private val kekw = ThirdPartyEmoteAsset(
        id = "bttv-1",
        code = "KEKW",
        provider = "betterttv",
        imageType = "webp",
        animated = true,
        imageUrl1x = "https://cdn/1x",
        imageUrl2x = "https://cdn/2x",
        imageUrl3x = "https://cdn/3x",
    )

    @Test
    fun replacesExactWhitespaceSeparatedToken() {
        val result = ThirdPartyEmoteParser.enrich(message("hello KEKW world"), mapOf("KEKW" to kekw))
        assertEquals("hello KEKW world", result.fragments.joinToString("") { it.text })
        assertTrue(result.fragments.any { it is ChatFragment.ThirdPartyEmote && it.text == "KEKW" })
    }

    @Test
    fun doesNotReplaceInsideAnotherWordOrWithPunctuation() {
        val result = ThirdPartyEmoteParser.enrich(message("xKEKW KEKW!"), mapOf("KEKW" to kekw))
        assertTrue(result.fragments.none { it is ChatFragment.ThirdPartyEmote })
    }


    @Test
    fun removesSeparatorBeforeZeroWidthCompositeAndMarksLayer() {
        val base = kekw.copy(id = "base", code = "Base", animated = false)
        val overlay = kekw.copy(
            id = "overlay",
            code = "Overlay",
            provider = "7tv",
            zeroWidth = true,
        )

        val result = ThirdPartyEmoteParser.enrich(
            message("Base Overlay"),
            mapOf("Base" to base, "Overlay" to overlay),
        )

        assertEquals(2, result.fragments.size)
        assertEquals("BaseOverlay", result.fragments.joinToString("") { it.text })
        assertTrue(result.fragments[0] is ChatFragment.ThirdPartyEmote)
        val layer = result.fragments[1] as ChatFragment.ThirdPartyEmote
        assertTrue(layer.zeroWidth)
        assertEquals("Overlay", layer.text)
    }

    @Test
    fun keepsExistingTwitchEmoteUntouched() {
        val source = message("hello").copy(
            fragments = listOf(
                ChatFragment.Text("KEKW "),
                ChatFragment.TwitchEmote("Kappa", "25"),
            ),
        )
        val result = ThirdPartyEmoteParser.enrich(source, mapOf("KEKW" to kekw))
        assertTrue(result.fragments[0] is ChatFragment.ThirdPartyEmote)
        assertTrue(result.fragments[2] is ChatFragment.TwitchEmote)
    }

    private fun message(text: String) = ChatMessage(
        id = "m1",
        channelId = "c1",
        channelLogin = "channel",
        author = ChatAuthor("u1", "user", "User"),
        text = text,
        fragments = listOf(ChatFragment.Text(text)),
        timestamp = "2026-07-22T00:00:00Z",
    )
}
