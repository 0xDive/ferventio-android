package io.ferventio.shared.history

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatNotice
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.ReplyContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatHistorySnapshotCodecTest {
    @Test
    fun richMessageRoundTripsWithoutTransientStateLoss() {
        val message = ChatMessage(
            id = "message-1",
            eventSubMessageId = "event-1",
            channelId = "channel-1",
            channelLogin = "ferventio",
            author = ChatAuthor(
                id = "user-1",
                login = "viewer",
                displayName = "Viewer",
                color = "#9147FF",
                badges = listOf(ChatBadge("subscriber", "12", "12")),
                profileImageUrl = "https://example.invalid/avatar.png",
            ),
            text = "hello Kappa @mod 100 https://example.invalid",
            fragments = listOf(
                ChatFragment.Text("hello "),
                ChatFragment.TwitchEmote("Kappa", "25", "0", "user-1", setOf("static", "animated")),
                ChatFragment.Text(" "),
                ChatFragment.Mention("@mod", "user-2", "mod", "Mod"),
                ChatFragment.Text(" "),
                ChatFragment.Cheermote("Cheer100", "Cheer", 100, 100),
                ChatFragment.Text(" "),
                ChatFragment.ThirdPartyEmote("OMEGALUL", "third-1", "7TV", true, "https://example.invalid/emote.webp", true),
                ChatFragment.Gif("gif", "gif-1", "https://example.invalid/gif.gif"),
                ChatFragment.Link("https://example.invalid", "https://example.invalid"),
                ChatFragment.Unknown("future", "future_fragment"),
            ),
            timestamp = "2026-08-19T09:00:00Z",
            timestampMillis = 1_755_594_000_000L,
            reply = ReplyContext(
                parentMessageId = "parent-1",
                parentMessageBody = "parent",
                parentUserId = "parent-user",
                parentUserLogin = "parent",
                parentUserName = "Parent",
                threadMessageId = "thread-1",
                threadUserId = "thread-user",
                threadUserLogin = "thread",
                threadUserName = "Thread",
            ),
            notice = ChatNotice(
                type = "sub",
                systemMessage = "subscribed",
                userMessage = "great stream",
                subTier = "1000",
                durationMonths = 12,
                cumulativeMonths = 18,
                streakMonths = 4,
                isGift = false,
                isAnonymous = false,
            ),
            reward = ChatReward("reward-1", "Hydrate", 500),
            type = ChatMessageType.RESUBSCRIPTION,
            flags = MessageFlags(
                isDeleted = true,
                isSystem = false,
                isAction = false,
                isFirstMessage = true,
                isReturningChatter = true,
            ),
            moderation = ModerationState(
                action = ModerationAction.TIMEOUT,
                actorUserId = "moderator-1",
                reason = "spam",
                atMillis = 1_755_594_100_000L,
            ),
        )

        val decoded = ChatHistorySnapshotCodec.decode(ChatHistorySnapshotCodec.encode(listOf(message)))

        assertEquals(listOf(message), decoded)
    }

    @Test
    fun corruptSnapshotDoesNotCrashHistoryBootstrap() {
        assertTrue(ChatHistorySnapshotCodec.decodeOrEmpty("{not-json").isEmpty())
        assertTrue(ChatHistorySnapshotCodec.decodeOrEmpty(null).isEmpty())
    }
}
