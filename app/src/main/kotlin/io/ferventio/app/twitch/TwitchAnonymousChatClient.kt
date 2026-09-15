package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.shared.chat.TwitchAnonymousChatConnectionStage
import io.ferventio.shared.chat.TwitchAnonymousChatConnectionUpdate
import io.ferventio.shared.chat.TwitchAnonymousChatSocketClient
import java.io.Closeable
import kotlin.random.Random

/** Android compatibility facade over the shared read-only Twitch IRC transport. */
class TwitchAnonymousChatClient(
    channels: List<ChatChannel>,
    private val onStatusChanged: (EventSubConnectionUpdate) -> Unit,
    onEvent: (ChatEvent) -> Unit,
    onRoomResolved: (channelLogin: String, roomId: String) -> Unit,
    onNotice: (String) -> Unit,
    onError: (Throwable) -> Unit,
    jitterFraction: () -> Double = { Random.nextDouble() },
) : Closeable {
    private val channelCount = channels
        .map { channel -> channel.login.trim().lowercase() }
        .filter(String::isNotEmpty)
        .distinct()
        .size
    private val delegate = TwitchAnonymousChatSocketClient(
        channels = channels,
        onStatusChanged = ::publish,
        onEvent = onEvent,
        onRoomResolved = onRoomResolved,
        onNotice = onNotice,
        onError = onError,
        jitterFraction = jitterFraction,
    )

    suspend fun run() = delegate.run()

    override fun close() {
        delegate.close()
    }

    private fun publish(update: TwitchAnonymousChatConnectionUpdate) {
        val detail = when (update.stage) {
            TwitchAnonymousChatConnectionStage.CONNECTING ->
                "Подключаем анонимное чтение Twitch IRC…"
            TwitchAnonymousChatConnectionStage.RECONNECTING ->
                "Переподключаем анонимное чтение Twitch IRC…"
            TwitchAnonymousChatConnectionStage.CONNECTED ->
                "Чтение без аккаунта · $channelCount каналов"
            TwitchAnonymousChatConnectionStage.RETRY_WAIT ->
                "Twitch IRC недоступен; повторяем подключение…"
            TwitchAnonymousChatConnectionStage.AUTHENTICATION_REJECTED ->
                "Twitch больше не разрешает анонимное IRC-подключение"
        }
        onStatusChanged(
            EventSubConnectionUpdate(
                status = update.status,
                detail = detail,
                attempt = update.attempt,
            ),
        )
    }
}
