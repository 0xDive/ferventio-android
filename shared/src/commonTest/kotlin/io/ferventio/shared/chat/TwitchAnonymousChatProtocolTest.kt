package io.ferventio.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchAnonymousChatProtocolTest {
    @Test
    fun handshakePreservesAndroidAnonymousIrcContract() {
        assertEquals(
            listOf(
                "PASS SCHMOOPIIE",
                "NICK justinfan12345",
                "CAP REQ :twitch.tv/tags twitch.tv/commands",
                "JOIN #alpha,#beta",
            ),
            TwitchAnonymousChatProtocol.handshake(
                channelLogins = listOf(" Alpha ", "beta", "ALPHA"),
                nickSuffix = 12_345,
            ),
        )
    }

    @Test
    fun pingReconnectAndAuthenticationSignalsAreRecognized() {
        assertEquals("PONG :tmi.twitch.tv", TwitchAnonymousChatProtocol.pongFor("PING :tmi.twitch.tv"))
        assertNull(TwitchAnonymousChatProtocol.pongFor(":tmi.twitch.tv NOTICE * :hello"))
        assertTrue(TwitchAnonymousChatProtocol.requestsReconnect(":tmi.twitch.tv RECONNECT"))
        assertFalse(TwitchAnonymousChatProtocol.requestsReconnect("PRIVMSG #channel :RECONNECT"))
        assertTrue(TwitchAnonymousChatProtocol.authenticationRejected("Login authentication failed"))
    }

    @Test
    fun reconnectPresentationDistinguishesInitialConnectFromReconnects() {
        assertFalse(
            TwitchAnonymousChatConnectionPolicy.isReconnectAttempt(
                attempt = 0,
                hasConnected = false,
            ),
        )
        assertTrue(
            TwitchAnonymousChatConnectionPolicy.isReconnectAttempt(
                attempt = 1,
                hasConnected = false,
            ),
        )
        assertTrue(
            TwitchAnonymousChatConnectionPolicy.isReconnectAttempt(
                attempt = 0,
                hasConnected = true,
            ),
        )
    }

    @Test
    fun reconnectBackoffMatchesAndroidPolicy() {
        assertEquals(800L, TwitchAnonymousChatConnectionPolicy.reconnectDelayMillis(1, 0.0))
        assertEquals(1_200L, TwitchAnonymousChatConnectionPolicy.reconnectDelayMillis(1, 1.0))
        assertEquals(16_000L, TwitchAnonymousChatConnectionPolicy.reconnectDelayMillis(5, 0.5))
        assertEquals(36_000L, TwitchAnonymousChatConnectionPolicy.reconnectDelayMillis(20, 1.0))
    }
}
