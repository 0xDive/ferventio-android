package io.ferventio.app.domain

/** A Twitch login referenced by an @mention in rendered chat text. */
data class ChatMention(
    val login: String,
    val range: IntRange,
)

object ChatMentionParser {
    private val mentionPattern = Regex("(?<![A-Za-z0-9_@])@([A-Za-z0-9_]{1,25})")

    fun findAll(text: String): List<ChatMention> =
        mentionPattern.findAll(text).map { match ->
            ChatMention(
                login = match.groupValues[1],
                range = match.range,
            )
        }.toList()

    fun findAt(text: String, offset: Int): ChatMention? {
        if (offset !in text.indices) return null
        return findAll(text).firstOrNull { mention -> offset in mention.range }
    }
}
