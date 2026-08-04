package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchIrcParserTest {
    @Test
    fun `parses room id badges and native Twitch emote`() {
        val line = "@badge-info=subscriber/8;badges=subscriber/6;color=#00FF7F;display-name=Viewer;" +
            "emotes=25:6-10;first-msg=1;id=message-1;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :hello Kappa"

        val events = TwitchIrcParser.parse(line) { null }
        val room = events.filterIsInstance<TwitchIrcEvent.RoomResolved>().single()
        assertEquals("channel", room.channelLogin)
        assertEquals("1234", room.roomId)

        val message = (events.filterIsInstance<TwitchIrcEvent.Chat>().single().event as ChatEvent.Message).message
        assertEquals("1234", message.channelId)
        assertEquals("Viewer", message.author.displayName)
        assertEquals("subscriber", message.author.badges.single().setId)
        assertTrue(message.flags.isFirstMessage)
        assertEquals("hello ", (message.fragments[0] as ChatFragment.Text).text)
        assertEquals("Kappa", (message.fragments[1] as ChatFragment.TwitchEmote).text)
        assertEquals("25", (message.fragments[1] as ChatFragment.TwitchEmote).emoteId)
    }

    @Test
    fun `emote positions remain correct after supplementary unicode character`() {
        val line = "@display-name=Viewer;emotes=25:2-6;id=message-2;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :😀 Kappa"

        val message = ((TwitchIrcParser.parse(line) { null }
            .filterIsInstance<TwitchIrcEvent.Chat>().single().event) as ChatEvent.Message).message
        assertEquals("😀 ", (message.fragments[0] as ChatFragment.Text).text)
        assertEquals("Kappa", (message.fragments[1] as ChatFragment.TwitchEmote).text)
    }


    @Test
    fun `action strips CTCP wrapper and keeps emote positions`() {
        val line = "@display-name=Viewer;emotes=25:8-12;id=message-action;login=viewer;room-id=1234;" +
            "tmi-sent-ts=1720000000000;user-id=55 :viewer!viewer@viewer.tmi.twitch.tv " +
            "PRIVMSG #channel :\u0001ACTION Kappa waves\u0001"

        val message = ((TwitchIrcParser.parse(line) { null }
            .filterIsInstance<TwitchIrcEvent.Chat>().single().event) as ChatEvent.Message).message

        assertEquals("Kappa waves", message.text)
        assertTrue(message.flags.isAction)
        assertEquals("Kappa", (message.fragments[0] as ChatFragment.TwitchEmote).text)
        assertEquals(" waves", (message.fragments[1] as ChatFragment.Text).text)
    }

    @Test
    fun `notice unescapes IRC tag values without affecting message text`() {
        val line = "@msg-id=msg\\sneeds\\:attention :tmi.twitch.tv NOTICE #channel :Read-only notice"

        val notice = TwitchIrcParser.parse(line) { "1234" }
            .filterIsInstance<TwitchIrcEvent.Notice>()
            .single()

        assertEquals("channel", notice.channelLogin)
        assertEquals("Read-only notice", notice.message)
    }

    @Test
    fun `custom reward tag marks reward message`() {
        val line = "@custom-reward-id=reward-1;display-name=Viewer;id=message-reward;login=viewer;" +
            "room-id=1234;tmi-sent-ts=1720000000000;user-id=55 " +
            ":viewer!viewer@viewer.tmi.twitch.tv PRIVMSG #channel :hydrate"

        val message = ((TwitchIrcParser.parse(line) { null }
            .filterIsInstance<TwitchIrcEvent.Chat>().single().event) as ChatEvent.Message).message

        assertEquals(io.ferventio.app.domain.ChatMessageType.REWARD, message.type)
        assertEquals("reward-1", message.reward?.id)
    }

    @Test
    fun `parses deleted message and full chat clear`() {
        val deleted = TwitchIrcParser.parse(
            "@room-id=1234;target-msg-id=dead :tmi.twitch.tv CLEARMSG #channel :removed",
        ) { null }.filterIsInstance<TwitchIrcEvent.Chat>().single().event
        assertEquals(ChatEvent.MessageDeleted("1234", "dead", eventId = "irc:clearmsg:dead"), deleted)

        val cleared = TwitchIrcParser.parse(
            "@room-id=1234 :tmi.twitch.tv CLEARCHAT #channel",
        ) { null }.filterIsInstance<TwitchIrcEvent.Chat>().single().event
        assertEquals(ChatEvent.ChatCleared("1234"), cleared)
    }

    @Test
    fun `clearchat distinguishes timeout and permanent ban`() {
        val timeout = TwitchIrcParser.parse(
            "@room-id=1234;target-user-id=55;ban-duration=600;tmi-sent-ts=1785657600000 " +
                ":tmi.twitch.tv CLEARCHAT #channel :viewer",
        ) { null }.filterIsInstance<TwitchIrcEvent.Chat>().single().event
        assertEquals(
            ChatEvent.UserMessagesCleared(
                channelId = "1234",
                userId = "55",
                userLogin = "viewer",
                durationSeconds = 600,
                isPermanent = false,
                eventId = "irc:clearchat:1234:55:1785657600000",
                createdAt = "2026-08-02T08:00:00Z",
            ),
            timeout,
        )

        val ban = TwitchIrcParser.parse(
            "@room-id=1234;target-user-id=55;tmi-sent-ts=1785657600000 " +
                ":tmi.twitch.tv CLEARCHAT #channel :viewer",
        ) { null }.filterIsInstance<TwitchIrcEvent.Chat>().single().event
        assertEquals(
            ChatEvent.UserMessagesCleared(
                channelId = "1234",
                userId = "55",
                userLogin = "viewer",
                isPermanent = true,
                eventId = "irc:clearchat:1234:55:1785657600000",
                createdAt = "2026-08-02T08:00:00Z",
            ),
            ban,
        )
    }
}
