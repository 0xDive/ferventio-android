package io.ferventio.shared.ui.chat

internal data class ChatMessageRenderSegment(
    val base: ChatMessageSegment,
    val overlays: List<ChatMessageSegment> = emptyList(),
)

internal fun groupChatMessageSegments(
    segments: List<ChatMessageSegment>,
): List<ChatMessageRenderSegment> = buildList {
    segments.forEach { segment ->
        if (segment.canOverlayPrevious()) {
            val directHost = lastOrNull()
            if (directHost != null && directHost.base.canHostOverlay()) {
                this[lastIndex] = directHost.copy(overlays = directHost.overlays + segment)
                return@forEach
            }

            val separator = lastOrNull()
            val separatedHost = getOrNull(lastIndex - 1)
            if (
                separator?.base?.isOverlaySeparator() == true &&
                separatedHost != null &&
                separatedHost.base.canHostOverlay()
            ) {
                removeAt(lastIndex)
                this[lastIndex] = separatedHost.copy(overlays = separatedHost.overlays + segment)
                return@forEach
            }
        }
        add(ChatMessageRenderSegment(base = segment))
    }
}

private fun ChatMessageSegment.canOverlayPrevious(): Boolean =
    zeroWidth &&
        kind == ChatMessageSegmentKind.THIRD_PARTY_EMOTE &&
        !imageUrl.isNullOrBlank()

private fun ChatMessageSegment.canHostOverlay(): Boolean =
    !zeroWidth &&
        !imageUrl.isNullOrBlank() &&
        when (kind) {
            ChatMessageSegmentKind.TWITCH_EMOTE,
            ChatMessageSegmentKind.THIRD_PARTY_EMOTE -> true
            else -> false
        }

private fun ChatMessageSegment.isOverlaySeparator(): Boolean =
    kind == ChatMessageSegmentKind.TEXT && text.isNotEmpty() && text.all(Char::isWhitespace)
