package io.ferventio.app.data.local

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatNotice
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ReplyContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryMapperTest {
    @Test
    fun roundTripsMessageWithFragmentsBadgesAndReply() {
        val source = ChatMessage(
            id = "message-1",
            eventSubMessageId = "eventsub-1",
            channelId = "channel-1",
            channelLogin = "streamer",
            author = ChatAuthor(
                id = "user-1",
                login = "viewer",
                displayName = "Viewer",
                color = "#00FF00",
                badges = listOf(ChatBadge("moderator", "1", "")),
            ),
            text = "Hello Kappa @friend Cheer100",
            fragments = listOf(
                ChatFragment.Text("Hello "),
                ChatFragment.TwitchEmote(
                    text = "Kappa",
                    emoteId = "25",
                    emoteSetId = "0",
                    formats = setOf("static", "animated"),
                ),
                ChatFragment.Text(" "),
                ChatFragment.Mention("@friend", "user-2", "friend", "Friend"),
                ChatFragment.Text(" "),
                ChatFragment.Cheermote("Cheer100", "Cheer", 100, 100),
            ),
            timestamp = "2026-07-21T18:00:00Z",
            timestampMillis = 1_753_121_600_000L,
            reply = ReplyContext(
                parentMessageId = "parent-1",
                parentMessageBody = "Parent",
                parentUserId = "parent-user",
                parentUserLogin = "parent",
                parentUserName = "Parent",
            ),
            reward = ChatReward(id = "reward-1", title = "Hydrate", cost = 1500),
            notice = ChatNotice(
                type = "raid",
                systemMessage = "Raider raided with 321 viewers!",
                userMessage = "Hello from the raid!",
                gifterUserName = "Gifter",
                raidUserId = "raider-id",
                raidUserLogin = "raider",
                raidUserName = "Raider",
                raidViewerCount = 321,
            ),
            type = ChatMessageType.CHEER,
            flags = MessageFlags(isFirstMessage = true),
        )

        val bundle = ChatHistoryMapper.toWriteBundle(source, nowMillis = 10L)
        val restored = ChatHistoryMapper.fromDetails(
            MessageWithDetails(
                message = bundle.message,
                user = bundle.user,
                badges = bundle.badges,
                fragments = bundle.fragments,
            ),
        )

        assertEquals(source.id, restored.id)
        assertEquals("eventsub-1", restored.eventSubMessageId)
        assertEquals(source.author, restored.author)
        assertEquals(source.reply, restored.reply)
        assertEquals(source.notice, restored.notice)
        assertEquals(source.reward, restored.reward)
        assertEquals("Hello from the raid!", restored.notice?.userMessage)
        assertEquals("Gifter", restored.notice?.gifterUserName)
        assertEquals(ChatMessageType.CHEER, restored.type)
        assertEquals(source.fragments, restored.fragments)
        assertTrue(restored.flags.isFirstMessage)
    }
    @Test
    fun normalizesLegacyReplyMentionWhenRestoringHistory() {
        val source = ChatMessage(
            id = "legacy-reply",
            channelId = "channel-1",
            channelLogin = "streamer",
            author = ChatAuthor(
                id = "user-1",
                login = "viewer",
                displayName = "Viewer",
            ),
            text = "@parent старое сообщение",
            fragments = listOf(
                ChatFragment.Mention("@parent", "parent-id", "parent", "Parent"),
                ChatFragment.Text(" старое сообщение"),
            ),
            timestamp = "2026-07-21T18:00:00Z",
            reply = ReplyContext(
                parentMessageId = "parent-message",
                parentUserId = "parent-id",
                parentUserLogin = "parent",
                parentUserName = "Parent",
            ),
        )

        val bundle = ChatHistoryMapper.toWriteBundle(source, nowMillis = 10L)
        val restored = ChatHistoryMapper.fromDetails(
            MessageWithDetails(
                message = bundle.message,
                user = bundle.user,
                badges = bundle.badges,
                fragments = bundle.fragments,
            ),
        )

        assertEquals("старое сообщение", restored.text)
        assertEquals("старое сообщение", restored.fragments.joinToString("") { it.text })
    }


    @Test
    fun roundTripsZeroWidthThirdPartyEmote() {
        val source = ChatMessage(
            id = "composite-message",
            channelId = "channel-1",
            channelLogin = "streamer",
            author = ChatAuthor(id = "user-1", login = "viewer", displayName = "Viewer"),
            text = "Base Overlay",
            fragments = listOf(
                ChatFragment.ThirdPartyEmote(
                    text = "Base",
                    emoteId = "base-id",
                    provider = "7tv",
                    imageUrl = "https://cdn/base.webp",
                ),
                ChatFragment.ThirdPartyEmote(
                    text = "Overlay",
                    emoteId = "overlay-id",
                    provider = "7tv",
                    imageUrl = "https://cdn/overlay.webp",
                    zeroWidth = true,
                ),
            ),
            timestamp = "2026-07-22T10:00:00Z",
        )

        val bundle = ChatHistoryMapper.toWriteBundle(source, nowMillis = 10L)
        val restored = ChatHistoryMapper.fromDetails(
            MessageWithDetails(
                message = bundle.message,
                user = bundle.user,
                badges = bundle.badges,
                fragments = bundle.fragments,
            ),
        )

        assertEquals(source.fragments, restored.fragments)
        assertTrue((restored.fragments.last() as ChatFragment.ThirdPartyEmote).zeroWidth)
    }

    @Test
    fun roundTripsGifFragment() {
        val source = ChatMessage(
            id = "gif-message",
            channelId = "channel-1",
            channelLogin = "streamer",
            author = ChatAuthor(id = "user-1", login = "viewer", displayName = "Viewer"),
            text = "FunnyGif",
            fragments = listOf(
                ChatFragment.Gif(
                    text = "FunnyGif",
                    gifId = "gif-1",
                    url = "https://example.test/gif-1.gif",
                ),
            ),
            timestamp = "2026-07-22T10:00:00Z",
        )

        val bundle = ChatHistoryMapper.toWriteBundle(source, nowMillis = 10L)
        val restored = ChatHistoryMapper.fromDetails(
            MessageWithDetails(
                message = bundle.message,
                user = bundle.user,
                badges = bundle.badges,
                fragments = bundle.fragments,
            ),
        )

        assertEquals(source.fragments, restored.fragments)
    }

    @Test
    fun splitsLegacyLinkFragmentThatContainsFollowingTextAndAnotherUrl() {
        val sourceText =
            "https://t.me/first - Общение! https://t.me/second - новости"
        val source = ChatMessage(
            id = "legacy-link-message",
            channelId = "channel-1",
            channelLogin = "streamer",
            author = ChatAuthor(id = "user-1", login = "viewer", displayName = "Viewer"),
            text = sourceText,
            fragments = listOf(
                ChatFragment.Link(text = sourceText, url = sourceText),
            ),
            timestamp = "2026-08-02T16:34:45Z",
        )

        val bundle = ChatHistoryMapper.toWriteBundle(source, nowMillis = 10L)
        val restored = ChatHistoryMapper.fromDetails(
            MessageWithDetails(
                message = bundle.message,
                user = bundle.user,
                badges = bundle.badges,
                fragments = bundle.fragments,
            ),
        )

        assertEquals(
            listOf(
                ChatFragment.Link("https://t.me/first", "https://t.me/first"),
                ChatFragment.Text(" - Общение! "),
                ChatFragment.Link("https://t.me/second", "https://t.me/second"),
                ChatFragment.Text(" - новости"),
            ),
            restored.fragments,
        )
        assertEquals(sourceText, restored.fragments.joinToString("") { it.text })
    }

}
