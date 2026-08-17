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
            val previous = lastOrNull()
            if (previous != null && previous.base.canHostOverlay()) {
                this[lastIndex] = previous.copy(overlays = previous.overlays + segment)
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
