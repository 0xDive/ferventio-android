package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThirdPartyEmoteCatalogResolverTest {
    @Test
    fun keepsStableProviderPrecedenceForSameCode() {
        val merged = ThirdPartyEmoteCatalogResolver.merge(
            betterTtv = mapOf("SameCode" to asset("bttv", "betterttv")),
            frankerFaceZ = mapOf("SameCode" to asset("ffz", "frankerfacez")),
            sevenTv = mapOf("SameCode" to asset("7tv", "7tv")),
        )

        assertEquals("bttv", merged.getValue("SameCode").id)
        assertEquals(
            1,
            ThirdPartyEmoteCatalogResolver.conflictCount(
                betterTtv = mapOf("SameCode" to asset("bttv", "betterttv")),
                frankerFaceZ = mapOf("SameCode" to asset("ffz", "frankerfacez")),
                sevenTv = mapOf("SameCode" to asset("7tv", "7tv")),
            ),
        )
    }

    @Test
    fun frankerFaceZWinsOverSevenTvWhenBetterTtvIsMissing() {
        val merged = ThirdPartyEmoteCatalogResolver.merge(
            betterTtv = emptyMap(),
            frankerFaceZ = mapOf("SameCode" to asset("ffz", "frankerfacez")),
            sevenTv = mapOf("SameCode" to asset("7tv", "7tv")),
        )

        assertEquals("ffz", merged.getValue("SameCode").id)
    }

    @Test
    fun keepsUniqueCodesFromAllProviders() {
        val merged = ThirdPartyEmoteCatalogResolver.merge(
            betterTtv = mapOf("BTTV" to asset("bttv", "betterttv")),
            frankerFaceZ = mapOf("FFZ" to asset("ffz", "frankerfacez")),
            sevenTv = mapOf("SEVENTV" to asset("7tv", "7tv")),
        )

        assertEquals(setOf("BTTV", "FFZ", "SEVENTV"), merged.keys)
    }

    private fun asset(id: String, provider: String) = ThirdPartyEmoteAsset(
        id = id,
        code = id,
        provider = provider,
        imageType = "webp",
        animated = false,
        imageUrl1x = "https://cdn/$id/1",
        imageUrl2x = "https://cdn/$id/2",
        imageUrl3x = "https://cdn/$id/3",
    )
}
