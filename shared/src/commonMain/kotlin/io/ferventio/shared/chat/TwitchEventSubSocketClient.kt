package io.ferventio.shared.chat

import io.ferventio.app.domain.ConnectionStatus
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.random.Random
import kotlin.time.Clock

data class TwitchEventSubConnectionUpdate(
    val status: ConnectionStatus,
    val attempt: Int,
    val error: String? = null,
)

/**
 * Shared EventSub WebSocket lifecycle. Notification payload interpretation is deliberately
 * delegated to the caller so transport migration does not couple reconnect logic to chat parsing.
 */
internal class TwitchEventSubSocketClient(
    private val client: HttpClient = createPlatformEventSubHttpClient(),
    private val onStatusChanged: (TwitchEventSubConnectionUpdate) -> Unit,
    private val onSessionReady: suspend (sessionId: String) -> Int,
    private val onEnvelope: (TwitchEventSubProtocolEnvelope) -> Unit,
    private val onMalformedEnvelope: (Throwable) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val delayAction: suspend (Long) -> Unit = { millis -> delay(millis) },
    private val jitterFraction: () -> Double = { Random.nextDouble() },
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val deliveryGate: TwitchEventSubDeliveryGate = TwitchEventSubDeliveryGate(),
) {
    private var closed = false

    suspend fun run() {
        check(!closed) { "EventSub socket client is already closed" }
        var socketUrl = TwitchEventSubConnectionPolicy.DEFAULT_SOCKET_URL
        var createSubscriptions = true
        var reconnectAttempt = 0

        while (currentCoroutineContext().isActive && !closed) {
            var twitchReconnectUrl: String? = null
            var stopAfterRevocation = false
            try {
                publishStatus(
                    if (reconnectAttempt == 0) {
                        ConnectionStatus.CONNECTING
                    } else {
                        ConnectionStatus.RECONNECTING
                    },
                    reconnectAttempt,
                )

                client.webSocket(urlString = socketUrl) {
                    publishStatus(ConnectionStatus.WAITING_WELCOME, reconnectAttempt)
                    val welcome = receiveProtocolEnvelope(
                        TwitchEventSubConnectionPolicy.WELCOME_TIMEOUT_MILLIS,
                    )
                    require(welcome.type == "session_welcome") {
                        "First Twitch EventSub message must be session_welcome"
                    }
                    val sessionId = welcome.sessionId
                        ?.takeIf(String::isNotBlank)
                        ?: error("Twitch EventSub welcome is missing a session id")
                    val keepaliveSeconds =
                        TwitchEventSubConnectionPolicy.keepaliveSeconds(
                            welcome.keepaliveTimeoutSeconds,
                        )

                    if (createSubscriptions) {
                        publishStatus(ConnectionStatus.CREATING_SUBSCRIPTIONS, reconnectAttempt)
                        val subscriptionCount = onSessionReady(sessionId)
                        require(subscriptionCount > 0) {
                            "Twitch EventSub session did not receive any subscriptions"
                        }
                    }
                    publishStatus(ConnectionStatus.CONNECTED, 0)
                    reconnectAttempt = 0

                    while (currentCoroutineContext().isActive && !closed) {
                        val envelope = receiveProtocolEnvelope(
                            TwitchEventSubConnectionPolicy.receiveTimeoutMillis(keepaliveSeconds),
                        )
                        if (!deliveryGate.shouldDeliver(envelope, nowEpochMillis())) {
                            continue
                        }
                        onEnvelope(envelope)
                        when (envelope.type) {
                            "session_reconnect" -> {
                                twitchReconnectUrl = envelope.reconnectUrl
                                    ?.takeIf(String::isNotBlank)
                                    ?: error("Twitch EventSub reconnect is missing reconnect_url")
                                publishStatus(ConnectionStatus.RECONNECTING, 0)
                                break
                            }

                            "revocation" -> {
                                if (TwitchEventSubConnectionPolicy.shouldStopAfterRevocation(
                                        envelope.revocationStatus,
                                    )
                                ) {
                                    stopAfterRevocation = true
                                    break
                                }
                            }
                        }
                    }
                }

                if (closed) return
                if (stopAfterRevocation) return
                if (twitchReconnectUrl != null) {
                    socketUrl = requireNotNull(twitchReconnectUrl)
                    createSubscriptions = false
                    continue
                }
                error("Twitch EventSub WebSocket closed without a reconnect instruction")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (setup: TwitchEventSubBootstrapException) {
                if (closed) return
                onError(setup)
                publishStatus(
                    status = ConnectionStatus.FAILED,
                    attempt = reconnectAttempt,
                    error = setup.message ?: "EventSub subscription bootstrap failed",
                )
                return
            } catch (error: Throwable) {
                if (closed) return
                onError(error)
            }

            if (closed) return
            reconnectAttempt += 1
            createSubscriptions = true
            socketUrl = TwitchEventSubConnectionPolicy.DEFAULT_SOCKET_URL
            if (!TwitchEventSubConnectionPolicy.canRetry(reconnectAttempt)) {
                publishStatus(ConnectionStatus.FAILED, reconnectAttempt, "automatic reconnect exhausted")
                return
            }
            publishStatus(ConnectionStatus.RECONNECTING, reconnectAttempt)
            delayAction(
                TwitchEventSubConnectionPolicy.reconnectDelayMillis(
                    attempt = reconnectAttempt,
                    jitterFraction = jitterFraction(),
                ),
            )
        }
    }

    fun close() {
        if (closed) return
        closed = true
        deliveryGate.clear()
        client.close()
    }

    private suspend fun io.ktor.client.plugins.websocket.DefaultClientWebSocketSession.receiveProtocolEnvelope(
        timeoutMillis: Long,
    ): TwitchEventSubProtocolEnvelope = withTimeout(timeoutMillis) {
        while (true) {
            val frame = incoming.receive()
            if (frame !is Frame.Text) continue
            val envelope = runCatching {
                TwitchEventSubProtocolParser.parse(frame.readText())
            }.onFailure(onMalformedEnvelope).getOrNull()
            if (envelope != null) return@withTimeout envelope
        }
        error("Unreachable EventSub receive loop")
    }

    private fun publishStatus(
        status: ConnectionStatus,
        attempt: Int,
        error: String? = null,
    ) {
        onStatusChanged(
            TwitchEventSubConnectionUpdate(
                status = status,
                attempt = attempt,
                error = error,
            ),
        )
    }
}

internal expect fun createPlatformEventSubHttpClient(): HttpClient
