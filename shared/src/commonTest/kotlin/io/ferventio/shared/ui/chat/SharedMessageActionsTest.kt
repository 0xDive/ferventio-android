package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ReplyContext
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedMessageActionsTest {
    @Test
    fun replyThreadResolverReturnsRootAndRepliesInOrder() {
        val root = message("root", 1_000L)
        val firstReply = message(
            id = "reply-1",
            timestampMillis = 2_000L,
            reply = ReplyContext(parentMessageId = "root", threadMessageId = "root"),
        )
        val secondReply = message(
            id = "reply-2",
            timestampMillis = 3_000L,
            reply = ReplyContext(parentMessageId = "reply-1"),
        )
        val unrelated = message("other", 4_000L)

        assertEquals(
            listOf("root", "reply-1", "reply-2"),
            resolveSharedReplyThreadMessages(
                target = firstReply,
                messages = listOf(unrelated, secondReply, root, firstReply),
            ).map(ChatMessage::id),
        )
    }

    private fun message(
        id: String,
        timestampMillis: Long,
        reply: ReplyContext? = null,
    ) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user-$id",
            login = "user_$id",
            displayName = "User $id",
        ),
        text = "message $id",
        timestamp = "1970-01-01T00:00:01Z",
        timestampMillis = timestampMillis,
        reply = reply,
    )
}
