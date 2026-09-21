package io.ferventio.app.application

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LegacyChatModerationReducerTest {
    @Test
    fun missingDeleteTargetDoesNotAllocateList() {
        assertNull(
            markLegacyMessageDeleted(
                messages = listOf(message("one", "user-a")),
                messageId = "missing",
                atMillis = 10L,
            ),
        )
    }

    @Test
    fun singleDeleteReplacesOnlyTargetEntry() {
        val first = message("one", "user-a")
        val target = message("two", "user-b")
        val messages = listOf(first, target)

        val updated = requireNotNull(
            markLegacyMessageDeleted(
                messages = messages,
                messageId = "two",
                atMillis = 20L,
            ),
        )

        assertTrue(updated[0] === first)
        assertTrue(updated[1].isDeleted)
        assertEquals(ModerationAction.DELETE, updated[1].moderation.action)
        assertEquals(20L, updated[1].moderation.atMillis)
    }

    @Test
    fun userClearCopiesLazilyAndPreservesUnrelatedRows() {
        val userOne = message("one", "user-a")
        val other = message("other", "user-b")
        val userTwo = message("two", "user-a")
        val messages = listOf(userOne, other, userTwo)

        val updated = requireNotNull(
            markLegacyUserMessagesDeleted(
                messages = messages,
                userId = "user-a",
                atMillis = 30L,
            ),
        )

        assertTrue(updated[1] === other)
        assertTrue(updated[0].isDeleted)
        assertTrue(updated[2].isDeleted)
        assertEquals(ModerationAction.TIMEOUT, updated[0].moderation.action)
        assertEquals(ModerationAction.TIMEOUT, updated[2].moderation.action)
    }

    private fun message(
        id: String,
        userId: String,
    ) = ChatMessage(
        id = id,
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = userId,
            login = userId,
            displayName = userId,
        ),
        text = id,
        timestamp = "2026-09-20T00:00:00Z",
    )
}
