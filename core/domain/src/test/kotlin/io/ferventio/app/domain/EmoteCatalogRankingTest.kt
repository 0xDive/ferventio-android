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
    fun indexedSearchMatchesDirectRanking() {
        val exact = asset("1", "Cat")
        val prefix = asset("2", "CatJam")
        val contains = asset("3", "MegaCat")
        val catalog = listOf(contains, prefix, exact)
        val recent = listOf(prefix.usageKey, prefix.usageKey)

        val direct = EmoteCatalogRanking.search(
            query = "Cat",
            catalog = catalog,
            recentEmoteKeys = recent,
            favoriteEmoteKeys = setOf(exact.usageKey),
            limit = 8,
        )
        val indexed = EmoteCatalogRanking.search(
            query = "Cat",
            index = EmoteCatalogRanking.buildSearchIndex(catalog),
            recentEmoteKeys = recent,
            favoriteEmoteKeys = setOf(exact.usageKey),
            limit = 8,
        )

        assertEquals(direct, indexed)
    }

    @Test
    fun prebuiltUsageRankingAvoidsRecountingRecentKeysPerQuery() {
        val regular = asset("1", "CatWave")
        val frequent = asset("2", "CatJam")
        val index = EmoteCatalogRanking.buildSearchIndex(listOf(regular, frequent))
        val usage = EmoteCatalogRanking.buildUsageRanking(
            listOf(frequent.usageKey, frequent.usageKey, regular.usageKey),
        )

        val result = EmoteCatalogRanking.search(
            query = "Cat",
            index = index,
            recentEmoteKeys = emptyList(),
            usageRanking = usage,
            limit = 8,
        )

        assertEquals(frequent, result.first())
    }

    @Test
    fun indexedSearchRespectsProviderFilter() {
        val sevenTv = asset("1", "WaveCat", provider = "7tv")
        val twitch = asset("2", "WaveCat", provider = "twitch")
        val index = EmoteCatalogRanking.buildSearchIndex(listOf(sevenTv, twitch))

        val result = EmoteCatalogRanking.search(
            query = "Wave",
            index = index,
            recentEmoteKeys = emptyList(),
            limit = 8,
            providerId = "twitch",
        )

        assertEquals(listOf(twitch), result)
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
        provider: String = "7tv",
    ) = ThirdPartyEmoteAsset(
        id = id,
        code = code,
        provider = provider,
        imageType = "webp",
        animated = false,
        imageUrl1x = "https://cdn/$id/1",
        imageUrl2x = "https://cdn/$id/2",
        imageUrl3x = "https://cdn/$id/3",
        scope = scope,
    )
}
