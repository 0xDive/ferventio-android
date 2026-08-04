package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CheermoteResolverTest {
    private val tiers = mapOf(
        "cheer" to listOf(
            CheermoteAsset(
                prefix = "Cheer",
                minBits = 1,
                tier = 1,
                color = "#979797",
                animatedImageUrl = "https://cdn/cheer-1.gif",
                staticImageUrl = "https://cdn/cheer-1.png",
            ),
            CheermoteAsset(
                prefix = "Cheer",
                minBits = 100,
                tier = 100,
                color = "#9C3EE8",
                animatedImageUrl = "https://cdn/cheer-100.gif",
                staticImageUrl = "https://cdn/cheer-100.png",
            ),
        ),
    )

    @Test
    fun choosesHighestEligibleTierCaseInsensitively() {
        val result = CheermoteResolver.resolve(
            prefix = "CHEER",
            bits = 150,
            animate = true,
            assetsByPrefix = tiers,
        )

        assertEquals(100, result?.tier)
        assertEquals("https://cdn/cheer-100.gif", result?.imageUrl(animate = true))
    }

    @Test
    fun usesStaticAssetWhenAnimationIsDisabled() {
        val result = CheermoteResolver.resolve(
            prefix = "Cheer",
            bits = 10,
            animate = false,
            assetsByPrefix = tiers,
        )

        assertEquals("https://cdn/cheer-1.png", result?.imageUrl(animate = false))
    }

    @Test
    fun returnsNullWhenNoTierIsEligible() {
        assertNull(
            CheermoteResolver.resolve(
                prefix = "Cheer",
                bits = 0,
                animate = true,
                assetsByPrefix = tiers,
            ),
        )
    }
}
