package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTextPreparationTest {
    @Test
    fun `prepares fragments and reply preview`() {
        val message = ChatMessage(
            id = "m1",
            channelId = "c1",
            channelLogin = "channel",
            author = ChatAuthor(id = "u1", login = "user", displayName = "User"),
            text = "hello Kappa",
            fragments = listOf(ChatFragment.Text("hello "), ChatFragment.TwitchEmote("Kappa", "25")),
            timestamp = "2026-07-25T00:00:00Z",
            reply = ReplyContext(
                parentMessageId = "parent",
                parentMessageBody = "original",
                parentUserName = "Other",
            ),
        )

        val prepared = ChatMessageTextPreparation.warm(message)

        assertEquals("hello Kappa", prepared.fragmentText)
        assertEquals("Ответ для @Other: original", prepared.replyPreview)
    }
}
