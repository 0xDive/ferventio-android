package io.ferventio.app.application

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage

internal fun stripLegacyThirdPartyEmoteFragments(
    message: ChatMessage,
): ChatMessage {
    val source = message.fragments
    var updated: MutableList<ChatFragment>? = null
    var previousSourceWasEmote = false

    for (index in source.indices) {
        val fragment = source[index]
        if (fragment is ChatFragment.ThirdPartyEmote) {
            val target = updated ?: ArrayList<ChatFragment>(source.size).also { copy ->
                for (prefixIndex in 0 until index) {
                    copy += source[prefixIndex]
                }
                updated = copy
            }
            if (fragment.zeroWidth && previousSourceWasEmote) {
                target.appendLegacyText(" ")
            }
            target.appendLegacyText(fragment.text)
            previousSourceWasEmote = true
        } else {
            updated?.add(fragment)
            previousSourceWasEmote = when (fragment) {
                is ChatFragment.TwitchEmote,
                is ChatFragment.Gif,
                is ChatFragment.Cheermote -> true
                else -> false
            }
        }
    }

    return updated?.let { fragments ->
        message.copy(fragments = fragments)
    } ?: message
}

private fun MutableList<ChatFragment>.appendLegacyText(value: String) {
    if (value.isEmpty()) return
    val previous = lastOrNull() as? ChatFragment.Text
    if (previous == null) {
        add(ChatFragment.Text(value))
    } else {
        this[lastIndex] = previous.copy(text = previous.text + value)
    }
}
