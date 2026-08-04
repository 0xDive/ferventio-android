package io.ferventio.app.domain

object ThirdPartyEmoteParser {
    private val tokenRegex = Regex("\\s+|\\S+")

    fun enrich(
        message: ChatMessage,
        emotesByCode: Map<String, ThirdPartyEmoteAsset>,
    ): ChatMessage {
        if (emotesByCode.isEmpty()) return message
        var changed = false
        val enriched = buildList {
            message.fragments.forEach { fragment ->
                if (fragment !is ChatFragment.Text) {
                    add(fragment)
                    return@forEach
                }
                tokenRegex.findAll(fragment.text).forEach { match ->
                    val token = match.value
                    val asset = if (token.any(Char::isWhitespace)) {
                        null
                    } else {
                        emotesByCode[token]?.takeIf(ThirdPartyEmoteAsset::textResolvable)
                    }
                    if (asset == null) {
                        appendText(token)
                    } else {
                        changed = true
                        if (asset.zeroWidth) removeWhitespaceBeforeComposite()
                        add(
                            ChatFragment.ThirdPartyEmote(
                                text = token,
                                emoteId = asset.id,
                                provider = asset.provider,
                                animated = asset.animated,
                                imageUrl = asset.imageUrl2x.ifBlank { asset.imageUrl1x },
                                zeroWidth = asset.zeroWidth,
                            ),
                        )
                    }
                }
            }
        }
        return if (changed) message.copy(fragments = enriched) else message
    }

    private fun MutableList<ChatFragment>.removeWhitespaceBeforeComposite() {
        val whitespace = lastOrNull() as? ChatFragment.Text ?: return
        if (whitespace.text.isBlank() && dropLast(1).lastOrNull().isEmote()) {
            removeAt(lastIndex)
        }
    }

    private fun ChatFragment?.isEmote(): Boolean = when (this) {
        is ChatFragment.TwitchEmote,
        is ChatFragment.ThirdPartyEmote,
        is ChatFragment.Gif,
        is ChatFragment.Cheermote -> true
        else -> false
    }

    private fun MutableList<ChatFragment>.appendText(value: String) {
        if (value.isEmpty()) return
        val previous = lastOrNull() as? ChatFragment.Text
        if (previous == null) {
            add(ChatFragment.Text(value))
        } else {
            this[lastIndex] = previous.copy(text = previous.text + value)
        }
    }
}
