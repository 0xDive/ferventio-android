package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class TwitchRecentMessagesRuntimeTest {
    private val channel = ChatChannel(
        id = CHANNEL_ID,
        login = "channel",
        displayName = "Channel",
    )

    @Test
    fun historicalRowsUseOverlayAndLiveDuplicateWins() = runTest {
        val state = ChatRuntimeStateHolder()
        state.append(message("duplicate", 20L, text = "live"))
        var loadCount = 0
        val runtime = TwitchRecentMessagesRuntime(
            state = state,
            history = null,
            loadRecentMessages = { _, limit ->
                loadCount += 1
                assertEquals(TwitchRecentMessagesClient.DEFAULT_LIMIT, limit)
                TwitchRecentMessagesResult(
                    messages = listOf(
                        message("recent", 10L, text = "history"),
                        message("duplicate", 15L, text = "stale snapshot"),
                    ),
                )
            },
        )

        runtime.loadChannels(
            listOf(
                channel,
                channel.copy(id = "same-login", displayName = "Same login"),
            ),
        )

        assertEquals(1, loadCount)
        val timeline = state.messages(CHANNEL_ID)
        assertEquals(listOf("recent", "duplicate"), timeline.map(ChatMessage::id))
        assertEquals("live", timeline.single { it.id == "duplicate" }.text)
        assertEquals(listOf("duplicate"), state.messagesByChannel.getValue(CHANNEL_ID).map(ChatMessage::id))
    }

    @Test
    fun snapshotFailureDoesNotInterruptLiveChatState() = runTest {
        val state = ChatRuntimeStateHolder()
        state.append(message("live", 20L))
        val runtime = TwitchRecentMessagesRuntime(
            state = state,
            history = null,
            loadRecentMessages = { _, _ -> throw IllegalStateException("offline") },
        )

        runtime.loadChannels(listOf(channel))

        assertEquals(listOf("live"), state.messages(CHANNEL_ID).map(ChatMessage::id))
    }

    private fun message(
        id: String,
        timestampMillis: Long,
        text: String = id,
    ) = ChatMessage(
        id = id,
        channelId = CHANNEL_ID,
        channelLogin = "channel",
        author = ChatAuthor(
            id = "author",
            login = "author",
            displayName = "Author",
        ),
        text = text,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = timestampMillis,
    )

    private companion object {
        const val CHANNEL_ID = "channel-id"
    }
}
