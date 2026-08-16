package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.InteractiveEventSubSubscriptionPolicy
import io.ferventio.app.domain.TwitchSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchEventSubSubscriptionPolicyTest {
    @Test
    fun regularChannelGetsChatSubscriptionsWithoutModeratorEvents() {
        val subscriptions = TwitchEventSubSubscriptionPolicy.subscriptionsFor(
            session = session(),
            channel = channel("channel-id"),
            moderatedChannelIds = emptySet(),
        )

        assertEquals(
            listOf(
                "channel.chat.message",
                "channel.chat.notification",
                "channel.chat.message_delete",
                "channel.chat.clear_user_messages",
                "channel.chat.clear",
                "channel.chat_settings.update",
            ),
            subscriptions.map { it.type },
        )
        assertTrue(subscriptions.all { it.version == "1" })
        assertTrue(subscriptions.all { it.identityConditionKey == "user_id" })
    }

    @Test
    fun moderatedChannelAddsAutomodAndModerationV2Subscriptions() {
        val subscriptions = TwitchEventSubSubscriptionPolicy.subscriptionsFor(
            session = session(),
            channel = channel("channel-id"),
            moderatedChannelIds = setOf("channel-id"),
        )

        val moderation = subscriptions.filter {
            it.type in TwitchEventSubSubscriptionPolicy.MODERATOR_EVENT_TYPES
        }
        assertEquals(3, moderation.size)
        assertTrue(moderation.all { it.version == "2" })
        assertTrue(moderation.all { it.identityConditionKey == "moderator_user_id" })
    }

    @Test
    fun ownChannelAddsScopedInteractiveSubscriptionsWithoutIdentityCondition() {
        val subscriptions = TwitchEventSubSubscriptionPolicy.subscriptionsFor(
            session = session(
                scopes = setOf(
                    "channel:read:polls",
                    "channel:manage:predictions",
                ),
            ),
            channel = channel("viewer-id"),
            moderatedChannelIds = setOf("viewer-id"),
        )

        val interactive = subscriptions.filter {
            it.type in InteractiveEventSubSubscriptionPolicy.ALL_EVENT_TYPES
        }
        assertEquals(7, interactive.size)
        assertTrue(interactive.all { it.version == "1" })
        assertTrue(interactive.all { it.identityConditionKey == null })
        assertTrue(interactive.any { it.type == InteractiveEventSubSubscriptionPolicy.POLL_BEGIN })
        assertTrue(interactive.any { it.type == InteractiveEventSubSubscriptionPolicy.PREDICTION_END })
    }

    @Test
    fun interactiveSubscriptionsAreNotAddedForAnotherBroadcaster() {
        val subscriptions = TwitchEventSubSubscriptionPolicy.subscriptionsFor(
            session = session(
                scopes = setOf("channel:read:polls", "channel:read:predictions"),
            ),
            channel = channel("another-channel"),
            moderatedChannelIds = emptySet(),
        )

        assertFalse(
            subscriptions.any {
                it.type in InteractiveEventSubSubscriptionPolicy.ALL_EVENT_TYPES
            },
        )
    }

    @Test
    fun descriptorMatchesAndroidConditionAndVersionRules() {
        val primary = TwitchEventSubSubscriptionPolicy.subscription(
            broadcasterId = " channel ",
            type = "channel.chat.message",
        )
        assertEquals("channel", primary.broadcasterId)
        assertEquals("1", primary.version)
        assertEquals("user_id", primary.identityConditionKey)

        val automod = TwitchEventSubSubscriptionPolicy.subscription(
            broadcasterId = "channel",
            type = "automod.message.hold",
        )
        assertEquals("2", automod.version)
        assertEquals("moderator_user_id", automod.identityConditionKey)

        val poll = TwitchEventSubSubscriptionPolicy.subscription(
            broadcasterId = "channel",
            type = InteractiveEventSubSubscriptionPolicy.POLL_PROGRESS,
        )
        assertEquals("1", poll.version)
        assertNull(poll.identityConditionKey)
    }

    private fun session(
        scopes: Set<String> = setOf("chat:read"),
    ) = TwitchSession(
        clientId = "client-id",
        userId = "viewer-id",
        login = "viewer",
        scopes = scopes,
        expiresInSeconds = 7_200L,
    )

    private fun channel(id: String) = ChatChannel(
        id = id,
        login = "channel",
        displayName = "Channel",
    )
}
