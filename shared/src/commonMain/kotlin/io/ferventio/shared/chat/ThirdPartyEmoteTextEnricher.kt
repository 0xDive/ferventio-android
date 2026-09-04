package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ThirdPartyEmoteAsset

internal fun enrichThirdPartyEmotes(
    fragments: List<ChatFragment>,
    catalogByCode: Map<String, ThirdPartyEmoteAsset>,
): List<ChatFragment> {
    if (fragments.isEmpty() || catalogByCode.isEmpty()) return fragments
    return fragments.flatMap { fragment ->
        when (fragment) {
            is ChatFragment.Text -> enrichThirdPartyText(fragment.text, catalogByCode)
            else -> listOf(fragment)
        }
    }
}

private fun enrichThirdPartyText(
    text: String,
    catalogByCode: Map<String, ThirdPartyEmoteAsset>,
): List<ChatFragment> {
    if (text.isEmpty()) return listOf(ChatFragment.Text(text))
    val result = mutableListOf<ChatFragment>()
    var cursor = 0
    while (cursor < text.length) {
        val whitespace = text[cursor].isWhitespace()
        var end = cursor + 1
        while (end < text.length && text[end].isWhitespace() == whitespace) {
            end += 1
        }
        val token = text.substring(cursor, end)
        if (!whitespace) {
            val asset = catalogByCode[token]
                ?.takeIf { it.textResolvable && it.code == token }
            if (asset != null) {
                result += ChatFragment.ThirdPartyEmote(
                    text = token,
                    emoteId = asset.id,
                    provider = asset.provider,
                    animated = asset.animated,
                    imageUrl = asset.preferredImageUrl(),
                    zeroWidth = asset.zeroWidth,
                )
            } else {
                result.appendText(token)
            }
        } else {
            result.appendText(token)
        }
        cursor = end
    }
    return result.ifEmpty { listOf(ChatFragment.Text(text)) }
}

private fun ThirdPartyEmoteAsset.preferredImageUrl(): String? =
    imageUrl2x.takeIf(String::isNotBlank)
        ?: imageUrl3x.takeIf(String::isNotBlank)
        ?: imageUrl1x.takeIf(String::isNotBlank)

private fun MutableList<ChatFragment>.appendText(value: String) {
    if (value.isEmpty()) return
    val previous = lastOrNull() as? ChatFragment.Text
    if (previous == null) {
        add(ChatFragment.Text(value))
    } else {
        this[lastIndex] = ChatFragment.Text(previous.text + value)
    }
}
