package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ReplyContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatMessagePresentationTest {
    @Test
    fun splitsLinksInsideTextWithoutMakingSurroundingTextClickable() {
        val presentation = projectChatMessage(
            message = message(
                text = "before https://example.com/path, after",
                fragments = listOf(
                    ChatFragment.Text("before https://example.com/path, after"),
                ),
            ),
            deletedPlaceholder = "[deleted]",
        )

        assertEquals(
            listOf("before ", "https://example.com/path", ", after"),
            presentation.segments.map(ChatMessageSegment::text),
        )
        assertEquals(
            listOf(
                ChatMessageSegmentKind.TEXT,
                ChatMessageSegmentKind.LINK,
                ChatMessageSegmentKind.TEXT,
            ),
            presentation.segments.map(ChatMessageSegment::kind),
        )
        assertNull(presentation.segments.first().url)
        assertEquals("https://example.com/path", presentation.segments[1].url)
        assertNull(presentation.segments.last().url)
    }

    @Test
    fun preservesFragmentKindsAssetsAndNormalizesExplicitLinks() {
        val presentation = projectChatMessage(
            message = message(
                text = "Kappa party @bob Cheer100 docs",
                fragments = listOf(
                    ChatFragment.TwitchEmote(
                        text = "Kappa",
                        emoteId = "25",
                        formats = setOf("static", "animated"),
                    ),
                    ChatFragment.Text(" "),
                    ChatFragment.ThirdPartyEmote(
                        text = "party",
                        emoteId = "third-party-id",
                        provider = "7tv",
                        imageUrl = "//cdn.example.test/party.webp",
                    ),
                    ChatFragment.Text(" "),
                    ChatFragment.Mention(
                        text = "@bob",
                        userId = "u2",
                        userLogin = "bob",
                        userName = "Bob",
                    ),
                    ChatFragment.Text(" "),
                    ChatFragment.Cheermote(
                        text = "Cheer100",
                        prefix = "Cheer",
                        bits = 100,
                        tier = 100,
                    ),
                    ChatFragment.Text(" "),
                    ChatFragment.Link(text = "docs", url = "www.example.com/docs"),
                ),
            ),
            deletedPlaceholder = "[deleted]",
        )

        assertEquals(ChatMessageSegmentKind.TWITCH_EMOTE, presentation.segments[0].kind)
        assertEquals(
            "https://static-cdn.jtvnw.net/emoticons/v2/25/static/dark/2.0",
            presentation.segments[0].imageUrl,
        )
        assertEquals(ChatMessageSegmentKind.THIRD_PARTY_EMOTE, presentation.segments[2].kind)
        assertEquals("https://cdn.example.test/party.webp", presentation.segments[2].imageUrl)
        assertEquals(ChatMessageSegmentKind.MENTION, presentation.segments[4].kind)
        assertEquals(ChatMessageSegmentKind.CHEERMOTE, presentation.segments[6].kind)
        assertEquals(ChatMessageSegmentKind.LINK, presentation.segments[8].kind)
        assertEquals("https://www.example.com/docs", presentation.segments[8].url)
    }

    @Test
    fun projectsGifAssetAndNormalizesReplyPreview() {
        val presentation = projectChatMessage(
            message = message(
                text = "party",
                fragments = listOf(
                    ChatFragment.Gif(
                        text = "party",
                        gifId = "gif-1",
                        url = "//cdn.example.test/party.gif",
                    ),
                ),
                reply = ReplyContext(
                    parentMessageId = "parent",
                    parentUserName = "  Bob  ",
                    parentMessageBody = " first line\n   second line ",
                ),
            ),
            deletedPlaceholder = "[deleted]",
        )

        assertEquals("Bob", presentation.reply?.authorLabel)
        assertEquals("first line second line", presentation.reply?.bodyPreview)
        assertEquals(ChatMessageSegmentKind.GIF, presentation.segments.single().kind)
        assertEquals("https://cdn.example.test/party.gif", presentation.segments.single().imageUrl)
    }

    @Test
    fun invalidThirdPartyAssetKeepsTextFallback() {
        val presentation = projectChatMessage(
            message = message(
                text = "party",
                fragments = listOf(
                    ChatFragment.ThirdPartyEmote(
                        text = "party",
                        emoteId = "third-party-id",
                        provider = "7tv",
                        imageUrl = "not-a-url",
                    ),
                ),
            ),
            deletedPlaceholder = "[deleted]",
        )

        assertEquals(ChatMessageSegmentKind.THIRD_PARTY_EMOTE, presentation.segments.single().kind)
        assertNull(presentation.segments.single().imageUrl)
    }

    @Test
    fun hidesDeletedBodyButKeepsReplyAndBadgeContext() {
        val presentation = projectChatMessage(
            message = message(
                text = "secret body",
                fragments = listOf(ChatFragment.Text("secret body")),
                flags = MessageFlags(isDeleted = true),
                reply = ReplyContext(
                    parentMessageId = "parent",
                    parentUserLogin = "alice",
                    parentMessageBody = "do not quote this",
                ),
                badges = listOf(
                    ChatBadge(setId = "moderator", id = "1"),
                    ChatBadge(setId = "subscriber", id = "12"),
                ),
            ),
            deletedPlaceholder = "[deleted]",
        )

        assertTrue(presentation.isDeleted)
        assertEquals(listOf("[deleted]"), presentation.segments.map(ChatMessageSegment::text))
        assertEquals("alice", presentation.reply?.authorLabel)
        assertEquals("do not quote this", presentation.reply?.bodyPreview)
        assertEquals(listOf("moderator", "subscriber"), presentation.badgeLabels)
        assertEquals(listOf("moderator", "subscriber"), presentation.badges.map(ChatBadge::setId))
        assertFalse(presentation.segments.any { "secret" in it.text })
    }

    private fun message(
        text: String,
        fragments: List<ChatFragment>,
        flags: MessageFlags = MessageFlags(),
        reply: ReplyContext? = null,
        badges: List<ChatBadge> = emptyList(),
    ): ChatMessage = ChatMessage(
        id = "message",
        channelId = "channel",
        channelLogin = "channel-login",
        author = ChatAuthor(
            id = "u1",
            login = "alice",
            displayName = "Alice",
            badges = badges,
        ),
        text = text,
        fragments = fragments,
        timestamp = "2026-08-17T00:00:00Z",
        timestampMillis = 0L,
        reply = reply,
        flags = flags,
    )
}
