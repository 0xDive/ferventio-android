package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.twitch.TwitchIrcEvent as SharedTwitchIrcEvent
import io.ferventio.app.domain.twitch.TwitchIrcParser as SharedTwitchIrcParser

/** Android compatibility facade while IRC parsing lives in the shared domain layer. */
sealed interface TwitchIrcEvent {
    data class RoomResolved(
        val channelLogin: String,
        val roomId: String,
    ) : TwitchIrcEvent

    data class Chat(
        val event: ChatEvent,
    ) : TwitchIrcEvent

    data class Notice(
        val channelLogin: String?,
        val message: String,
    ) : TwitchIrcEvent
}

object TwitchIrcParser {
    fun parse(
        rawLine: String,
        channelIdForLogin: (String) -> String?,
    ): List<TwitchIrcEvent> = SharedTwitchIrcParser.parse(rawLine, channelIdForLogin).map { event ->
        when (event) {
            is SharedTwitchIrcEvent.RoomResolved -> TwitchIrcEvent.RoomResolved(
                channelLogin = event.channelLogin,
                roomId = event.roomId,
            )
            is SharedTwitchIrcEvent.Chat -> TwitchIrcEvent.Chat(event.event)
            is SharedTwitchIrcEvent.Notice -> TwitchIrcEvent.Notice(
                channelLogin = event.channelLogin,
                message = event.message,
            )
        }
    }

    internal const val MAX_IRC_LINE_CHARS = SharedTwitchIrcParser.MAX_IRC_LINE_CHARS
}
