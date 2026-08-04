package io.ferventio.app.domain

object CheermoteResolver {
    fun resolve(
        prefix: String,
        bits: Int,
        animate: Boolean,
        assetsByPrefix: Map<String, List<CheermoteAsset>>,
    ): CheermoteAsset? = assetsByPrefix[prefix.lowercase()]
        .orEmpty()
        .asSequence()
        .filter { asset -> asset.minBits <= bits && asset.imageUrl(animate) != null }
        .maxByOrNull(CheermoteAsset::minBits)
}
