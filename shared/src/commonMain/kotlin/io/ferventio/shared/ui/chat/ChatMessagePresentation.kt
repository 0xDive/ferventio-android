package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAssetResolver
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatLinkParser
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.CheermoteResolver
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.enrichThirdPartyEmotes

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
    val imageUrl: String? = null,
    val zeroWidth: Boolean = false,
    val bttvModifiers: Set<BttvEmoteModifier> = emptySet(),
)

internal data class ChatReplyPresentation(
    val authorLabel: String,
    val bodyPreview: String? = null,
)

internal data class ChatMessagePresentation(
    val badges: List<ChatBadge>,
    val badgeLabels: List<String>,
    val reply: ChatReplyPresentation?,
    val segments: List<ChatMessageSegment>,
    val isDeleted: Boolean,
)

internal fun projectChatMessage(
    message: ChatMessage,
    deletedPlaceholder: String,
    thirdPartyEmotes: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    cheermoteAssetsByPrefix: Map<String, List<CheermoteAsset>> = emptyMap(),
    animatedMediaSupported: Boolean = supportsAnimatedChatMedia,
): ChatMessagePresentation {
    val badges = message.badges.distinctBy { badge -> "${badge.setId}/${badge.id}" }
    val replyAuthor = message.reply?.let { reply ->
        reply.parentUserName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: reply.parentUserLogin
                ?.trim()
                ?.takeIf(String::isNotEmpty)
    }
    val replyBodyPreview = message.reply
        ?.parentMessageBody
        ?.normalizeReplyPreview()
    return ChatMessagePresentation(
        badges = badges,
        badgeLabels = badges.map { badge ->
            badge.setId.trim().takeIf(String::isNotEmpty) ?: badge.id.trim()
        }.filter(String::isNotEmpty),
        reply = replyAuthor?.let { authorLabel ->
            ChatReplyPresentation(
                authorLabel = authorLabel,
                bodyPreview = replyBodyPreview,
            )
        },
        segments = if (message.isDeleted) {
            listOf(
                ChatMessageSegment(
                    text = deletedPlaceholder,
                    kind = ChatMessageSegmentKind.TEXT,
                ),
            )
        } else {
            val sourceFragments = message.fragments
                .takeIf(List<ChatFragment>::isNotEmpty)
                ?: listOf(ChatFragment.Text(message.text))
            val projected = enrichThirdPartyEmotes(sourceFragments, thirdPartyEmotes)
                .flatMap { fragment ->
                    projectFragment(
                        fragment = fragment,
                        cheermoteAssetsByPrefix = cheermoteAssetsByPrefix,
                        animatedMediaSupported = animatedMediaSupported,
                    )
                }
                .takeIf(List<ChatMessageSegment>::isNotEmpty)
                ?: projectPlainText(message.text)
            applyBttvEmoteModifiers(projected)
        },
        isDeleted = message.isDeleted,
    )
}

private fun projectFragment(
    fragment: ChatFragment,
    cheermoteAssetsByPrefix: Map<String, List<CheermoteAsset>>,
    animatedMediaSupported: Boolean,
): List<ChatMessageSegment> = when (fragment) {
    is ChatFragment.Text -> projectPlainText(fragment.text)
    is ChatFragment.Link -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.LINK,
            url = ChatLinkParser.normalize(fragment.url),
        ),
    )
    is ChatFragment.TwitchEmote -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.TWITCH_EMOTE,
            imageUrl = ChatAssetResolver.twitchEmoteUrl(
                fragment = fragment,
                animate = animatedMediaSupported,
            ),
        ),
    )
    is ChatFragment.ThirdPartyEmote -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            imageUrl = ChatAssetResolver.absoluteImageUrl(fragment.imageUrl),
            zeroWidth = fragment.zeroWidth,
        ),
    )
    is ChatFragment.Gif -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.GIF,
            imageUrl = ChatAssetResolver.absoluteImageUrl(fragment.url),
        ),
    )
    is ChatFragment.Mention -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.MENTION,
        ),
    )
    is ChatFragment.Cheermote -> {
        val asset = CheermoteResolver.resolve(
            prefix = fragment.prefix,
            bits = fragment.bits,
            animate = animatedMediaSupported,
            assetsByPrefix = cheermoteAssetsByPrefix,
        )
        listOf(
            ChatMessageSegment(
                text = fragment.text,
                kind = ChatMessageSegmentKind.CHEERMOTE,
                imageUrl = asset?.imageUrl(animatedMediaSupported),
            ),
        )
    }
    is ChatFragment.Unknown -> listOf(
        ChatMessageSegment(
            text = fragment.text,
            kind = ChatMessageSegmentKind.UNKNOWN,
        ),
    )
}

private fun projectPlainText(value: String): List<ChatMessageSegment> {
    if (value.isEmpty()) return emptyList()
    val links = ChatLinkParser.findAll(value)
    if (links.isEmpty()) {
        return listOf(ChatMessageSegment(value, ChatMessageSegmentKind.TEXT))
    }
    return buildList {
        var cursor = 0
        links.forEach { link ->
            if (link.start > cursor) {
                add(
                    ChatMessageSegment(
                        text = value.substring(cursor, link.start),
                        kind = ChatMessageSegmentKind.TEXT,
                    ),
                )
            }
            add(
                ChatMessageSegment(
                    text = value.substring(link.start, link.endExclusive),
                    kind = ChatMessageSegmentKind.LINK,
                    url = link.url,
                ),
            )
            cursor = link.endExclusive
        }
        if (cursor < value.length) {
            add(
                ChatMessageSegment(
                    text = value.substring(cursor),
                    kind = ChatMessageSegmentKind.TEXT,
                ),
            )
        }
    }
}

private fun String.normalizeReplyPreview(): String? =
    trim()
        .replace(Regex("\\s+"), " ")
        .takeIf(String::isNotEmpty)
