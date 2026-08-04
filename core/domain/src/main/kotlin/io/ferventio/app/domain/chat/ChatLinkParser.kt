package io.ferventio.app.domain

/** A safe clickable HTTP(S) link found inside visible chat text. */
data class ChatLinkMatch(
    val start: Int,
    val endExclusive: Int,
    val url: String,
)

object ChatLinkParser {
    private val urlRegex = Regex(
        pattern = """(?i)(?<![@\w/:])((?:https?://|www\.)[^\s<>\[\]{}\"']+|(?:[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?\.)+[a-z]{2,24}(?:/[^\s<>\[\]{}\"']*)?)""",
    )

    fun findAll(text: String): List<ChatLinkMatch> = urlRegex.findAll(text).mapNotNull { match ->
        val raw = trimTrailingPunctuation(match.value)
        if (raw.isEmpty()) return@mapNotNull null
        val normalized = normalize(raw) ?: return@mapNotNull null
        ChatLinkMatch(
            start = match.range.first,
            endExclusive = match.range.first + raw.length,
            url = normalized,
        )
    }.toList()

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
            trimmed.contains('.') && trimmed.none(Char::isWhitespace) && "://" !in trimmed -> "https://$trimmed"
            else -> null
        }
    }

    private fun trimTrailingPunctuation(value: String): String {
        var result = value
        while (result.isNotEmpty()) {
            val last = result.last()
            val shouldTrim = when (last) {
                '.', ',', '!', '?', ';', ':', ']', '}', '»', '”' -> true
                ')' -> result.count { it == ')' } > result.count { it == '(' }
                else -> false
            }
            if (!shouldTrim) break
            result = result.dropLast(1)
        }
        return result
    }
}
