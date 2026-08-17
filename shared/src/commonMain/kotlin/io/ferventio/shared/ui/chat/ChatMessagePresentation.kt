package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatLinkParser
import io.ferventio.app.domain.ChatMessage

internal enum class ChatMessageSegmentKind {
    TEXT,
    LINK,
    TWITCH_EMOTE,
    THIRD_PARTY_EMOTE,
    GIF,
    MENTION,
    CHEERMOTE,
    UNKNOWN,
}

internal data class ChatMessageSegment(
    val text: String,
    val kind: ChatMessageSegmentKind,
    val url: String? = null,
)

internal data class ChatReplyPresentation(
    val authorLabel: String?,
)

internal data class ChatMessagePresentation(
    val segments: List<ChatMessageSegment>,
    val reply: ChatReplyPresentation?,
    val badgeLabels: List<String>,
    val isDeleted: Boolean,
)

internal fun projectChatMessage(
    message: ChatMessage,
    deletedPlaceholder: String,
): ChatMessagePresentation {
    val reply = message.reply?.let { context ->
        ChatReplyPresentation(
            authorLabel = context.parentUserName
                ?.takeIf(String::isNotBlank)
                ?: context.parentUserLogin?.takeIf(String::isNotBlank),
        )
    }
    val badgeLabels = message.badges
        .mapNotNull { badge ->
            badge.setId.takeIf(String::isNotBlank)
                ?: badge.id.takeIf(String::isNotBlank)
        }
        .distinct()

    if (message.isDeleted) {
        return ChatMessagePresentation(
            segments = listOf(
                ChatMessageSegment(
                    text = deletedPlaceholder,
                    kind = ChatMessageSegmentKind.TEXT,
                ),
            ),
            reply = reply,
            badgeLabels = badgeLabels,
            isDeleted = true,
        )
    }

    val sourceFragments = message.fragments.ifEmpty {
        listOf(ChatFragment.Text(message.text))
    }
    val segments = sourceFragments.flatMap(::projectFragment)

    return ChatMessagePresentation(
        segments = segments,
        reply = reply,
        badgeLabels = badgeLabels,
        isDeleted = false,
    )
}

private fun projectFragment(fragment: ChatFragment): List<ChatMessageSegment> = when (fragment) {
    is ChatFragment.Text -> projectTextFragment(fragment.text)
    is ChatFragment.Link -> {
        val safeUrl = ChatLinkParser.normalize(fragment.url)
        listOf(
            ChatMessageSegment(
                text = fragment.text,
                kind = if (safeUrl == null) {
                    ChatMessageSegmentKind.TEXT
                } else {
                    ChatMessageSegmentKind.LINK
                },
                url = safeUrl,
            ),
        )
    }
    is ChatFragment.TwitchEmote -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.TWITCH_EMOTE),
    )
    is ChatFragment.ThirdPartyEmote -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.THIRD_PARTY_EMOTE),
    )
    is ChatFragment.Gif -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.GIF),
    )
    is ChatFragment.Mention -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.MENTION),
    )
    is ChatFragment.Cheermote -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.CHEERMOTE),
    )
    is ChatFragment.Unknown -> listOf(
        ChatMessageSegment(fragment.text, ChatMessageSegmentKind.UNKNOWN),
    )
}

private fun projectTextFragment(text: String): List<ChatMessageSegment> {
    if (text.isEmpty()) return emptyList()

    val links = ChatLinkParser.findAll(text)
    if (links.isEmpty()) {
        return listOf(ChatMessageSegment(text, ChatMessageSegmentKind.TEXT))
    }

    val segments = mutableListOf<ChatMessageSegment>()
    var cursor = 0
    links.forEach { link ->
        if (link.start > cursor) {
            segments += ChatMessageSegment(
                text = text.substring(cursor, link.start),
                kind = ChatMessageSegmentKind.TEXT,
            )
        }
        segments += ChatMessageSegment(
            text = text.substring(link.start, link.endExclusive),
            kind = ChatMessageSegmentKind.LINK,
            url = link.url,
        )
        cursor = link.endExclusive
    }
    if (cursor < text.length) {
        segments += ChatMessageSegment(
            text = text.substring(cursor),
            kind = ChatMessageSegmentKind.TEXT,
        )
    }
    return segments
}
