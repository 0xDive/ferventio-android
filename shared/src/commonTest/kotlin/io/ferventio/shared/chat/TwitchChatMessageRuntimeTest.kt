package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatSendResult
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TwitchChatMessageRuntimeTest {
    @Test
    fun successfulSendCreatesOptimisticMessageAndStoresServerId() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = RecordingGateway(result = ChatSendResult("server-id"))
        val runtime = TwitchChatMessageRuntime(
            chatState = state,
            gateway = gateway,
            currentEpochMillis = { 1_000L },
        )

        runtime.send(authentication(), channel(), " hello ")

        val message = state.messages("channel-id").single()
        assertTrue(message.id.startsWith("local-1000-"))
        assertEquals("hello", message.text)
        assertEquals(OutgoingMessageState.SENT, message.outgoingState)
        assertEquals("server-id", message.serverMessageId)
        assertNotNull(message.clientNonce)
        assertEquals("channel-id", gateway.broadcasterId)
        assertEquals("hello", gateway.message)
    }

    @Test
    fun replyContextIsCopiedFromParentAndSentToTwitch() = runTest {
        val state = ChatRuntimeStateHolder()
        state.append(parentMessage())
        val gateway = RecordingGateway(result = ChatSendResult("server-id"))
        val runtime = TwitchChatMessageRuntime(state, gateway, currentEpochMillis = { 2_000L })

        runtime.send(
            authentication = authentication(),
            channel = channel(),
            message = "reply",
            replyParentMessageId = "parent-id",
        )

        val outgoing = state.messages("channel-id").first { it.id.startsWith("local-") }
        assertEquals("parent-id", outgoing.reply?.parentMessageId)
        assertEquals("parent text", outgoing.reply?.parentMessageBody)
        assertEquals("author-id", outgoing.reply?.parentUserId)
        assertEquals("Author", outgoing.reply?.parentUserName)
        assertEquals("parent-id", gateway.replyParentMessageId)
    }

    @Test
    fun failureLeavesRetryableOptimisticMessage() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = RecordingGateway(error = TwitchChatMessageDroppedException("slow_mode", "Slow mode"))
        val runtime = TwitchChatMessageRuntime(state, gateway, currentEpochMillis = { 3_000L })

        assertFailsWith<TwitchChatMessageDroppedException> {
            runtime.send(authentication(), channel(), "hello")
        }

        val failed = state.messages("channel-id").single()
        assertEquals(OutgoingMessageState.FAILED, failed.outgoingState)
        assertEquals("Slow mode", failed.outgoingError)
        assertFalse(state.authenticationRequired)
    }

    @Test
    fun retryReusesLocalMessageAndMarksItSent() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = RecordingGateway(error = TwitchChatMessageDroppedException("slow_mode", "Slow mode"))
        val runtime = TwitchChatMessageRuntime(state, gateway, currentEpochMillis = { 4_000L })
        assertFailsWith<TwitchChatMessageDroppedException> {
            runtime.send(authentication(), channel(), "hello")
        }
        val failed = state.messages("channel-id").single()

        gateway.error = null
        gateway.result = ChatSendResult("server-id")
        runtime.retry(authentication(), channel(), failed)

        val sent = state.messages("channel-id").single()
        assertEquals(failed.id, sent.id)
        assertEquals(OutgoingMessageState.SENT, sent.outgoingState)
        assertEquals("server-id", sent.serverMessageId)
    }

    @Test
    fun unauthorizedSendMarksAuthenticationRequired() = runTest {
        val state = ChatRuntimeStateHolder()
        val gateway = RecordingGateway(
            error = TwitchChatMessageMutationException(401, "invalid token"),
        )
        val runtime = TwitchChatMessageRuntime(state, gateway, currentEpochMillis = { 5_000L })

        assertFailsWith<TwitchChatMessageMutationException> {
            runtime.send(authentication(), channel(), "hello")
        }

        assertTrue(state.authenticationRequired)
        assertEquals(OutgoingMessageState.FAILED, state.messages("channel-id").single().outgoingState)
    }

    @Test
    fun eventSubEchoReplacesOptimisticRowWithoutDuplicate() {
        val state = ChatRuntimeStateHolder()
        val optimistic = parentMessage().copy(
            id = "local-1",
            text = "mine",
            outgoingState = OutgoingMessageState.SENT,
            clientNonce = "local-1",
            serverMessageId = "server-id",
        )
        state.append(optimistic)

        state.append(
            parentMessage().copy(
                id = "server-id",
                text = "mine",
                author = ChatAuthor("sender-id", "sender", "sender"),
            ),
        )

        val messages = state.messages("channel-id")
        assertEquals(1, messages.size)
        assertEquals("server-id", messages.single().id)
        assertEquals("local-1", messages.single().clientNonce)
        assertEquals(OutgoingMessageState.SENT, messages.single().outgoingState)
    }

    private class RecordingGateway(
        var result: ChatSendResult = ChatSendResult("server-id"),
        var error: Throwable? = null,
    ) : TwitchChatMessageGateway {
        var broadcasterId: String? = null
        var message: String? = null
        var replyParentMessageId: String? = null

        override suspend fun sendMessage(
            authentication: StoredAuthentication,
            broadcasterId: String,
            message: String,
            replyParentMessageId: String?,
        ): ChatSendResult {
            this.broadcasterId = broadcasterId
            this.message = message
            this.replyParentMessageId = replyParentMessageId
            error?.let { throw it }
            return result
        }
    }

    private fun channel() = ChatChannel(
        id = "channel-id",
        login = "channel",
        displayName = "Channel",
    )

    private fun parentMessage() = ChatMessage(
        id = "parent-id",
        channelId = "channel-id",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "author-id",
            login = "author",
            displayName = "Author",
        ),
        text = "parent text",
        timestamp = "1970-01-01T00:00:01Z",
        timestampMillis = 1_000L,
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
                userId = "sender-id",
                login = "sender",
                scopes = setOf("user:write:chat"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
