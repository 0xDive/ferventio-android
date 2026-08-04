package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyThreadResolverTest {
    @Test
    fun resolvesRootDirectReplyAndNestedReplyChronologically() {
        val root = message("root", 1)
        val child = message("child", 2, ReplyContext(parentMessageId = "root", threadMessageId = "root"))
        val nested = message("nested", 3, ReplyContext(parentMessageId = "child", threadMessageId = "root"))
        val unrelated = message("other", 4)

        val result = ReplyThreadResolver.resolve(child, listOf(unrelated, nested, root, child))

        assertEquals(listOf("root", "child", "nested"), result.map(ChatMessage::id))
    }

    @Test
    fun rootMessageIdFallsBackToParentForLegacyReplies() {
        val reply = message("reply", 2, ReplyContext(parentMessageId = "root"))

        assertEquals("root", ReplyThreadResolver.rootMessageId(reply))
    }

    private fun message(id: String, second: Long, reply: ReplyContext? = null) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor("user-$id", id, id),
        text = id,
        timestamp = "2026-07-22T10:00:0${second}Z",
        timestampMillis = second * 1_000L,
        reply = reply,
    )
}
