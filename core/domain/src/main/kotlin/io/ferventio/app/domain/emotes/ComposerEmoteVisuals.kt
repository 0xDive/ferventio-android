package io.ferventio.app.domain

/**
 * Finds exact emote codes in a composer draft without changing the text that is sent to Twitch.
 * Twitch and third-party emote codes are treated as whitespace-delimited tokens.
 */
object ComposerEmoteVisuals {
    data class Match(
        val start: Int,
        val endExclusive: Int,
        val asset: ThirdPartyEmoteAsset,
    )

    /**
     * Immutable lookup prepared once when the catalog changes. Building this map on every
     * keystroke was noticeably expensive for accounts with several thousand Twitch emotes.
     */
    class Index internal constructor(
        internal val byCode: Map<String, ThirdPartyEmoteAsset>,
    )

    fun buildIndex(catalog: List<ThirdPartyEmoteAsset>): Index = Index(
        catalog.asSequence()
            .filter { it.textResolvable && it.code.isNotBlank() }
            .distinctBy { it.code }
            .associateBy { it.code },
    )

    fun findMatches(
        text: String,
        index: Index,
    ): List<Match> {
        if (text.isBlank() || index.byCode.isEmpty()) return emptyList()

        val matches = ArrayList<Match>()
        var cursor = 0
        while (cursor < text.length) {
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
            if (cursor >= text.length) break
            val start = cursor
            while (cursor < text.length && !text[cursor].isWhitespace()) cursor++
            val asset = index.byCode[text.substring(start, cursor)] ?: continue
            matches += Match(start = start, endExclusive = cursor, asset = asset)
        }
        return matches
    }

    /** Kept for domain tests and callers that do not retain a catalog index. */
    fun findMatches(
        text: String,
        catalog: List<ThirdPartyEmoteAsset>,
    ): List<Match> = findMatches(text, buildIndex(catalog))
}
