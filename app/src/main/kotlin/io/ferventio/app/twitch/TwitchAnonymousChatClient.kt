package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ConnectionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Read-only Twitch IRC connection used when no Twitch account is authorized.
 * Twitch's current EventSub chat transport requires a user authorization, so anonymous reading
 * uses the legacy IRC WebSocket endpoint. Sending and moderation always remain disabled.
 */
class TwitchAnonymousChatClient(
    channels: List<ChatChannel>,
    private val onStatusChanged: (EventSubConnectionUpdate) -> Unit,
    private val onEvent: (ChatEvent) -> Unit,
    private val onRoomResolved: (channelLogin: String, roomId: String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val jitterFraction: () -> Double = { Random.nextDouble() },
) : Closeable {
    private val channelIdByLogin = ConcurrentHashMap(
        channels.associate { channel -> channel.login.lowercase() to channel.id },
    )
    private val channelLogins = channels.map { it.login.lowercase() }.distinct()
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            install(WebSockets)
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun run() {
        if (channelLogins.isEmpty()) return
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            try {
                publish(
                    status = if (attempt == 0) ConnectionStatus.CONNECTING else ConnectionStatus.RECONNECTING,
                    detail = if (attempt == 0) {
                        "Подключаем анонимное чтение Twitch IRC…"
                    } else {
                        "Переподключаем анонимное чтение Twitch IRC…"
                    },
                    attempt = attempt,
                )
                client.webSocket(urlString = SOCKET_URL) {
                    send(Frame.Text("PASS SCHMOOPIIE"))
                    send(Frame.Text("NICK justinfan${Random.nextInt(10_000, 99_999)}"))
                    send(Frame.Text("CAP REQ :twitch.tv/tags twitch.tv/commands"))
                    send(Frame.Text("JOIN ${channelLogins.joinToString(",") { "#$it" }}"))
                    publish(
                        status = ConnectionStatus.CONNECTED,
                        detail = "Чтение без аккаунта · ${channelLogins.size} каналов",
                        attempt = 0,
                    )
                    attempt = 0

                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        frame.readText().split("\r\n", "\n").forEach { rawLine ->
                            val line = rawLine.trimEnd('\r')
                            if (line.isBlank()) return@forEach
                            if (line.startsWith("PING")) {
                                send(Frame.Text(line.replaceFirst("PING", "PONG")))
                                return@forEach
                            }
                            if (line.contains(" RECONNECT")) throw IrcReconnectRequested()

                            TwitchIrcParser.parse(line) { login -> channelIdByLogin[login] }
                                .forEach { event ->
                                    when (event) {
                                        is TwitchIrcEvent.RoomResolved -> {
                                            val previous = channelIdByLogin.put(event.channelLogin, event.roomId)
                                            if (previous != event.roomId) {
                                                onRoomResolved(event.channelLogin, event.roomId)
                                            }
                                        }

                                        is TwitchIrcEvent.Chat -> onEvent(event.event)
                                        is TwitchIrcEvent.Notice -> {
                                            onNotice(event.message)
                                            if (event.message.contains("authentication failed", ignoreCase = true)) {
                                                throw IrcAuthenticationRejected(event.message)
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
                error("Twitch IRC закрыл WebSocket")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IrcReconnectRequested) {
                attempt = 0
            } catch (error: IrcAuthenticationRejected) {
                publish(
                    status = ConnectionStatus.FAILED,
                    detail = "Twitch больше не разрешает анонимное IRC-подключение",
                    attempt = attempt,
                )
                onError(error)
                return
            } catch (error: Throwable) {
                onError(error)
                attempt += 1
                publish(
                    status = ConnectionStatus.RECONNECTING,
                    detail = "Twitch IRC недоступен; повторяем подключение…",
                    attempt = attempt,
                )
                delay(reconnectDelayMillis(attempt))
            }
        }
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private fun publish(status: ConnectionStatus, detail: String, attempt: Int) {
        onStatusChanged(EventSubConnectionUpdate(status, detail, attempt))
    }

    private fun reconnectDelayMillis(attempt: Int): Long {
        val exponent = 1L shl min(attempt.coerceAtLeast(1) - 1, 5)
        val base = (1_000L * exponent).coerceAtMost(30_000L)
        return (base * (0.8 + jitterFraction().coerceIn(0.0, 1.0) * 0.4)).toLong()
    }

    private class IrcReconnectRequested : RuntimeException()
    private class IrcAuthenticationRejected(message: String) : RuntimeException(message)

    private companion object {
        const val SOCKET_URL = "wss://irc-ws.chat.twitch.tv:443"
    }
}
