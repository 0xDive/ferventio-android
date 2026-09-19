package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.MessageRuleEvaluator
import io.ferventio.app.domain.ReplyContext
import io.ferventio.app.domain.TwitchSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatAttentionStateTest {
    private val session = TwitchSession(
        clientId = "client-id",
        userId = "viewer-id",
        login = "viewer",
        scopes = emptySet(),
        expiresInSeconds = 3_600,
    )
    private val evaluator = MessageRuleEvaluator.compile(
        highlights = emptyList(),
        ignores = emptyList(),
        session = session,
    )

    @Test
    fun nonVisibleOtherUserMessageIncrementsUnread() {
        val state = ChatAttentionStateHolder()

        state.recordIncoming(message("one", "hello"), session, evaluator)

        val attention = state.attention("channel-id")
        assertEquals(1, attention.unreadCount)
        assertEquals(0, attention.mentionCount)
        assertEquals("one", attention.firstUnreadMessageId)
        assertTrue(state.attentionEntries.isEmpty())
    }

    @Test
    fun directMentionIncrementsUnreadAndMention() {
        val state = ChatAttentionStateHolder()

        state.recordIncoming(
            message(
                id = "mention",
                text = "hey @viewer",
                fragments = listOf(
                    ChatFragment.Text("hey "),
                    ChatFragment.Mention("@viewer", "viewer-id", "viewer", "viewer"),
                ),
            ),
            session,
            evaluator,
        )

        val attention = state.attention("channel-id")
        assertEquals(1, attention.unreadCount)
        assertEquals(1, attention.mentionCount)
        assertEquals(1, state.mentionUnreadCount)
        val entry = state.attentionEntries.single()
        assertTrue(entry.isDirectMention)
        assertFalse(entry.isRead)
    }

    @Test
    fun replyToCurrentUserCountsAsDirectMention() {
        val state = ChatAttentionStateHolder()
        val reply = message("reply", "answer").copy(
            reply = ReplyContext(
                parentMessageId = "parent",
                parentUserId = "viewer-id",
                parentUserLogin = "viewer",
                parentUserName = "viewer",
            ),
        )

        state.recordIncoming(reply, session, evaluator)

        assertEquals(1, state.attention("channel-id").mentionCount)
        assertTrue(state.attentionEntries.single().isDirectMention)
    }

    @Test
    fun visibleChannelAtLiveTailDoesNotAccumulateUnread() {
        val state = ChatAttentionStateHolder()
        state.updateViewport("channel-id", visible = true, isAtLiveTail = true)

        state.recordIncoming(message("one", "@viewer hello"), session, evaluator)

        assertEquals(0, state.attention("channel-id").unreadCount)
        assertEquals(0, state.mentionUnreadCount)
        assertTrue(state.attentionEntries.single().isRead)
    }

    @Test
    fun visibleButScrolledUpStillAccumulatesUnread() {
        val state = ChatAttentionStateHolder()
        state.updateViewport("channel-id", visible = true, isAtLiveTail = false)

        state.recordIncoming(message("one", "hello"), session, evaluator)

        assertEquals(1, state.attention("channel-id").unreadCount)
    }

    @Test
    fun ownAndSystemMessagesNeverAccumulateUnread() {
        val state = ChatAttentionStateHolder()
        val own = message("own", "hello").copy(
            author = ChatAuthor("viewer-id", "viewer", "viewer"),
        )
        val system = message("system", "notice").copy(
            type = ChatMessageType.SYSTEM,
            flags = MessageFlags(isSystem = true),
        )

        state.recordIncoming(own, session, evaluator)
        state.recordIncoming(system, session, evaluator)

        assertEquals(0, state.attention("channel-id").unreadCount)
        assertTrue(state.attentionEntries.isEmpty())
    }

    @Test
    fun returningToLiveTailMarksChannelAndAttentionEntriesRead() {
        val state = ChatAttentionStateHolder()
        state.recordIncoming(message("mention", "@viewer"), session, evaluator)
        assertEquals(1, state.attention("channel-id").mentionCount)

        state.updateViewport("channel-id", visible = true, isAtLiveTail = true)

        assertEquals(0, state.attention("channel-id").unreadCount)
        assertEquals(0, state.mentionUnreadCount)
        assertTrue(state.attentionEntries.single().isRead)
        assertNull(state.attention("channel-id").firstUnreadMessageId)
    }

    @Test
    fun navigationTargetIsConsumedOnlyByMatchingChannelAndMessage() {
        val state = ChatAttentionStateHolder()
        state.requestMessageNavigation("channel-id", "message-id")

        assertEquals("message-id", state.navigationTarget("channel-id"))
        assertFalse(state.consumeMessageNavigation("channel-id", "other-message"))
        assertEquals("message-id", state.navigationTarget("channel-id"))
        assertTrue(state.consumeMessageNavigation("channel-id", "message-id"))
        assertNull(state.navigationTarget("channel-id"))
    }

    @Test
    fun retainingWorkspaceChannelsPrunesRemovedAttentionAndNavigationTargets() {
        val state = ChatAttentionStateHolder()
        state.recordIncoming(message("one", "@viewer"), session, evaluator)
        state.recordIncoming(
            message("two", "@viewer", channelId = "other-channel", channelLogin = "other"),
            session,
            evaluator,
        )
        state.requestMessageNavigation("channel-id", "one")
        state.requestMessageNavigation("other-channel", "two")

        state.retainChannels(listOf("other-channel"))

        assertEquals(0, state.attention("channel-id").unreadCount)
        assertEquals(1, state.attention("other-channel").unreadCount)
        assertEquals(listOf("other-channel"), state.attentionEntries.map { it.channelId })
        assertNull(state.navigationTarget("channel-id"))
        assertEquals("two", state.navigationTarget("other-channel"))
    }

    @Test
    fun clearRemovesNavigationTargets() {
        val state = ChatAttentionStateHolder()
        state.requestMessageNavigation("channel-id", "one")

        state.clear()

        assertNull(state.navigationTarget("channel-id"))
    }

    private fun message(
        id: String,
        text: String,
        channelId: String = "channel-id",
        channelLogin: String = "channel",
        fragments: List<ChatFragment> = listOf(ChatFragment.Text(text)),
    ) = ChatMessage(
        id = id,
        channelId = channelId,
        channelLogin = channelLogin,
        author = ChatAuthor("author-id", "author", "Author"),
        text = text,
        fragments = fragments,
        timestamp = "2026-08-18T10:00:00Z",
        timestampMillis = 1_000L,
    )
}
