package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.ModerationAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRuntimeStateHolderTest {
    @Test
    fun appendDeduplicatesByMessageIdAndKeepsAndroidMemoryWindow() {
        val holder = ChatRuntimeStateHolder()

        repeat(5_001) { index ->
            holder.append(message(id = "m$index", timestampMillis = index.toLong()))
        }

        assertEquals(5_000, holder.messages(CHANNEL_ID).size)
        assertEquals("m1", holder.messages(CHANNEL_ID).first().id)
        assertEquals("m5000", holder.messages(CHANNEL_ID).last().id)

        holder.append(
            message(
                id = "m5000",
                timestampMillis = 5_000L,
                text = "updated",
            ),
        )

        assertEquals(5_000, holder.messages(CHANNEL_ID).size)
        assertEquals("updated", holder.messages(CHANNEL_ID).last().text)
    }

    @Test
    fun replaceAndHistoryNormalizeOrderingAndDuplicateIds() {
        val holder = ChatRuntimeStateHolder()
        holder.replaceChannelMessages(
            CHANNEL_ID,
            listOf(
                message("m2", 20L),
                message("m1", 10L, text = "old"),
                message("m1", 10L, text = "new"),
            ),
        )

        assertEquals(listOf("m1", "m2"), holder.messages(CHANNEL_ID).map { it.id })
        assertEquals("new", holder.messages(CHANNEL_ID).first().text)

        holder.prependHistory(
            CHANNEL_ID,
            listOf(
                message("m0", 0L),
                message("m1", 10L, text = "history duplicate"),
            ),
        )

        assertEquals(listOf("m0", "m1", "m2"), holder.messages(CHANNEL_ID).map { it.id })
        assertEquals("new", holder.messages(CHANNEL_ID)[1].text)
    }

    @Test
    fun deletionMutationsMatchAndroidCanonicalState() {
        val holder = ChatRuntimeStateHolder()
        holder.append(message("target", 1L, authorId = "user-a"))
        holder.append(message("same-user", 2L, authorId = "user-a"))
        holder.append(message("other-user", 3L, authorId = "user-b"))
        holder.append(message("other-channel", 4L, channelId = "other", authorId = "user-a"))

        assertTrue(holder.markMessageDeleted(CHANNEL_ID, "target", atMillis = 10L))
        val target = holder.messages(CHANNEL_ID).first { it.id == "target" }
        assertTrue(target.isDeleted)
        assertEquals(ModerationAction.DELETE, target.moderation.action)
        assertEquals(10L, target.moderation.atMillis)

        assertEquals(2, holder.markUserMessagesDeleted(CHANNEL_ID, "user-a", atMillis = 20L))
        val channelMessages = holder.messages(CHANNEL_ID)
        assertTrue(channelMessages.first { it.id == "target" }.isDeleted)
        assertTrue(channelMessages.first { it.id == "same-user" }.isDeleted)
        assertFalse(channelMessages.first { it.id == "other-user" }.isDeleted)
        assertEquals(
            ModerationAction.TIMEOUT,
            channelMessages.first { it.id == "same-user" }.moderation.action,
        )
        assertFalse(holder.messages("other").single().isDeleted)

        assertTrue(holder.clearChannelMessages(CHANNEL_ID))
        assertEquals(emptyList(), holder.messages(CHANNEL_ID))
        assertEquals(listOf("other-channel"), holder.messages("other").map(ChatMessage::id))
    }

    @Test
    fun retainChannelsDropsOnlyRemovedWorkspaceBuckets() {
        val holder = ChatRuntimeStateHolder()
        holder.append(message("a", 1L, channelId = "1"))
        holder.append(message("b", 2L, channelId = "2"))

        holder.retainChannels(listOf("2", "3"))

        assertEquals(emptyList(), holder.messages("1"))
        assertEquals(listOf("b"), holder.messages("2").map { it.id })
    }

    @Test
    fun connectionStateIsNormalizedAndClearResetsIt() {
        val holder = ChatRuntimeStateHolder()
        holder.updateConnection(
            status = ConnectionStatus.RECONNECTING,
            detail = " retrying ",
            attempt = 3,
            errorMessage = " network ",
        )

        assertEquals(ConnectionStatus.RECONNECTING, holder.connectionStatus)
        assertEquals("retrying", holder.connectionDetail)
        assertEquals(3, holder.connectionAttempt)
        assertEquals("network", holder.connectionErrorMessage)

        holder.clear()

        assertEquals(ConnectionStatus.DISCONNECTED, holder.connectionStatus)
        assertEquals(0, holder.connectionAttempt)
        assertNull(holder.connectionDetail)
        assertNull(holder.connectionErrorMessage)
    }

    @Test
    fun mismatchedBucketAndNegativeAttemptAreRejected() {
        val holder = ChatRuntimeStateHolder()
        assertFailsWith<IllegalArgumentException> {
            holder.replaceChannelMessages(
                channelId = "other",
                messages = listOf(message("m", 1L)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            holder.updateConnection(ConnectionStatus.CONNECTING, attempt = -1)
        }
    }

    private fun message(
        id: String,
        timestampMillis: Long,
        text: String = id,
        channelId: String = CHANNEL_ID,
        authorId: String = "author",
    ) = ChatMessage(
        id = id,
        channelId = channelId,
        channelLogin = "channel",
        author = ChatAuthor(
            id = authorId,
            login = authorId,
            displayName = authorId,
        ),
        text = text,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = timestampMillis,
    )

    private companion object {
        const val CHANNEL_ID = "channel-id"
    }
}
