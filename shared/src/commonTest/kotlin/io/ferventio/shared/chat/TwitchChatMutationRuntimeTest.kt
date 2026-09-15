package io.ferventio.shared.chat

import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwitchChatMutationRuntimeTest {
    @Test
    fun deletionNotificationsMutateCanonicalSharedState() {
        val state = ChatRuntimeStateHolder()
        state.append(message("delete-me", "user-a"))
        state.append(message("same-user", "user-a"))
        state.append(message("other-user", "user-b"))
        val runtime = runtime(state)

        assertTrue(
            runtime.onEnvelope(
                envelope(
                    subscriptionType = "channel.chat.message_delete",
                    event = """
                    {
                      "broadcaster_user_id": "channel-1",
                      "message_id": "delete-me"
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val deleted = state.messages("channel-1").first { it.id == "delete-me" }
        assertTrue(deleted.isDeleted)
        assertEquals(ModerationAction.DELETE, deleted.moderation.action)

        assertTrue(
            runtime.onEnvelope(
                envelope(
                    subscriptionType = "channel.chat.clear_user_messages",
                    event = """
                    {
                      "broadcaster_user_id": "channel-1",
                      "target_user_id": "user-a",
                      "target_user_login": "user_a"
                    }
                    """.trimIndent(),
                ),
            ),
        )
        val afterUserClear = state.messages("channel-1")
        assertTrue(afterUserClear.first { it.id == "same-user" }.isDeleted)
        assertEquals(
            ModerationAction.TIMEOUT,
            afterUserClear.first { it.id == "same-user" }.moderation.action,
        )
        assertFalse(afterUserClear.first { it.id == "other-user" }.isDeleted)

        assertTrue(
            runtime.onEnvelope(
                envelope(
                    subscriptionType = "channel.chat.clear",
                    event = """
                    {"broadcaster_user_id": "channel-1"}
                    """.trimIndent(),
                ),
            ),
        )
        assertTrue(state.messages("channel-1").isEmpty())
    }

    @Test
    fun malformedMutationIsIgnoredWithoutChangingMessages() {
        val state = ChatRuntimeStateHolder()
        state.append(message("message-1", "user-a"))
        val runtime = runtime(state)

        assertFalse(
            runtime.onEnvelope(
                envelope(
                    subscriptionType = "channel.chat.message_delete",
                    event = """
                    {"broadcaster_user_id": "channel-1"}
                    """.trimIndent(),
                ),
            ),
        )
        assertEquals(listOf("message-1"), state.messages("channel-1").map(ChatMessage::id))
    }

    private fun envelope(
        subscriptionType: String,
        event: String,
    ): TwitchEventSubProtocolEnvelope = TwitchEventSubProtocolParser.parse(
        """
        {
          "metadata": {
            "message_id": "event-$subscriptionType",
            "message_type": "notification",
            "message_timestamp": "2026-08-16T20:00:00Z"
          },
          "payload": {
            "subscription": {"type": "$subscriptionType", "version": "1"},
            "event": $event
          }
        }
        """.trimIndent(),
    )

    private fun message(id: String, authorId: String) = ChatMessage(
        id = id,
        channelId = "channel-1",
        channelLogin = "channel",
        author = ChatAuthor(
            id = authorId,
            login = authorId,
            displayName = authorId,
        ),
        text = id,
        timestamp = "2026-08-16T20:00:00Z",
    )

    private fun runtime(state: ChatRuntimeStateHolder) = TwitchChatSessionRuntime(
        authentication = authentication(),
        workspace = WorkspaceRuntimeSnapshot(channels = listOf(channel())),
        state = state,
        bootstrapCoordinator = TwitchEventSubBootstrapCoordinator { _, _, _ -> Unit },
    )

    private fun channel() = ChatChannel(
        id = "channel-1",
        login = "channel",
        displayName = "Channel",
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
                userId = "viewer-id",
                login = "viewer",
                scopes = setOf("chat:read"),
                expiresInSeconds = 7_200L,
            ),
        ),
    )
}
