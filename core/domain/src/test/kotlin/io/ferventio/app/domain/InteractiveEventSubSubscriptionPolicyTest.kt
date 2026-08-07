package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractiveEventSubSubscriptionPolicyTest {
    @Test
    fun `read polls scope enables poll events for own channel`() {
        val types = InteractiveEventSubSubscriptionPolicy.eventTypesFor(
            session = session(scopes = setOf("channel:read:polls")),
            channel = channel("self"),
        )

        assertEquals(InteractiveEventSubSubscriptionPolicy.POLL_EVENT_TYPES, types)
    }

    @Test
    fun `manage scopes enable all matching event families`() {
        val types = InteractiveEventSubSubscriptionPolicy.eventTypesFor(
            session = session(
                scopes = setOf(
                    "channel:manage:polls",
                    "channel:manage:predictions",
                ),
            ),
            channel = channel("self"),
        )

        assertEquals(
            InteractiveEventSubSubscriptionPolicy.POLL_EVENT_TYPES +
                InteractiveEventSubSubscriptionPolicy.PREDICTION_EVENT_TYPES,
            types,
        )
    }

    @Test
    fun `prediction read scope enables only prediction events`() {
        val types = InteractiveEventSubSubscriptionPolicy.eventTypesFor(
            session = session(scopes = setOf("channel:read:predictions")),
            channel = channel("self"),
        )

        assertEquals(InteractiveEventSubSubscriptionPolicy.PREDICTION_EVENT_TYPES, types)
    }

    @Test
    fun `other broadcaster channel never receives interactive subscriptions`() {
        val types = InteractiveEventSubSubscriptionPolicy.eventTypesFor(
            session = session(
                scopes = setOf(
                    "channel:manage:polls",
                    "channel:manage:predictions",
                ),
            ),
            channel = channel("someone-else"),
        )

        assertTrue(types.isEmpty())
    }

    @Test
    fun `missing scopes fail closed`() {
        val types = InteractiveEventSubSubscriptionPolicy.eventTypesFor(
            session = session(scopes = setOf("user:read:chat")),
            channel = channel("self"),
        )

        assertTrue(types.isEmpty())
    }

    private fun session(scopes: Set<String>) = TwitchSession(
        clientId = "client",
        userId = "self",
        login = "me",
        scopes = scopes,
        expiresInSeconds = 3600,
    )

    private fun channel(id: String) = ChatChannel(
        id = id,
        login = id,
        displayName = id,
    )
}
