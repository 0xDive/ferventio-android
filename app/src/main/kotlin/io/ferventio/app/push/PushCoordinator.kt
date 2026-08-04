package io.ferventio.app.push

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.ferventio.app.BuildConfig
import io.ferventio.app.data.SettingsStore
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.HighlightAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class PushCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val client: PushRegistrationClient = PushRegistrationClient(),
) {
    private val appContext = context.applicationContext
    private val provider = PlatformPushProviderFactory.create(appContext)
    private val notificationPresenter = NotificationPresenter(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private var registrationContextProvider: () -> PushRegistrationContext = { PushRegistrationContext() }
    private var payloadHandler: (PushNotificationPayload) -> Unit = {}

    private val mutableState = MutableStateFlow(
        PushUiState(
            transport = provider.transport,
            serverUrl = settingsStore.pushServerUrl,
            enabled = settingsStore.pushEnabled,
            providerConfigured = provider.isConfigured,
            status = initialStatus(),
            detail = initialDetail(),
            lastHeartbeatAtMillis = settingsStore.pushLastHeartbeatAtMillis.takeIf { it > 0L },
            lastConnectedAtMillis = settingsStore.pushLastConnectedAtMillis.takeIf { it > 0L },
            foregroundServiceRequired = provider.transport == PushTransport.EMBEDDED_SOCKET,
        ),
    )
    val state: StateFlow<PushUiState> = mutableState.asStateFlow()

    fun setRegistrationContextProvider(provider: () -> PushRegistrationContext) {
        registrationContextProvider = provider
    }

    fun setPayloadHandler(handler: (PushNotificationPayload) -> Unit) {
        payloadHandler = handler
    }

    fun bootstrap() {
        notificationPresenter.createChannels()
        // Do not start a foreground service from Application.onCreate(): the
        // process can be created from a background component where Android 12+
        // rejects a new FGS. Activity, BOOT_COMPLETED, package replacement, and
        // START_STICKY recovery own the explicit service start paths.
    }

    fun shouldRequestNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission() &&
            !settingsStore.notificationPermissionRequested

    fun markNotificationPermissionRequested() {
        settingsStore.notificationPermissionRequested = true
    }

    fun refresh(activity: Activity?) {
        ensureAutomaticRegistration(activity)
    }

    fun syncRegistration() {
        if (registrationContextProvider().userId.isNullOrBlank()) return
        ensureAutomaticRegistration(null)
    }

    fun ensureAutomaticRegistration(activity: Activity?) {
        if (!hasNotificationPermission()) {
            onPermissionRequired()
            return
        }
        val serverUrl = settingsStore.pushServerUrl
        if (!isValidServerUrl(serverUrl)) {
            return fail("В этой сборке неверно настроен адрес сервера Ferventio")
        }
        if (registrationContextProvider().userId.isNullOrBlank()) {
            settingsStore.pushEnabled = false
            mutableState.update {
                it.copy(
                    serverUrl = serverUrl,
                    enabled = false,
                    status = PushStatus.REGISTERING,
                    detail = "Уведомления подключатся после входа в Twitch",
                    errorMessage = null,
                )
            }
            return
        }
        if (!provider.isConfigured) {
            settingsStore.pushEnabled = false
            mutableState.update { it.copy(enabled = false, providerConfigured = false) }
            return fail(
                if (provider.transport == PushTransport.FCM) {
                    "Уведомления недоступны в этой Play-сборке"
                } else {
                    "Автономные уведомления недоступны в этой сборке"
                },
            )
        }

        settingsStore.pushEnabled = true
        mutableState.update {
            it.copy(
                serverUrl = serverUrl,
                enabled = true,
                providerConfigured = provider.isConfigured,
                status = PushStatus.REGISTERING,
                detail = "Регистрируем ${provider.transport.displayName}…",
                foregroundServiceRequired = provider.transport == PushTransport.EMBEDDED_SOCKET,
                errorMessage = null,
            )
        }

        scope.launch {
            runCatching {
                withContext(Dispatchers.Main) {
                    provider.register(activity, null)
                }
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun disableForLogout() {
        val serverUrl = settingsStore.pushServerUrl
        val installationId = settingsStore.installationId
        val deviceSecret = settingsStore.installationSecret
        settingsStore.pushEnabled = false
        provider.unregister()
        mutableState.update {
            it.copy(
                enabled = false,
                status = PushStatus.REGISTERING,
                detail = "Уведомления подключатся после следующего входа в Twitch",
                reconnectAttempt = 0,
                errorMessage = null,
            )
        }
        unregisterServerRegistration(serverUrl, installationId, deviceSecret)
    }

    fun sendTest() {
        val serverUrl = settingsStore.pushServerUrl
        if (!settingsStore.pushEnabled || serverUrl.isBlank()) {
            return fail("Уведомления ещё не зарегистрированы")
        }
        scope.launch {
            runCatching {
                client.sendSelfTest(
                    serverUrl = serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                )
            }.onSuccess {
                mutableState.update { state ->
                    state.copy(detail = "Тестовое уведомление поставлено в очередь")
                }
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun reconnect() {
        if (!settingsStore.pushEnabled) return
        provider.activate()
    }

    fun onPlatformRegistration(registration: PlatformPushRegistration) {
        if (!settingsStore.pushEnabled) return
        val serverUrl = settingsStore.pushServerUrl
        if (serverUrl.isBlank()) return fail("В этой сборке не настроен сервер Ferventio")

        scope.launch {
            mutableState.update {
                it.copy(status = PushStatus.REGISTERING, detail = "Сохраняем регистрацию на сервере…")
            }
            runCatching {
                val context = registrationContextProvider()
                val request = when (registration) {
                    is PlatformPushRegistration.FirebaseInstallation -> PushRegistrationRequest(
                        installationId = settingsStore.installationId,
                        deviceSecret = settingsStore.installationSecret,
                        provider = registration.transport.wireName,
                        firebaseInstallationId = registration.fid,
                        appVersion = BuildConfig.VERSION_NAME,
                        platform = "android",
                        userId = context.userId,
                        userLogin = context.userLogin,
                        channelIds = context.channelIds,
                        moderatorChannelIds = context.moderatorChannelIds,
                        notificationRules = context.notificationRules,
                        highlightPhrases = context.highlightPhrases,
                        selectedUserLogins = context.selectedUserLogins,
                    )

                    PlatformPushRegistration.EmbeddedSocket -> PushRegistrationRequest(
                        installationId = settingsStore.installationId,
                        deviceSecret = settingsStore.installationSecret,
                        provider = registration.transport.wireName,
                        appVersion = BuildConfig.VERSION_NAME,
                        platform = "android",
                        userId = context.userId,
                        userLogin = context.userLogin,
                        channelIds = context.channelIds,
                        moderatorChannelIds = context.moderatorChannelIds,
                        notificationRules = context.notificationRules,
                        highlightPhrases = context.highlightPhrases,
                        selectedUserLogins = context.selectedUserLogins,
                    )
                }
                client.register(serverUrl, request)
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        enabled = true,
                        status = if (provider.transport == PushTransport.EMBEDDED_SOCKET) {
                            PushStatus.CONNECTING
                        } else {
                            PushStatus.ACTIVE
                        },
                        detail = if (provider.transport == PushTransport.EMBEDDED_SOCKET) {
                            "Регистрация сохранена, подключаем фоновую службу…"
                        } else {
                            "${provider.transport.displayName} подключён"
                        },
                        errorMessage = null,
                    )
                }
                provider.activate()
            }.onFailure { error -> fail(error.userMessage()) }
        }
    }

    fun showReplyNotification(message: ChatMessage) {
        if (!settingsStore.pushEnabled || !settingsStore.replyNotificationsEnabled) return
        notificationPresenter.show(
            PushNotificationPayload(
                type = "reply",
                title = "Ответ от ${message.userDisplayName}",
                body = message.text,
                channelId = message.channelId,
                channelLogin = message.channelLogin,
                messageId = message.id,
            ),
        )
    }

    fun showHighlightAlert(alert: HighlightAlert) {
        if (alert.playSound && !alert.push) {
            runCatching {
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(appContext, sound)?.play()
            }
        }
        if (!alert.push) return
        notificationPresenter.show(
            PushNotificationPayload(
                type = "highlight",
                title = "Highlight: ${alert.message.userDisplayName}",
                body = alert.message.text,
                channelId = alert.message.channelId,
                channelLogin = alert.message.channelLogin,
                messageId = alert.message.id,
                destination = "mentions",
                silent = !alert.playSound,
            ),
        )
    }

    fun showAutoModNotification(message: AutoModHeldMessage) {
        if (!settingsStore.autoModNotificationsEnabled) return
        notificationPresenter.show(
            PushNotificationPayload(
                type = "automod_hold",
                title = "AutoMod: ${message.userName}",
                body = message.text,
                channelId = message.channelId,
                channelLogin = message.channelLogin,
                messageId = message.messageId,
                destination = "moderation",
            ),
        )
    }

    fun onPushPayload(content: ByteArray) {
        val raw = content.toString(Charsets.UTF_8)
        runCatching { json.decodeFromString<PushNotificationPayload>(raw) }
            .onSuccess(::onPushPayload)
            .onFailure { fail("Некорректный push: ${it.userMessage()}") }
    }

    fun onPushPayload(payload: PushNotificationPayload): Boolean {
        val eventId = payload.eventId
        if (!eventId.isNullOrBlank()) {
            val recent = settingsStore.pushRecentEventIds
            if (eventId in recent) return false
            settingsStore.pushRecentEventIds = recent + eventId
            settingsStore.pushLastEventId = eventId
        }
        runCatching { payloadHandler(payload) }
        notificationPresenter.show(payload)
        mutableState.update {
            it.copy(
                status = PushStatus.ACTIVE,
                detail = "Последнее уведомление получено",
                lastMessageAtMillis = System.currentTimeMillis(),
                errorMessage = null,
            )
        }
        return true
    }

    fun onEmbeddedConnecting(attempt: Int, detail: String) {
        mutableState.update {
            it.copy(
                status = PushStatus.CONNECTING,
                detail = detail,
                reconnectAttempt = attempt,
                errorMessage = null,
            )
        }
    }

    fun onEmbeddedConnected(detail: String = "Автономные уведомления подключены") {
        val now = System.currentTimeMillis()
        settingsStore.pushLastConnectedAtMillis = now
        mutableState.update {
            it.copy(
                status = PushStatus.ACTIVE,
                detail = detail,
                reconnectAttempt = 0,
                lastConnectedAtMillis = now,
                errorMessage = null,
            )
        }
    }

    fun onEmbeddedHeartbeat() {
        val now = System.currentTimeMillis()
        settingsStore.pushLastHeartbeatAtMillis = now
        mutableState.update {
            it.copy(
                status = PushStatus.ACTIVE,
                lastHeartbeatAtMillis = now,
                errorMessage = null,
            )
        }
    }

    fun onProviderUnregistered() {
        if (!settingsStore.pushEnabled) return
        mutableState.update {
            it.copy(
                status = PushStatus.NEEDS_CONFIGURATION,
                detail = "Push-провайдер отключил регистрацию",
            )
        }
    }

    fun onProviderTemporarilyUnavailable(message: String) {
        mutableState.update {
            it.copy(status = PushStatus.TEMPORARILY_UNAVAILABLE, detail = message)
        }
    }

    fun onProviderError(message: String) = fail(message)

    fun onPermissionDenied() {
        settingsStore.notificationPermissionRequested = true
        onPermissionRequired("Android не разрешил показывать уведомления")
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun serviceNotification(status: String) = notificationPresenter.backgroundServiceNotification(status)

    val lastEventId: String?
        get() = settingsStore.pushLastEventId

    val installationId: String
        get() = settingsStore.installationId

    val installationSecret: String
        get() = settingsStore.installationSecret

    val serverUrl: String
        get() = settingsStore.pushServerUrl

    val isEnabled: Boolean
        get() = settingsStore.pushEnabled

    private fun initialStatus(): PushStatus = when {
        !hasNotificationPermission() -> PushStatus.NEEDS_CONFIGURATION
        !settingsStore.pushEnabled -> PushStatus.DISABLED
        !provider.isConfigured -> PushStatus.NEEDS_CONFIGURATION
        provider.transport == PushTransport.EMBEDDED_SOCKET -> PushStatus.CONNECTING
        else -> PushStatus.REGISTERING
    }

    private fun initialDetail(): String = when {
        !hasNotificationPermission() -> "Разреши уведомления в настройках Android"
        !settingsStore.pushEnabled -> "Уведомления будут подключены автоматически"
        !provider.isConfigured -> "Уведомления недоступны в этой сборке"
        provider.transport == PushTransport.EMBEDDED_SOCKET -> "Восстанавливаем автономное соединение"
        else -> "Ожидаем обновление регистрации"
    }


    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun onPermissionRequired(
        detail: String = "Разреши уведомления в настройках Android",
    ) {
        val wasEnabled = settingsStore.pushEnabled
        val serverUrl = settingsStore.pushServerUrl
        val installationId = settingsStore.installationId
        val deviceSecret = settingsStore.installationSecret
        settingsStore.pushEnabled = false
        provider.unregister()
        if (wasEnabled) unregisterServerRegistration(serverUrl, installationId, deviceSecret)
        mutableState.update {
            it.copy(
                enabled = false,
                status = PushStatus.NEEDS_CONFIGURATION,
                detail = detail,
                errorMessage = null,
            )
        }
    }


    private fun unregisterServerRegistration(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
    ) {
        if (serverUrl.isBlank()) return
        scope.launch {
            runCatching { client.unregister(serverUrl, installationId, deviceSecret) }
        }
    }

    private fun isValidServerUrl(value: String): Boolean {
        if (value.startsWith("https://") && value.length > "https://".length) return true
        if (BuildConfig.DEBUG &&
            (value.startsWith("http://10.0.2.2") ||
                value.startsWith("http://localhost") ||
                value.startsWith("http://127.0.0.1"))
        ) {
            return true
        }
        return false
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(status = PushStatus.ERROR, detail = message, errorMessage = message)
        }
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf(String::isNotBlank) ?: this::class.simpleName.orEmpty()
}
