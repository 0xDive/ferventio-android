package io.ferventio.app.domain

object ThirdPartyEmoteParser {
    fun enrich(
        message: ChatMessage,
        emotesByCode: Map<String, ThirdPartyEmoteAsset>,
    ): ChatMessage {
        if (emotesByCode.isEmpty()) return message
        if (!message.hasResolvableThirdPartyToken(emotesByCode)) return message

        var changed = false
        val enriched = buildList {
            message.fragments.forEach { fragment ->
                if (fragment !is ChatFragment.Text) {
                    add(fragment)
                    return@forEach
                }
                fragment.text.forEachTokenRun { token, whitespace ->
                    val asset = if (whitespace) {
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

    private fun ChatMessage.hasResolvableThirdPartyToken(
        emotesByCode: Map<String, ThirdPartyEmoteAsset>,
    ): Boolean {
        fragments.forEach { fragment ->
            val text = (fragment as? ChatFragment.Text)?.text ?: return@forEach
            if (text.hasResolvableThirdPartyToken(emotesByCode)) return true
        }
        return false
    }

    private fun String.hasResolvableThirdPartyToken(
        emotesByCode: Map<String, ThirdPartyEmoteAsset>,
    ): Boolean {
        if (isEmpty()) return false
        var start = 0
        var whitespace = this[0].isWhitespace()
        var index = 1
        while (index <= length) {
            val atEnd = index == length
            val nextWhitespace = if (atEnd) whitespace else this[index].isWhitespace()
            if (atEnd || nextWhitespace != whitespace) {
                if (!whitespace) {
                    val asset = emotesByCode[substring(start, index)]
                    if (asset?.textResolvable == true) return true
                }
                start = index
                whitespace = nextWhitespace
            }
            index += 1
        }
        return false
    }

    private inline fun String.forEachTokenRun(
        block: (token: String, whitespace: Boolean) -> Unit,
    ) {
        if (isEmpty()) return
        var start = 0
        var whitespace = this[0].isWhitespace()
        var index = 1
        while (index < length) {
            val nextWhitespace = this[index].isWhitespace()
            if (nextWhitespace != whitespace) {
                block(substring(start, index), whitespace)
                start = index
                whitespace = nextWhitespace
            }
            index += 1
        }
        block(substring(start, length), whitespace)
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
