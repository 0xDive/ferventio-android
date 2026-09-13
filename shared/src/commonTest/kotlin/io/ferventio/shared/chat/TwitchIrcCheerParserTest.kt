package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.twitch.TwitchIrcEvent
import io.ferventio.app.domain.twitch.TwitchIrcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TwitchIrcCheerParserTest {
    @Test
    fun positiveBitsTagMarksPrivmsgAsCheer() {
        val message = parseMessage(
            """@badges=bits/100;color=#1E90FF;display-name=Viewer;id=cheer-1;login=viewer;bits=100;room-id=42;tmi-sent-ts=1700000000000;user-id=7 :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :Cheer100 great stream""",
        )

        assertEquals(ChatMessageType.CHEER, message.type)
        assertEquals("Cheer100 great stream", message.text)
        assertEquals("42", message.channelId)
    }

    @Test
    fun missingZeroOrMalformedBitsRemainRegularChat() {
        listOf(null, "0", "-5", "oops").forEachIndexed { index, bits ->
            val bitsTag = bits?.let { ";bits=$it" }.orEmpty()
            val message = parseMessage(
                """@badges=;color=#1E90FF;display-name=Viewer;id=chat-$index;login=viewer$bitsTag;room-id=42;tmi-sent-ts=170000000000$index;user-id=7 :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :plain text""",
            )

            assertEquals(ChatMessageType.CHAT, message.type, "bits=$bits")
        }
    }

    @Test
    fun actionKeepsActionPrecedenceWhenBitsTagIsPresent() {
        val message = parseMessage(
            """@badges=bits/100;color=#1E90FF;display-name=Viewer;id=action-cheer;login=viewer;bits=100;room-id=42;tmi-sent-ts=1700000000000;user-id=7 :viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :\u0001ACTION Cheer100 waves\u0001""",
        )

        assertEquals(ChatMessageType.ACTION, message.type)
        assertEquals("Cheer100 waves", message.text)
    }

    private fun parseMessage(raw: String): ChatMessage {
        val chat = TwitchIrcParser.parse(raw) { login -> "anonymous:$login" }
            .filterIsInstance<TwitchIrcEvent.Chat>()
            .single()
        return assertIs<ChatEvent.Message>(chat.event).message
    }
}
