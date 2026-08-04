package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class EmoteCatalogRankingTest {
    @Test
    fun exactMatchComesBeforePrefixAndContainsMatches() {
        val exact = asset("1", "GAGAGA")
        val prefix = asset("2", "GAGAGAZoom")
        val contains = asset("3", "MegaGAGAGA")

        val result = EmoteCatalogRanking.suggestions(
            input = "hello GAGAGA",
            catalog = listOf(contains, prefix, exact),
            recentEmoteKeys = emptyList(),
        )

        assertEquals(listOf(exact, prefix, contains), result)
    }

    @Test
    fun channelMatchComesBeforeGlobalMatchWithSamePrefix() {
        val global = asset("1", "PepeSmile", scope = EmoteScope.GLOBAL)
        val channel = asset("2", "PepeWave", scope = EmoteScope.CHANNEL)

        val result = EmoteCatalogRanking.suggestions(
            input = "Pepe",
            catalog = listOf(global, channel),
            recentEmoteKeys = emptyList(),
        )

        assertEquals(channel, result.first())
    }

    @Test
    fun frequentlyUsedMatchWinsInsideSameSearchClass() {
        val first = asset("1", "CatWave")
        val frequent = asset("2", "CatJam")

        val result = EmoteCatalogRanking.suggestions(
            input = "Cat",
            catalog = listOf(first, frequent),
            recentEmoteKeys = listOf(frequent.usageKey, frequent.usageKey, first.usageKey),
        )

        assertEquals(frequent, result.first())
    }


    @Test
    fun favouriteMatchWinsBeforeEquallyUsedMatch() {
        val regular = asset("1", "CatWave")
        val favourite = asset("2", "CatJam")

        val result = EmoteCatalogRanking.suggestions(
            input = "Cat",
            catalog = listOf(regular, favourite),
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = setOf(favourite.usageKey),
        )

        assertEquals(favourite, result.first())
    }

    @Test
    fun recentKeepsLastUsedOrderAndRemovesDuplicates() {
        val older = asset("1", "Older")
        val newest = asset("2", "Newest")

        val result = EmoteCatalogRanking.recent(
            catalog = listOf(older, newest),
            recentEmoteKeys = listOf(newest.usageKey, older.usageKey, newest.usageKey),
            limit = 10,
        )

        assertEquals(listOf(newest, older), result)
    }

    @Test
    fun recordsOnlyExactEmoteTokensFromSentMessage() {
        val cat = asset("1", "cat")
        val catJam = asset("2", "catJAM")

        val result = EmoteCatalogRanking.usedInText(
            text = "hello catJAM catapult catJAM",
            catalog = listOf(cat, catJam),
        )

        assertEquals(listOf(catJam, catJam), result)
    }

    private fun asset(
        id: String,
        code: String,
        scope: EmoteScope = EmoteScope.GLOBAL,
    ) = ThirdPartyEmoteAsset(
        id = id,
        code = code,
        provider = "7tv",
        imageType = "webp",
        animated = false,
        imageUrl1x = "https://cdn/$id/1",
        imageUrl2x = "https://cdn/$id/2",
        imageUrl3x = "https://cdn/$id/3",
        scope = scope,
    )
}
