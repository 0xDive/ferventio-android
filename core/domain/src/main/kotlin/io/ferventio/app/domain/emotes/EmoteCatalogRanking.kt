package io.ferventio.app.domain

object EmoteCatalogRanking {
    fun suggestions(
        input: String,
        catalog: List<ThirdPartyEmoteAsset>,
        recentEmoteKeys: List<String>,
        favoriteEmoteKeys: Set<String> = emptySet(),
        limit: Int = 8,
    ): List<ThirdPartyEmoteAsset> {
        val token = input.takeLastWhile { !it.isWhitespace() }
            .takeIf { it.length >= 2 && !it.startsWith('/') }
            ?: return emptyList()
        return search(token, catalog, recentEmoteKeys, favoriteEmoteKeys, limit)
    }

    fun search(
        query: String,
        catalog: List<ThirdPartyEmoteAsset>,
        recentEmoteKeys: List<String>,
        favoriteEmoteKeys: Set<String> = emptySet(),
        limit: Int,
    ): List<ThirdPartyEmoteAsset> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()
        val usage = buildUsageStats(recentEmoteKeys)
        return catalog.asSequence()
            .filter { it.code.contains(normalizedQuery, ignoreCase = true) }
            .distinctBy { it.usageKey }
            .sortedWith(searchComparator(normalizedQuery, usage, favoriteEmoteKeys))
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    fun recent(
        catalog: List<ThirdPartyEmoteAsset>,
        recentEmoteKeys: List<String>,
        limit: Int,
    ): List<ThirdPartyEmoteAsset> {
        if (recentEmoteKeys.isEmpty()) return emptyList()
        val byKey = catalog.asSequence().distinctBy { it.usageKey }.associateBy { it.usageKey }
        return recentEmoteKeys.asSequence()
            .distinct()
            .mapNotNull(byKey::get)
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    fun frequent(
        catalog: List<ThirdPartyEmoteAsset>,
        recentEmoteKeys: List<String>,
        limit: Int,
    ): List<ThirdPartyEmoteAsset> {
        val usage = buildUsageStats(recentEmoteKeys)
        if (usage.isEmpty()) return emptyList()
        return catalog.asSequence()
            .distinctBy { it.usageKey }
            .filter { it.usageKey in usage }
            .sortedWith(
                compareByDescending<ThirdPartyEmoteAsset> { usage[it.usageKey]?.count ?: 0 }
                    .thenBy { usage[it.usageKey]?.mostRecentIndex ?: Int.MAX_VALUE }
                    .thenBy { it.code.lowercase() },
            )
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    fun usedInText(
        text: String,
        catalog: List<ThirdPartyEmoteAsset>,
    ): List<ThirdPartyEmoteAsset> {
        if (text.isBlank() || catalog.isEmpty()) return emptyList()
        val byCode = catalog.asSequence()
            .associateBy { it.code.lowercase() }
        return text.split(Regex("\\s+"))
            .mapNotNull { token -> byCode[token.lowercase()] }
    }

    private fun buildUsageStats(recentEmoteKeys: List<String>): Map<String, EmoteUsageStat> {
        if (recentEmoteKeys.isEmpty()) return emptyMap()
        val counts = recentEmoteKeys.groupingBy { it }.eachCount()
        val newestIndex = buildMap<String, Int> {
            recentEmoteKeys.forEachIndexed { index, key ->
                if (!containsKey(key)) put(key, index)
            }
        }
        return counts.mapValues { (key, count) ->
            EmoteUsageStat(
                count = count,
                mostRecentIndex = newestIndex[key] ?: Int.MAX_VALUE,
            )
        }
    }

    private fun searchComparator(
        query: String,
        usage: Map<String, EmoteUsageStat>,
        favoriteEmoteKeys: Set<String>,
    ): Comparator<ThirdPartyEmoteAsset> =
        compareBy<ThirdPartyEmoteAsset> {
            when {
                it.code.equals(query, ignoreCase = true) -> 0
                it.code.startsWith(query, ignoreCase = true) -> 1
                else -> 2
            }
        }
            .thenBy { if (it.scope == EmoteScope.CHANNEL) 0 else 1 }
            .thenBy { if (it.usageKey in favoriteEmoteKeys) 0 else 1 }
            .thenByDescending { usage[it.usageKey]?.count ?: 0 }
            .thenBy { usage[it.usageKey]?.mostRecentIndex ?: Int.MAX_VALUE }
            .thenBy { providerSortOrder(it.provider) }
            .thenBy { it.code.lowercase() }

    private fun providerSortOrder(providerId: String): Int = when (providerId) {
        "twitch" -> 0
        "betterttv" -> 1
        "frankerfacez" -> 2
        "7tv" -> 3
        else -> Int.MAX_VALUE
    }

    private data class EmoteUsageStat(
        val count: Int,
        val mostRecentIndex: Int,
    )
}
