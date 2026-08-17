package io.ferventio.shared.moderation

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchModerationRuntimeTest {
    @Test
    fun successfulDeleteUpdatesSharedMessageState() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("message-1", "user-a"))
        }
        val gateway = FakeModerationGateway()
        val runtime = TwitchModerationRuntime(
            chatState = state,
            gateway = gateway,
            currentEpochMillis = { 42L },
        )

        assertTrue(
            runtime.deleteChatMessage(
                authentication = authentication(),
                broadcasterId = CHANNEL_ID,
                messageId = "message-1",
            ),
        )

        val updated = state.messages(CHANNEL_ID).single()
        assertTrue(updated.isDeleted)
        assertEquals(ModerationAction.DELETE, updated.moderation.action)
        assertEquals(42L, updated.moderation.atMillis)
        assertEquals(listOf("delete:message-1"), gateway.operations)
    }

    @Test
    fun timeoutAndBanPreserveDistinctModerationActions() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("a-1", "user-a"))
            append(message("a-2", "user-a"))
            append(message("b-1", "user-b"))
        }
        var now = 100L
        val gateway = FakeModerationGateway()
        val runtime = TwitchModerationRuntime(
            chatState = state,
            gateway = gateway,
            currentEpochMillis = { now },
        )

        assertEquals(
            2,
            runtime.timeoutUser(
                authentication = authentication(),
                broadcasterId = CHANNEL_ID,
                targetUserId = "user-a",
                durationSeconds = 600,
                reason = "spam",
            ),
        )
        assertEquals(
            ModerationAction.TIMEOUT,
            state.messages(CHANNEL_ID).first { it.id == "a-1" }.moderation.action,
        )

        now = 200L
        assertEquals(
            1,
            runtime.banUser(
                authentication = authentication(),
                broadcasterId = CHANNEL_ID,
                targetUserId = "user-b",
                reason = "repeat abuse",
            ),
        )
        val banned = state.messages(CHANNEL_ID).first { it.id == "b-1" }
        assertTrue(banned.isDeleted)
        assertEquals(ModerationAction.BAN, banned.moderation.action)
        assertEquals(200L, banned.moderation.atMillis)
        assertEquals(
            listOf("timeout:user-a:600:spam", "ban:user-b:repeat abuse"),
            gateway.operations,
        )
    }

    @Test
    fun successfulClearDropsOnlyTargetChannelMessages() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("target", "user-a"))
            append(message("other", "user-b", channelId = "other-channel"))
        }
        val gateway = FakeModerationGateway()
        val runtime = TwitchModerationRuntime(state, gateway)

        assertTrue(runtime.clearChatMessages(authentication(), CHANNEL_ID))

        assertEquals(emptyList(), state.messages(CHANNEL_ID))
        assertEquals(listOf("other"), state.messages("other-channel").map(ChatMessage::id))
        assertEquals(listOf("clear"), gateway.operations)
    }

    @Test
    fun rateLimitedMutationDoesNotChangeSharedOrAuthenticationState() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("message-1", "user-a"))
            append(message("message-2", "user-a"))
        }
        val gateway = FakeModerationGateway(
            failure = TwitchModerationMutationException(
                operation = "timeout",
                statusCode = 429,
                twitchMessage = "rate limited",
            ),
        )
        val runtime = TwitchModerationRuntime(state, gateway)

        assertFailsWith<TwitchModerationMutationException> {
            runtime.timeoutUser(
                authentication = authentication(),
                broadcasterId = CHANNEL_ID,
                targetUserId = "user-a",
                durationSeconds = 60,
            )
        }

        assertTrue(state.messages(CHANNEL_ID).all { message -> !message.isDeleted })
        assertTrue(state.messages(CHANNEL_ID).all { message -> message.moderation.action == null })
        assertFalse(state.authenticationRequired)
    }

    @Test
    fun authenticationRejectionPreservesMessagesAndRequestsReauthentication() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("message-1", "user-a"))
        }
        val gateway = FakeModerationGateway(
            failure = TwitchModerationMutationException(
                operation = "ban",
                statusCode = 401,
                twitchMessage = "OAuth token is invalid",
            ),
        )
        val runtime = TwitchModerationRuntime(state, gateway)

        assertFailsWith<TwitchModerationMutationException> {
            runtime.banUser(
                authentication = authentication(),
                broadcasterId = CHANNEL_ID,
                targetUserId = "user-a",
            )
        }

        assertFalse(state.messages(CHANNEL_ID).single().isDeleted)
        assertTrue(state.authenticationRequired)
        assertEquals(ConnectionStatus.FAILED, state.connectionStatus)
        assertTrue(state.connectionErrorMessage.orEmpty().contains("HTTP 401"))
    }

    @Test
    fun unbanDoesNotRestorePreviouslyModeratedMessages() = runTest {
        val state = ChatRuntimeStateHolder().apply {
            append(message("message-1", "user-a"))
            markUserMessagesDeleted(
                channelId = CHANNEL_ID,
                userId = "user-a",
                atMillis = 10L,
                action = ModerationAction.BAN,
            )
        }
        val gateway = FakeModerationGateway()
        val runtime = TwitchModerationRuntime(state, gateway)

        runtime.unbanUser(authentication(), CHANNEL_ID, "user-a")

        assertTrue(state.messages(CHANNEL_ID).single().isDeleted)
        assertEquals(ModerationAction.BAN, state.messages(CHANNEL_ID).single().moderation.action)
        assertEquals(listOf("unban:user-a"), gateway.operations)
    }

    private class FakeModerationGateway(
        private val failure: Throwable? = null,
    ) : TwitchModerationGateway {
        val operations = mutableListOf<String>()

        override suspend fun banUser(
            authentication: StoredAuthentication,
            broadcasterId: String,
            targetUserId: String,
            reason: String?,
        ) {
            failIfNeeded()
            operations += "ban:$targetUserId:${reason.orEmpty()}"
        }

        override suspend fun timeoutUser(
            authentication: StoredAuthentication,
            broadcasterId: String,
            targetUserId: String,
            durationSeconds: Int,
            reason: String?,
        ) {
            failIfNeeded()
            operations += "timeout:$targetUserId:$durationSeconds:${reason.orEmpty()}"
        }

        override suspend fun unbanUser(
            authentication: StoredAuthentication,
            broadcasterId: String,
            targetUserId: String,
        ) {
            failIfNeeded()
            operations += "unban:$targetUserId"
        }

        override suspend fun deleteChatMessage(
            authentication: StoredAuthentication,
            broadcasterId: String,
            messageId: String,
        ) {
            failIfNeeded()
            operations += "delete:$messageId"
        }

        override suspend fun clearChatMessages(
            authentication: StoredAuthentication,
            broadcasterId: String,
        ) {
            failIfNeeded()
            operations += "clear"
        }

        private fun failIfNeeded() {
            failure?.let { throw it }
        }
    }

    private fun message(
        id: String,
        authorId: String,
        channelId: String = CHANNEL_ID,
    ) = ChatMessage(
        id = id,
        channelId = channelId,
        channelLogin = "channel",
        author = ChatAuthor(
            id = authorId,
            login = authorId,
            displayName = authorId,
        ),
        text = id,
        timestamp = "2026-01-01T00:00:00Z",
        timestampMillis = id.hashCode().toLong(),
    )

    private fun authentication() = StoredAuthentication(
        backendCredential = BackendSessionCredential(
            serverUrl = "https://example.test",
            token = "backend-session",
            expiresAtEpochMillis = 4_600_000L,
        ),
        accessLease = TwitchAccessLease(
            accessToken = "access-token",
            leaseExpiresAtEpochMillis = 1_300_000L,
            twitchExpiresAtEpochMillis = 8_200_000L,
            twitchValidatedAtEpochMillis = 1_000_000L,
            backendSessionExpiresAtEpochMillis = 4_600_000L,
            session = TwitchSession(
                clientId = "client-id",
                userId = "moderator-id",
                login = "moderator",
                scopes = setOf(
                    "moderator:manage:banned_users",
                    "moderator:manage:chat_messages",
                ),
                expiresInSeconds = 7_200L,
            ),
        ),
    )

    private companion object {
        const val CHANNEL_ID = "channel-id"
    }
}
