package io.ferventio.app.push

import io.ferventio.app.network.FerventioServerUrlPolicy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.ferventio.app.FerventioApplication
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

class FerventioPushConnectionService : Service() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val networkSignal = Channel<Unit>(Channel.CONFLATED)
    private val client = HttpClient(OkHttp) {

        install(WebSockets)
        expectSuccess = false
    }
    private var connectionJob: Job? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val coordinator: PushCoordinator
        get() = (application as FerventioApplication).container.pushCoordinator

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            NotificationPresenter.BACKGROUND_NOTIFICATION_ID,
            coordinator.serviceNotification("Подключаем автономные уведомления…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !coordinator.isEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (connectionJob?.isActive != true) {
            connectionJob = scope.launch { connectionLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        connectionJob?.cancel()
        unregisterNetworkCallback()
        client.close()
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun connectionLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive && coordinator.isEnabled) {
            try {
                coordinator.onEmbeddedConnecting(attempt, if (attempt == 0) {
                    "Открываем защищённое соединение с сервером…"
                } else {
                    "Переподключение автономных уведомлений: попытка $attempt"
                })
                updateForeground(if (attempt == 0) "Подключение к серверу…" else "Переподключение: попытка $attempt")
                connectOnce()
                attempt = 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                attempt += 1
                val detail = error.message?.takeIf(String::isNotBlank)
                    ?: "Соединение автономных уведомлений разорвано"
                coordinator.onProviderTemporarilyUnavailable(detail)
                updateForeground("Нет соединения. Повторяем попытку…")
                awaitReconnect(attempt)
            }
        }
    }

    private suspend fun connectOnce() {
        val url = socketUrl(coordinator.serverUrl)
        client.webSocket(urlString = url) {
            send(
                Frame.Text(
                    json.encodeToString(
                        PushSocketClientMessage(
                            type = "authenticate",
                            installationId = coordinator.installationId,
                            deviceSecret = coordinator.installationSecret,
                            lastEventId = coordinator.lastEventId,
                        ),
                    ),
                ),
            )

            while (currentCoroutineContext().isActive && coordinator.isEnabled) {
                val frame = withTimeout(SOCKET_IDLE_TIMEOUT_MILLIS) { incoming.receive() }
                if (frame !is Frame.Text) continue
                val message = json.decodeFromString<PushSocketServerMessage>(frame.readText())
                when (message.type) {
                    "authenticated" -> {
                        coordinator.onEmbeddedConnected()
                        updateForeground("Автономные уведомления подключены")
                    }
                    "heartbeat" -> {
                        coordinator.onEmbeddedHeartbeat()
                        send(Frame.Text(json.encodeToString(PushSocketClientMessage(type = "pong"))))
                    }
                    "notification" -> {
                        val payload = message.payload ?: continue
                        coordinator.onPushPayload(payload)
                        message.eventId?.let { eventId ->
                            send(
                                Frame.Text(
                                    json.encodeToString(
                                        PushSocketClientMessage(type = "ack", eventId = eventId),
                                    ),
                                ),
                            )
                        }
                    }
                    "error" -> error(message.message ?: "Сервер отклонил push-соединение")
                }
            }
        }
        error("Сервер закрыл push-соединение")
    }

    private suspend fun awaitReconnect(attempt: Int) {
        val delayMillis = EmbeddedPushReconnectPolicy.delayMillis(
            attempt = attempt,
            jitterFraction = Random.nextDouble(-0.2, 0.2),
        )
        withTimeoutOrNull(delayMillis) {
            networkSignal.receive()
        }
    }

    private fun updateForeground(status: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(
            NotificationPresenter.BACKGROUND_NOTIFICATION_ID,
            coordinator.serviceNotification(status),
        )
    }

    private fun registerNetworkCallback() {
        val manager = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkSignal.trySend(Unit)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { networkCallback = callback }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching {
            getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback)
        }
        networkCallback = null
    }

    private fun socketUrl(serverUrl: String): String {
        val normalized = FerventioServerUrlPolicy.validate(serverUrl).baseUrl
        return when {
            normalized.startsWith("https://") -> "wss://${normalized.removePrefix("https://")}/v1/push/socket"
            normalized.startsWith("http://") -> "ws://${normalized.removePrefix("http://")}/v1/push/socket"
            else -> error("Некорректный адрес сервера Ferventio")
        }
    }

    companion object {
        private const val ACTION_START = "io.ferventio.app.push.START"
        private const val ACTION_STOP = "io.ferventio.app.push.STOP"
        private const val SOCKET_IDLE_TIMEOUT_MILLIS = 120_000L

        fun start(context: Context): Result<Unit> = runCatching {
            val intent = Intent(context, FerventioPushConnectionService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FerventioPushConnectionService::class.java))
        }
    }
}
