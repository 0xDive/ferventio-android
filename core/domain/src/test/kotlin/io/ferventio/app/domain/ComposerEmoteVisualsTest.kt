package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposerEmoteVisualsTest {
    private val kappa = ThirdPartyEmoteAsset(
        id = "1",
        code = "Kappa",
        provider = "twitch",
        imageType = "png",
        animated = false,
        imageUrl1x = "1x",
        imageUrl2x = "2x",
        imageUrl3x = "3x",
    )

    @Test
    fun `matches exact whitespace delimited codes`() {
        val matches = ComposerEmoteVisuals.findMatches("hello Kappa world Kappa", listOf(kappa))
        assertEquals(2, matches.size)
        assertEquals("Kappa", "hello Kappa world Kappa".substring(matches[0].start, matches[0].endExclusive))
    }

    @Test
    fun `does not replace code inside another token`() {
        assertTrue(ComposerEmoteVisuals.findMatches("Kappa123", listOf(kappa)).isEmpty())
    }

    @Test
    fun `ignores catalog items that cannot be sent as text codes`() {
        val unavailable = kappa.copy(textResolvable = false)
        assertTrue(ComposerEmoteVisuals.findMatches("Kappa", listOf(unavailable)).isEmpty())
    }
    @Test
    fun `prebuilt index can be reused for multiple drafts`() {
        val index = ComposerEmoteVisuals.buildIndex(listOf(kappa))
        assertEquals(1, ComposerEmoteVisuals.findMatches("Kappa", index).size)
        assertEquals(1, ComposerEmoteVisuals.findMatches("hello Kappa", index).size)
    }

}
