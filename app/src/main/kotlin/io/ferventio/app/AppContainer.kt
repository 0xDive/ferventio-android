package io.ferventio.app

import android.content.Context
import io.ferventio.app.crash.CrashReporter
import io.ferventio.app.data.ImageCacheManager
import io.ferventio.app.data.SecureTokenStore
import io.ferventio.app.data.SettingsStore
import io.ferventio.app.data.local.ChatHistoryRepository
import io.ferventio.app.data.local.FerventioDatabase
import io.ferventio.app.application.FerventioController
import io.ferventio.app.application.InteractiveChatCoordinator
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.emote.EmoteRepository
import io.ferventio.app.network.FerventioBackendClient
import io.ferventio.app.network.NetworkMonitor
import io.ferventio.app.push.PushCoordinator
import io.ferventio.app.push.PushRegistrationContext
import io.ferventio.app.twitch.TwitchApiClient
import io.ferventio.app.twitch.TwitchPinnedChatGqlClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CrashReporter.coroutineExceptionHandler("application_scope"),
    )
    private val settingsStore = SettingsStore(context)
    private val tokenStore = SecureTokenStore(context)
    private val twitchApiClient = TwitchApiClient()
    private val twitchPinnedChatGqlClient = TwitchPinnedChatGqlClient()
    private val backendClient = FerventioBackendClient()
    private val emoteRepository = EmoteRepository(twitchApiClient)
    private val imageCacheManager = ImageCacheManager(context)
    private val historyRepository = ChatHistoryRepository(FerventioDatabase.getInstance(context))

    val interactiveChatCoordinator = InteractiveChatCoordinator()

    val pushCoordinator = PushCoordinator(
        context = context,
        scope = applicationScope,
        settingsStore = settingsStore,
    )

    val controller = FerventioController(
        scope = applicationScope,
        settingsStore = settingsStore,
        tokenStore = tokenStore,
        api = twitchApiClient,
        pinnedChatClient = twitchPinnedChatGqlClient,
        backend = backendClient,
        emoteRepository = emoteRepository,
        imageCacheManager = imageCacheManager,
        historyRepository = historyRepository,
        onSessionEnded = pushCoordinator::disableForLogout,
        onReplyReceived = pushCoordinator::showReplyNotification,
        onAutoModHeld = pushCoordinator::showAutoModNotification,
        onHighlightAlert = pushCoordinator::showHighlightAlert,
        onInteractiveChatEvent = interactiveChatCoordinator::ingest,
        onInteractiveChatRefresh = interactiveChatCoordinator::refresh,
        onInteractivePollCreate = { auth, draft ->
            interactiveChatCoordinator.createPoll(auth, draft)
        },
        onInteractivePredictionCreate = { auth, draft ->
            interactiveChatCoordinator.createPrediction(auth, draft)
        },
        onInteractivePollEnd = { auth, pollId, status ->
            interactiveChatCoordinator.endPoll(auth, pollId, status)
        },
        onInteractivePredictionEnd = { auth, predictionId, status, winningOutcomeId ->
            interactiveChatCoordinator.endPrediction(
                auth = auth,
                predictionId = predictionId,
                status = status,
                winningOutcomeId = winningOutcomeId,
            )
        },
    )

    private val networkMonitor = NetworkMonitor(
        context = context,
        onAvailable = controller::onNetworkAvailable,
        onLost = controller::onNetworkLost,
    )

    init {
        val registrationContext = {
            val state = controller.state.value
            PushRegistrationContext(
                userId = state.session?.userId,
                userLogin = state.session?.login,
                channelIds = state.channels.map { it.id }.filter(String::isNotBlank).distinct(),
                moderatorChannelIds = state.moderatedChannelIds.filter(String::isNotBlank).distinct(),
                highlightPhrases = state.highlightRules
                    .filter { it.enabled && it.push && it.type in setOf(HighlightRuleType.WORD, HighlightRuleType.USERNAME) }
                    .map { it.pattern.trim() }
                    .filter(String::isNotBlank)
                    .distinct(),
                selectedUserLogins = state.highlightRules
                    .filter { it.enabled && it.push && it.type == HighlightRuleType.USER }
                    .map { it.pattern.trim().removePrefix("@") }
                    .filter(String::isNotBlank)
                    .distinct(),
            )
        }
        pushCoordinator.setRegistrationContextProvider(registrationContext)
        pushCoordinator.setPayloadHandler(controller::ingestPushNotification)
        applicationScope.launch {
            controller.state
                .map { registrationContext() }
                .distinctUntilChanged()
                .collect {
                    delay(750)
                    pushCoordinator.syncRegistration()
                }
        }
        networkMonitor.start()
    }
}
