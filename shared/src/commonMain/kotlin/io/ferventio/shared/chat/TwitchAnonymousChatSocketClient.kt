package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.twitch.TwitchIrcEvent
import io.ferventio.app.domain.twitch.TwitchIrcParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class TwitchAnonymousChatConnectionStage {
    CONNECTING,
    RECONNECTING,
    CONNECTED,
    RETRY_WAIT,
    AUTHENTICATION_REJECTED,
}

data class TwitchAnonymousChatConnectionUpdate(
    val status: ConnectionStatus,
    val stage: TwitchAnonymousChatConnectionStage,
    val attempt: Int,
    val error: String? = null,
)

/** Shared read-only Twitch IRC WebSocket transport for signed-out chat. */
class TwitchAnonymousChatSocketClient internal constructor(
    channels: List<ChatChannel>,
    private val onStatusChanged: (TwitchAnonymousChatConnectionUpdate) -> Unit,
    private val onEvent: (ChatEvent) -> Unit,
    private val onRoomResolved: (channelLogin: String, roomId: String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val client: HttpClient,
    private val delayAction: suspend (Long) -> Unit,
    private val jitterFraction: () -> Double,
    private val nickSuffix: () -> Int,
) {
    constructor(
        channels: List<ChatChannel>,
        onStatusChanged: (TwitchAnonymousChatConnectionUpdate) -> Unit,
        onEvent: (ChatEvent) -> Unit,
        onRoomResolved: (channelLogin: String, roomId: String) -> Unit,
        onNotice: (String) -> Unit,
        onError: (Throwable) -> Unit,
        jitterFraction: () -> Double = { Random.nextDouble() },
    ) : this(
        channels = channels,
        onStatusChanged = onStatusChanged,
        onEvent = onEvent,
        onRoomResolved = onRoomResolved,
        onNotice = onNotice,
        onError = onError,
        client = createPlatformEventSubHttpClient(),
        delayAction = { millis -> delay(millis) },
        jitterFraction = jitterFraction,
        nickSuffix = { Random.nextInt(10_000, 99_999) },
    )

    private val channelIdByLogin = channels
        .associate { channel -> channel.login.trim().lowercase() to channel.id }
        .toMutableMap()
    private val channelLogins = channels
        .map { channel -> channel.login.trim().lowercase() }
        .filter(String::isNotEmpty)
        .distinct()
    private var closed = false

    suspend fun run() {
        check(!closed) { "Anonymous IRC socket client is already closed" }
        if (channelLogins.isEmpty()) return

        var attempt = 0
        while (currentCoroutineContext().isActive && !closed) {
            try {
                publish(
                    status = if (attempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING,
                    stage = if (attempt == 0) {
                        TwitchAnonymousChatConnectionStage.CONNECTING
                    } else {
                        TwitchAnonymousChatConnectionStage.RECONNECTING
                    },
                    attempt = attempt,
                )
                client.webSocket(urlString = SOCKET_URL) {
                    TwitchAnonymousChatProtocol.handshake(
                        channelLogins = channelLogins,
                        nickSuffix = nickSuffix(),
                    ).forEach { command ->
                        send(Frame.Text(command))
                    }
                    publish(
                        status = ConnectionStatus.CONNECTED,
                        stage = TwitchAnonymousChatConnectionStage.CONNECTED,
                        attempt = 0,
                    )
                    attempt = 0

                    for (frame in incoming) {
                        if (closed) return@webSocket
                        if (frame !is Frame.Text) continue
                        frame.readText().split("\r\n", "\n").forEach { rawLine ->
                            val line = rawLine.trimEnd('\r')
                            if (line.isBlank()) return@forEach
                            TwitchAnonymousChatProtocol.pongFor(line)?.let { pong ->
                                send(Frame.Text(pong))
                                return@forEach
                            }
                            if (TwitchAnonymousChatProtocol.requestsReconnect(line)) {
                                throw AnonymousIrcReconnectRequested()
                            }

                            TwitchIrcParser.parse(line) { login -> channelIdByLogin[login] }
                                .forEach { event ->
                                    when (event) {
                                        is TwitchIrcEvent.RoomResolved -> {
                                            val previous = channelIdByLogin.put(
                                                event.channelLogin,
                                                event.roomId,
                                            )
                                            if (previous != event.roomId) {
                                                onRoomResolved(event.channelLogin, event.roomId)
                                            }
                                        }
                                        is TwitchIrcEvent.Chat -> onEvent(event.event)
                                        is TwitchIrcEvent.Notice -> {
                                            onNotice(event.message)
                                            if (TwitchAnonymousChatProtocol.authenticationRejected(event.message)) {
                                                throw AnonymousIrcAuthenticationRejected(event.message)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                if (closed) return
                error("Twitch IRC closed the WebSocket")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: AnonymousIrcReconnectRequested) {
                attempt = 0
            } catch (error: AnonymousIrcAuthenticationRejected) {
                publish(
                    status = ConnectionStatus.FAILED,
                    stage = TwitchAnonymousChatConnectionStage.AUTHENTICATION_REJECTED,
                    attempt = attempt,
                    error = error.message,
                )
                onError(error)
                return
            } catch (error: Throwable) {
                if (closed) return
                onError(error)
                attempt += 1
                publish(
                    status = ConnectionStatus.RECONNECTING,
                    stage = TwitchAnonymousChatConnectionStage.RETRY_WAIT,
                    attempt = attempt,
                    error = error.message,
                )
                delayAction(
                    TwitchAnonymousChatConnectionPolicy.reconnectDelayMillis(
                        attempt = attempt,
                        jitterFraction = jitterFraction(),
                    ),
                )
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        client.close()
    }

    private fun publish(
        status: ConnectionStatus,
        stage: TwitchAnonymousChatConnectionStage,
        attempt: Int,
        error: String? = null,
    ) {
        onStatusChanged(
            TwitchAnonymousChatConnectionUpdate(
                status = status,
                stage = stage,
                attempt = attempt,
                error = error,
            ),
        )
    }

    private class AnonymousIrcReconnectRequested : RuntimeException()
    private class AnonymousIrcAuthenticationRejected(message: String) : RuntimeException(message)

    private companion object {
        const val SOCKET_URL = "wss://irc-ws.chat.twitch.tv:443"
    }
}

internal object TwitchAnonymousChatProtocol {
    fun handshake(channelLogins: List<String>, nickSuffix: Int): List<String> {
        val normalized = channelLogins
            .map(String::trim)
            .map(String::lowercase)
            .filter(String::isNotEmpty)
            .distinct()
        if (normalized.isEmpty()) return emptyList()
        val suffix = nickSuffix.coerceIn(10_000, 99_998)
        return listOf(
            "PASS SCHMOOPIIE",
            "NICK justinfan$suffix",
            "CAP REQ :twitch.tv/tags twitch.tv/commands",
            "JOIN ${normalized.joinToString(",") { login -> "#$login" }}",
        )
    }

    fun pongFor(line: String): String? =
        line.takeIf { value -> value.startsWith("PING") }
            ?.replaceFirst("PING", "PONG")

    fun requestsReconnect(line: String): Boolean = line.contains(" RECONNECT")

    fun authenticationRejected(notice: String): Boolean =
        notice.contains("authentication failed", ignoreCase = true)
}

internal object TwitchAnonymousChatConnectionPolicy {
    fun reconnectDelayMillis(attempt: Int, jitterFraction: Double): Long {
        val exponent = 1L shl min(attempt.coerceAtLeast(1) - 1, 5)
        val base = (1_000L * exponent).coerceAtMost(30_000L)
        return (base * (0.8 + jitterFraction.coerceIn(0.0, 1.0) * 0.4)).toLong()
    }
}
