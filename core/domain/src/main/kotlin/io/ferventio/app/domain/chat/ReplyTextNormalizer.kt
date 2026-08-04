package io.ferventio.app.domain

/**
 * Twitch clients commonly prepend @parentLogin to the visible body of a reply.
 * The reply relation already contains that user, so the leading mention is redundant.
 *
 * Only one matching mention at the very beginning is removed. Mentions written later,
 * including a second intentional mention of the same user, are preserved.
 */
object ReplyTextNormalizer {
    data class Result(
        val text: String,
        val fragments: List<ChatFragment>,
    )

    fun normalize(
        text: String,
        fragments: List<ChatFragment>,
        reply: ReplyContext?,
    ): Result {
        val prefixLength = removablePrefixLength(text, reply)
        if (prefixLength <= 0) {
            return Result(text = text, fragments = fragments)
        }

        val normalizedText = text.substring(prefixLength)
        val normalizedFragments = removePrefixFromFragments(fragments, prefixLength)
            .ifEmpty {
                if (normalizedText.isEmpty()) emptyList()
                else listOf(ChatFragment.Text(normalizedText))
            }

        return Result(
            text = normalizedText,
            fragments = normalizedFragments,
        )
    }

    fun normalizeText(text: String, reply: ReplyContext?): String {
        val prefixLength = removablePrefixLength(text, reply)
        return if (prefixLength > 0) text.substring(prefixLength) else text
    }

    private fun removablePrefixLength(text: String, reply: ReplyContext?): Int {
        if (reply == null || text.isEmpty()) return 0

        val aliases = listOfNotNull(reply.parentUserLogin, reply.parentUserName)
            .asSequence()
            .map { it.trim().removePrefix("@") }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedByDescending(String::length)

        return aliases.firstNotNullOfOrNull { alias ->
            prefixEnd(text, alias)
        } ?: 0
    }

    private fun prefixEnd(text: String, alias: String): Int? {
        val mention = "@$alias"
        if (text.length < mention.length ||
            !text.regionMatches(
                thisOffset = 0,
                other = mention,
                otherOffset = 0,
                length = mention.length,
                ignoreCase = true,
            )
        ) {
            return null
        }

        var index = mention.length
        if (index == text.length) return index

        val boundary = text[index]
        if (!boundary.isWhitespace() && boundary != ',' && boundary != ':') {
            return null
        }

        if (boundary == ',' || boundary == ':') {
            index += 1
        }
        while (index < text.length && text[index].isWhitespace()) {
            index += 1
        }
        return index
    }

    private fun removePrefixFromFragments(
        fragments: List<ChatFragment>,
        prefixLength: Int,
    ): List<ChatFragment> {
        var remaining = prefixLength
        val result = ArrayList<ChatFragment>(fragments.size)

        fragments.forEach { fragment ->
            if (remaining <= 0) {
                result += fragment
                return@forEach
            }

            if (remaining >= fragment.text.length) {
                remaining -= fragment.text.length
                return@forEach
            }

            val remainingText = fragment.text.substring(remaining)
            remaining = 0
            if (remainingText.isNotEmpty()) {
                result += fragment.withText(remainingText)
            }
        }

        return result
    }

    private fun ChatFragment.withText(value: String): ChatFragment = when (this) {
        is ChatFragment.Text -> copy(text = value)
        is ChatFragment.TwitchEmote -> copy(text = value)
        is ChatFragment.ThirdPartyEmote -> copy(text = value)
        is ChatFragment.Gif -> copy(text = value)
        is ChatFragment.Mention -> copy(text = value)
        is ChatFragment.Cheermote -> copy(text = value)
        is ChatFragment.Link -> copy(text = value)
        is ChatFragment.Unknown -> copy(text = value)
    }
}
