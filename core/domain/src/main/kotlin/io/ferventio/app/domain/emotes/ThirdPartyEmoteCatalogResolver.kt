package io.ferventio.app.domain

object ThirdPartyEmoteCatalogResolver {
    /**
     * Twitch emotes are dedicated EventSub fragments and never enter this map.
     * Existing provider precedence is kept stable when 7TV is enabled:
     * BetterTTV > FrankerFaceZ > 7TV.
     */
    fun merge(
        betterTtv: Map<String, ThirdPartyEmoteAsset>,
        frankerFaceZ: Map<String, ThirdPartyEmoteAsset>,
        sevenTv: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        putAll(sevenTv.filterValues(ThirdPartyEmoteAsset::textResolvable))
        putAll(frankerFaceZ.filterValues(ThirdPartyEmoteAsset::textResolvable))
        putAll(betterTtv.filterValues(ThirdPartyEmoteAsset::textResolvable))
    }

    fun conflictCount(
        betterTtv: Map<String, ThirdPartyEmoteAsset>,
        frankerFaceZ: Map<String, ThirdPartyEmoteAsset>,
        sevenTv: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    ): Int = (betterTtv.keys + frankerFaceZ.keys + sevenTv.keys)
        .toSet()
        .count { code ->
            listOf(betterTtv, frankerFaceZ, sevenTv).count { code in it } > 1
        }
}
