package io.ferventio.app.application

import io.ferventio.app.domain.*
import io.ferventio.app.BuildConfig
import io.ferventio.app.data.ImageCacheManager
import io.ferventio.app.data.SecureTokenStore
import io.ferventio.app.data.SettingsStore
import io.ferventio.app.data.SettingsBackupCodec
import io.ferventio.app.data.SettingsBackupDocument
import io.ferventio.app.data.SettingsBackupImportResult
import io.ferventio.app.data.local.ChatHistoryRepository
import io.ferventio.app.emote.EmoteProviderContext
import io.ferventio.app.emote.EmoteRepository
import io.ferventio.app.twitch.TwitchApiClient
import io.ferventio.app.twitch.TwitchPinnedChatGqlClient
import io.ferventio.app.twitch.TwitchPinnedChatGqlException
import io.ferventio.app.twitch.TwitchApiException
import io.ferventio.app.twitch.TwitchAnonymousChatClient
import io.ferventio.app.twitch.EventSubActivity
import io.ferventio.app.twitch.EventSubConnectionUpdate
import io.ferventio.app.twitch.EventSubRevocation
import io.ferventio.app.twitch.EventSubSessionSetup
import io.ferventio.app.twitch.EventSubSubscriptionConflictException
import io.ferventio.app.twitch.EventSubSetupException
import io.ferventio.app.twitch.TwitchEventSubClient
import io.ferventio.app.twitch.TwitchChatSendException
import io.ferventio.app.network.FerventioBackendClient
import io.ferventio.app.network.FerventioServerTransportSecurity
import io.ferventio.app.network.FerventioBackendException
import io.ferventio.app.network.BackendSettingsPutResult
import io.ferventio.app.network.BackendSettingsSnapshot
import io.ferventio.app.push.PushNotificationPayload
import io.ferventio.app.security.SafeLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.seconds

class FerventioController(
    private val scope: CoroutineScope,
    private val settingsStore: SettingsStore,
    private val tokenStore: SecureTokenStore,
    private val api: TwitchApiClient,
    private val pinnedChatClient: TwitchPinnedChatGqlClient = TwitchPinnedChatGqlClient(),
    private val backend: FerventioBackendClient,
    private val emoteRepository: EmoteRepository,
    private val imageCacheManager: ImageCacheManager,
    private val historyRepository: ChatHistoryRepository,
    private val onSessionEnded: () -> Unit = {},
    private val onReplyReceived: (ChatMessage) -> Unit = {},
    private val onAutoModHeld: (AutoModHeldMessage) -> Unit = {},
    private val onHighlightAlert: (HighlightAlert) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(
        freshUiState(isBootstrapping = true),
    )
    val state: StateFlow<FerventioUiState> = mutableState.asStateFlow()

    private var backendCredential: BackendSessionCredential? = null
    private var credentials: TwitchAccessLease? = null
    @Volatile
    private var accessLeaseFallbackActive: Boolean = false
    private var eventSubClient: TwitchEventSubClient? = null
    private var eventSubJob: Job? = null
    private val pinnedMessageRefreshJobs = ConcurrentHashMap<String, Job>()
    private val pinnedMessageRequestGenerations = ConcurrentHashMap<String, AtomicLong>()
    private var anonymousChatClient: TwitchAnonymousChatClient? = null
    private var anonymousChatJob: Job? = null
    private val anonymousRestoreMutex = Mutex()
    private val bootstrapGeneration = AtomicLong(0L)
    private var tokenValidationJob: Job? = null
    private var serverAuthorizationJob: Job? = null
    private var deviceRevocationJob: Job? = null
    private var allSessionsRevocationJob: Job? = null
    private var authRestoreJob: Job? = null
    private var profileHydrationJob: Job? = null
    private var chatAssetsJob: Job? = null
    private var emoteLiveRefreshJob: Job? = null
    private var selectedTwitchEmoteJob: Job? = null
    private var messageRuleRebuildJob: Job? = null
    private var settingsSyncJob: Job? = null
    private var performanceScenarioJob: Job? = null
    @Volatile
    private var performanceScenarioActive: Boolean = false
    private val userProfileCache = BoundedLruCache<String, TwitchUser>(MAX_CACHED_USERS)
    private val userColorCache = BoundedLruCache<String, String>(MAX_CACHED_USER_COLORS)
    @Volatile
    private var settingsApplyInProgress: Boolean = false
    @Volatile
    private var messageRuleEvaluator = MessageRuleEvaluator.compile(
        highlights = settingsStore.highlightRules,
        ignores = settingsStore.ignoreRules,
        session = null,
    )
    private val scrollSaveJobs = mutableMapOf<String, Job>()
    private val liveScrollPositions = mutableMapOf<String, ChatScrollPosition>()
    private var draftSaveJob: Job? = null
    private var channelOrderSaveJob: Job? = null
    private val pendingUserProfileIds = linkedSetOf<String>()
    private val eventSubMutex = Mutex()
    private val tokenRefreshMutex = Mutex()
    private val settingsSyncMutex = Mutex()
    private val eventSubGeneration = AtomicLong(0L)
    private val twitchEmoteGeneration = AtomicLong(0L)
    private val lastEventSubActivityPublishedAtMillis = AtomicLong(0L)
    private val seenMessageIds = RecentIdSet(MAX_SEEN_IDS, SEEN_ID_TTL_MILLIS)
    private val seenEventSubMessageIds = RecentIdSet(MAX_SEEN_IDS, SEEN_ID_TTL_MILLIS)
    private var eventSubConnectionStartedAtMillis: Long? = null
    private val parsedThirdPartyEmotesByChannel = ConcurrentHashMap<String, Map<String, ThirdPartyEmoteAsset>>()
    @Volatile
    private var networkAvailable: Boolean = false
    private val historyWriteQueue = Channel<HistoryWriteRequest>(Channel.UNLIMITED)
    @Suppress("unused")
    private val historyWriterJob = scope.launch {
        for (request in historyWriteQueue) {
            val batch = ArrayList<HistoryWriteRequest>(HISTORY_WRITE_BATCH_SIZE).apply {
                add(request)
            }
            // A short collection window turns a burst from a fast chat into one Room
            // transaction instead of competing with every frame of the user's fling.
            delay(HISTORY_WRITE_BATCH_WINDOW_MILLIS)
            while (batch.size < HISTORY_WRITE_BATCH_SIZE) {
                val next = historyWriteQueue.tryReceive().getOrNull() ?: break
                batch += next
            }
            runCatching {
                historyRepository.saveMessages(
                    messages = batch.map(HistoryWriteRequest::message),
                    enabled = settingsStore.localHistoryEnabled,
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
                historyRepository.saveAttentionEntries(batch.mapNotNull(HistoryWriteRequest::attention))
            }.onFailure { error ->
                mutableState.update { state ->
                    state.copy(lastConnectionError = state.lastConnectionError ?: "Room: ${error.userMessage()}")
                }
            }
        }
    }

    private val eventQueue = Channel<ChatEvent>(
        capacity = EVENT_QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        onUndeliveredElement = { _: ChatEvent ->
            mutableState.update { state: FerventioUiState ->
                state.copy(eventSubDroppedEventCount = state.eventSubDroppedEventCount + 1)
            }
        },
    )
    @Suppress("unused")
    private val eventQueueJob = scope.launch {
        for (event in eventQueue) {
            handleChatEvent(event)
        }
    }

    init {
        settingsStore.setSyncRelevantChangeListener {
            if (!settingsApplyInProgress) scheduleSettingsSync()
        }
    }

    fun bootstrap() {
        val generation = bootstrapGeneration.incrementAndGet()
        authRestoreJob?.cancel()
        authRestoreJob = scope.launch {
            try {
                val storedAuthentication = tokenStore.loadAuthentication()
                val storedCredential = storedAuthentication?.backendCredential
                val restoredLease = storedAuthentication?.accessLease
                    ?.takeIf { TwitchAccessLeasePolicy.canUseDuringBackendOutage(it) }
                if (storedAuthentication?.accessLease != null && restoredLease == null && storedCredential != null) {
                    tokenStore.save(storedCredential, accessLease = null)
                }
                currentCoroutineContext().ensureActive()
                if (performanceScenarioActive || generation != bootstrapGeneration.get()) return@launch
                credentials = restoredLease
                if (storedCredential == null) {
                    restoreAnonymousChannelsAndConnect(expectedBootstrapGeneration = generation)
                    return@launch
                }

                try {
                    val lease = obtainAccessLease(storedCredential, forceRefresh = false)
                    if (generation != bootstrapGeneration.get()) return@launch
                    completeLogin(storedCredential, validateAccessLeaseAtStartup(lease))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (generation != bootstrapGeneration.get()) return@launch
                    backendCredential = null
                    credentials = null
                    accessLeaseFallbackActive = false
                    val permanentFailure = error.isPermanentAuthenticationFailure()
                    if (permanentFailure) tokenStore.clear()
                    restoreAnonymousChannelsAndConnect(
                        warning = if (permanentFailure) {
                            "Сессия Ferventio завершена: ${error.userMessage()}"
                        } else {
                            "Сервер Ferventio временно недоступен. Чаты открыты без аккаунта; вход восстановится после сети."
                        },
                        reauthorizationRequired = permanentFailure,
                        expectedBootstrapGeneration = generation,
                    )
                }
            } finally {
                if (generation == bootstrapGeneration.get()) {
                    authRestoreJob = null
                }
            }
        }
    }

    fun startServerAuthorization() {
        val serverUrl = runCatching {
            FerventioServerTransportSecurity.validateServerUrl(BuildConfig.FERVENTIO_SERVER_URL).baseUrl
        }.getOrElse { error ->
            showError("В этой сборке неверно настроен сервер Ferventio: ${error.userMessage()}")
            return
        }
        cancelServerAuthorization(updateState = false)
        authRestoreJob?.cancel()
        authRestoreJob = null
        mutableState.update {
            it.copy(
                isAuthorizing = true,
                pendingExternalUri = null,
                errorMessage = null,
            )
        }
        serverAuthorizationJob = scope.launch {
            try {
                val authorization = backend.startAuthorization(
                    serverUrl = serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    appCallbackUri = "${BuildConfig.APPLICATION_ID}://oauth/callback",
                )
                settingsStore.savePendingAuth(
                    state = authorization.state,
                    expiresAtMillis = authorization.expiresAtEpochMillis,
                    serverUrl = serverUrl,
                )
                mutableState.update {
                    it.copy(
                        isAuthorizing = true,
                        pendingExternalUri = authorization.authorizationUrl,
                        errorMessage = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                settingsStore.clearPendingAuth()
                mutableState.update {
                    it.copy(
                        isAuthorizing = false,
                        pendingExternalUri = null,
                        errorMessage = "Авторизация через сервер: ${error.userMessage()}",
                    )
                }
            } finally {
                serverAuthorizationJob = null
            }
        }
    }

    fun consumePendingExternalUri() {
        mutableState.update { it.copy(pendingExternalUri = null) }
    }

    fun reportAuthorizationBrowserError(message: String?) {
        failServerAuthorization(
            "Не удалось открыть сайт авторизации: ${message.orEmpty().ifBlank { "нет браузера" }}",
        )
    }

    fun handleAuthorizationCallback(code: String?, state: String?, errorCode: String?) {
        val expectedState = settingsStore.pendingAuthState
        val expectedExpiresAt = settingsStore.pendingAuthExpiresAtMillis
        val authorizationServerUrl = settingsStore.pendingAuthServerUrl
        if (state.isNullOrBlank() || expectedState.isNullOrBlank() || !secureStateEquals(expectedState, state)) {
            mutableState.update {
                it.copy(errorMessage = "Отклонён посторонний или устаревший OAuth callback")
            }
            return
        }
        if (expectedExpiresAt <= System.currentTimeMillis()) {
            failServerAuthorization("Ссылка авторизации истекла. Запусти вход ещё раз.")
            return
        }
        if (authorizationServerUrl.isNullOrBlank()) {
            failServerAuthorization("Не найден сервер, который начал авторизацию. Запусти вход ещё раз.")
            return
        }
        if (!errorCode.isNullOrBlank()) {
            failServerAuthorization(
                if (errorCode == "access_denied") {
                    "Вход через Twitch отменён"
                } else {
                    "Twitch OAuth: $errorCode"
                },
            )
            return
        }
        if (code.isNullOrBlank()) {
            failServerAuthorization("Сервер не вернул код авторизации")
            return
        }

        serverAuthorizationJob?.cancel()
        authRestoreJob?.cancel()
        authRestoreJob = null
        mutableState.update { it.copy(isAuthorizing = true, pendingExternalUri = null, errorMessage = null) }
        serverAuthorizationJob = scope.launch {
            try {
                val (credential, lease) = backend.completeAuthorization(
                    serverUrl = authorizationServerUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    code = code,
                    state = state,
                )
                settingsStore.clearPendingAuth()
                completeLogin(credential, lease)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                failServerAuthorization("Не удалось завершить вход: ${error.userMessage()}")
            } finally {
                serverAuthorizationJob = null
            }
        }
    }

    private fun failServerAuthorization(message: String) {
        settingsStore.clearPendingAuth()
        mutableState.update {
            it.copy(
                isAuthorizing = false,
                pendingExternalUri = null,
                errorMessage = message,
            )
        }
    }

    fun cancelServerAuthorization() {
        cancelServerAuthorization(updateState = true)
    }

    fun addChannel(loginInput: String) {
        val login = loginInput.trim().removePrefix("#").lowercase()
        if (login.isEmpty()) return showError("Укажи имя канала")
        if (mutableState.value.channels.any { it.login == login }) return
        if (mutableState.value.channels.size >= MAX_CHANNELS) {
            return showError("Можно открыть до $MAX_CHANNELS каналов")
        }

        scope.launch {
            runCatching {
                val session = mutableState.value.session
                val accessToken = credentials?.accessToken
                val channel = if (session != null && accessToken != null) {
                    api.getChannel(session.clientId, accessToken, login)
                } else {
                    historyRepository.loadChannels(listOf(login)).firstOrNull()
                        ?: ChatChannel(
                            id = anonymousChannelId(login),
                            login = login,
                            displayName = login,
                        )
                }
                val updated = (mutableState.value.channels + channel)
                    .distinctBy { it.login.lowercase() }
                settingsStore.channelLogins = updated.map(ChatChannel::login)
                settingsStore.selectedChannelLogin = channel.login
                if (!channel.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX)) {
                    runCatching { historyRepository.saveChannel(channel) }
                }
                val savedMessages = if (channel.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX)) {
                    emptyList()
                } else {
                    runCatching {
                        historyRepository.loadRecentMessages(
                            channelIds = listOf(channel.id),
                            enabled = settingsStore.localHistoryEnabled,
                            limitPerChannel = settingsStore.localHistoryLimit,
                            retentionDays = settingsStore.localHistoryRetentionDays,
                            maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                        )[channel.id].orEmpty()
                    }.getOrDefault(emptyList())
                }
                ChatMessageTextPreparation.warm(savedMessages)
                val savedScrollPosition = if (channel.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX)) {
                    null
                } else {
                    runCatching { historyRepository.loadScrollPositions(listOf(channel.id))[channel.id] }.getOrNull()
                }
                mutableState.update {
                    it.copy(
                        channels = updated,
                        selectedChannelId = channel.id,
                        messagesByChannel = it.messagesByChannel +
                            (channel.id to savedMessages.takeLast(MAX_MESSAGES_PER_CHANNEL)),
                        historyPagingByChannel = it.historyPagingByChannel + (
                            channel.id to HistoryPagingState(
                                endReached = channel.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX),
                                loadedCount = savedMessages.size,
                            )
                        ),
                        scrollPositionsByChannel = savedScrollPosition?.let { position ->
                            it.scrollPositionsByChannel + (channel.id to position)
                        } ?: it.scrollPositionsByChannel,
                        restoredHistoryMessageCount = it.restoredHistoryMessageCount + savedMessages.size,
                        errorMessage = null,
                    )
                }
                normalizeChannelPreferences(updated, channel.id)
                selectChannel(channel.id)
                if (session != null && accessToken != null) {
                    refreshTwitchChatAssets(session, accessToken, updated)
                    connectEventSub(session, accessToken)
                } else {
                    refreshAnonymousChatAssets(updated)
                    connectAnonymousChat(updated)
                }
            }.onFailure { showError(it.userMessage()) }
        }
    }

    fun removeChannel(channelId: String) {
        val current = mutableState.value
        val removedIndex = current.channels.indexOfFirst { it.id == channelId }
        if (removedIndex < 0) return
        val session = current.session
        val accessToken = credentials?.accessToken
        val updated = current.channels.filterNot { it.id == channelId }
        val nextSelectedId = when {
            current.selectedChannelId != channelId && updated.any { it.id == current.selectedChannelId } ->
                current.selectedChannelId
            updated.isEmpty() -> null
            else -> updated[removedIndex.coerceAtMost(updated.lastIndex)].id
        }
        scrollSaveJobs.remove(channelId)?.cancel()
        liveScrollPositions.remove(channelId)
        settingsStore.channelLogins = updated.map(ChatChannel::login)
        settingsStore.markChannelsExplicitlyEmpty(updated.isEmpty())
        settingsStore.selectedChannelLogin = updated.firstOrNull { it.id == nextSelectedId }?.login
        settingsStore.channelTabTitles = settingsStore.channelTabTitles - channelId
        settingsStore.draftsByChannel = settingsStore.draftsByChannel - channelId
        settingsStore.sentMessageHistoryByChannel = settingsStore.sentMessageHistoryByChannel - channelId
        mutableState.update { state ->
            state.copy(
                channels = updated,
                selectedChannelId = nextSelectedId,
                channelTabTitles = state.channelTabTitles - channelId,
                messagesByChannel = state.messagesByChannel - channelId,
                historyPagingByChannel = state.historyPagingByChannel - channelId,
                scrollPositionsByChannel = state.scrollPositionsByChannel - channelId,
                channelAttention = state.channelAttention - channelId,
                messageNavigationTargets = state.messageNavigationTargets - channelId,
                draftsByChannel = state.draftsByChannel - channelId,
                sentMessageHistoryByChannel = state.sentMessageHistoryByChannel - channelId,
                rateLimitsByChannel = state.rateLimitsByChannel - channelId,
                pinnedMessagesByChannel = state.pinnedMessagesByChannel - channelId,
                badgeAssetsByChannel = state.badgeAssetsByChannel - channelId,
                frankerFaceZChannelBadgesByChannel = state.frankerFaceZChannelBadgesByChannel - channelId,
                cheermoteAssetsByChannel = state.cheermoteAssetsByChannel - channelId,
                emoteCatalogByChannel = state.emoteCatalogByChannel - channelId,
                betterTtvEmotesByChannel = state.betterTtvEmotesByChannel - channelId,
                frankerFaceZEmotesByChannel = state.frankerFaceZEmotesByChannel - channelId,
                sevenTvEmotesByChannel = state.sevenTvEmotesByChannel - channelId,
            )
        }
        parsedThirdPartyEmotesByChannel.remove(channelId)
        normalizeChannelPreferences(updated, nextSelectedId)
        if (session != null && accessToken != null) {
            refreshTwitchChatAssets(session, accessToken, updated)
            connectEventSub(session, accessToken)
        } else {
            refreshAnonymousChatAssets(updated)
            connectAnonymousChat(updated)
        }
    }

    fun selectChannel(channelId: String) {
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId } ?: return
        settingsStore.selectedChannelLogin = channel.login
        mutableState.update { state ->
            if (state.selectedChannelId == channelId &&
                state.workspaceLayout.activeTab?.activeSplit?.channelId == channelId
            ) {
                state
            } else {
                val layout = state.workspaceLayout.selectChannelInActiveSplit(channelId)
                settingsStore.workspaceLayoutJson = WorkspaceLayoutCodec.encode(layout)
                state.copy(
                    selectedChannelId = channelId,
                    workspaceLayout = layout,
                )
            }
        }
        refreshPinnedMessage(channelId)
        if (mutableState.value.isAuthenticated) {
            refreshSelectedTwitchChannelEmotes(channelId)
        }
    }

    fun moveChannel(channelId: String, targetIndex: Int) {
        val current = mutableState.value
        val reordered = ChannelOrder.move(current.channels, channelId, targetIndex)
        if (reordered === current.channels) return
        mutableState.update { state ->
            val latest = ChannelOrder.move(state.channels, channelId, targetIndex)
            if (latest === state.channels) state else state.copy(channels = latest)
        }
        channelOrderSaveJob?.cancel()
        channelOrderSaveJob = scope.launch {
            delay(CHANNEL_ORDER_SAVE_DEBOUNCE_MILLIS)
            val orderedLogins = mutableState.value.channels.map(ChatChannel::login)
            withContext(Dispatchers.IO) {
                settingsStore.channelLogins = orderedLogins
            }
            channelOrderSaveJob = null
        }
    }

    fun setVisibleChannels(channelIds: Set<String>) {
        val knownIds = mutableState.value.channels.map(ChatChannel::id).toSet()
        val visible = channelIds.intersect(knownIds)
        mutableState.update { state ->
            if (state.visibleChannelIds == visible) state else state.copy(visibleChannelIds = visible)
        }
    }


    fun togglePinnedChannel(channelId: String) {
        if (mutableState.value.channels.none { it.id == channelId }) return
        mutableState.update { state ->
            val updated = if (channelId in state.pinnedChannelIds) {
                state.pinnedChannelIds - channelId
            } else {
                (state.pinnedChannelIds + channelId).distinct()
            }
            settingsStore.pinnedChannelIds = updated
            state.copy(pinnedChannelIds = updated)
        }
    }


    fun renameChannelTab(channelId: String, title: String) {
        if (mutableState.value.channels.none { it.id == channelId }) return
        val normalizedTitle = title.trim().take(32)
        mutableState.update { state ->
            val updated = if (normalizedTitle.isBlank()) {
                state.channelTabTitles - channelId
            } else {
                state.channelTabTitles + (channelId to normalizedTitle)
            }
            settingsStore.channelTabTitles = updated
            state.copy(channelTabTitles = updated)
        }
    }

    fun markChannelRead(channelId: String) {
        val canMarkRead = ChannelReadPolicy.canMarkRead(channelId, mutableState.value.visibleChannelIds)
        if (!canMarkRead) return
        mutableState.update { state ->
            val unreadForChannel = state.attentionEntries.count { it.channelId == channelId && !it.isRead }
            state.copy(
                channelAttention = state.channelAttention - channelId,
                attentionEntries = state.attentionEntries.map { entry ->
                    if (entry.channelId == channelId) entry.copy(isRead = true) else entry
                },
                mentionUnreadCount = (state.mentionUnreadCount - unreadForChannel).coerceAtLeast(0),
            )
        }
        scope.launch { runCatching { historyRepository.markChannelAttentionRead(channelId) } }
    }

    fun markAllMentionsRead() {
        mutableState.update { state ->
            state.copy(
                attentionEntries = state.attentionEntries.map { entry -> entry.copy(isRead = true) },
                mentionUnreadCount = 0,
                channelAttention = state.channelAttention.mapValues { (_, attention) ->
                    attention.copy(mentionCount = 0)
                }.filterValues(ChannelAttention::hasUnread),
            )
        }
        scope.launch { runCatching { historyRepository.markAllAttentionRead() } }
    }

    fun ingestPushNotification(payload: PushNotificationPayload) {
        val kind = payload.type.lowercase()
        if (kind !in PUSH_ATTENTION_TYPES) return
        val channelLogin = payload.channelLogin.orEmpty().trim().removePrefix("#")
        val channelId = payload.channelId.orEmpty().trim().ifBlank {
            if (channelLogin.isBlank()) return else "remote:$channelLogin"
        }
        val messageId = payload.messageId.orEmpty().trim().ifBlank {
            payload.eventId.orEmpty().trim().ifBlank { return }
        }
        val timestampMillis = payload.createdAtEpochMillis
            ?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val entry = AttentionEntry(
            messageId = messageId,
            channelId = channelId,
            channelLogin = channelLogin,
            authorId = payload.actorId.orEmpty(),
            authorLogin = payload.actorLogin.orEmpty(),
            authorDisplayName = payload.actorDisplayName
                .orEmpty()
                .ifBlank { payload.actorLogin.orEmpty() }
                .ifBlank { payload.title.substringAfterLast(" ").trim() }
                .ifBlank { "Twitch" },
            text = payload.body,
            timestamp = Instant.ofEpochMilli(timestampMillis).toString(),
            timestampMillis = timestampMillis,
            isRead = false,
            isDirectMention = kind == "mention" || kind == "reply",
            isHighlight = kind == "highlight" || kind == "selected_user",
            highlightReasons = listOf(kind),
        )
        var shouldPersist = false
        mutableState.update { state ->
            if (state.attentionEntries.any { it.messageId == messageId }) return@update state
            shouldPersist = true
            val previous = state.channelAttention[channelId] ?: ChannelAttention()
            state.copy(
                attentionEntries = (listOf(entry) + state.attentionEntries)
                    .take(MAX_ATTENTION_ENTRIES),
                mentionUnreadCount = (state.mentionUnreadCount + 1)
                    .coerceAtMost(MAX_ATTENTION_COUNT),
                channelAttention = state.channelAttention + (
                    channelId to previous.copy(
                        mentionCount = (previous.mentionCount + 1)
                            .coerceAtMost(MAX_ATTENTION_COUNT),
                    )
                ),
            )
        }
        if (shouldPersist) {
            scope.launch { runCatching { historyRepository.saveAttentionEntries(listOf(entry)) } }
        }
    }

    fun openAttentionEntry(entry: AttentionEntry) {
        val current = mutableState.value
        val channel = current.channels.firstOrNull { channel ->
            channel.id == entry.channelId || channel.login.equals(entry.channelLogin, ignoreCase = true)
        }
        val resolvedChannelId = channel?.id ?: entry.channelId
        mutableState.update { state ->
            val existing = state.messagesByChannel[resolvedChannelId].orEmpty()
            val hasMessage = existing.any { it.id == entry.messageId }
            val injected = if (hasMessage) {
                existing
            } else {
                (existing + entry.copy(channelId = resolvedChannelId).asMessage())
                    .sortedWith(compareBy(ChatMessage::timestampMillis, ChatMessage::id))
                    .takeLast(MAX_MESSAGES_PER_CHANNEL)
            }
            val currentAttention = state.channelAttention[resolvedChannelId]
            val nextChannelAttention = if (!entry.isRead && currentAttention != null) {
                val updated = currentAttention.copy(
                    mentionCount = (currentAttention.mentionCount - 1).coerceAtLeast(0),
                )
                if (updated.hasUnread) {
                    state.channelAttention + (resolvedChannelId to updated)
                } else {
                    state.channelAttention - resolvedChannelId
                }
            } else {
                state.channelAttention
            }
            state.copy(
                attentionEntries = state.attentionEntries.map { item ->
                    if (item.messageId == entry.messageId) item.copy(isRead = true) else item
                },
                mentionUnreadCount = (state.mentionUnreadCount - if (entry.isRead) 0 else 1).coerceAtLeast(0),
                channelAttention = nextChannelAttention,
                messagesByChannel = state.messagesByChannel + (resolvedChannelId to injected),
            )
        }
        scope.launch { runCatching { historyRepository.markAttentionRead(entry.messageId) } }
        navigateToMessage(resolvedChannelId, entry.messageId)
    }

    fun upsertHighlightRule(rule: HighlightRule) {
        val normalized = rule.copy(pattern = rule.pattern.trim().take(MAX_RULE_PATTERN_LENGTH))
        val updated = (mutableState.value.highlightRules.filterNot { it.id == normalized.id } + normalized)
            .take(MAX_MESSAGE_RULES)
        settingsStore.highlightRules = updated
        mutableState.update { it.copy(highlightRules = updated) }
        rebuildMessageRuleEvaluation()
    }

    fun deleteHighlightRule(ruleId: String) {
        val updated = mutableState.value.highlightRules.filterNot { it.id == ruleId }
        settingsStore.highlightRules = updated
        mutableState.update { it.copy(highlightRules = updated) }
        rebuildMessageRuleEvaluation()
    }

    fun upsertIgnoreRule(rule: IgnoreRule) {
        val normalized = rule.copy(pattern = rule.pattern.trim().take(MAX_RULE_PATTERN_LENGTH))
        val updated = (mutableState.value.ignoreRules.filterNot { it.id == normalized.id } + normalized)
            .take(MAX_MESSAGE_RULES)
        settingsStore.ignoreRules = updated
        mutableState.update { it.copy(ignoreRules = updated) }
        rebuildMessageRuleEvaluation()
    }

    fun deleteIgnoreRule(ruleId: String) {
        val updated = mutableState.value.ignoreRules.filterNot { it.id == ruleId }
        settingsStore.ignoreRules = updated
        mutableState.update { it.copy(ignoreRules = updated) }
        rebuildMessageRuleEvaluation()
    }

    fun upsertSavedMessageFilter(filter: SavedMessageFilter): Boolean {
        val normalized = filter.copy(
            name = filter.name.trim().take(80),
            expression = filter.expression.trim().take(MAX_FILTER_EXPRESSION_LENGTH),
        )
        if (normalized.name.isBlank()) {
            showError("Укажи название фильтра")
            return false
        }
        val compiled = MessageFilterLanguage.compile(normalized.expression)
        val error = compiled.diagnostics.firstOrNull { it.severity == FilterDiagnosticSeverity.ERROR }
        if (error != null) {
            showError("Фильтр: ${error.message}")
            return false
        }
        val current = mutableState.value.savedMessageFilters
        if (current.any { it.id != normalized.id && it.name.equals(normalized.name, ignoreCase = true) }) {
            showError("Фильтр с таким названием уже существует")
            return false
        }
        val updated = (current.filterNot { it.id == normalized.id } + normalized)
            .take(MAX_SAVED_FILTERS)
        settingsStore.savedMessageFilters = updated
        mutableState.update { it.copy(savedMessageFilters = updated) }
        return true
    }

    fun deleteSavedMessageFilter(filterId: String) {
        val filter = mutableState.value.savedMessageFilters.firstOrNull { it.id == filterId }
            ?: return
        val reference = savedFilterReference(filterId)
        updateWorkspaceLayout { layout ->
            layout.copy(workspaces = layout.workspaces.map { workspace ->
                workspace.copy(tabs = workspace.tabs.map { tab ->
                    tab.copy(splits = tab.splits.map { split ->
                        if (split.filterQuery == reference) split.withFilterQuery(filter.expression) else split
                    })
                })
            })
        }
        val updated = mutableState.value.savedMessageFilters.filterNot { it.id == filterId }
        settingsStore.savedMessageFilters = updated
        mutableState.update { it.copy(savedMessageFilters = updated) }
    }

    fun exportSavedMessageFilters(): String =
        MessageFilterCodec.encode(mutableState.value.savedMessageFilters)

    fun importSavedMessageFilters(raw: String): Boolean {
        val imported = MessageFilterCodec.decode(raw).getOrElse { error ->
            showError("Не удалось импортировать фильтры: ${error.userMessage()}")
            return false
        }
        if (imported.isEmpty()) {
            showError("В импорте нет фильтров")
            return false
        }
        imported.forEach { filter ->
            val error = MessageFilterLanguage.compile(filter.expression).diagnostics
                .firstOrNull { it.severity == FilterDiagnosticSeverity.ERROR }
            if (error != null) {
                showError("Фильтр '${filter.name}': ${error.message}")
                return false
            }
        }
        val merged = MessageFilterCodec.merge(mutableState.value.savedMessageFilters, imported)
        settingsStore.savedMessageFilters = merged
        mutableState.update { it.copy(savedMessageFilters = merged) }
        return true
    }

    fun addSavedFilterSplit(filterId: String) {
        val filter = mutableState.value.savedMessageFilters.firstOrNull { it.id == filterId }
            ?: return showError("Фильтр не найден")
        addExpressionFilteredSplit(savedFilterReference(filter.id))
    }

    private fun addExpressionFilteredSplit(expression: String) {
        val activeTab = mutableState.value.workspaceLayout.activeTab
        if (activeTab == null) return showError("Нет активной вкладки")
        if (activeTab.splits.size >= MAX_SPLITS_PER_TAB) {
            return showError("В этой вкладке уже максимум $MAX_SPLITS_PER_TAB split")
        }
        updateWorkspaceLayout { layout ->
            val activeWorkspaceId = layout.activeWorkspaceId ?: return@updateWorkspaceLayout layout
            layout.copy(workspaces = layout.workspaces.map workspaceMap@{ workspace ->
                if (workspace.id != activeWorkspaceId) return@workspaceMap workspace
                val activeTabId = workspace.activeTabId ?: workspace.tabs.firstOrNull()?.id
                    ?: return@workspaceMap workspace
                workspace.copy(tabs = workspace.tabs.map tabMap@{ tab ->
                    if (tab.id != activeTabId) return@tabMap tab
                    val channelId = tab.activeSplit?.channelId ?: mutableState.value.selectedChannelId
                    val split = FilteredSplit(
                        id = newLayoutId("split"),
                        channelId = channelId,
                        filterQuery = expression.trim().take(MAX_FILTER_EXPRESSION_LENGTH),
                    )
                    tab.copy(splits = tab.splits + split, activeSplitId = split.id)
                })
            })
        }
        mutableState.update { it.copy(requestedMainSection = MainSection.CHATS) }
    }

    fun addHighlightsFilteredSplit() {
        val activeTab = mutableState.value.workspaceLayout.activeTab
        if (activeTab == null) return showError("Нет активной вкладки")
        if (activeTab.splits.size >= MAX_SPLITS_PER_TAB) {
            return showError("В этой вкладке уже максимум $MAX_SPLITS_PER_TAB split")
        }
        updateWorkspaceLayout { layout ->
            val activeWorkspaceId = layout.activeWorkspaceId ?: return@updateWorkspaceLayout layout
            layout.copy(workspaces = layout.workspaces.map workspaceMap@{ workspace ->
                if (workspace.id != activeWorkspaceId) return@workspaceMap workspace
                val activeTabId = workspace.activeTabId ?: workspace.tabs.firstOrNull()?.id
                    ?: return@workspaceMap workspace
                workspace.copy(tabs = workspace.tabs.map tabMap@{ tab ->
                    if (tab.id != activeTabId) return@tabMap tab
                    val channelId = tab.activeSplit?.channelId ?: mutableState.value.selectedChannelId
                    val split = FilteredSplit(
                        id = newLayoutId("split"),
                        channelId = channelId,
                        filterQuery = HIGHLIGHTS_FILTER_QUERY,
                    )
                    tab.copy(
                        splits = tab.splits + split,
                        activeSplitId = split.id,
                    )
                })
            })
        }
        mutableState.update { it.copy(requestedMainSection = MainSection.CHATS) }
    }



    fun recordEmoteUsage(asset: ThirdPartyEmoteAsset) {
        val key = asset.usageKey
        if (key.isBlank()) return
        val updated = (listOf(key) + mutableState.value.recentEmoteKeys).take(MAX_RECENT_EMOTE_USES)
        settingsStore.recentEmoteKeys = updated
        mutableState.update { state -> state.copy(recentEmoteKeys = updated) }
    }

    fun toggleFavoriteEmote(asset: ThirdPartyEmoteAsset) {
        val key = asset.usageKey
        if (key.isBlank()) return
        mutableState.update { state ->
            val updated = if (key in state.favoriteEmoteKeys) {
                state.favoriteEmoteKeys - key
            } else {
                state.favoriteEmoteKeys + key
            }
            settingsStore.favoriteEmoteKeys = updated
            state.copy(favoriteEmoteKeys = updated)
        }
    }

    fun updateDraft(channelId: String, value: String) {
        if (channelId.isBlank()) return
        val normalized = value.take(MAX_DRAFT_LENGTH)
        mutableState.update { state ->
            val previous = state.draftsByChannel[channelId].orEmpty()
            if (previous == normalized) {
                state
            } else {
                val updated = if (normalized.isEmpty()) {
                    state.draftsByChannel - channelId
                } else {
                    state.draftsByChannel + (channelId to normalized)
                }
                state.copy(draftsByChannel = updated)
            }
        }
        draftSaveJob?.cancel()
        draftSaveJob = scope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MILLIS)
            settingsStore.draftsByChannel = mutableState.value.draftsByChannel
            draftSaveJob = null
        }
    }

    fun setSendOnEnter(enabled: Boolean) {
        settingsStore.sendOnEnter = enabled
        mutableState.update { it.copy(sendOnEnter = enabled) }
    }

    fun setShowComposerEmoteImages(enabled: Boolean) {
        settingsStore.showComposerEmoteImages = enabled
        mutableState.update { it.copy(showComposerEmoteImages = enabled) }
    }

    fun addUserCardTimeoutPreset(raw: String): Boolean {
        val seconds = ChatCommandParser.parseDurationSeconds(raw.trim())
        if (seconds == null) {
            showError("Интервал должен быть в формате 10s, 5m, 2h или 1d")
            return false
        }
        val current = mutableState.value.userCardTimeoutPresetsSeconds
        if (seconds in current) return true
        if (current.size >= MAX_USER_CARD_TIMEOUT_PRESETS) {
            showError("Можно сохранить не больше $MAX_USER_CARD_TIMEOUT_PRESETS интервалов")
            return false
        }
        persistUserCardTimeoutPresets(current + seconds)
        return true
    }

    fun removeUserCardTimeoutPreset(seconds: Int) {
        val current = mutableState.value.userCardTimeoutPresetsSeconds
        if (seconds !in current || current.size <= 1) {
            if (current.size <= 1) showError("Оставь хотя бы один timeout-интервал")
            return
        }
        persistUserCardTimeoutPresets(current - seconds)
    }

    fun resetUserCardTimeoutPresets() {
        persistUserCardTimeoutPresets(DEFAULT_USER_CARD_TIMEOUT_PRESETS)
    }

    fun setUserCardShowBanAction(enabled: Boolean) {
        settingsStore.userCardShowBanAction = enabled
        mutableState.update { it.copy(userCardShowBanAction = enabled) }
    }

    fun moveUserCardModerationAction(actionId: String, direction: Int) {
        if (direction == 0) return
        val state = mutableState.value
        val updated = UserCardModerationLayout.move(
            storedOrder = state.userCardModerationActionOrder,
            timeoutPresetsSeconds = state.userCardTimeoutPresetsSeconds,
            actionId = actionId,
            direction = direction,
            hiddenActionIds = if (state.userCardShowBanAction) emptySet() else setOf(UserCardModerationLayout.BAN),
        )
        if (updated == state.userCardModerationActionOrder) return
        settingsStore.userCardModerationActionOrder = updated
        mutableState.update { it.copy(userCardModerationActionOrder = updated) }
    }

    private fun persistUserCardTimeoutPresets(values: List<Int>) {
        val normalized = values.distinct().take(MAX_USER_CARD_TIMEOUT_PRESETS)
        settingsStore.userCardTimeoutPresetsSeconds = normalized
        val updatedOrder = UserCardModerationLayout.normalize(
            storedOrder = mutableState.value.userCardModerationActionOrder,
            timeoutPresetsSeconds = normalized,
        )
        settingsStore.userCardModerationActionOrder = updatedOrder
        mutableState.update {
            it.copy(
                userCardTimeoutPresetsSeconds = normalized,
                userCardModerationActionOrder = updatedOrder,
            )
        }
    }


    fun setReplyNotificationsEnabled(enabled: Boolean) {
        settingsStore.replyNotificationsEnabled = enabled
        mutableState.update { it.copy(replyNotificationsEnabled = enabled) }
    }

    fun saveCustomCommand(command: CustomCommand, oldName: String? = null): Boolean {
        val validated = CustomCommandCodec.validate(command, oldName).getOrElse { error ->
            showError(error.userMessage())
            return false
        }
        val oldNormalized = oldName?.let(CommandRegistry::normalizeName)
        val current = mutableState.value.customCommands
        if (current.any { existing ->
                existing.normalizedName == validated.normalizedName && existing.normalizedName != oldNormalized
            }
        ) {
            showError("Команда /${validated.normalizedName} уже существует")
            return false
        }
        val updated = current
            .filterNot { it.normalizedName == oldNormalized || it.normalizedName == validated.normalizedName }
            .plus(validated)
            .sortedBy(CustomCommand::normalizedName)
        persistCustomCommands(updated, "Команда /${validated.normalizedName} сохранена")
        return true
    }

    fun deleteCustomCommand(name: String) {
        val normalized = CommandRegistry.normalizeName(name)
        val updated = mutableState.value.customCommands.filterNot { it.normalizedName == normalized }
        if (updated.size == mutableState.value.customCommands.size) return
        persistCustomCommands(updated, "Команда /$normalized удалена")
    }

    fun setCustomCommandEnabled(name: String, enabled: Boolean) {
        val normalized = CommandRegistry.normalizeName(name)
        val updated = mutableState.value.customCommands.map { command ->
            if (command.normalizedName == normalized) command.copy(enabled = enabled) else command
        }
        persistCustomCommands(updated, if (enabled) "Команда /$normalized включена" else "Команда /$normalized отключена")
    }

    fun exportCustomCommands(): String = CustomCommandCodec.encode(mutableState.value.customCommands)

    fun importCustomCommands(raw: String): Boolean {
        val imported = CustomCommandCodec.decode(raw).getOrElse { error ->
            showError("Не удалось импортировать команды: ${error.userMessage()}")
            return false
        }
        val merged = (mutableState.value.customCommands + imported)
            .associateBy(CustomCommand::normalizedName)
            .values
            .sortedBy(CustomCommand::normalizedName)
        persistCustomCommands(merged, "Импортировано команд: ${imported.size}")
        return true
    }

    fun clearCustomCommandStatus() {
        mutableState.update { it.copy(customCommandStatusMessage = null) }
    }

    private fun persistCustomCommands(commands: List<CustomCommand>, status: String?) {
        settingsStore.customCommandsJson = CustomCommandCodec.encode(commands)
        mutableState.update { it.copy(customCommands = commands, customCommandStatusMessage = status) }
    }

    suspend fun searchLocalHistory(request: ChatSearchRequest): Result<List<ChatMessage>> =
        historyRepository.searchMessages(request)

    suspend fun loadSearchContext(messageId: String, radius: Int = 3): Result<List<ChatMessage>> =
        runCatching { historyRepository.loadMessageContext(messageId, radius) }

    fun openSearchResult(message: ChatMessage) {
        scope.launch { prepareSearchMessage(message, navigate = true) }
    }

    fun openSearchUserCard(message: ChatMessage) {
        scope.launch {
            if (prepareSearchMessage(message, navigate = false)) openUserCard(message)
        }
    }

    private suspend fun prepareSearchMessage(message: ChatMessage, navigate: Boolean): Boolean {
        val context = historyRepository.loadMessageContext(message.id, radius = 20)
            .ifEmpty { listOf(message) }
        val wasNewChannel = mutableState.value.channels.none { it.id == message.channelId }
        var channel = mutableState.value.channels.firstOrNull { it.id == message.channelId }
        if (channel == null) {
            channel = historyRepository.loadChannels(listOf(message.channelLogin)).firstOrNull()
                ?: ChatChannel(
                    id = message.channelId,
                    login = message.channelLogin,
                    displayName = message.channelLogin,
                )
            if (mutableState.value.channels.size >= MAX_CHANNELS) {
                showError("Закрой один канал, чтобы открыть результат из #${message.channelLogin}")
                return false
            }
        }
        val resolvedChannel = requireNotNull(channel)
        mutableState.update { state ->
            val channels = (state.channels + resolvedChannel).distinctBy(ChatChannel::id)
            val existing = state.messagesByChannel[resolvedChannel.id].orEmpty()
            val merged = (existing + context)
                .distinctBy(ChatMessage::id)
                .sortedWith(compareBy(ChatMessage::timestampMillis, ChatMessage::id))
            val visible = keepSearchTargetInWindow(merged, message.id)
            state.copy(
                channels = channels,
                selectedChannelId = if (navigate) resolvedChannel.id else state.selectedChannelId,
                requestedMainSection = if (navigate) MainSection.CHATS else state.requestedMainSection,
                messagesByChannel = state.messagesByChannel + (resolvedChannel.id to visible),
                messageNavigationTargets = if (navigate) {
                    state.messageNavigationTargets + (resolvedChannel.id to message.id)
                } else {
                    state.messageNavigationTargets
                },
            )
        }
        settingsStore.channelLogins = mutableState.value.channels.map(ChatChannel::login)
        if (navigate) settingsStore.selectedChannelLogin = resolvedChannel.login
        normalizeChannelPreferences(
            channels = mutableState.value.channels,
            selectedChannelId = if (navigate) resolvedChannel.id else mutableState.value.selectedChannelId,
        )
        if (navigate) {
            selectChannel(resolvedChannel.id)
            if (wasNewChannel) {
                refreshChatAssetsForCurrentSession()
                val currentSession = mutableState.value.session
                val accessToken = credentials?.accessToken
                if (currentSession != null && accessToken != null) {
                    connectEventSub(currentSession, accessToken)
                } else {
                    connectAnonymousChat(mutableState.value.channels)
                }
            }
        }
        return true
    }

    private fun keepSearchTargetInWindow(messages: List<ChatMessage>, targetId: String): List<ChatMessage> {
        if (messages.size <= MAX_MESSAGES_PER_CHANNEL) return messages
        val targetIndex = messages.indexOfFirst { it.id == targetId }
        if (targetIndex < 0) return messages.takeLast(MAX_MESSAGES_PER_CHANNEL)
        val contextBudget = MAX_MESSAGES_PER_CHANNEL / 2
        val start = (targetIndex - contextBudget).coerceAtLeast(0)
        val end = (start + MAX_MESSAGES_PER_CHANNEL).coerceAtMost(messages.size)
        val adjustedStart = (end - MAX_MESSAGES_PER_CHANNEL).coerceAtLeast(0)
        return messages.subList(adjustedStart, end)
    }

    fun navigateToMessage(channelId: String, messageId: String) {
        if (messageId.isBlank()) return
        selectChannel(channelId)
        mutableState.update { state ->
            state.copy(messageNavigationTargets = state.messageNavigationTargets + (channelId to messageId))
        }
    }

    fun replyFromUserCard() {
        val data = mutableState.value.userCard.data ?: return
        val targetId = data.sourceMessageId
            ?: data.recentMessages.lastOrNull()?.id
            ?: return showError("Нет сообщения, на которое можно ответить")
        selectChannel(data.channelId)
        mutableState.update { state ->
            state.copy(
                userCard = UserCardUiState(),
                messageNavigationTargets = state.messageNavigationTargets + (data.channelId to targetId),
                replyComposerTargets = state.replyComposerTargets + (data.channelId to targetId),
            )
        }
    }

    fun mentionFromUserCard() {
        val data = mutableState.value.userCard.data ?: return
        val current = mutableState.value.draftsByChannel[data.channelId].orEmpty()
        val mention = "@${data.user.login}"
        val updated = buildString {
            append(current.trimEnd())
            if (isNotEmpty()) append(' ')
            append(mention)
            append(' ')
        }
        updateDraft(data.channelId, updated)
        selectChannel(data.channelId)
        closeUserCard()
    }

    fun consumeReplyComposerTarget(channelId: String, messageId: String) {
        mutableState.update { state ->
            if (state.replyComposerTargets[channelId] != messageId) state else state.copy(
                replyComposerTargets = state.replyComposerTargets - channelId,
            )
        }
    }

    fun consumeMessageNavigation(channelId: String, messageId: String) {
        mutableState.update { state ->
            if (state.messageNavigationTargets[channelId] != messageId) state else state.copy(
                messageNavigationTargets = state.messageNavigationTargets - channelId,
            )
        }
    }

    fun selectWorkspace(workspaceId: String) = updateWorkspaceLayout { layout ->
        if (layout.workspaces.none { it.id == workspaceId }) layout else layout.copy(activeWorkspaceId = workspaceId)
    }

    fun addWorkspace() = updateWorkspaceLayout { layout ->
        val workspace = Workspace.default(mutableState.value.selectedChannelId).copy(
            name = "Workspace ${layout.workspaces.size + 1}",
        )
        layout.copy(
            workspaces = layout.workspaces + workspace,
            activeWorkspaceId = workspace.id,
        )
    }

    fun removeWorkspace(workspaceId: String) = updateWorkspaceLayout { layout ->
        if (layout.workspaces.size <= 1) return@updateWorkspaceLayout layout
        val remaining = layout.workspaces.filterNot { it.id == workspaceId }
        layout.copy(
            workspaces = remaining,
            activeWorkspaceId = if (layout.activeWorkspaceId == workspaceId) remaining.firstOrNull()?.id else layout.activeWorkspaceId,
        )
    }

    fun selectWorkspaceTab(tabId: String) = updateWorkspaceLayout { layout ->
        layout.mapActiveWorkspace { workspace ->
            if (workspace.tabs.none { it.id == tabId }) workspace else workspace.copy(activeTabId = tabId)
        }
    }

    fun addWorkspaceTab() = updateWorkspaceLayout { layout ->
        layout.mapActiveWorkspace { workspace ->
            val tab = WorkspaceTab.default(mutableState.value.selectedChannelId).copy(
                title = "Вкладка ${workspace.tabs.size + 1}",
            )
            workspace.copy(tabs = workspace.tabs + tab, activeTabId = tab.id)
        }
    }

    fun removeWorkspaceTab(tabId: String) = updateWorkspaceLayout { layout ->
        layout.mapActiveWorkspace { workspace ->
            if (workspace.tabs.size <= 1) return@mapActiveWorkspace workspace
            val remaining = workspace.tabs.filterNot { it.id == tabId }
            workspace.copy(
                tabs = remaining,
                activeTabId = if (workspace.activeTabId == tabId) remaining.firstOrNull()?.id else workspace.activeTabId,
            )
        }
    }

    fun addChatSplit(channelId: String? = null) = updateWorkspaceLayout { layout ->
        layout.mapActiveTab { tab ->
            if (tab.splits.size >= MAX_SPLITS_PER_TAB) return@mapActiveTab tab
            val split = ChatSplit(
                id = newLayoutId("split"),
                channelId = channelId ?: mutableState.value.selectedChannelId ?: mutableState.value.channels.firstOrNull()?.id,
            )
            tab.copy(splits = tab.splits + split, activeSplitId = split.id)
        }
    }

    fun removeChatSplit(splitId: String) = updateWorkspaceLayout { layout ->
        layout.mapActiveTab { tab ->
            if (tab.splits.size <= 1) return@mapActiveTab tab
            val remaining = tab.splits.filterNot { it.id == splitId }
            tab.copy(
                splits = remaining,
                activeSplitId = if (tab.activeSplitId == splitId) remaining.firstOrNull()?.id else tab.activeSplitId,
            )
        }
    }

    fun focusChatSplit(splitId: String) = updateWorkspaceLayout { layout ->
        layout.mapActiveTab { tab ->
            if (tab.splits.none { it.id == splitId }) tab else tab.copy(activeSplitId = splitId)
        }
    }

    fun setChatSplitChannel(splitId: String, channelId: String) {
        if (mutableState.value.channels.none { it.id == channelId }) return
        updateWorkspaceLayout { layout ->
            layout.mapActiveTab { tab ->
                tab.copy(
                    splits = tab.splits.map { split ->
                        if (split.id == splitId) split.withChannelId(channelId) else split
                    },
                    activeSplitId = splitId,
                )
            }
        }
        selectChannel(channelId)
    }

    fun setChatSplitFilter(splitId: String, query: String) = updateWorkspaceLayout { layout ->
        layout.mapActiveTab { tab ->
            tab.copy(splits = tab.splits.map { split ->
                if (split.id == splitId) split.withFilterQuery(query) else split
            })
        }
    }

    fun setSplitPrimaryFraction(fraction: Float) = updateWorkspaceLayout { layout ->
        layout.mapActiveTab { tab -> tab.copy(primaryFraction = fraction.coerceIn(0.25f, 0.75f)) }
    }

    fun saveScrollPosition(
        channelId: String,
        anchorMessageId: String?,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
        isAtBottom: Boolean,
    ) {
        if (channelId.isBlank()) return
        val position = ChatScrollPosition(
            channelId = channelId,
            anchorMessageId = anchorMessageId?.takeIf(String::isNotBlank),
            firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
            isAtBottom = isAtBottom,
        )
        val previous = liveScrollPositions[channelId]
            ?: mutableState.value.scrollPositionsByChannel[channelId]
        if (previous == position) return
        liveScrollPositions[channelId] = position
        scrollSaveJobs.remove(channelId)?.cancel()
        scrollSaveJobs[channelId] = scope.launch {
            delay(SCROLL_SAVE_DEBOUNCE_MILLIS)
            val latest = liveScrollPositions[channelId] ?: return@launch
            mutableState.update { state ->
                if (state.scrollPositionsByChannel[channelId] == latest) state else state.copy(
                    scrollPositionsByChannel = state.scrollPositionsByChannel + (channelId to latest),
                )
            }
            runCatching { historyRepository.saveScrollPosition(latest) }
            scrollSaveJobs.remove(channelId)
        }
    }

    fun onAppBackgrounded() {
        val positions = (mutableState.value.scrollPositionsByChannel + liveScrollPositions).values.toList()
        scrollSaveJobs.values.forEach { job -> job.cancel() }
        scrollSaveJobs.clear()
        draftSaveJob?.cancel()
        draftSaveJob = null
        channelOrderSaveJob?.cancel()
        channelOrderSaveJob = null
        settingsStore.draftsByChannel = mutableState.value.draftsByChannel
        settingsStore.channelLogins = mutableState.value.channels.map(ChatChannel::login)
        scope.launch {
            positions.forEach { position ->
                runCatching { historyRepository.saveScrollPosition(position) }
            }
            runCatching { historyRepository.compactIfNeeded() }
        }
    }

    fun loadOlderHistory(channelId: String) {
        val initial = mutableState.value
        val paging = initial.historyPagingByChannel[channelId] ?: HistoryPagingState()
        if (!initial.localHistoryEnabled || paging.isLoading || paging.endReached) return
        val currentMessages = initial.messagesByChannel[channelId].orEmpty()
        val oldest = currentMessages.firstOrNull() ?: return
        val remaining = (initial.localHistoryLimit - currentMessages.size).coerceAtLeast(0)
        if (remaining == 0) {
            mutableState.update { state ->
                state.copy(
                    historyPagingByChannel = state.historyPagingByChannel + (
                        channelId to paging.copy(endReached = true, loadedCount = currentMessages.size)
                    ),
                )
            }
            return
        }

        mutableState.update { state ->
            val latest = state.historyPagingByChannel[channelId] ?: HistoryPagingState()
            state.copy(
                historyPagingByChannel = state.historyPagingByChannel + (
                    channelId to latest.copy(isLoading = true, loadedCount = currentMessages.size)
                ),
            )
        }
        scope.launch {
            val requested = minOf(HISTORY_PAGE_SIZE, remaining)
            runCatching {
                historyRepository.loadOlderMessages(
                    channelId = channelId,
                    beforeTimestampMillis = oldest.timestampMillis,
                    beforeMessageId = oldest.id,
                    limit = requested,
                )
            }.onSuccess { older ->
                ChatMessageTextPreparation.warm(older)
                mutableState.update { state ->
                    val existing = state.messagesByChannel[channelId].orEmpty()
                    val seen = HashSet<String>(older.size + existing.size)
                    val merged = (older + existing)
                        .filter { message -> seen.add(message.id) }
                        .sortedWith(compareBy(ChatMessage::timestampMillis, ChatMessage::id))
                        .takeLast(minOf(MAX_MESSAGES_PER_CHANNEL, state.localHistoryLimit))
                    val endReached = older.size < requested || merged.size >= state.localHistoryLimit
                    state.copy(
                        messagesByChannel = state.messagesByChannel + (channelId to merged),
                        restoredHistoryMessageCount = state.messagesByChannel
                            .mapValues { (id, messages) -> if (id == channelId) merged.size else messages.size }
                            .values
                            .sum(),
                        historyPagingByChannel = state.historyPagingByChannel + (
                            channelId to HistoryPagingState(
                                isLoading = false,
                                endReached = endReached,
                                loadedCount = merged.size,
                            )
                        ),
                        historyErrorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableState.update { state ->
                    state.copy(
                        historyPagingByChannel = state.historyPagingByChannel + (
                            channelId to (state.historyPagingByChannel[channelId] ?: HistoryPagingState())
                                .copy(isLoading = false)
                        ),
                        historyErrorMessage = "Не удалось загрузить старые сообщения: ${error.userMessage()}",
                    )
                }
            }
        }
    }

    fun setLocalHistoryEnabled(enabled: Boolean) {
        settingsStore.localHistoryEnabled = enabled
        mutableState.update { it.copy(localHistoryEnabled = enabled) }
        if (enabled) reloadLocalHistory() else showNotice("Новые сообщения больше не сохраняются локально")
    }

    fun setLocalHistoryLimit(limit: Int) {
        val safeLimit = limit.coerceIn(100, 5_000)
        settingsStore.localHistoryLimit = safeLimit
        mutableState.update { state ->
            state.copy(
                localHistoryLimit = safeLimit,
                messagesByChannel = state.messagesByChannel.mapValues { (_, messages) ->
                    messages.takeLast(safeLimit)
                },
                historyPagingByChannel = state.historyPagingByChannel.mapValues { (channelId, paging) ->
                    paging.copy(
                        isLoading = false,
                        endReached = state.messagesByChannel[channelId].orEmpty().size >= safeLimit,
                        loadedCount = minOf(state.messagesByChannel[channelId].orEmpty().size, safeLimit),
                    )
                },
            )
        }
        scope.launch {
            runCatching {
                historyRepository.trimAll(
                    channelIds = mutableState.value.channels.map(ChatChannel::id),
                    limitPerChannel = safeLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
            }
        }
    }

    fun setLocalHistoryRetentionDays(days: Int) {
        val safeDays = days.coerceIn(0, 365)
        settingsStore.localHistoryRetentionDays = safeDays
        mutableState.update { it.copy(localHistoryRetentionDays = safeDays) }
        scope.launch {
            runCatching {
                historyRepository.trimAll(
                    channelIds = mutableState.value.channels.map(ChatChannel::id),
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = safeDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
            }
        }
    }

    fun setLocalHistoryMaxSizeMb(sizeMb: Int) {
        val safeSize = sizeMb.coerceIn(0, ChatHistoryRepository.MAX_DATABASE_SIZE_MB)
        settingsStore.localHistoryMaxSizeMb = safeSize
        mutableState.update { it.copy(localHistoryMaxSizeMb = safeSize) }
        scope.launch {
            runCatching {
                historyRepository.trimAll(
                    channelIds = mutableState.value.channels.map(ChatChannel::id),
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = safeSize,
                )
            }.onFailure { error ->
                mutableState.update { state ->
                    state.copy(historyErrorMessage = "Не удалось применить лимит базы: ${error.userMessage()}")
                }
            }
        }
    }

    fun clearLocalHistory() {
        scope.launch {
            runCatching { historyRepository.clearAll() }
                .onSuccess {
                    mutableState.update { state ->
                        state.copy(
                            messagesByChannel = state.channels.associate { channel ->
                                channel.id to emptyList()
                            },
                            scrollPositionsByChannel = emptyMap(),
                            restoredHistoryMessageCount = 0,
                            historyErrorMessage = null,
                            attentionEntries = emptyList(),
                            mentionUnreadCount = 0,
                            messageDecorationsById = emptyMap(),
                            channelAttention = emptyMap(),
                            historyPagingByChannel = emptyMap(),
                        )
                    }
                    ChatMessageTextPreparation.clear()
                    showNotice("Локальная история очищена")
                }
                .onFailure { showError("Не удалось очистить историю: ${it.userMessage()}") }
        }
    }

    private fun reloadLocalHistory() {
        val current = mutableState.value
        if (current.channels.isEmpty()) return
        mutableState.update { it.copy(isHistoryLoading = true) }
        scope.launch {
            var historyError: String? = null
            val loaded = runCatching {
                historyRepository.loadRecentMessages(
                    channelIds = current.channels.map(ChatChannel::id),
                    enabled = true,
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
            }.getOrElse { error ->
                historyError = "Не удалось загрузить историю: ${error.userMessage()}"
                emptyMap()
            }
            ChatMessageTextPreparation.warm(loaded.values.flatten())
            mutableState.update { state ->
                val restored = state.channels.associate { channel ->
                    channel.id to loaded[channel.id].orEmpty().takeLast(MAX_MESSAGES_PER_CHANNEL)
                }
                val next = state.copy(
                    messagesByChannel = restored,
                    historyPagingByChannel = restored.mapValues { (_, messages) ->
                        HistoryPagingState(
                            isLoading = false,
                            endReached = messages.size >= state.localHistoryLimit,
                            loadedCount = messages.size,
                        )
                    },
                    isHistoryLoading = false,
                    restoredHistoryMessageCount = loaded.values.sumOf { messages -> messages.size },
                    historyErrorMessage = historyError,
                )
                next.copy(messagesByChannel = reprocessThirdPartyEmotes(next))
            }
            rebuildMessageRuleEvaluation()
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        settingsStore.appLanguage = language
        mutableState.update { it.copy(appLanguage = language) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsStore.themeMode = mode
        mutableState.update { it.copy(themeMode = mode) }
    }

    fun setFontScalePercent(percent: Int) {
        val safePercent = percent.coerceIn(80, 150)
        settingsStore.fontScalePercent = safePercent
        mutableState.update { it.copy(fontScalePercent = safePercent) }
    }

    fun setMessageDensity(density: MessageDensity) {
        settingsStore.messageDensity = density
        mutableState.update { it.copy(messageDensity = density) }
    }

    fun setChatNameStyle(style: ChatNameStyle) {
        settingsStore.chatNameStyle = style
        mutableState.update { it.copy(chatNameStyle = style) }
    }

    fun setWrapMessageLines(enabled: Boolean) {
        settingsStore.wrapMessageLines = enabled
        mutableState.update { it.copy(wrapMessageLines = enabled) }
    }

    fun setMentionColorArgb(argb: Long) {
        val safe = argb and 0xFFFFFFFFL
        settingsStore.mentionColorArgb = safe
        mutableState.update { it.copy(mentionColorArgb = safe) }
    }

    fun setAutoScrollEnabled(enabled: Boolean) {
        settingsStore.autoScrollEnabled = enabled
        mutableState.update { it.copy(autoScrollEnabled = enabled) }
    }

    fun setShowAvatars(enabled: Boolean) {
        settingsStore.showAvatars = enabled
        val snapshot = mutableState.value
        mutableState.update { it.copy(showAvatars = enabled) }
        if (enabled && snapshot.isAuthenticated) {
            snapshot.visibleChannelIds
                .flatMap { channelId -> snapshot.messagesByChannel[channelId].orEmpty().takeLast(100) }
                .map(ChatMessage::userId)
                .filter(String::isNotBlank)
                .distinct()
                .forEach(::queueUserProfileHydration)
        }
    }

    fun setShowBadges(enabled: Boolean) {
        settingsStore.showBadges = enabled
        mutableState.update {
            it.copy(
                showBadges = enabled,
                frankerFaceZBadgesByUserId = if (enabled) it.frankerFaceZBadgesByUserId else emptyMap(),
                frankerFaceZChannelBadgesByChannel = if (enabled) {
                    it.frankerFaceZChannelBadgesByChannel
                } else {
                    emptyMap()
                },
            )
        }
        if (enabled) refreshChatAssetsForCurrentSession()
    }

    fun setShowTimestamps(enabled: Boolean) {
        settingsStore.showTimestamps = enabled
        mutableState.update { it.copy(showTimestamps = enabled) }
    }

    fun setShowDeletedMessageContent(enabled: Boolean) {
        settingsStore.showDeletedMessageContent = enabled
        mutableState.update { it.copy(showDeletedMessageContent = enabled) }
    }

    fun setShowSystemMessages(enabled: Boolean) {
        settingsStore.showSystemMessages = enabled
        mutableState.update { it.copy(showSystemMessages = enabled) }
    }

    fun setAnimateEmotes(enabled: Boolean) {
        settingsStore.animateEmotes = enabled
        mutableState.update { it.copy(animateEmotes = enabled) }
    }

    fun setEmoteScalePercent(percent: Int) {
        val safePercent = percent.coerceIn(75, 200)
        settingsStore.emoteScalePercent = safePercent
        mutableState.update { it.copy(emoteScalePercent = safePercent) }
    }

    fun setBetterTtvEnabled(enabled: Boolean) {
        settingsStore.betterTtvEnabled = enabled
        mutableState.update { state ->
            val next = state.copy(
                betterTtvEnabled = enabled,
                betterTtvEmotesByChannel = if (enabled) state.betterTtvEmotesByChannel else emptyMap(),
            )
            next.copy(messagesByChannel = reprocessThirdPartyEmotes(next))
        }
        refreshChatAssetsForCurrentSession()
    }

    fun setFrankerFaceZEnabled(enabled: Boolean) {
        settingsStore.frankerFaceZEnabled = enabled
        mutableState.update { state ->
            val next = state.copy(
                frankerFaceZEnabled = enabled,
                frankerFaceZEmotesByChannel = if (enabled) state.frankerFaceZEmotesByChannel else emptyMap(),
            )
            next.copy(messagesByChannel = reprocessThirdPartyEmotes(next))
        }
        refreshChatAssetsForCurrentSession()
    }

    fun setSevenTvEnabled(enabled: Boolean) {
        settingsStore.sevenTvEnabled = enabled
        mutableState.update { state ->
            val next = state.copy(
                sevenTvEnabled = enabled,
                sevenTvEmotesByChannel = if (enabled) state.sevenTvEmotesByChannel else emptyMap(),
            )
            next.copy(messagesByChannel = reprocessThirdPartyEmotes(next))
        }
        refreshChatAssetsForCurrentSession()
    }

    fun refreshEmoteCatalogs() {
        if (mutableState.value.isAuthenticated) {
            scope.launch {
                emoteRepository.invalidateTwitchCache()
                refreshChatAssetsForCurrentSession()
            }
        } else {
            refreshChatAssetsForCurrentSession()
        }
    }

    fun clearImageCache() {
        if (mutableState.value.isImageCacheClearing) return
        mutableState.update { it.copy(isImageCacheClearing = true, imageCacheStatusMessage = null) }
        scope.launch {
            runCatching { imageCacheManager.clear() }
                .onSuccess { result ->
                    mutableState.update {
                        it.copy(
                            isImageCacheClearing = false,
                            imageCacheStatusMessage = "Очищено ${formatBytes(result.totalBytes)}",
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isImageCacheClearing = false,
                            imageCacheStatusMessage = "Не удалось очистить кэш: ${error.userMessage()}",
                        )
                    }
                }
        }
    }

    private fun refreshChatAssetsForCurrentSession() {
        val session = mutableState.value.session
        val token = credentials?.accessToken
        if (session != null && token != null) {
            refreshTwitchChatAssets(session, token)
        } else {
            refreshAnonymousChatAssets()
        }
    }

    fun closeUserCard() {
        mutableState.update { it.copy(userCard = UserCardUiState()) }
    }

    fun openUserCard(message: ChatMessage) {
        val channel = mutableState.value.channels.firstOrNull { it.id == message.channelId } ?: return
        val session = mutableState.value.session
        val accessToken = credentials?.accessToken
        val recent = mutableState.value.messagesByChannel[message.channelId].orEmpty()
            .filter { it.userId == message.userId }
            .takeLast(50)
        val latestBadgedMessage = (recent + message)
            .lastOrNull { it.badges.isNotEmpty() }
        val badges = latestBadgedMessage?.badges.orEmpty()
        val subscriberBadge = badges.firstOrNull { it.setId == "subscriber" || it.setId == "founder" }
        val cached = mutableState.value.userProfilesById[message.userId]
        val fallbackUser = cached ?: TwitchUser(
            id = message.userId,
            login = message.userLogin,
            displayName = message.userDisplayName,
            profileImageUrl = message.author.profileImageUrl,
        )
        val targetRole = when {
            message.userId == channel.id -> ChannelUserRole.BROADCASTER
            badges.any { it.setId == "moderator" } -> ChannelUserRole.MODERATOR
            badges.any { it.setId == "vip" } -> ChannelUserRole.VIP
            subscriberBadge != null -> ChannelUserRole.SUBSCRIBER
            else -> ChannelUserRole.VIEWER
        }
        val canModerateChannel = message.channelId in mutableState.value.moderatedChannelIds
        val canModerateTarget = canModerateChannel &&
            message.userId != session?.userId &&
            message.userId != channel.id
        val canLoadProfileFromTwitch = message.userId.isNotBlank() &&
            !message.userId.startsWith("anonymous:") &&
            !message.userId.startsWith("twitch:")
        val canLoadPublicRelationship = message.userLogin.isNotBlank() && channel.login.isNotBlank()
        val sourceMessageId = message.id
            .takeIf { id -> id.isNotBlank() && !id.startsWith("usercard-") }
            ?: recent.lastOrNull()?.id
        val badgeMonths = subscriberBadge?.info?.toIntOrNull()
            ?: subscriberBadge?.id?.toIntOrNull()

        mutableState.update {
            it.copy(
                userCard = UserCardUiState(
                    isLoading = canLoadPublicRelationship ||
                        (session != null && accessToken != null && canLoadProfileFromTwitch),
                    data = UserCardData(
                        channelId = message.channelId,
                        user = fallbackUser,
                        role = targetRole,
                        canModerate = canModerateTarget,
                        subscriberMonths = badgeMonths,
                        subscriberTier = subscriberBadge?.id,
                        isCurrentlySubscribed = subscriberBadge?.let { true },
                        sourceMessageId = sourceMessageId,
                        recentMessages = recent,
                    ),
                ),
            )
        }

        if (!canLoadPublicRelationship &&
            (session == null || accessToken == null || !canLoadProfileFromTwitch)
        ) {
            mutableState.update { state -> state.copy(userCard = state.userCard.copy(isLoading = false)) }
            return
        }

        scope.launch {
            val relationshipDeferred = async {
                if (!canLoadPublicRelationship) null else runCatching {
                    api.getPublicChannelRelationship(message.userLogin, channel.login)
                }.getOrNull()
            }
            val profileDeferred = async {
                if (session == null || accessToken == null || !canLoadProfileFromTwitch) {
                    null
                } else {
                    runCatching {
                        val user = api.getUserById(session.clientId, accessToken, message.userId)
                        val colors = runCatching {
                            api.getChatColors(session.clientId, accessToken, listOf(user.id))
                        }.getOrDefault(emptyMap())
                        historyRepository.saveUsers(listOf(user), colors)
                        user to colors
                    }
                }
            }

            val relationship = relationshipDeferred.await()
            val profileResult = profileDeferred.await()
            val profile = profileResult?.getOrNull()
            val profileError = profileResult?.exceptionOrNull()
            mutableState.update { state ->
                val current = state.userCard.data
                val user = profile?.first
                val colors = profile?.second.orEmpty()
                val profileState = if (user == null) state else state.copy(
                    userProfilesById = mergeUserProfiles(state.userProfilesById, mapOf(user.id to user)),
                    userColorsById = mergeUserColors(state.userColorsById, colors),
                )
                if (current?.channelId != message.channelId || current.user.id != message.userId) {
                    profileState
                } else {
                    profileState.copy(
                        userCard = profileState.userCard.copy(
                            isLoading = false,
                            data = current.copy(
                                user = user ?: current.user,
                                followerInfo = current.followerInfo.copy(
                                    followedAt = relationship?.followedAt ?: current.followerInfo.followedAt,
                                ),
                                subscriberMonths = relationship?.subscriberMonths ?: current.subscriberMonths,
                                subscriberTier = relationship?.subscriberTier ?: current.subscriberTier,
                                subscriptionStatusHidden = relationship?.subscriptionStatusHidden
                                    ?: current.subscriptionStatusHidden,
                                isCurrentlySubscribed = relationship?.isCurrentlySubscribed
                                    ?: current.isCurrentlySubscribed,
                            ),
                            errorMessage = profileError?.userMessage(),
                        ),
                    )
                }
            }
        }
    }

    fun openUserCardByLogin(channelId: String?, userLogin: String) {
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId }
            ?: return showError("Выбери канал")
        val normalized = userLogin.trim().removePrefix("@").lowercase()
        if (normalized.isBlank()) return showError("Укажи пользователя")
        val localMessage = mutableState.value.messagesByChannel[channel.id].orEmpty()
            .lastOrNull { it.userLogin.equals(normalized, ignoreCase = true) }
        if (localMessage != null) {
            openUserCard(localMessage)
            return
        }

        val session = mutableState.value.session ?: run {
            showError("Пользователь @$normalized не найден в загруженной ленте. Войди через Twitch для поиска.")
            return
        }
        val token = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        mutableState.update { it.copy(userCard = UserCardUiState(isLoading = true)) }
        scope.launch {
            runCatching {
                val user = api.getUserByLogin(session.clientId, token, normalized)
                val synthetic = ChatMessage(
                    id = "usercard-${user.id}",
                    channelId = channel.id,
                    channelLogin = channel.login,
                    author = ChatAuthor(
                        id = user.id,
                        login = user.login,
                        displayName = user.displayName,
                        profileImageUrl = user.profileImageUrl,
                    ),
                    text = "",
                    timestamp = Instant.now().toString(),
                )
                openUserCard(synthetic)
            }.onFailure { error ->
                mutableState.update {
                    it.copy(userCard = UserCardUiState(errorMessage = error.userMessage()))
                }
                showError(error.userMessage())
            }
        }
    }

    fun timeoutUser(
        channelId: String,
        userId: String,
        userLogin: String,
        seconds: Int,
    ) = moderateChannel(channelId) { context ->
        api.timeoutUser(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            targetUserId = userId,
            durationSeconds = seconds,
        )
        historyRepository.recordModerationAction(
            channelId = channelId,
            targetUserId = userId,
            targetUserLogin = userLogin,
            action = "TIMEOUT",
            durationSeconds = seconds,
        )
        showNotice("@$userLogin: timeout ${formatDuration(seconds)}")
    }

    fun banUser(channelId: String, userId: String, userLogin: String) = moderateChannel(channelId) { context ->
        api.banUser(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            targetUserId = userId,
        )
        historyRepository.recordModerationAction(
            channelId = channelId,
            targetUserId = userId,
            targetUserLogin = userLogin,
            action = "BAN",
        )
        showNotice("@$userLogin заблокирован в чате")
    }

    fun unbanUser(channelId: String, userId: String, userLogin: String) = moderateChannel(channelId) { context ->
        api.unbanUser(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            targetUserId = userId,
        )
        historyRepository.recordModerationAction(
            channelId = channelId,
            targetUserId = userId,
            targetUserLogin = userLogin,
            action = "UNBAN",
        )
        showNotice("Ban/timeout снят с @$userLogin")
    }

    fun warnUser(
        channelId: String,
        userId: String,
        userLogin: String,
        reason: String,
    ) = moderateChannel(channelId) { context ->
        val normalizedReason = reason.trim()
        require(normalizedReason.isNotEmpty()) { "Укажи причину предупреждения" }
        api.warnUser(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            targetUserId = userId,
            reason = normalizedReason,
        )
        historyRepository.recordModerationAction(
            channelId = channelId,
            targetUserId = userId,
            targetUserLogin = userLogin,
            action = "WARN",
            reason = normalizedReason,
        )
        showNotice("@$userLogin получил предупреждение")
    }

    fun blockUser(channelId: String, userId: String, userLogin: String) {
        if (mutableState.value.session == null) return
        val session = mutableState.value.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        if (userId == session.userId) return showError("Нельзя заблокировать самого себя")
        scope.launch {
            runCatching {
                withAuthenticationRetry { context ->
                    api.blockUser(
                        clientId = context.session.clientId,
                        token = context.accessToken,
                        targetUserId = userId,
                    )
                }
                historyRepository.recordModerationAction(
                    channelId = channelId,
                    targetUserId = userId,
                    targetUserLogin = userLogin,
                    action = "BLOCK",
                )
                showNotice("@$userLogin заблокирован для твоего аккаунта")
            }.onFailure { showError(it.userMessage()) }
        }
    }

    fun openModeration(channelId: String) {
        mutableState.update { state ->
            state.copy(
                requestedMainSection = MainSection.MODERATION,
                moderation = state.moderation.copy(selectedChannelId = channelId),
            )
        }
        refreshModerationDashboard(channelId)
    }

    fun openMentions() {
        mutableState.update { state -> state.copy(requestedMainSection = MainSection.MENTIONS) }
    }

    fun exportSettingsBackup(): String = SettingsBackupCodec.encode(
        SettingsBackupCodec.capture(settingsStore, BuildConfig.VERSION_NAME),
    )

    fun reportBackupExported(fileName: String?) {
        mutableState.update {
            it.copy(backupStatusMessage = "Резервная копия сохранена${fileName?.let { name -> ": $name" }.orEmpty()}")
        }
    }

    fun reportBackupError(message: String) {
        mutableState.update { it.copy(backupStatusMessage = message) }
    }

    fun clearBackupStatus() {
        mutableState.update { it.copy(backupStatusMessage = null) }
    }

    fun importSettingsBackup(raw: String) {
        val document = runCatching { SettingsBackupCodec.decode(raw) }
            .getOrElse { error ->
                reportBackupError("Импорт отклонён: ${error.userMessage()}")
                return
            }
        applySettingsDocument(
            document = document,
            createPreImportBackup = true,
            statusMessage = "Настройки импортированы",
        )
        if (settingsStore.settingsSyncEnabled) scheduleSettingsSync()
    }

    fun restorePreImportBackup() {
        val raw = settingsStore.lastImportBackupJson
        if (raw.isNullOrBlank()) {
            reportBackupError("Нет резервной копии, созданной перед импортом")
            return
        }
        val document = runCatching { SettingsBackupCodec.decode(raw) }
            .getOrElse { error ->
                reportBackupError("Резервная копия повреждена: ${error.userMessage()}")
                return
            }
        applySettingsDocument(
            document = document,
            createPreImportBackup = false,
            statusMessage = "Состояние до импорта восстановлено",
        )
        if (settingsStore.settingsSyncEnabled) scheduleSettingsSync()
    }

    fun setSettingsSyncEnabled(enabled: Boolean) {
        if (enabled && mutableState.value.session == null) {
            mutableState.update {
                it.copy(
                    settingsSyncStatus = SettingsSyncStatus.ERROR,
                    settingsSyncErrorMessage = "Для синхронизации нужен вход через сервер Ferventio",
                )
            }
            return
        }
        settingsStore.withSyncNotificationsSuppressed {
            settingsStore.settingsSyncEnabled = enabled
        }
        settingsSyncJob?.cancel()
        mutableState.update {
            it.copy(
                settingsSyncEnabled = enabled,
                settingsSyncStatus = if (enabled) SettingsSyncStatus.IDLE else SettingsSyncStatus.DISABLED,
                settingsSyncErrorMessage = null,
                settingsSyncConflict = null,
            )
        }
        if (enabled) scheduleSettingsSync(immediate = true)
    }

    fun synchronizeSettings() {
        scheduleSettingsSync(immediate = true)
    }

    fun useServerSettings() {
        val conflict = mutableState.value.settingsSyncConflict ?: return
        val document = runCatching { SettingsBackupCodec.decode(conflict.serverPayload) }
            .getOrElse { error ->
                mutableState.update {
                    it.copy(
                        settingsSyncStatus = SettingsSyncStatus.ERROR,
                        settingsSyncErrorMessage = "Серверная копия повреждена: ${error.userMessage()}",
                    )
                }
                return
            }
        applySettingsDocument(
            document = document,
            createPreImportBackup = true,
            statusMessage = "Применена серверная ревизия ${conflict.serverRevision}",
            syncedRevision = conflict.serverRevision,
            syncedContentHash = document.contentHash,
        )
        mutableState.update {
            it.copy(
                settingsSyncConflict = null,
                settingsSyncStatus = SettingsSyncStatus.IDLE,
                settingsSyncErrorMessage = null,
            )
        }
        loadSettingsSyncHistory()
    }

    fun overwriteServerSettings() {
        settingsSyncJob?.cancel()
        settingsSyncJob = scope.launch { performSettingsSync(forceUpload = true) }
    }

    fun loadSettingsSyncHistory() {
        val credential = backendCredential ?: tokenStore.load() ?: return
        if (mutableState.value.session == null) return
        scope.launch {
            runCatching {
                backend.getSettingsHistory(
                    serverUrl = credential.serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    sessionToken = credential.token,
                )
            }.onSuccess { history ->
                mutableState.update { state ->
                    state.copy(
                        settingsSyncHistory = history.map { item ->
                            SettingsSyncHistoryEntry(
                                revision = item.revision,
                                updatedAt = item.updatedAt,
                                updatedByInstallationId = item.updatedByInstallationId,
                                appVersion = item.appVersion,
                            )
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update { state ->
                    state.copy(settingsSyncErrorMessage = "История синхронизации: ${error.userMessage()}")
                }
            }
        }
    }

    fun restoreSettingsSyncRevision(revision: Long) {
        val credential = backendCredential ?: tokenStore.load()
        if (credential == null || mutableState.value.session == null) {
            mutableState.update {
                it.copy(
                    settingsSyncStatus = SettingsSyncStatus.ERROR,
                    settingsSyncErrorMessage = "Для восстановления нужен вход через сервер Ferventio",
                )
            }
            return
        }
        settingsSyncJob?.cancel()
        settingsSyncJob = scope.launch {
            mutableState.update {
                it.copy(settingsSyncStatus = SettingsSyncStatus.SYNCING, settingsSyncErrorMessage = null)
            }
            runCatching {
                backend.restoreSettingsRevision(
                    serverUrl = credential.serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    sessionToken = credential.token,
                    revision = revision,
                )
            }.onSuccess { snapshot ->
                applyRemoteSettingsSnapshot(snapshot, "Восстановлена серверная ревизия $revision")
                loadSettingsSyncHistory()
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        settingsSyncStatus = SettingsSyncStatus.ERROR,
                        settingsSyncErrorMessage = "Не удалось восстановить ревизию: ${error.userMessage()}",
                    )
                }
            }
        }
    }

    private fun scheduleSettingsSync(immediate: Boolean = false) {
        if (!settingsStore.settingsSyncEnabled || mutableState.value.session == null || settingsApplyInProgress) return
        settingsSyncJob?.cancel()
        settingsSyncJob = scope.launch {
            if (!immediate) delay(SETTINGS_SYNC_DEBOUNCE_MILLIS)
            performSettingsSync(forceUpload = false)
        }
    }

    private suspend fun performSettingsSync(forceUpload: Boolean) {
        settingsSyncMutex.withLock {
            val credential = backendCredential ?: tokenStore.load()
            if (credential == null || mutableState.value.session == null) {
                mutableState.update {
                    it.copy(
                        settingsSyncStatus = SettingsSyncStatus.ERROR,
                        settingsSyncErrorMessage = "Для синхронизации нужен вход через сервер Ferventio",
                    )
                }
                return
            }
            mutableState.update {
                it.copy(
                    settingsSyncStatus = SettingsSyncStatus.SYNCING,
                    settingsSyncErrorMessage = null,
                )
            }
            val localDocument = SettingsBackupCodec.capture(settingsStore, BuildConfig.VERSION_NAME)
            val localPayload = SettingsBackupCodec.encode(localDocument, pretty = false)
            val localHash = localDocument.contentHash
            val localRevision = settingsStore.settingsSyncRevision
            val lastSyncedHash = settingsStore.settingsSyncLastContentHash
            try {
                val remote = backend.getSettingsSnapshot(
                    serverUrl = credential.serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    sessionToken = credential.token,
                )
                if (forceUpload) {
                    uploadSettingsSnapshot(
                        credential = credential,
                        baseRevision = remote?.revision ?: 0L,
                        payload = localPayload,
                        force = true,
                    )
                    return
                }
                if (remote == null) {
                    uploadSettingsSnapshot(credential, 0L, localPayload, force = false)
                    return
                }
                if (remote.contentHash == localHash) {
                    rememberSettingsSyncSnapshot(remote)
                    return
                }
                val localChanged = lastSyncedHash == null || localHash != lastSyncedHash
                val remoteChanged = remote.revision != localRevision
                when {
                    !remoteChanged && localChanged -> uploadSettingsSnapshot(
                        credential = credential,
                        baseRevision = remote.revision,
                        payload = localPayload,
                        force = false,
                    )
                    remoteChanged && !localChanged -> applyRemoteSettingsSnapshot(
                        remote,
                        "Настройки обновлены с другого устройства",
                    )
                    !remoteChanged && !localChanged -> rememberSettingsSyncSnapshot(remote)
                    else -> mutableState.update {
                        it.copy(
                            settingsSyncStatus = SettingsSyncStatus.CONFLICT,
                            settingsSyncConflict = SettingsSyncConflict(
                                serverRevision = remote.revision,
                                serverUpdatedAt = remote.updatedAt,
                                serverUpdatedByInstallationId = remote.updatedByInstallationId,
                                serverPayload = remote.payload,
                            ),
                            settingsSyncErrorMessage = "Настройки изменены и на этом, и на другом устройстве",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        settingsSyncStatus = SettingsSyncStatus.ERROR,
                        settingsSyncErrorMessage = "Синхронизация: ${error.userMessage()}",
                    )
                }
            }
        }
    }

    private suspend fun uploadSettingsSnapshot(
        credential: BackendSessionCredential,
        baseRevision: Long,
        payload: String,
        force: Boolean,
    ) {
        when (
            val result = backend.putSettingsSnapshot(
                serverUrl = credential.serverUrl,
                installationId = settingsStore.installationId,
                deviceSecret = settingsStore.installationSecret,
                sessionToken = credential.token,
                baseRevision = baseRevision,
                force = force,
                payload = payload,
            )
        ) {
            is BackendSettingsPutResult.Success -> {
                rememberSettingsSyncSnapshot(result.snapshot)
                loadSettingsSyncHistory()
            }
            is BackendSettingsPutResult.Conflict -> mutableState.update {
                it.copy(
                    settingsSyncStatus = SettingsSyncStatus.CONFLICT,
                    settingsSyncConflict = SettingsSyncConflict(
                        serverRevision = result.snapshot.revision,
                        serverUpdatedAt = result.snapshot.updatedAt,
                        serverUpdatedByInstallationId = result.snapshot.updatedByInstallationId,
                        serverPayload = result.snapshot.payload,
                    ),
                    settingsSyncErrorMessage = "Серверная ревизия изменилась во время синхронизации",
                )
            }
        }
    }

    private fun applyRemoteSettingsSnapshot(snapshot: BackendSettingsSnapshot, message: String) {
        val document = SettingsBackupCodec.decode(snapshot.payload)
        require(document.contentHash == snapshot.contentHash) { "Серверная контрольная сумма не совпадает" }
        applySettingsDocument(
            document = document,
            createPreImportBackup = true,
            statusMessage = message,
            syncedRevision = snapshot.revision,
            syncedContentHash = snapshot.contentHash,
        )
    }

    private fun rememberSettingsSyncSnapshot(snapshot: BackendSettingsSnapshot) {
        settingsStore.withSyncNotificationsSuppressed {
            settingsStore.settingsSyncRevision = snapshot.revision
            settingsStore.settingsSyncLastContentHash = snapshot.contentHash
            settingsStore.settingsSyncLastSyncedAtMillis = System.currentTimeMillis()
        }
        mutableState.update {
            it.copy(
                settingsSyncRevision = snapshot.revision,
                settingsSyncLastSyncedAtMillis = settingsStore.settingsSyncLastSyncedAtMillis,
                settingsSyncStatus = SettingsSyncStatus.IDLE,
                settingsSyncErrorMessage = null,
                settingsSyncConflict = null,
            )
        }
    }

    private fun applySettingsDocument(
        document: SettingsBackupDocument,
        createPreImportBackup: Boolean,
        statusMessage: String,
        syncedRevision: Long? = null,
        syncedContentHash: String? = null,
    ) {
        val result = runCatching {
            settingsApplyInProgress = true
            SettingsBackupCodec.apply(
                store = settingsStore,
                document = document,
                currentAppVersion = BuildConfig.VERSION_NAME,
                createPreImportBackup = createPreImportBackup,
            )
        }.also {
            settingsApplyInProgress = false
        }.getOrElse { error ->
            reportBackupError("Не удалось применить настройки: ${error.userMessage()}")
            return
        }
        if (syncedRevision != null && syncedContentHash != null) {
            settingsStore.withSyncNotificationsSuppressed {
                settingsStore.settingsSyncRevision = syncedRevision
                settingsStore.settingsSyncLastContentHash = syncedContentHash
                settingsStore.settingsSyncLastSyncedAtMillis = System.currentTimeMillis()
            }
        }
        refreshStateFromSettingsStore(
            backupStatus = "$statusMessage: ${result.summary()}",
            syncedRevision = syncedRevision,
        )
        reloadChannelsAfterSettingsChange()
    }

    private fun refreshStateFromSettingsStore(
        backupStatus: String? = null,
        syncedRevision: Long? = null,
    ) {
        messageRuleEvaluator = MessageRuleEvaluator.compile(
            highlights = settingsStore.highlightRules,
            ignores = settingsStore.ignoreRules,
            session = mutableState.value.session,
        )
        parsedThirdPartyEmotesByChannel.clear()
        mutableState.update { state ->
            state.copy(
                pinnedChannelIds = (settingsStore.pinnedChannelIds + settingsStore.favoriteChannelIds).distinct(),
                recentChannelIds = settingsStore.recentChannelIds,
                channelTabTitles = settingsStore.channelTabTitles,
                workspaceLayout = WorkspaceLayoutCodec.decodeOrDefault(settingsStore.workspaceLayoutJson),
                recentEmoteKeys = settingsStore.recentEmoteKeys,
                favoriteEmoteKeys = settingsStore.favoriteEmoteKeys,
                customCommands = CustomCommandCodec.decode(settingsStore.customCommandsJson).getOrDefault(emptyList()),
                sendOnEnter = settingsStore.sendOnEnter,
                showComposerEmoteImages = settingsStore.showComposerEmoteImages,
                userCardTimeoutPresetsSeconds = settingsStore.userCardTimeoutPresetsSeconds,
                userCardShowBanAction = settingsStore.userCardShowBanAction,
                userCardModerationActionOrder = settingsStore.userCardModerationActionOrder,
                replyNotificationsEnabled = settingsStore.replyNotificationsEnabled,
                highlightRules = settingsStore.highlightRules,
                ignoreRules = settingsStore.ignoreRules,
                savedMessageFilters = settingsStore.savedMessageFilters,
                moderation = state.moderation.copy(
                    autoModNotificationsEnabled = settingsStore.autoModNotificationsEnabled,
                ),
                localHistoryEnabled = settingsStore.localHistoryEnabled,
                localHistoryLimit = settingsStore.localHistoryLimit,
                localHistoryRetentionDays = settingsStore.localHistoryRetentionDays,
                localHistoryMaxSizeMb = settingsStore.localHistoryMaxSizeMb,
                appLanguage = settingsStore.appLanguage,
                themeMode = settingsStore.themeMode,
                fontScalePercent = settingsStore.fontScalePercent,
                messageDensity = settingsStore.messageDensity,
                chatNameStyle = settingsStore.chatNameStyle,
                wrapMessageLines = settingsStore.wrapMessageLines,
                mentionColorArgb = settingsStore.mentionColorArgb,
                autoScrollEnabled = settingsStore.autoScrollEnabled,
                showAvatars = settingsStore.showAvatars,
                showBadges = settingsStore.showBadges,
                showTimestamps = settingsStore.showTimestamps,
                showDeletedMessageContent = settingsStore.showDeletedMessageContent,
                showSystemMessages = settingsStore.showSystemMessages,
                animateEmotes = settingsStore.animateEmotes,
                emoteScalePercent = settingsStore.emoteScalePercent,
                betterTtvEnabled = settingsStore.betterTtvEnabled,
                frankerFaceZEnabled = settingsStore.frankerFaceZEnabled,
                sevenTvEnabled = settingsStore.sevenTvEnabled,
                backupStatusMessage = backupStatus ?: state.backupStatusMessage,
                settingsSyncEnabled = settingsStore.settingsSyncEnabled,
                settingsSyncRevision = syncedRevision ?: settingsStore.settingsSyncRevision,
                settingsSyncLastSyncedAtMillis = settingsStore.settingsSyncLastSyncedAtMillis,
                settingsSyncStatus = if (settingsStore.settingsSyncEnabled) SettingsSyncStatus.IDLE else SettingsSyncStatus.DISABLED,
                settingsSyncErrorMessage = null,
                settingsSyncConflict = null,
            )
        }
        rebuildMessageRuleEvaluation()
    }

    private fun reloadChannelsAfterSettingsChange() {
        scope.launch {
            val session = mutableState.value.session
            val accessToken = credentials?.accessToken
            if (session != null && accessToken != null) {
                restoreChannelsAndConnect(session, accessToken)
                refreshChatAssetsForCurrentSession()
            } else {
                restoreAnonymousChannelsAndConnect()
            }
        }
    }

    private fun SettingsBackupImportResult.summary(): String = buildList {
        add("каналов $channelCount")
        add("workspaces $workspaceCount")
        if (filterCount > 0) add("фильтров $filterCount")
        if (highlightCount > 0) add("highlights $highlightCount")
        if (ignoreCount > 0) add("ignore $ignoreCount")
        if (commandCount > 0) add("команд $commandCount")
        if (favouriteEmoteCount > 0) add("избранных emotes $favouriteEmoteCount")
    }.joinToString(" · ")

    fun openChats() {
        mutableState.update { state -> state.copy(requestedMainSection = MainSection.CHATS) }
    }

    fun openSettings() {
        mutableState.update { state -> state.copy(requestedMainSection = MainSection.SETTINGS) }
    }

    fun consumeRequestedMainSection() {
        mutableState.update { it.copy(requestedMainSection = null) }
    }

    fun selectModerationChannel(channelId: String) {
        val allowed = mutableState.value.channels.any { it.id == channelId } &&
            channelId in mutableState.value.moderatedChannelIds
        if (!allowed) return showError("У тебя нет прав модератора в этом канале")
        mutableState.update { state ->
            state.copy(moderation = state.moderation.copy(selectedChannelId = channelId, errorMessage = null))
        }
        refreshModerationDashboard(channelId)
    }

    fun selectChatUsersChannel(channelId: String) {
        val current = mutableState.value
        if (current.channels.none { it.id == channelId }) return
        val observed = observedChatters(current, channelId)
        mutableState.update { state ->
            state.copy(
                moderation = state.moderation.copy(
                    selectedChannelId = channelId,
                    peopleTab = ModerationPeopleTab.CHATTERS,
                    chatters = observed,
                    chattersAreComplete = false,
                    peopleNotice = null,
                    errorMessage = null,
                ),
            )
        }
        refreshModerationPeople(ModerationPeopleTab.CHATTERS)
    }

    fun refreshModerationDashboard(channelId: String? = mutableState.value.moderation.selectedChannelId) {
        val targetChannelId = channelId
            ?: mutableState.value.selectedChannelId?.takeIf { it in mutableState.value.moderatedChannelIds }
            ?: mutableState.value.channels.firstOrNull { it.id in mutableState.value.moderatedChannelIds }?.id
            ?: return
        val session = mutableState.value.session ?: return
        val accessToken = credentials?.accessToken ?: return
        val channel = mutableState.value.channels.firstOrNull { it.id == targetChannelId } ?: return
        if (targetChannelId !in mutableState.value.moderatedChannelIds) return
        mutableState.update { state ->
            state.copy(
                moderation = state.moderation.copy(
                    selectedChannelId = targetChannelId,
                    isLoading = true,
                    errorMessage = null,
                ),
            )
        }
        scope.launch {
            val settings = runCatching {
                api.getChatSettings(
                    clientId = session.clientId,
                    token = accessToken,
                    broadcasterId = targetChannelId,
                    moderatorId = session.userId,
                )
            }
            mutableState.update { state ->
                state.copy(
                    moderation = state.moderation.copy(
                        selectedChannelId = channel.id,
                        isLoading = false,
                        chatSettings = settings.getOrNull() ?: state.moderation.chatSettings
                            ?.takeIf { it.channelId == targetChannelId },
                        errorMessage = settings.exceptionOrNull()?.userMessage(),
                    ),
                )
            }
            refreshModerationPeople(stateTab = mutableState.value.moderation.peopleTab)
        }
    }

    fun refreshModerationPeople(stateTab: ModerationPeopleTab) {
        val current = mutableState.value
        val channelId = current.moderation.selectedChannelId ?: return
        if (current.channels.none { it.id == channelId }) return

        if (stateTab == ModerationPeopleTab.CHATTERS) {
            val observed = observedChatters(current, channelId)
            val canReadCompleteList = channelId in current.moderatedChannelIds && current.isAuthenticated
            val session = current.session
            val accessToken = credentials?.accessToken
            mutableState.update { state ->
                state.copy(
                    moderation = state.moderation.copy(
                        peopleTab = stateTab,
                        chatters = observed,
                        chattersAreComplete = false,
                        isRefreshingPeople = canReadCompleteList && session != null && accessToken != null,
                        peopleNotice = if (canReadCompleteList) null else OBSERVED_CHATTERS_NOTICE,
                        errorMessage = null,
                    ),
                )
            }
            if (!canReadCompleteList || session == null || accessToken == null) return

            scope.launch {
                val result = runCatching {
                    api.getChatters(
                        clientId = session.clientId,
                        token = accessToken,
                        broadcasterId = channelId,
                        moderatorId = session.userId,
                        first = 1_000,
                    ).users.map { ModerationUser(it.id, it.login, it.displayName) }
                }
                mutableState.update { state ->
                    if (state.moderation.selectedChannelId != channelId) return@update state
                    val remote = result.getOrNull()
                    state.copy(
                        moderation = state.moderation.copy(
                            peopleTab = ModerationPeopleTab.CHATTERS,
                            chatters = remote ?: observed,
                            chattersAreComplete = remote != null,
                            isRefreshingPeople = false,
                            peopleNotice = if (remote != null) null else FALLBACK_CHATTERS_NOTICE,
                            errorMessage = null,
                        ),
                    )
                }
            }
            return
        }

        val session = current.session ?: return
        val accessToken = credentials?.accessToken ?: return
        if (channelId !in current.moderatedChannelIds) return
        mutableState.update { state ->
            state.copy(
                moderation = state.moderation.copy(
                    peopleTab = stateTab,
                    isRefreshingPeople = true,
                    peopleNotice = null,
                    errorMessage = null,
                ),
            )
        }
        scope.launch {
            when (stateTab) {
                ModerationPeopleTab.CHATTERS -> Unit
                ModerationPeopleTab.MODERATORS -> {
                    if (channelId != session.userId) {
                        updateModerationPeopleResult(
                            tab = stateTab,
                            error = IllegalStateException(
                                "Twitch API выдаёт полный список модераторов только владельцу канала",
                            ),
                        ) { it.copy(moderators = emptyList()) }
                    } else {
                        val result = runCatching { api.getModerators(session.clientId, accessToken, channelId) }
                        updateModerationPeopleResult(stateTab, result.exceptionOrNull()) { moderation ->
                            moderation.copy(moderators = result.getOrNull() ?: moderation.moderators)
                        }
                    }
                }
                ModerationPeopleTab.VIPS -> {
                    if (channelId != session.userId) {
                        updateModerationPeopleResult(
                            tab = stateTab,
                            error = IllegalStateException(
                                "Twitch API выдаёт полный список VIP только владельцу канала",
                            ),
                        ) { it.copy(vips = emptyList()) }
                    } else {
                        val result = runCatching { api.getVips(session.clientId, accessToken, channelId) }
                        updateModerationPeopleResult(stateTab, result.exceptionOrNull()) { moderation ->
                            moderation.copy(vips = result.getOrNull() ?: moderation.vips)
                        }
                    }
                }
                ModerationPeopleTab.BANNED -> {
                    if (channelId != session.userId) {
                        updateModerationPeopleResult(
                            tab = stateTab,
                            error = IllegalStateException(
                                "Twitch API выдаёт полный список банов и timeout только владельцу канала",
                            ),
                        ) { it.copy(bannedUsers = emptyList()) }
                    } else {
                        val result = runCatching { api.getBannedUsers(session.clientId, accessToken, channelId) }
                        updateModerationPeopleResult(stateTab, result.exceptionOrNull()) { moderation ->
                            moderation.copy(bannedUsers = result.getOrNull() ?: moderation.bannedUsers)
                        }
                    }
                }
            }
        }
    }

    private fun observedChatters(
        state: FerventioUiState,
        channelId: String,
    ): List<ModerationUser> {
        val users = linkedMapOf<String, ModerationUser>()
        state.messagesByChannel[channelId].orEmpty().asReversed().forEach { message ->
            val login = message.userLogin.trim()
            if (login.isBlank() || message.isSystem) return@forEach
            val id = message.userId.trim()
            val key = id.takeIf(String::isNotBlank) ?: login.lowercase()
            users.putIfAbsent(
                key,
                ModerationUser(
                    id = id.ifBlank { "observed:${login.lowercase()}" },
                    login = login,
                    displayName = message.userDisplayName.ifBlank { login },
                ),
            )
        }
        return users.values.take(MAX_OBSERVED_CHATTERS)
    }

    private fun updateModerationPeopleResult(
        tab: ModerationPeopleTab,
        error: Throwable?,
        transform: (ModerationUiState) -> ModerationUiState,
    ) {
        mutableState.update { state ->
            state.copy(
                moderation = transform(state.moderation).copy(
                    peopleTab = tab,
                    isRefreshingPeople = false,
                    peopleNotice = null,
                    errorMessage = error?.userMessage(),
                ),
            )
        }
    }

    fun setAutoModNotificationsEnabled(enabled: Boolean) {
        settingsStore.autoModNotificationsEnabled = enabled
        mutableState.update { state ->
            state.copy(moderation = state.moderation.copy(autoModNotificationsEnabled = enabled))
        }
    }

    fun decideAutoModMessage(messageId: String, approve: Boolean) {
        val current = mutableState.value
        val session = current.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        val held = current.moderation.autoModQueue.firstOrNull { it.messageId == messageId }
            ?: return showError("Сообщение AutoMod уже обработано")
        if (held.channelId !in current.moderatedChannelIds) return showError("Нет прав модератора")
        scope.launch {
            runCatching {
                withAuthenticationRetry { context ->
                    api.manageHeldAutoModMessage(
                        clientId = context.session.clientId,
                        token = context.accessToken,
                        moderatorId = context.session.userId,
                        messageId = messageId,
                        allow = approve,
                    )
                }
            }.onSuccess {
                val status = if (approve) AutoModMessageStatus.APPROVED else AutoModMessageStatus.DENIED
                mutableState.update { state ->
                    state.copy(
                        moderation = state.moderation.copy(
                            autoModQueue = state.moderation.autoModQueue.map { item ->
                                if (item.messageId == messageId) item.copy(status = status) else item
                            },
                        ),
                    )
                }
                showNotice(if (approve) "Сообщение AutoMod разрешено" else "Сообщение AutoMod отклонено")
            }.onFailure { showError(it.userMessage()) }
        }
    }

    fun clearModeratedChat(channelId: String) {
        val session = mutableState.value.session ?: return showError("Войди через Twitch для модерации")
        if (CLEAR_CHAT_SCOPE !in session.scopes) {
            showError("Для очистки чата нужен доступ $CLEAR_CHAT_SCOPE. Выйди из аккаунта и войди снова через Ferventio.")
            return
        }
        moderate { context ->
            api.clearChat(
                clientId = context.session.clientId,
                token = context.accessToken,
                broadcasterId = channelId,
                moderatorId = context.session.userId,
            )
            handleChatEvent(ChatEvent.ChatCleared(channelId))
            historyRepository.recordModerationAction(channelId = channelId, action = "CLEAR")
            showNotice("Чат очищен")
        }
    }

    fun unbanFromModeration(channelId: String, user: BannedChatUser) = moderateChannel(channelId) { context ->
        api.unbanUser(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            targetUserId = user.id,
        )
        historyRepository.recordModerationAction(
            channelId = channelId,
            targetUserId = user.id,
            targetUserLogin = user.login,
            action = "UNBAN",
        )
        mutableState.update { state ->
            state.copy(
                moderation = state.moderation.copy(
                    bannedUsers = state.moderation.bannedUsers.filterNot { it.id == user.id },
                ),
            )
        }
    }

    fun updateModerationChatSettings(
        channelId: String,
        slowMode: Boolean? = null,
        slowSeconds: Int? = null,
        followerMode: Boolean? = null,
        followerMinutes: Int? = null,
        subscriberMode: Boolean? = null,
        emoteMode: Boolean? = null,
        uniqueChatMode: Boolean? = null,
    ) = moderateChannel(channelId) { context ->
        api.updateChatSettings(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            slowMode = slowMode,
            slowModeWaitSeconds = slowSeconds,
            followerMode = followerMode,
            followerModeDurationMinutes = followerMinutes,
            subscriberMode = subscriberMode,
            emoteMode = emoteMode,
            uniqueChatMode = uniqueChatMode,
        )
        val refreshed = api.getChatSettings(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
        )
        mutableState.update { state ->
            state.copy(moderation = state.moderation.copy(chatSettings = refreshed, errorMessage = null))
        }
    }

    fun reconnectEventSub() {
        reconnectCurrentTransport(force = true, reason = "Ручное переподключение")
    }

    fun onAppForegrounded() {
        if (performanceScenarioActive) return
        if (credentials != null || tokenStore.load() != null) {
            // Validate/refresh first. Reconnecting immediately with the access token captured
            // before validation can revive EventSub with a token that expired in background.
            restoreOrValidateAuthenticatedSession("Приложение снова активно")
        } else {
            reconnectCurrentTransport(force = false, reason = "Приложение снова активно")
        }
        mutableState.value.selectedChannelId?.let(::refreshPinnedMessage)
        if (settingsStore.settingsSyncEnabled) scheduleSettingsSync(immediate = true)
    }

    fun onNetworkAvailable() {
        if (performanceScenarioActive) return
        networkAvailable = true
        if (credentials != null || tokenStore.load() != null) {
            restoreOrValidateAuthenticatedSession("Сеть снова доступна")
        } else {
            reconnectCurrentTransport(force = false, reason = "Сеть снова доступна")
        }
        mutableState.value.selectedChannelId?.let(::refreshPinnedMessage)
    }

    fun onNetworkLost() {
        if (performanceScenarioActive) return
        networkAvailable = false
        val current = mutableState.value
        if (current.channels.isEmpty()) return

        stopAllChatTransports()
        mutableState.update {
            it.copy(
                connectionStatus = ConnectionStatus.RECONNECTING,
                connectionDetail = "Сеть недоступна; ожидаем восстановления…",
                connectionAttempt = 0,
                lastConnectionError = "Потеряно сетевое подключение",
            )
        }
    }

    fun sendMessage(textInput: String, replyParentMessageId: String? = null): Boolean =
        sendMessageInternal(
            channelId = mutableState.value.selectedChannelId,
            textInput = textInput,
            replyParentMessageId = replyParentMessageId,
        )

    fun sendMessageToChannel(
        channelId: String,
        textInput: String,
        replyParentMessageId: String? = null,
    ): Boolean = sendMessageInternal(channelId, textInput, replyParentMessageId)

    private fun sendMessageInternal(
        channelId: String?,
        textInput: String,
        replyParentMessageId: String?,
    ): Boolean {
        val text = textInput.trim()
        if (text.isEmpty()) return false
        if (mutableState.value.session == null || credentials == null) {
            showError("Войди через Twitch, чтобы отправлять сообщения")
            return false
        }
        // Keep arbitrary bot/custom commands unrestricted. Twitch's message transport only
        // supports /me as a chat command, so the native moderator /clear action must use Helix.
        if (text.equals("/clear", ignoreCase = true)) {
            val targetChannelId = channelId ?: return false
            clearModeratedChat(targetChannelId)
            return true
        }
        return sendPlainMessage(channelId, text, replyParentMessageId)
    }

    private fun sendPlainMessage(
        channelId: String?,
        text: String,
        replyParentMessageId: String?,
    ): Boolean {
        val current = mutableState.value
        val session = current.session ?: run {
            showError("Нет Twitch-сессии")
            return false
        }
        val accessToken = credentials?.accessToken ?: run {
            showError("Нет OAuth-токена")
            return false
        }
        val channel = current.channels.firstOrNull { it.id == channelId } ?: run {
            showError("Выбери канал")
            return false
        }
        val isAction = text.startsWith("/me ")
        val visibleText = if (isAction) text.removePrefix("/me ") else text
        val parent = replyParentMessageId?.let { parentId ->
            current.messagesByChannel[channel.id].orEmpty().firstOrNull { it.id == parentId }
        }
        val profile = current.userProfilesById[session.userId]
        val localId = "local:${UUID.randomUUID()}"
        val optimistic = ChatMessage(
            id = localId,
            channelId = channel.id,
            channelLogin = channel.login,
            author = ChatAuthor(
                id = session.userId,
                login = session.login,
                displayName = profile?.displayName?.takeIf(String::isNotBlank) ?: session.login,
                color = null,
                profileImageUrl = profile?.profileImageUrl,
            ),
            text = visibleText,
            fragments = listOf(ChatFragment.Text(visibleText)),
            timestamp = Instant.now().toString(),
            reply = parent?.let { message ->
                ReplyContext(
                    parentMessageId = message.id,
                    parentMessageBody = message.text,
                    parentUserId = message.userId,
                    parentUserLogin = message.userLogin,
                    parentUserName = message.userDisplayName,
                    threadMessageId = message.reply?.threadMessageId ?: message.reply?.parentMessageId ?: message.id,
                    threadUserId = message.reply?.threadUserId ?: message.userId,
                    threadUserLogin = message.reply?.threadUserLogin ?: message.userLogin,
                    threadUserName = message.reply?.threadUserName ?: message.userDisplayName,
                )
            },
            type = if (isAction) ChatMessageType.ACTION else ChatMessageType.CHAT,
            flags = MessageFlags(isAction = isAction),
            outgoingState = OutgoingMessageState.SENDING,
            clientNonce = localId,
        )
        appendOptimisticMessage(optimistic)
        recordSentMessage(channel.id, text)
        updateDraft(channel.id, "")
        launchOutgoingSend(
            localMessageId = localId,
            channel = channel,
            session = session,
            accessToken = accessToken,
            wireText = text,
            replyParentMessageId = replyParentMessageId,
        )
        return true
    }

    fun retryOutgoingMessage(message: ChatMessage) {
        if (message.outgoingState != OutgoingMessageState.FAILED) return
        val current = mutableState.value
        val session = current.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        val channel = current.channels.firstOrNull { it.id == message.channelId }
            ?: return showError("Канал больше не открыт")
        mutableState.update { state ->
            state.copy(
                messagesByChannel = state.messagesByChannel + (
                    channel.id to state.messagesByChannel[channel.id].orEmpty().map { existing ->
                        if (existing.id == message.id) {
                            existing.copy(outgoingState = OutgoingMessageState.SENDING, outgoingError = null)
                        } else existing
                    }
                ),
                rateLimitsByChannel = state.rateLimitsByChannel - channel.id,
            )
        }
        val wireText = if (message.isAction) "/me ${message.text}" else message.text
        launchOutgoingSend(
            localMessageId = message.id,
            channel = channel,
            session = session,
            accessToken = accessToken,
            wireText = wireText,
            replyParentMessageId = message.reply?.parentMessageId,
        )
    }

    private fun appendOptimisticMessage(message: ChatMessage) {
        ChatMessageTextPreparation.warm(message)
        mutableState.update { state ->
            val existing = state.messagesByChannel[message.channelId].orEmpty()
            val memoryLimit = if (state.localHistoryEnabled) {
                minOf(MAX_MESSAGES_PER_CHANNEL, state.localHistoryLimit)
            } else {
                MAX_MESSAGES_PER_CHANNEL
            }
            state.copy(
                messagesByChannel = state.messagesByChannel + (
                    message.channelId to (existing + message).takeLast(memoryLimit)
                ),
            )
        }
    }

    private fun launchOutgoingSend(
        localMessageId: String,
        channel: ChatChannel,
        session: TwitchSession,
        accessToken: String,
        wireText: String,
        replyParentMessageId: String?,
    ) {
        scope.launch {
            runCatching {
                withAuthenticationRetry { context ->
                    api.sendMessage(
                        clientId = context.session.clientId,
                        token = context.accessToken,
                        broadcasterId = channel.id,
                        senderId = context.session.userId,
                        message = wireText,
                        replyParentMessageId = replyParentMessageId,
                    )
                }
            }.onSuccess { result ->
                mutableState.update { state ->
                    state.copy(
                        messagesByChannel = state.messagesByChannel + (
                            channel.id to state.messagesByChannel[channel.id].orEmpty().map { message ->
                                if (message.id == localMessageId) {
                                    message.copy(
                                        outgoingState = OutgoingMessageState.SENT,
                                        outgoingError = null,
                                        serverMessageId = result.messageId,
                                    )
                                } else message
                            }
                        ),
                        rateLimitsByChannel = state.rateLimitsByChannel - channel.id,
                    )
                }
            }.onFailure { error ->
                val sendError = error as? TwitchChatSendException
                mutableState.update { state ->
                    val rateLimit = if (sendError?.statusCode == 429) {
                        state.rateLimitsByChannel + (
                            channel.id to ChatRateLimitState(
                                message = sendError.apiMessage,
                                retryAtMillis = sendError.retryAtMillis,
                            )
                        )
                    } else state.rateLimitsByChannel
                    state.copy(
                        messagesByChannel = state.messagesByChannel + (
                            channel.id to state.messagesByChannel[channel.id].orEmpty().map { message ->
                                if (message.id == localMessageId) {
                                    message.copy(
                                        outgoingState = OutgoingMessageState.FAILED,
                                        outgoingError = error.userMessage(),
                                    )
                                } else message
                            }
                        ),
                        rateLimitsByChannel = rateLimit,
                    )
                }
            }
        }
    }

    private fun recordSentMessage(channelId: String, text: String) {
        val normalized = text.trim()
        if (normalized.isEmpty()) return
        mutableState.update { state ->
            val updatedChannel = (listOf(normalized) + state.sentMessageHistoryByChannel[channelId].orEmpty())
                .distinct()
                .take(MAX_SENT_MESSAGE_HISTORY)
            val updated = state.sentMessageHistoryByChannel + (channelId to updatedChannel)
            settingsStore.sentMessageHistoryByChannel = updated
            state.copy(sentMessageHistoryByChannel = updated)
        }
    }

    private fun executeCustomCommand(
        channelId: String?,
        custom: ParsedChatInput.Custom,
        replyParentMessageId: String?,
    ) {
        val session = mutableState.value.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId }
            ?: return showError("Выбери канал")
        scope.launch {
            runCatching {
                val channelInfo = runCatching {
                    api.getChannelInformation(session.clientId, accessToken, channel.id)
                }.getOrNull()
                val context = CustomCommandContext(
                    channelName = channel.login,
                    channelId = channel.id,
                    myName = session.login,
                    myId = session.userId,
                    streamTitle = channelInfo?.title.orEmpty(),
                    streamGame = channelInfo?.gameName.orEmpty(),
                )
                resolveCustomCommand(
                    commandName = custom.name,
                    arguments = custom.arguments,
                    context = context,
                    stack = emptySet(),
                    depth = 0,
                )
            }.onSuccess { expanded ->
                sendMessageInternal(channel.id, expanded, replyParentMessageId)
            }.onFailure { error -> showError(error.userMessage()) }
        }
    }

    private fun resolveCustomCommand(
        commandName: String,
        arguments: List<String>,
        context: CustomCommandContext,
        stack: Set<String>,
        depth: Int,
    ): String {
        require(depth < MAX_CUSTOM_COMMAND_DEPTH) { "Слишком глубокая цепочка пользовательских команд" }
        val normalized = CommandRegistry.normalizeName(commandName)
        require(normalized !in stack) { "Обнаружен цикл пользовательских команд: /$normalized" }
        val command = mutableState.value.customCommands.firstOrNull {
            it.enabled && it.normalizedName == normalized
        } ?: error("Пользовательская команда /$normalized не найдена")
        val expanded = when (val result = CustomCommandExpander.expand(command, arguments, context)) {
            is CustomCommandExpansionResult.Success -> result.value
            is CustomCommandExpansionResult.Error -> error(result.message)
        }
        val customNames = mutableState.value.customCommands.filter(CustomCommand::enabled)
            .map(CustomCommand::normalizedName).toSet()
        val parsed = when (val result = ChatCommandParser.parse(expanded, customNames)) {
            is ChatInputParseResult.Success -> result.input
            is ChatInputParseResult.Error -> error(result.message)
        }
        return if (parsed is ParsedChatInput.Custom) {
            resolveCustomCommand(
                commandName = parsed.name,
                arguments = parsed.arguments,
                context = context,
                stack = stack + normalized,
                depth = depth + 1,
            )
        } else expanded
    }

    private fun clearChannelMessages(channelId: String?) {
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId }
            ?: return showError("Выбери канал")
        mutableState.update { state ->
            state.copy(
                messagesByChannel = state.messagesByChannel + (channel.id to emptyList()),
                scrollPositionsByChannel = state.scrollPositionsByChannel - channel.id,
                channelAttention = state.channelAttention - channel.id,
            )
        }
        scope.launch { runCatching { historyRepository.clearChannel(channel.id) } }
        showNotice("Локальные сообщения #${channel.displayName} очищены")
    }

    private fun executeAuthenticatedChannelCommand(
        channelId: String?,
        action: suspend (ModerationContext, ChatChannel) -> Unit,
    ) {
        val session = mutableState.value.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId }
            ?: return showError("Выбери канал")
        scope.launch {
            runCatching {
                withAuthenticationRetry { context ->
                    action(context, channel)
                }
            }.onFailure { showError(it.userMessage()) }
        }
    }

    private fun executeUserCommand(
        channelId: String?,
        userLogin: String,
        action: suspend (ModerationContext, ChatChannel, TwitchUser) -> Unit,
    ) {
        executeChannelCommand(channelId) { context, channel ->
            val target = api.getUserByLogin(
                clientId = context.session.clientId,
                token = context.accessToken,
                login = userLogin,
            )
            action(context, channel, target)
        }
    }

    private fun executeChannelCommand(
        channelId: String?,
        action: suspend (ModerationContext, ChatChannel) -> Unit,
    ) {
        val session = mutableState.value.session ?: return showError("Нет Twitch-сессии")
        val accessToken = credentials?.accessToken ?: return showError("Нет OAuth-токена")
        val channel = mutableState.value.channels.firstOrNull { it.id == channelId }
            ?: return showError("Выбери канал")
        if (channel.id !in mutableState.value.moderatedChannelIds) {
            showError("У тебя нет прав модератора в этом канале")
            return
        }
        scope.launch {
            runCatching {
                withAuthenticationRetry { context ->
                    action(context, channel)
                }
            }.onFailure { showError(it.userMessage()) }
        }
    }

    fun deleteMessage(message: ChatMessage) = moderateChannel(message.channelId) {
        api.deleteMessage(
            clientId = it.session.clientId,
            token = it.accessToken,
            broadcasterId = message.channelId,
            moderatorId = it.session.userId,
            messageId = message.id,
        )
        markMessageDeleted(message.channelId, message.id)
        runCatching {
            historyRepository.recordModerationAction(
                channelId = message.channelId,
                targetUserId = message.userId,
                targetUserLogin = message.userLogin,
                messageId = message.id,
                action = "DELETE",
            )
        }
    }

    fun refreshPinnedMessage(channelId: String) {
        val generation = pinnedMessageRequestGenerations
            .computeIfAbsent(channelId) { AtomicLong(0L) }
            .incrementAndGet()
        pinnedMessageRefreshJobs.remove(channelId)?.cancel()
        val current = mutableState.value
        if (channelId.isBlank() || current.channels.none { it.id == channelId }) {
            clearPinnedMessageSnapshot(channelId, invalidateRequest = false)
            return
        }
        lateinit var refreshJob: Job
        refreshJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result: Result<PinnedChatMessage?> = try {
                    Result.success(pinnedChatClient.getPinnedChatMessage(channelId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Result.failure(error)
                }
                if (pinnedMessageRequestGenerations[channelId]?.get() != generation) return@launch
                result.onSuccess { pinned ->
                    mutableState.update { state ->
                        val updated = if (pinned == null) {
                            state.pinnedMessagesByChannel - channelId
                        } else {
                            state.pinnedMessagesByChannel + (channelId to pinned)
                        }
                        state.copy(pinnedMessagesByChannel = updated)
                    }
                }.onFailure { error ->
                    if (error is TwitchPinnedChatGqlException && error.invalidatesSnapshot) {
                        mutableState.update { state ->
                            if (channelId !in state.pinnedMessagesByChannel) state
                            else state.copy(pinnedMessagesByChannel = state.pinnedMessagesByChannel - channelId)
                        }
                    }
                    // Network/rate-limit/server failures keep the last confirmed banner. A
                    // successful empty response or a definitive query/schema rejection clears it.
                    SafeLog.w("PinnedChat", "Unable to refresh public pinned message: ${error.userMessage()}")
                }
            } finally {
                pinnedMessageRefreshJobs.remove(channelId, refreshJob)
            }
        }
        pinnedMessageRefreshJobs[channelId] = refreshJob
        refreshJob.start()
    }

    private fun clearPinnedMessageSnapshot(channelId: String, invalidateRequest: Boolean = true) {
        if (invalidateRequest) {
            pinnedMessageRequestGenerations
                .computeIfAbsent(channelId) { AtomicLong(0L) }
                .incrementAndGet()
        }
        pinnedMessageRefreshJobs.remove(channelId)?.cancel()
        mutableState.update { state ->
            if (channelId !in state.pinnedMessagesByChannel) state
            else state.copy(pinnedMessagesByChannel = state.pinnedMessagesByChannel - channelId)
        }
    }

    fun pinMessage(message: ChatMessage) = moderateChannel(message.channelId) { context ->
        val messageId = message.serverMessageId?.takeIf(String::isNotBlank) ?: message.id
        require(
            messageId.isNotBlank() &&
                !message.isSystem &&
                !message.isDeleted &&
                !messageId.startsWith("local-") &&
                message.outgoingState != OutgoingMessageState.SENDING &&
                message.outgoingState != OutgoingMessageState.FAILED
        ) { "Это сообщение ещё нельзя закрепить" }
        api.pinChatMessage(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = message.channelId,
            moderatorId = context.session.userId,
            messageId = messageId,
        )
        refreshPinnedMessage(message.channelId)
        showNotice("Сообщение закреплено")
    }

    fun unpinMessage(channelId: String, messageId: String) = moderateChannel(channelId) { context ->
        api.unpinChatMessage(
            clientId = context.session.clientId,
            token = context.accessToken,
            broadcasterId = channelId,
            moderatorId = context.session.userId,
            messageId = messageId,
        )
        clearPinnedMessageSnapshot(channelId)
        showNotice("Закреплённое сообщение снято")
    }

    fun timeoutUser(message: ChatMessage, seconds: Int = 600) =
        timeoutUser(message.channelId, message.userId, message.userLogin, seconds)

    fun banUser(message: ChatMessage) =
        banUser(message.channelId, message.userId, message.userLogin)

    fun logout() {
        deviceRevocationJob?.cancel()
        deviceRevocationJob = null
        allSessionsRevocationJob?.cancel()
        allSessionsRevocationJob = null
        val credentialToDelete = backendCredential ?: tokenStore.load()
        cancelServerAuthorization(updateState = false)
        authRestoreJob?.cancel()
        authRestoreJob = null
        stopAllChatTransports()
        stopTokenValidation()
        stopAuthenticatedJobs()
        tokenStore.clear()
        settingsStore.clearPendingAuth()
        backendCredential = null
        credentials = null
        accessLeaseFallbackActive = false
        seenEventSubMessageIds.clear()
        clearEventQueue()
        performanceScenarioJob?.cancel()
        performanceScenarioJob = null
        userProfileCache.clear()
        userColorCache.clear()
        ChatMessageTextPreparation.clear()
        onSessionEnded()
        scope.launch {
            if (credentialToDelete != null && credentialToDelete.serverUrl.isNotBlank()) {
                runCatching {
                    backend.logout(
                        serverUrl = credentialToDelete.serverUrl,
                        installationId = settingsStore.installationId,
                        deviceSecret = settingsStore.installationSecret,
                        sessionToken = credentialToDelete.token,
                    )
                }
            }
            restoreAnonymousChannelsAndConnect(
                warning = "Выполнен выход из Twitch. Чаты остались доступны только для чтения.",
            )
        }
    }

    fun revokeDevice() {
        if (deviceRevocationJob?.isActive == true || allSessionsRevocationJob?.isActive == true) return
        val credential = backendCredential ?: tokenStore.load()
        if (credential == null || credential.serverUrl.isBlank()) {
            showError("Нет активной серверной сессии для отзыва устройства")
            return
        }
        mutableState.update { it.copy(isRevokingDevice = true, errorMessage = null) }
        deviceRevocationJob = scope.launch {
            try {
                backend.revokeDevice(
                    serverUrl = credential.serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    sessionToken = credential.token,
                )
                cancelServerAuthorization(updateState = false)
                authRestoreJob?.cancel()
                authRestoreJob = null
                stopAllChatTransports()
                stopTokenValidation()
                stopAuthenticatedJobs()
                tokenStore.clear()
                settingsStore.clearPendingAuth()
                backendCredential = null
                credentials = null
                accessLeaseFallbackActive = false
                seenEventSubMessageIds.clear()
                clearEventQueue()
                userProfileCache.clear()
                userColorCache.clear()
                ChatMessageTextPreparation.clear()
                onSessionEnded()
                restoreAnonymousChannelsAndConnect(
                    warning = "Это устройство отозвано. Серверные сессии и push-регистрация удалены; локальная история сохранена.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isRevokingDevice = false,
                        errorMessage = "Не удалось отозвать устройство: ${error.userMessage()}",
                    )
                }
            } finally {
                deviceRevocationJob = null
            }
        }
    }

    fun revokeAllSessions() {
        if (allSessionsRevocationJob?.isActive == true || deviceRevocationJob?.isActive == true) return
        val credential = backendCredential ?: tokenStore.load()
        if (credential == null || credential.serverUrl.isBlank()) {
            showError("Нет активной серверной сессии для отзыва всех устройств")
            return
        }
        mutableState.update { it.copy(isRevokingAllSessions = true, errorMessage = null) }
        allSessionsRevocationJob = scope.launch {
            try {
                backend.revokeAllSessions(
                    serverUrl = credential.serverUrl,
                    installationId = settingsStore.installationId,
                    deviceSecret = settingsStore.installationSecret,
                    sessionToken = credential.token,
                )
                cancelServerAuthorization(updateState = false)
                authRestoreJob?.cancel()
                authRestoreJob = null
                stopAllChatTransports()
                stopTokenValidation()
                stopAuthenticatedJobs()
                tokenStore.clear()
                settingsStore.clearPendingAuth()
                backendCredential = null
                credentials = null
                accessLeaseFallbackActive = false
                seenEventSubMessageIds.clear()
                clearEventQueue()
                userProfileCache.clear()
                userColorCache.clear()
                ChatMessageTextPreparation.clear()
                onSessionEnded()
                restoreAnonymousChannelsAndConnect(
                    warning = "Все сессии аккаунта Ferventio отозваны. На устройствах потребуется повторный вход; локальные данные сохранены.",
                    reauthorizationRequired = true,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                mutableState.update {
                    it.copy(
                        isRevokingAllSessions = false,
                        errorMessage = "Не удалось отозвать все сессии: ${error.userMessage()}",
                    )
                }
            } finally {
                allSessionsRevocationJob = null
            }
        }
    }

    fun clearError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun resetEventSubDiagnostics() {
        eventSubConnectionStartedAtMillis = null
        lastEventSubActivityPublishedAtMillis.set(0L)
        mutableState.update {
            it.copy(
                lastEventSubActivityAtMillis = null,
                lastEventSubActivityType = null,
                eventSubConnectedAtMillis = null,
                lastConnectionDurationMillis = null,
                lastConnectionError = null,
                eventSubReconnectCount = 0,
                eventSubDuplicateCount = 0,
                eventSubDroppedEventCount = 0,
                eventSubMalformedEnvelopeCount = 0,
            )
        }
    }

    fun buildEventSubDiagnosticReport(): String {
        val current = mutableState.value
        return buildString {
            appendLine("Ferventio ${BuildConfig.VERSION_NAME}")
            appendLine("EventSub status: ${current.connectionStatus}")
            appendLine("Detail: ${current.connectionDetail.orEmpty()}")
            appendLine("Attempt: ${current.connectionAttempt}")
            appendLine("Channels: ${current.channels.joinToString { it.login }}")
            appendLine("Last activity type: ${current.lastEventSubActivityType.orEmpty()}")
            appendLine("Last activity millis: ${current.lastEventSubActivityAtMillis ?: 0L}")
            appendLine("Connected at millis: ${current.eventSubConnectedAtMillis ?: 0L}")
            appendLine("Last connect duration millis: ${current.lastConnectionDurationMillis ?: 0L}")
            appendLine("Reconnects: ${current.eventSubReconnectCount}")
            appendLine("Duplicates dropped: ${current.eventSubDuplicateCount}")
            appendLine("Queue events dropped: ${current.eventSubDroppedEventCount}")
            appendLine("Malformed envelopes: ${current.eventSubMalformedEnvelopeCount}")
            appendLine("Notice subscriptions ready: ${current.eventSubNoticeChannelIds.size}/${current.channels.size}")
            appendLine("Notice subscription failures: ${current.eventSubNoticeFailures.entries.joinToString { "${it.key}=${it.value}" }}")
            appendLine("Local history enabled: ${current.localHistoryEnabled}")
            appendLine("Local history limit: ${current.localHistoryLimit}")
            appendLine("Local history retention days: ${current.localHistoryRetentionDays}")
            appendLine("Local history max database size MB: ${current.localHistoryMaxSizeMb}")
            appendLine("Restored history messages: ${current.restoredHistoryMessageCount}")
            appendLine("Saved scroll positions: ${current.scrollPositionsByChannel.size}")
            appendLine("History error: ${current.historyErrorMessage.orEmpty()}")
            appendLine("Last error: ${current.lastConnectionError.orEmpty()}")
        }
    }

    private suspend fun obtainAccessLease(
        storedCredential: BackendSessionCredential,
        forceRefresh: Boolean,
    ): TwitchAccessLease = tokenRefreshMutex.withLock {
        check(storedCredential.expiresAtEpochMillis > System.currentTimeMillis()) {
            "Серверная сессия Ferventio истекла"
        }
        val current = credentials
        if (!forceRefresh && current != null && TwitchAccessLeasePolicy.canReuseWithoutBackendCall(current)) {
            ensureRequiredScopes(current.session)
            persistAuthentication(storedCredential, current)
            return@withLock current
        }
        try {
            val lease = backend.leaseAccessToken(
                serverUrl = storedCredential.serverUrl,
                installationId = settingsStore.installationId,
                deviceSecret = settingsStore.installationSecret,
                sessionToken = storedCredential.token,
                forceRefresh = forceRefresh,
            )
            ensureRequiredScopes(lease.session)
            accessLeaseFallbackActive = false
            persistAuthentication(storedCredential, lease)
            lease
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (
                !forceRefresh &&
                error.isTransientBackendFailure() &&
                current != null &&
                TwitchAccessLeasePolicy.canUseDuringBackendOutage(current)
            ) {
                val fallbackLease = validateCachedLeaseForOutage(current)
                ensureRequiredScopes(fallbackLease.session)
                check(TwitchAccessLeasePolicy.canUseDuringBackendOutage(fallbackLease)) {
                    "Сохранённый Twitch access token больше нельзя использовать"
                }
                accessLeaseFallbackActive = true
                persistAuthentication(storedCredential, fallbackLease)
                fallbackLease
            } else {
                throw error
            }
        }
    }

    private suspend fun validateAccessLeaseAtStartup(lease: TwitchAccessLease): TwitchAccessLease {
        if (!TwitchAccessLeasePolicy.needsDirectValidationAtStartup(lease)) return lease
        return validateAccessLeaseDirectly(lease)
    }

    private suspend fun validateCachedLeaseForOutage(lease: TwitchAccessLease): TwitchAccessLease {
        if (!TwitchAccessLeasePolicy.needsDirectValidationDuringOutage(lease)) return lease
        return validateAccessLeaseDirectly(lease)
    }

    private suspend fun validateAccessLeaseDirectly(lease: TwitchAccessLease): TwitchAccessLease =
        TwitchAccessLeaseValidation.updateAfterDirectValidation(
            cachedLease = lease,
            validatedSession = api.validateAccessToken(lease.accessToken),
            requiredScopes = BASE_REQUIRED_SCOPES,
        )

    private fun persistAuthentication(
        current: BackendSessionCredential,
        lease: TwitchAccessLease,
    ) {
        val renewedCredential = current.copy(
            expiresAtEpochMillis = lease.backendSessionExpiresAtEpochMillis,
        )
        val normalizedLease = lease.copy(
            backendSessionExpiresAtEpochMillis = renewedCredential.expiresAtEpochMillis,
        )
        val previousCredential = backendCredential
        val previousLease = credentials
        backendCredential = renewedCredential
        credentials = normalizedLease
        val cachedCredentialChanged = previousLease == null ||
            !TwitchAccessLeasePolicy.representsSameCachedCredential(previousLease, normalizedLease)
        if (renewedCredential != previousCredential || cachedCredentialChanged) {
            tokenStore.save(renewedCredential, normalizedLease)
        }
    }

    private suspend fun refreshAfterUnauthorized(
        failedAccessToken: String,
    ): ModerationContext {
        val credential = backendCredential ?: tokenStore.load()
            ?: error("Нет сохранённой сессии Ferventio")
        val lease = tokenRefreshMutex.withLock {
            val current = credentials
            if (current != null && current.accessToken != failedAccessToken) {
                return@withLock current
            }
            val refreshed = backend.leaseAccessToken(
                serverUrl = credential.serverUrl,
                installationId = settingsStore.installationId,
                deviceSecret = settingsStore.installationSecret,
                sessionToken = credential.token,
                forceRefresh = true,
            )
            ensureRequiredScopes(refreshed.session)
            accessLeaseFallbackActive = false
            persistAuthentication(credential, refreshed)
            refreshed
        }
        mutableState.update {
            it.copy(
                clientId = lease.session.clientId,
                session = lease.session,
                lastConnectionError = null,
            )
        }
        connectEventSub(lease.session, lease.accessToken)
        return ModerationContext(lease.session, lease.accessToken)
    }

    private suspend fun <T> withAuthenticationRetry(
        action: suspend (ModerationContext) -> T,
    ): T {
        val credential = backendCredential ?: tokenStore.load()
            ?: error("Нет сохранённой сессии Ferventio")
        val activeLease = obtainAccessLease(credential, forceRefresh = false)
        val initial = ModerationContext(activeLease.session, activeLease.accessToken)
        return try {
            action(initial)
        } catch (error: Throwable) {
            if (!error.hasUnauthorizedApiCause()) throw error
            val refreshed = refreshAfterUnauthorized(initial.accessToken)
            action(refreshed)
        }
    }

    private suspend fun completeLogin(
        credential: BackendSessionCredential,
        lease: TwitchAccessLease,
    ) {
        currentCoroutineContext().ensureActive()
        if (performanceScenarioActive) return
        val session = lease.session
        ensureRequiredScopes(session)
        stopAllChatTransports()
        stopTokenValidation()
        stopAuthenticatedJobs()
        settingsStore.bindSettingsSyncUser(session.userId)
        persistAuthentication(credential, lease)
        currentCoroutineContext().ensureActive()
        if (performanceScenarioActive) return
        mutableState.update {
            it.copy(
                clientId = session.clientId,
                session = session,
                isAuthorizing = false,
                reauthorizationRequired = false,
                isBootstrapping = true,
                isChannelsLoading = true,
                pendingExternalUri = null,
                errorMessage = null,
                historyErrorMessage = null,
                lastConnectionError = cachedLeaseWarningOrNull(),
                moderatedChannelIds = emptySet(),
                settingsSyncRevision = settingsStore.settingsSyncRevision,
                settingsSyncLastSyncedAtMillis = settingsStore.settingsSyncLastSyncedAtMillis,
                settingsSyncStatus = if (settingsStore.settingsSyncEnabled) {
                    SettingsSyncStatus.IDLE
                } else {
                    SettingsSyncStatus.DISABLED
                },
                settingsSyncErrorMessage = null,
                settingsSyncConflict = null,
            )
        }
        rebuildMessageRuleEvaluation()
        startTokenValidation()
        val moderated = runCatching {
            api.getModeratedChannelIds(session.clientId, lease.accessToken, session.userId)
        }.getOrDefault(emptySet()) + session.userId
        currentCoroutineContext().ensureActive()
        if (performanceScenarioActive) return
        mutableState.update { state ->
            state.copy(
                moderatedChannelIds = moderated,
                moderation = state.moderation.copy(
                    selectedChannelId = state.moderation.selectedChannelId
                        ?.takeIf(moderated::contains)
                        ?: state.selectedChannelId?.takeIf(moderated::contains),
                ),
            )
        }
        currentCoroutineContext().ensureActive()
        if (performanceScenarioActive) return
        restoreChannelsAndConnect(session, lease.accessToken)
        if (performanceScenarioActive) return
        restoreAttentionEntries()
        refreshModeratedChannels(session)
        queueUserProfileHydration(session.userId)
        if (settingsStore.settingsSyncEnabled) scheduleSettingsSync(immediate = true)
    }

    private fun restoreOrValidateAuthenticatedSession(reason: String) {
        if (authRestoreJob?.isActive == true || serverAuthorizationJob?.isActive == true) return
        val storedCredential = tokenStore.load() ?: return
        authRestoreJob = scope.launch {
            try {
                val previousLease = credentials
                val previousSession = mutableState.value.session
                val activeLease = obtainAccessLease(storedCredential, forceRefresh = false)
                val activeSession = activeLease.session
                if (previousSession == null) {
                    completeLogin(storedCredential, activeLease)
                } else {
                    val changed = previousLease?.accessToken != activeLease.accessToken || previousSession != activeSession
                    mutableState.update {
                        it.copy(
                            clientId = activeSession.clientId,
                            session = activeSession,
                            errorMessage = null,
                            lastConnectionError = cachedLeaseWarningOrNull(),
                        )
                    }
                    rebuildMessageRuleEvaluation()
                    if (changed) {
                        connectEventSub(activeSession, activeLease.accessToken)
                    } else {
                        reconnectCurrentTransport(force = false, reason = reason)
                    }
                    refreshModeratedChannels(activeSession)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (error.isPermanentAuthenticationFailure()) {
                    terminateSession("Сессия Ferventio завершена: ${error.userMessage()}")
                } else {
                    mutableState.update { state ->
                        state.copy(
                            lastConnectionError = "$reason: ${error.userMessage()}",
                            errorMessage = if (state.isAnonymous) {
                                "Не удалось восстановить вход через сервер. Чаты продолжают работать без аккаунта."
                            } else {
                                state.errorMessage
                            },
                        )
                    }
                }
            } finally {
                authRestoreJob = null
            }
        }
    }

    private suspend fun restoreAnonymousChannelsAndConnect(
        warning: String? = null,
        reauthorizationRequired: Boolean = false,
        expectedBootstrapGeneration: Long? = null,
    ) {
        anonymousRestoreMutex.withLock {
            if (expectedBootstrapGeneration != null &&
                expectedBootstrapGeneration != bootstrapGeneration.get()
            ) {
                return@withLock
            }
            restoreAnonymousChannelsAndConnectLocked(
                warning = warning,
                reauthorizationRequired = reauthorizationRequired,
                expectedBootstrapGeneration = expectedBootstrapGeneration,
            )
        }
    }

    private suspend fun restoreAnonymousChannelsAndConnectLocked(
        warning: String?,
        reauthorizationRequired: Boolean,
        expectedBootstrapGeneration: Long?,
    ) {
        stopEventSub()
        stopTokenValidation()
        stopAuthenticatedJobs()
        val savedLogins = loadPersistedChannelLogins()
        val cachedChannels = runCatching { historyRepository.loadChannels(savedLogins) }.getOrDefault(emptyList())
        val cachedByLogin = cachedChannels.associateBy { it.login.lowercase() }
        val channels = savedLogins.map { login ->
            cachedByLogin[login.lowercase()] ?: ChatChannel(
                id = anonymousChannelId(login),
                login = login.lowercase(),
                displayName = login,
            )
        }.distinctBy { it.login.lowercase() }
        val realChannelIds = channels
            .map(ChatChannel::id)
            .filterNot { it.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX) }
        var historyError: String? = null
        val savedMessages = if (realChannelIds.isEmpty() || !settingsStore.localHistoryEnabled) {
            emptyMap()
        } else {
            runCatching {
                historyRepository.loadRecentMessages(
                    channelIds = realChannelIds,
                    enabled = true,
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
            }.getOrElse { error ->
                historyError = "Не удалось восстановить сообщения: ${error.userMessage()}"
                emptyMap()
            }
        }
        val savedPositions = if (realChannelIds.isEmpty()) {
            emptyMap()
        } else {
            runCatching { historyRepository.loadScrollPositions(realChannelIds) }.getOrElse { error ->
                historyError = historyError ?: "Не удалось восстановить позицию: ${error.userMessage()}"
                emptyMap()
            }
        }
        val selectedLogin = settingsStore.selectedChannelLogin
        val selected = channels.firstOrNull { it.login.equals(selectedLogin, ignoreCase = true) }
            ?: channels.firstOrNull()
        // Room/IO calls above use runCatching and can otherwise swallow cancellation.
        currentCoroutineContext().ensureActive()
        if (performanceScenarioActive) return
        if (expectedBootstrapGeneration != null &&
            expectedBootstrapGeneration != bootstrapGeneration.get()
        ) {
            return
        }
        val base = freshUiState(isBootstrapping = false, errorMessage = warning)
        mutableState.value = base.copy(
            clientId = "",
            session = null,
            reauthorizationRequired = reauthorizationRequired,
            channels = channels,
            selectedChannelId = selected?.id,
            messagesByChannel = channels.associate { channel ->
                channel.id to savedMessages[channel.id].orEmpty().takeLast(MAX_MESSAGES_PER_CHANNEL)
            },
            scrollPositionsByChannel = savedPositions,
            moderatedChannelIds = emptySet(),
            pinnedMessagesByChannel = emptyMap(),
            isChannelsLoading = false,
            isHistoryLoading = false,
            restoredHistoryMessageCount = savedMessages.values.sumOf { it.size },
            historyErrorMessage = historyError,
            connectionDetail = if (channels.isEmpty()) null else "Чтение Twitch без аккаунта",
        )
        normalizeChannelPreferences(channels, selected?.id)
        restoreAttentionEntries()
        refreshAnonymousChatAssets(channels)
        connectAnonymousChat(channels)
    }

    private fun refreshAnonymousChatAssets(
        channels: List<ChatChannel> = mutableState.value.channels,
    ) {
        chatAssetsJob?.cancel()
        emoteLiveRefreshJob?.cancel()
        if (channels.isEmpty()) {
            scope.launch { emoteRepository.stopLiveUpdates() }
            mutableState.update {
                it.copy(
                    badgeAssetsByChannel = emptyMap(),
                    frankerFaceZBadgesByUserId = emptyMap(),
                    frankerFaceZChannelBadgesByChannel = emptyMap(),
                    cheermoteAssetsByChannel = emptyMap(),
                    emoteCatalogByChannel = emptyMap(),
                    emoteLiveProviders = emptySet(),
                    emoteCatalogErrorMessage = null,
                    betterTtvEmotesByChannel = emptyMap(),
                    frankerFaceZEmotesByChannel = emptyMap(),
                    sevenTvEmotesByChannel = emptyMap(),
                )
            }
            return
        }
        chatAssetsJob = scope.launch {
            val enabledProviders = buildSet {
                if (settingsStore.betterTtvEnabled) add(EmoteRepository.BETTER_TTV)
                if (settingsStore.frankerFaceZEnabled) add(EmoteRepository.FRANKER_FACE_Z)
                if (settingsStore.sevenTvEnabled) add(EmoteRepository.SEVEN_TV)
            }
            val (snapshot, publicBadges) = coroutineScope {
                val emotesDeferred = async {
                    emoteRepository.refresh(
                        context = EmoteProviderContext("", "", ""),
                        channels = channels,
                        enabledProviders = enabledProviders,
                        includeTwitch = false,
                    )
                }
                val badgesDeferred = async { loadAnonymousBadgeAssets(channels) }
                emotesDeferred.await() to badgesDeferred.await()
            }
            val activeChannelIds = mutableState.value.channels.map(ChatChannel::id).toSet()
            val activeChannels = channels.filter { it.id in activeChannelIds }
            mutableState.update { state ->
                val retainedBadgeAssets = state.badgeAssetsByChannel.filterKeys(activeChannelIds::contains)
                val nextBadgeAssets = if (publicBadges == null) {
                    retainedBadgeAssets
                } else {
                    activeChannels.associate { channel ->
                        val previous = retainedBadgeAssets[channel.id].orEmpty()
                        val channelAssets = publicBadges.channelAssets[channel.id]
                        val globalAssets = publicBadges.globalAssets
                        val combined = when {
                            channelAssets != null -> globalAssets.orEmpty() + channelAssets
                            globalAssets != null -> previous + globalAssets
                            else -> previous
                        }
                        channel.id to combined
                    }
                }
                val next = state.copy(
                    badgeAssetsByChannel = nextBadgeAssets,
                    frankerFaceZBadgesByUserId = if (state.showBadges) {
                        publicBadges?.globalFfzBadges ?: state.frankerFaceZBadgesByUserId
                    } else emptyMap(),
                    frankerFaceZChannelBadgesByChannel = if (state.showBadges) {
                        activeChannels.associate { channel ->
                            channel.id to (
                                publicBadges?.channelFfzBadges?.get(channel.id)
                                    ?: state.frankerFaceZChannelBadgesByChannel[channel.id].orEmpty()
                            )
                        }
                    } else emptyMap(),
                    cheermoteAssetsByChannel = emptyMap(),
                    emoteCatalogByChannel = snapshot.catalogByChannel.filterKeys(activeChannelIds::contains),
                    emoteLiveProviders = snapshot.liveProviders,
                    emoteCatalogErrorMessage = snapshot.errorMessage,
                    betterTtvEmotesByChannel = if (state.betterTtvEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.BETTER_TTV, channel.id)
                        }
                    } else emptyMap(),
                    frankerFaceZEmotesByChannel = if (state.frankerFaceZEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.FRANKER_FACE_Z, channel.id)
                        }
                    } else emptyMap(),
                    sevenTvEmotesByChannel = if (state.sevenTvEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.SEVEN_TV, channel.id)
                        }
                    } else emptyMap(),
                )
                val changedChannels = changedThirdPartyCatalogChannels(
                    before = state,
                    after = next,
                    candidateChannelIds = activeChannelIds,
                )
                next.copy(messagesByChannel = reprocessThirdPartyEmotes(next, changedChannels))
            }
            val resolvedChannels = activeChannels.filterNot { it.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX) }
            emoteRepository.startLiveUpdates(
                scope = scope,
                channels = resolvedChannels,
                snapshot = snapshot,
                enabledProviders = enabledProviders,
            ) { _, _ ->
                emoteLiveRefreshJob?.cancel()
                emoteLiveRefreshJob = scope.launch {
                    delay(700)
                    if (mutableState.value.isAnonymous) refreshAnonymousChatAssets()
                }
            }
        }
    }

    private suspend fun loadAnonymousBadgeAssets(
        channels: List<ChatChannel>,
    ): AnonymousBadgeSnapshot? {
        val serverUrl = BuildConfig.FERVENTIO_SERVER_URL.trim().removeSuffix("/")
        val resolvedChannels = channels.filterNot { it.id.startsWith(ANONYMOUS_CHANNEL_ID_PREFIX) }
        return coroutineScope {
            val globalTwitchDeferred = async {
                if (serverUrl.isBlank()) null
                else runCatching { api.getPublicGlobalChatBadges(serverUrl) }.getOrNull()
            }
            val globalFfzDeferred = async {
                if (!settingsStore.showBadges) null
                else runCatching { api.getFrankerFaceZBadgesByUserId() }.getOrNull()
            }
            val channelSemaphore = Semaphore(4)
            val channelDeferred = resolvedChannels.map { channel ->
                async {
                    channelSemaphore.withPermit {
                        val twitchDeferred = async {
                            if (serverUrl.isBlank()) null
                            else runCatching {
                                api.getPublicChannelChatBadges(serverUrl, channel.id)
                            }.getOrNull()
                        }
                        val ffzDeferred = async {
                            if (!settingsStore.showBadges) null
                            else runCatching {
                                api.getFrankerFaceZChannelBadgesByUserId(channel.id)
                            }.getOrNull()
                        }
                        Triple(channel.id, twitchDeferred.await(), ffzDeferred.await())
                    }
                }
            }
            val globalAssets = globalTwitchDeferred.await()
            val globalFfzBadges = globalFfzDeferred.await()
            val perChannel = channelDeferred.awaitAll()
            val channelAssets = perChannel.mapNotNull { (channelId, assets, _) ->
                assets?.let { channelId to it }
            }.toMap()
            val channelFfzBadges = perChannel.mapNotNull { (channelId, _, badges) ->
                badges?.let { channelId to it }
            }.toMap()
            if (globalAssets == null && globalFfzBadges == null &&
                channelAssets.isEmpty() && channelFfzBadges.isEmpty()
            ) {
                null
            } else {
                AnonymousBadgeSnapshot(
                    globalAssets = globalAssets,
                    channelAssets = channelAssets,
                    globalFfzBadges = globalFfzBadges,
                    channelFfzBadges = channelFfzBadges,
                )
            }
        }
    }

    private fun connectAnonymousChat(channels: List<ChatChannel> = mutableState.value.channels) {
        stopEventSub()
        stopAnonymousChat()
        stopPinnedMessageRefreshes(clearSnapshots = true)
        if (channels.isEmpty()) {
            mutableState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    connectionDetail = null,
                    connectionAttempt = 0,
                    lastConnectionError = null,
                )
            }
            return
        }
        val client = TwitchAnonymousChatClient(
            channels = channels,
            onStatusChanged = ::applyConnectionUpdate,
            onEvent = ::enqueueChatEvent,
            onRoomResolved = ::onAnonymousRoomResolved,
            onNotice = { notice ->
                mutableState.update { state -> state.copy(lastConnectionError = notice.take(300)) }
            },
            onError = { error ->
                mutableState.update { state -> state.copy(lastConnectionError = error.userMessage()) }
            },
        )
        anonymousChatClient = client
        anonymousChatJob = scope.launch {
            try {
                client.run()
            } finally {
                client.close()
                if (anonymousChatClient === client) {
                    anonymousChatClient = null
                    anonymousChatJob = null
                }
            }
        }
    }

    private fun onAnonymousRoomResolved(channelLogin: String, roomId: String) {
        if (roomId.isBlank() || mutableState.value.isAuthenticated) return
        val current = mutableState.value
        val oldChannel = current.channels.firstOrNull { it.login.equals(channelLogin, ignoreCase = true) } ?: return
        if (oldChannel.id == roomId) return
        val replacement = oldChannel.copy(id = roomId)
        remapAnonymousChannel(oldChannel.id, replacement)
        scope.launch {
            runCatching { historyRepository.saveChannel(replacement) }
            refreshAnonymousChatAssets()
        }
    }

    private fun remapAnonymousChannel(oldId: String, replacement: ChatChannel) {
        fun <T> Map<String, T>.remap(): Map<String, T> {
            val value = this[oldId]
            return if (value == null) this else (this - oldId) + (replacement.id to value)
        }
        fun List<String>.remapIds(): List<String> = map { if (it == oldId) replacement.id else it }.distinct()
        mutableState.update { state ->
            val oldMessages = state.messagesByChannel[oldId].orEmpty()
                .map { message -> message.copy(channelId = replacement.id, channelLogin = replacement.login) }
            val messages = if (oldId in state.messagesByChannel) {
                (state.messagesByChannel - oldId) + (replacement.id to oldMessages)
            } else state.messagesByChannel
            val updated = state.copy(
                channels = state.channels.map { if (it.id == oldId) replacement else it },
                selectedChannelId = if (state.selectedChannelId == oldId) replacement.id else state.selectedChannelId,
                pinnedChannelIds = state.pinnedChannelIds.remapIds(),
                recentChannelIds = state.recentChannelIds.remapIds(),
                visibleChannelIds = state.visibleChannelIds.mapTo(linkedSetOf()) { if (it == oldId) replacement.id else it },
                channelTabTitles = state.channelTabTitles.remap(),
                channelAttention = state.channelAttention.remap(),
                messageNavigationTargets = state.messageNavigationTargets.remap(),
                messagesByChannel = messages,
                scrollPositionsByChannel = state.scrollPositionsByChannel[oldId]?.let { position ->
                    (state.scrollPositionsByChannel - oldId) +
                        (replacement.id to position.copy(channelId = replacement.id))
                } ?: state.scrollPositionsByChannel,
                draftsByChannel = state.draftsByChannel.remap(),
                sentMessageHistoryByChannel = state.sentMessageHistoryByChannel.remap(),
                replyComposerTargets = state.replyComposerTargets.remap(),
                rateLimitsByChannel = state.rateLimitsByChannel.remap(),
                badgeAssetsByChannel = state.badgeAssetsByChannel.remap(),
                frankerFaceZChannelBadgesByChannel = state.frankerFaceZChannelBadgesByChannel.remap(),
                cheermoteAssetsByChannel = state.cheermoteAssetsByChannel.remap(),
                emoteCatalogByChannel = state.emoteCatalogByChannel.remap(),
                betterTtvEmotesByChannel = state.betterTtvEmotesByChannel.remap(),
                frankerFaceZEmotesByChannel = state.frankerFaceZEmotesByChannel.remap(),
                sevenTvEmotesByChannel = state.sevenTvEmotesByChannel.remap(),
                workspaceLayout = state.workspaceLayout.remapChannelId(oldId, replacement.id),
            )
            settingsStore.pinnedChannelIds = updated.pinnedChannelIds
            settingsStore.recentChannelIds = updated.recentChannelIds
            settingsStore.channelTabTitles = updated.channelTabTitles
            settingsStore.draftsByChannel = updated.draftsByChannel
            settingsStore.sentMessageHistoryByChannel = updated.sentMessageHistoryByChannel
            settingsStore.workspaceLayoutJson = WorkspaceLayoutCodec.encode(updated.workspaceLayout)
            updated
        }
        parsedThirdPartyEmotesByChannel.remove(oldId)?.let { catalog ->
            parsedThirdPartyEmotesByChannel[replacement.id] = catalog
        }
    }

    private fun stopAnonymousChat() {
        val client = anonymousChatClient
        val job = anonymousChatJob
        anonymousChatClient = null
        anonymousChatJob = null
        client?.close()
        job?.cancel()
    }

    private fun stopAllChatTransports() {
        stopEventSub()
        stopAnonymousChat()
        stopPinnedMessageRefreshes(clearSnapshots = true)
    }

    private fun reconnectCurrentTransport(force: Boolean, reason: String) {
        val current = mutableState.value
        if (current.channels.isEmpty()) return
        if (!force && !networkAvailable) return
        if (current.isAuthenticated) {
            val session = current.session ?: return
            val accessToken = credentials?.accessToken ?: return
            if (!force && eventSubJob?.isActive == true) return
            mutableState.update { it.copy(connectionDetail = "$reason; переподключаемся…") }
            connectEventSub(session, accessToken)
        } else {
            if (!force && anonymousChatJob?.isActive == true) return
            mutableState.update { it.copy(connectionDetail = "$reason; переподключаем чтение…") }
            connectAnonymousChat(current.channels)
        }
    }

    private fun stopAuthenticatedJobs() {
        settingsSyncJob?.cancel()
        settingsSyncJob = null
        profileHydrationJob?.cancel()
        profileHydrationJob = null
        chatAssetsJob?.cancel()
        chatAssetsJob = null
        emoteLiveRefreshJob?.cancel()
        emoteLiveRefreshJob = null
        twitchEmoteGeneration.incrementAndGet()
        selectedTwitchEmoteJob?.cancel()
        selectedTwitchEmoteJob = null
        scope.launch { emoteRepository.stopLiveUpdates() }
        scrollSaveJobs.values.forEach { job -> job.cancel() }
        scrollSaveJobs.clear()
        synchronized(pendingUserProfileIds) { pendingUserProfileIds.clear() }
    }

    private suspend fun loadPersistedChannelLogins(): List<String> {
        val saved = settingsStore.channelLogins
        if (saved.isNotEmpty()) return saved
        if (settingsStore.channelsExplicitlyEmpty) return emptyList()

        // Some pre-public builds could leave an empty preference while Room still contained
        // the actual channel list. A missing explicit-empty marker means this is legacy data
        // and may be safely recovered from the persisted channel table.
        val recovered = runCatching { historyRepository.loadAllChannels() }
            .getOrDefault(emptyList())
            .map(ChatChannel::login)
            .map { it.trim().lowercase() }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_CHANNELS)
        if (recovered.isNotEmpty()) {
            settingsStore.channelLogins = recovered
            if (settingsStore.selectedChannelLogin.isNullOrBlank()) {
                settingsStore.selectedChannelLogin = recovered.first()
            }
        }
        return recovered
    }

    private fun anonymousChannelId(login: String): String =
        "$ANONYMOUS_CHANNEL_ID_PREFIX${login.lowercase()}"

    private suspend fun restoreChannelsAndConnect(session: TwitchSession, accessToken: String) {
        val savedLogins = loadPersistedChannelLogins()
        if (savedLogins.isEmpty()) {
            mutableState.update {
                it.copy(
                    channels = emptyList(),
                    selectedChannelId = null,
                    messagesByChannel = emptyMap(),
                    scrollPositionsByChannel = emptyMap(),
                    isBootstrapping = false,
                    isChannelsLoading = false,
                    isHistoryLoading = false,
                    restoredHistoryMessageCount = 0,
                    historyErrorMessage = null,
                )
            }
            normalizeChannelPreferences(emptyList(), null)
            connectEventSub(session, accessToken)
            return
        }

        suspend fun loadHistorySnapshot(
            channels: List<ChatChannel>,
        ): Triple<Map<String, List<ChatMessage>>, Map<String, ChatScrollPosition>, String?> {
            if (channels.isEmpty() || !settingsStore.localHistoryEnabled) {
                return Triple(emptyMap(), emptyMap(), null)
            }
            val channelIds = channels.map(ChatChannel::id)
            var errorMessage: String? = null
            val messages = runCatching {
                historyRepository.loadRecentMessages(
                    channelIds = channelIds,
                    enabled = true,
                    limitPerChannel = settingsStore.localHistoryLimit,
                    retentionDays = settingsStore.localHistoryRetentionDays,
                    maxDatabaseSizeMb = settingsStore.localHistoryMaxSizeMb,
                )
            }.getOrElse { error ->
                errorMessage = "Не удалось восстановить сообщения: ${error.userMessage()}"
                emptyMap()
            }
            ChatMessageTextPreparation.warm(messages.values.flatten())
            val positions = runCatching {
                historyRepository.loadScrollPositions(channelIds)
            }.getOrElse { error ->
                errorMessage = errorMessage ?: "Не удалось восстановить позицию: ${error.userMessage()}"
                emptyMap()
            }
            return Triple(messages, positions, errorMessage)
        }

        val cachedChannels = runCatching {
            historyRepository.loadChannels(savedLogins)
        }.getOrElse { emptyList() }
        val cachedByLogin = cachedChannels.associateBy { it.login.lowercase() }
        val selectedLogin = settingsStore.selectedChannelLogin?.lowercase()

        if (cachedChannels.isNotEmpty()) {
            mutableState.update { it.copy(isHistoryLoading = settingsStore.localHistoryEnabled) }
            val (cachedMessages, cachedPositions, cachedError) = loadHistorySnapshot(cachedChannels)
            val cachedSelected = selectedLogin
                ?.let(cachedByLogin::get)
                ?: cachedChannels.firstOrNull()
            mutableState.update {
                it.copy(
                    channels = cachedChannels,
                    selectedChannelId = cachedSelected?.id,
                    messagesByChannel = cachedChannels.associate { channel ->
                        channel.id to cachedMessages[channel.id].orEmpty().takeLast(MAX_MESSAGES_PER_CHANNEL)
                    },
                    scrollPositionsByChannel = cachedPositions,
                    isBootstrapping = false,
                    isChannelsLoading = true,
                    isHistoryLoading = false,
                    restoredHistoryMessageCount = cachedMessages.values.sumOf { messages -> messages.size },
                    historyErrorMessage = cachedError,
                )
            }
            normalizeChannelPreferences(cachedChannels, cachedSelected?.id)
        }

        val refreshedChannels = runCatching {
            withTimeout(CHANNEL_REFRESH_TIMEOUT_MILLIS) {
                api.getChannelsByLogins(session.clientId, accessToken, savedLogins)
            }
        }.getOrElse { error ->
            if (cachedChannels.isEmpty()) {
                mutableState.update {
                    it.copy(
                        isBootstrapping = false,
                        isChannelsLoading = false,
                        isHistoryLoading = false,
                        errorMessage = "Не удалось загрузить сохранённые каналы: ${error.userMessage()}",
                    )
                }
            }
            emptyList()
        }
        val refreshedByLogin = refreshedChannels.associateBy { it.login.lowercase() }
        val channels = savedLogins.mapNotNull { login ->
            refreshedByLogin[login.lowercase()] ?: cachedByLogin[login.lowercase()]
        }.distinctBy(ChatChannel::id)

        if (channels.isEmpty()) {
            mutableState.update {
                it.copy(
                    channels = emptyList(),
                    selectedChannelId = null,
                    messagesByChannel = emptyMap(),
                    scrollPositionsByChannel = emptyMap(),
                    isBootstrapping = false,
                    isChannelsLoading = false,
                    isHistoryLoading = false,
                    restoredHistoryMessageCount = 0,
                )
            }
            normalizeChannelPreferences(emptyList(), null)
            connectEventSub(session, accessToken)
            return
        }

        runCatching { historyRepository.saveChannels(channels) }
            .onFailure { error ->
                mutableState.update { state ->
                    state.copy(historyErrorMessage = "Не удалось обновить кэш каналов: ${error.userMessage()}")
                }
            }

        val (savedMessages, savedScrollPositions, historyError) = loadHistorySnapshot(channels)
        val selected = selectedLogin
            ?.let { login -> channels.firstOrNull { it.login.equals(login, ignoreCase = true) } }
            ?: channels.firstOrNull()

        mutableState.update {
            it.copy(
                channels = channels,
                selectedChannelId = selected?.id,
                messagesByChannel = channels.associate { channel ->
                    channel.id to savedMessages[channel.id].orEmpty().takeLast(MAX_MESSAGES_PER_CHANNEL)
                },
                scrollPositionsByChannel = savedScrollPositions,
                isBootstrapping = false,
                isChannelsLoading = false,
                isHistoryLoading = false,
                restoredHistoryMessageCount = savedMessages.values.sumOf { messages -> messages.size },
                historyErrorMessage = historyError ?: it.historyErrorMessage,
            )
        }
        normalizeChannelPreferences(channels, selected?.id)
        refreshTwitchChatAssets(session, accessToken, channels)
        savedMessages.values.flatten().forEach { message ->
            if (message.author.profileImageUrl.isNullOrBlank()) queueUserProfileHydration(message.userId)
        }
        connectEventSub(session, accessToken)
    }

    private fun refreshSelectedTwitchChannelEmotes(
        requestedChannelId: String? = mutableState.value.selectedChannelId,
        force: Boolean = false,
    ) {
        val current = mutableState.value
        val session = current.session ?: return
        val accessToken = credentials?.accessToken ?: return
        val channelId = requestedChannelId ?: return
        val channel = current.channels.firstOrNull { it.id == channelId } ?: return
        val generation = twitchEmoteGeneration.incrementAndGet()
        selectedTwitchEmoteJob?.cancel()
        selectedTwitchEmoteJob = scope.launch {
            val providerContext = EmoteProviderContext(
                twitchClientId = session.clientId,
                twitchAccessToken = accessToken,
                twitchUserId = session.userId,
            )
            val currentCatalog = mutableState.value.emoteCatalogByChannel[channel.id].orEmpty()
            emoteRepository.refreshTwitchChannel(
                context = providerContext,
                channel = channel,
                currentCatalog = currentCatalog,
                force = force,
            ).onSuccess { catalog ->
                if (generation != twitchEmoteGeneration.get()) return@onSuccess
                mutableState.update { state ->
                    if (state.channels.none { it.id == channel.id } ||
                        state.emoteCatalogByChannel[channel.id] == catalog
                    ) {
                        state
                    } else {
                        state.copy(
                            emoteCatalogByChannel = state.emoteCatalogByChannel + (channel.id to catalog),
                            emoteCatalogErrorMessage = null,
                        )
                    }
                }
            }.onFailure { error ->
                if (generation == twitchEmoteGeneration.get() &&
                    error !is CancellationException &&
                    mutableState.value.selectedChannelId == channel.id
                ) {
                    mutableState.update { state ->
                        state.copy(emoteCatalogErrorMessage = "Twitch: ${error.userMessage()}")
                    }
                }
            }
            if (generation == twitchEmoteGeneration.get()) selectedTwitchEmoteJob = null
        }
    }

    private fun refreshTwitchChatAssets(
        session: TwitchSession,
        accessToken: String,
        channels: List<ChatChannel> = mutableState.value.channels,
    ) {
        chatAssetsJob?.cancel()
        emoteLiveRefreshJob?.cancel()
        if (channels.isEmpty()) {
            scope.launch { emoteRepository.stopLiveUpdates() }
            mutableState.update {
                it.copy(
                    badgeAssetsByChannel = emptyMap(),
                    frankerFaceZBadgesByUserId = emptyMap(),
                    frankerFaceZChannelBadgesByChannel = emptyMap(),
                    cheermoteAssetsByChannel = emptyMap(),
                    emoteCatalogByChannel = emptyMap(),
                    emoteLiveProviders = emptySet(),
                    emoteCatalogErrorMessage = null,
                    betterTtvEmotesByChannel = emptyMap(),
                    frankerFaceZEmotesByChannel = emptyMap(),
                    sevenTvEmotesByChannel = emptyMap(),
                )
            }
            return
        }
        chatAssetsJob = scope.launch {
            val providerContext = EmoteProviderContext(
                twitchClientId = session.clientId,
                twitchAccessToken = accessToken,
                twitchUserId = session.userId,
            )
            val enabledProviders = buildSet {
                if (settingsStore.betterTtvEnabled) add(EmoteRepository.BETTER_TTV)
                if (settingsStore.frankerFaceZEnabled) add(EmoteRepository.FRANKER_FACE_Z)
                if (settingsStore.sevenTvEnabled) add(EmoteRepository.SEVEN_TV)
            }
            val globalBadgesDeferred = async {
                runCatching { api.getGlobalChatBadges(session.clientId, accessToken) }
                    .getOrDefault(emptyMap())
            }
            val ffzBadgesDeferred = async {
                if (!settingsStore.showBadges) emptyMap()
                else runCatching { api.getFrankerFaceZBadgesByUserId() }.getOrDefault(emptyMap())
            }
            val repositoryDeferred = async {
                emoteRepository.refresh(
                    context = providerContext,
                    channels = channels,
                    enabledProviders = enabledProviders,
                )
            }
            val perChannelDeferred = async {
                coroutineScope {
                    channels.map { channel ->
                        async {
                            val badgesDeferred = async {
                                runCatching {
                                    api.getChannelChatBadges(
                                        clientId = session.clientId,
                                        token = accessToken,
                                        broadcasterId = channel.id,
                                    )
                                }.getOrDefault(emptyMap())
                            }
                            val cheermotesDeferred = async {
                                runCatching {
                                    api.getCheermotes(
                                        clientId = session.clientId,
                                        token = accessToken,
                                        broadcasterId = channel.id,
                                    )
                                }.getOrDefault(emptyMap())
                            }
                            val ffzChannelBadgesDeferred = async {
                                if (!settingsStore.showBadges) emptyMap()
                                else runCatching {
                                    api.getFrankerFaceZChannelBadgesByUserId(channel.id)
                                }.getOrDefault(emptyMap())
                            }
                            channel.id to Triple(
                                badgesDeferred.await(),
                                cheermotesDeferred.await(),
                                ffzChannelBadgesDeferred.await(),
                            )
                        }
                    }.awaitAll().toMap()
                }
            }

            val globalBadges = globalBadgesDeferred.await()
            val ffzBadges = ffzBadgesDeferred.await()
            val snapshot = repositoryDeferred.await()
            val perChannel = perChannelDeferred.await()
            val activeChannelIds = mutableState.value.channels.map(ChatChannel::id).toSet()
            val activeChannels = channels.filter { it.id in activeChannelIds }

            mutableState.update { state ->
                val badges = activeChannels.associate { channel ->
                    channel.id to (globalBadges + perChannel[channel.id]?.first.orEmpty())
                }
                val next = state.copy(
                    badgeAssetsByChannel = badges,
                    frankerFaceZBadgesByUserId = if (state.showBadges) ffzBadges else emptyMap(),
                    frankerFaceZChannelBadgesByChannel = if (state.showBadges) {
                        activeChannels.associate { channel ->
                            channel.id to perChannel[channel.id]?.third.orEmpty()
                        }
                    } else {
                        emptyMap()
                    },
                    cheermoteAssetsByChannel = activeChannels.associate { channel ->
                        channel.id to perChannel[channel.id]?.second.orEmpty()
                    },
                    emoteCatalogByChannel = snapshot.catalogByChannel.filterKeys { it in activeChannelIds },
                    emoteLiveProviders = snapshot.liveProviders,
                    emoteCatalogErrorMessage = snapshot.errorMessage,
                    betterTtvEmotesByChannel = if (state.betterTtvEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.BETTER_TTV, channel.id)
                        }
                    } else emptyMap(),
                    frankerFaceZEmotesByChannel = if (state.frankerFaceZEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.FRANKER_FACE_Z, channel.id)
                        }
                    } else emptyMap(),
                    sevenTvEmotesByChannel = if (state.sevenTvEnabled) {
                        activeChannels.associate { channel ->
                            channel.id to snapshot.emotes(EmoteRepository.SEVEN_TV, channel.id)
                        }
                    } else emptyMap(),
                )
                val changedChannels = changedThirdPartyCatalogChannels(
                    before = state,
                    after = next,
                    candidateChannelIds = activeChannelIds,
                )
                next.copy(messagesByChannel = reprocessThirdPartyEmotes(next, changedChannels))
            }

            refreshSelectedTwitchChannelEmotes()
            emoteRepository.startLiveUpdates(
                scope = scope,
                channels = activeChannels,
                snapshot = snapshot,
                enabledProviders = enabledProviders,
            ) { _, _ ->
                emoteLiveRefreshJob?.cancel()
                emoteLiveRefreshJob = scope.launch {
                    delay(700)
                    refreshChatAssetsForCurrentSession()
                }
            }
        }
    }

    private fun connectEventSub(session: TwitchSession, accessToken: String) {
        stopAnonymousChat()
        val channels = mutableState.value.channels
        if (channels.isEmpty()) {
            stopEventSub()
            mutableState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.DISCONNECTED,
                    connectionDetail = null,
                    connectionAttempt = 0,
                    lastConnectionError = null,
                    eventSubNoticeChannelIds = emptySet(),
                    eventSubNoticeFailures = emptyMap(),
                )
            }
            return
        }

        val generation = eventSubGeneration.incrementAndGet()
        val previousJob = eventSubJob
        val previousClient = eventSubClient

        previousClient?.close()
        previousJob?.cancel()
        eventSubConnectionStartedAtMillis = System.currentTimeMillis()
        mutableState.update {
            it.copy(
                connectionStatus = ConnectionStatus.CONNECTING,
                connectionDetail = "Запускаем EventSub для ${channels.size} каналов…",
                connectionAttempt = 0,
                lastConnectionError = null,
                eventSubNoticeChannelIds = emptySet(),
                eventSubNoticeFailures = emptyMap(),
            )
        }

        val newJob = scope.launch {
            eventSubMutex.withLock {
                previousJob?.cancelAndJoin()
                if (generation != eventSubGeneration.get()) return@withLock

                val client = TwitchEventSubClient(
                    onStatusChanged = { update ->
                        if (generation == eventSubGeneration.get()) {
                            applyConnectionUpdate(update)
                        }
                    },
                    onSessionReady = { sessionId ->
                        if (generation != eventSubGeneration.get()) {
                            throw CancellationException("EventSub connection superseded")
                        }
                        createInitialEventSubSubscription(
                            generation = generation,
                            session = session,
                            accessToken = accessToken,
                            sessionId = sessionId,
                            channels = channels,
                        )
                    },
                    onEvent = { event ->
                        if (generation == eventSubGeneration.get()) enqueueChatEvent(event)
                    },
                    onActivity = { activity ->
                        generation == eventSubGeneration.get() && recordEventSubActivity(activity)
                    },
                    onRevocation = { revocation ->
                        if (generation == eventSubGeneration.get()) {
                            handleEventSubRevocation(revocation)
                        }
                    },
                    onMalformedEnvelope = { error ->
                        if (generation == eventSubGeneration.get()) {
                            recordMalformedEventSubEnvelope(error)
                        }
                    },
                    onError = { error ->
                        if (generation == eventSubGeneration.get()) {
                            val message = error.userMessage()
                            mutableState.update {
                                it.copy(lastConnectionError = message)
                            }
                            if (error.hasUnauthorizedApiCause()) {
                                scope.launch {
                                    runCatching { refreshAfterUnauthorized(accessToken) }
                                        .onFailure { refreshError ->
                                            if (refreshError.isPermanentAuthenticationFailure()) {
                                                terminateSession(
                                                    "Twitch-сессия завершена: ${refreshError.userMessage()}",
                                                )
                                            } else {
                                                mutableState.update {
                                                    it.copy(
                                                        lastConnectionError =
                                                            "Обновление Twitch-токена: ${refreshError.userMessage()}",
                                                    )
                                                }
                                            }
                                        }
                                }
                            }
                        }
                    },
                )
                eventSubClient = client
                try {
                    client.run()
                } finally {
                    client.close()
                    if (generation == eventSubGeneration.get()) {
                        eventSubClient = null
                        eventSubJob = null
                    }
                }
            }
        }

        eventSubJob = newJob
    }

    private fun reconnectEventSubIfIdle(reason: String) {
        val current = mutableState.value
        val session = current.session
        val accessToken = credentials?.accessToken
        if (!EventSubRecoveryPolicy.shouldReconnect(
                isAuthenticated = current.isAuthenticated,
                channelCount = current.channels.size,
                hasActiveJob = eventSubJob?.isActive == true,
                hasSession = session != null,
                hasAccessToken = accessToken != null,
                networkAvailable = networkAvailable,
            )
        ) {
            return
        }
        mutableState.update { it.copy(connectionDetail = "$reason; переподключаемся…") }
        connectEventSub(requireNotNull(session), requireNotNull(accessToken))
    }

    private fun applyConnectionUpdate(update: EventSubConnectionUpdate) {
        val now = System.currentTimeMillis()
        mutableState.update { current ->
            if ((update.status == ConnectionStatus.CONNECTING &&
                    current.connectionStatus != ConnectionStatus.CONNECTING) ||
                (update.status == ConnectionStatus.RECONNECTING &&
                    (current.connectionStatus != ConnectionStatus.RECONNECTING ||
                        current.connectionAttempt != update.attempt))
            ) {
                eventSubConnectionStartedAtMillis = now
            }
            val becameConnected = update.status == ConnectionStatus.CONNECTED &&
                current.connectionStatus != ConnectionStatus.CONNECTED
            val reconnectIncrement = if (
                update.status == ConnectionStatus.RECONNECTING &&
                (current.connectionStatus != ConnectionStatus.RECONNECTING ||
                    current.connectionAttempt != update.attempt)
            ) {
                1
            } else {
                0
            }
            current.copy(
                connectionStatus = update.status,
                connectionDetail = update.detail,
                connectionAttempt = update.attempt,
                eventSubConnectedAtMillis = if (becameConnected) now else current.eventSubConnectedAtMillis,
                lastConnectionDurationMillis = if (becameConnected) {
                    eventSubConnectionStartedAtMillis?.let { started -> (now - started).coerceAtLeast(0L) }
                } else {
                    current.lastConnectionDurationMillis
                },
                eventSubReconnectCount = current.eventSubReconnectCount + reconnectIncrement,
                lastConnectionError = when {
                    update.error != null -> update.error
                    update.status == ConnectionStatus.CONNECTED -> null
                    else -> current.lastConnectionError
                },
                errorMessage = update.error?.let { "EventSub: $it" } ?: current.errorMessage,
            )
        }
    }

    private fun recordEventSubActivity(activity: EventSubActivity): Boolean {
        val messageId = activity.messageId
        if (!messageId.isNullOrBlank() &&
            !seenEventSubMessageIds.addIfNew(messageId, activity.receivedAtMillis)
        ) {
            mutableState.update {
                it.copy(eventSubDuplicateCount = it.eventSubDuplicateCount + 1)
            }
            return false
        }
        val now = System.currentTimeMillis()
        while (true) {
            val previous = lastEventSubActivityPublishedAtMillis.get()
            if (now - previous < EVENTSUB_ACTIVITY_PUBLISH_INTERVAL_MILLIS) break
            if (lastEventSubActivityPublishedAtMillis.compareAndSet(previous, now)) {
                mutableState.update {
                    it.copy(
                        lastEventSubActivityAtMillis = activity.receivedAtMillis,
                        lastEventSubActivityType = activity.type,
                    )
                }
                break
            }
        }
        return true
    }

    private fun enqueueChatEvent(event: ChatEvent) {
        eventQueue.trySend(event)
    }

    private fun clearEventQueue() {
        while (eventQueue.tryReceive().isSuccess) {
            // Drain events from a previous account/session.
        }
    }

    private fun recordMalformedEventSubEnvelope(error: Throwable) {
        mutableState.update {
            it.copy(
                eventSubMalformedEnvelopeCount = it.eventSubMalformedEnvelopeCount + 1,
                lastConnectionError = error.userMessage(),
            )
        }
    }

    private fun handleEventSubRevocation(revocation: EventSubRevocation) {
        val type = revocation.subscriptionType.ifBlank { "неизвестная подписка" }
        val status = revocation.status.ifBlank { "неизвестная причина" }
        if (status == TwitchEventSubClient.AUTHORIZATION_REVOKED) {
            terminateSession("Twitch отозвал авторизацию EventSub. Выполни вход заново.")
            return
        }

        val message = "Twitch отозвал подписку $type: $status"
        mutableState.update {
            it.copy(
                connectionDetail = message,
                lastConnectionError = message,
                errorMessage = message,
            )
        }
    }

    private suspend fun createInitialEventSubSubscription(
        generation: Long,
        session: TwitchSession,
        accessToken: String,
        sessionId: String,
        channels: List<ChatChannel>,
    ): EventSubSessionSetup {
        val bootstrapFailures = mutableListOf<EventSubSubscriptionAttempt>()
        val createdSubscriptions = linkedSetOf<Pair<String, String>>()
        var bootstrapChannel: ChatChannel? = null

        var bootstrapNoticeReady = false
        for (channel in channels) {
            val chatError = createSubscriptionAttempt(
                session = session,
                accessToken = accessToken,
                sessionId = sessionId,
                channel = channel,
                type = PRIMARY_EVENT_TYPE,
            )
            if (!EventSubBootstrapPolicy.acceptPrimaryChat(chatError)) {
                bootstrapFailures += EventSubSubscriptionAttempt(
                    channel,
                    PRIMARY_EVENT_TYPE,
                    requireNotNull(chatError),
                )
                continue
            }

            // Reading messages is the only mandatory bootstrap subscription. System notices
            // are useful but optional: Twitch may reject them independently because of scope,
            // account role, duplicate subscription, or a temporary API condition. Waiting for
            // both subscriptions on every saved channel made an otherwise readable chat look
            // like an endless connection attempt.
            createdSubscriptions += channel.id to PRIMARY_EVENT_TYPE
            bootstrapChannel = channel

            val noticeError = createSubscriptionAttempt(
                session = session,
                accessToken = accessToken,
                sessionId = sessionId,
                channel = channel,
                type = NOTICE_EVENT_TYPE,
            )
            if (noticeError == null) {
                createdSubscriptions += channel.id to NOTICE_EVENT_TYPE
                bootstrapNoticeReady = true
                mutableState.update { current ->
                    current.copy(
                        eventSubNoticeChannelIds = current.eventSubNoticeChannelIds + channel.id,
                        eventSubNoticeFailures = current.eventSubNoticeFailures - channel.login,
                    )
                }
            } else {
                bootstrapFailures += EventSubSubscriptionAttempt(channel, NOTICE_EVENT_TYPE, noticeError)
                mutableState.update { current ->
                    current.copy(
                        eventSubNoticeFailures = current.eventSubNoticeFailures +
                            (channel.login to noticeError.userMessage()),
                    )
                }
            }
            break
        }

        val connectedChannel = bootstrapChannel ?: run {
            val firstError = bootstrapFailures.firstNotNullOfOrNull { it.error?.userMessage() }
            throw EventSubSetupException(
                buildString {
                    append("Не удалось подключить чат и системные события ни одного канала")
                    firstError?.let { append(": $it") }
                },
                bootstrapFailures.firstNotNullOfOrNull { it.error },
            )
        }

        CoroutineScope(currentCoroutineContext()).launch {
            createRemainingEventSubSubscriptions(
                generation = generation,
                session = session,
                accessToken = accessToken,
                sessionId = sessionId,
                channels = channels,
                alreadyCreated = createdSubscriptions,
            )
        }

        return EventSubSessionSetup(
            subscriptionCount = createdSubscriptions.size,
            detail = EventSubBootstrapPolicy.connectedDetail(
                channelLogin = connectedChannel.login,
                noticeReady = bootstrapNoticeReady,
            ),
        )
    }

    private suspend fun createRemainingEventSubSubscriptions(
        generation: Long,
        session: TwitchSession,
        accessToken: String,
        sessionId: String,
        channels: List<ChatChannel>,
        alreadyCreated: Set<Pair<String, String>>,
    ) = coroutineScope {
        val semaphore = Semaphore(EVENTSUB_SETUP_CONCURRENCY)
        val requests = channels.flatMap { channel ->
            EVENT_TYPES.mapNotNull { type ->
                when {
                    (channel.id to type) in alreadyCreated -> null
                    type in MODERATOR_EVENT_TYPES && channel.id !in mutableState.value.moderatedChannelIds -> null
                    else -> channel to type
                }
            }
        }

        val attempts = requests.map { (channel, type) ->
            async {
                semaphore.withPermit {
                    EventSubSubscriptionAttempt(
                        channel = channel,
                        type = type,
                        error = createSubscriptionAttempt(
                            session = session,
                            accessToken = accessToken,
                            sessionId = sessionId,
                            channel = channel,
                            type = type,
                        ),
                    )
                }
            }
        }.awaitAll()

        if (generation != eventSubGeneration.get()) return@coroutineScope

        val successful = attempts.filter { it.error == null }
        val allSuccessful = alreadyCreated + successful.map { it.channel.id to it.type }
        val readableChannelIds = allSuccessful
            .filter { (_, type) -> type == PRIMARY_EVENT_TYPE }
            .mapTo(linkedSetOf()) { (channelId, _) -> channelId }
        val noticeChannelIds = allSuccessful
            .filter { (_, type) -> type == NOTICE_EVENT_TYPE }
            .mapTo(linkedSetOf()) { (channelId, _) -> channelId }
        val unreadableChannels = channels.filterNot { it.id in readableChannelIds }
        val noticeFailures = attempts
            .filter { it.type == NOTICE_EVENT_TYPE && it.error != null }
            .associate { it.channel.login to requireNotNull(it.error).userMessage() }
        val failedOtherOptional = attempts.count {
            it.error != null && it.type != PRIMARY_EVENT_TYPE && it.type != NOTICE_EVENT_TYPE
        }

        mutableState.update { current ->
            current.copy(
                eventSubNoticeChannelIds = noticeChannelIds,
                eventSubNoticeFailures = noticeFailures,
                connectionDetail = buildString {
                    append("Чатов: ${readableChannelIds.size}/${channels.size}")
                    append("; системных событий: ${noticeChannelIds.size}/${channels.size}")
                    append("; подписок: ${allSuccessful.size}")
                    if (failedOtherOptional > 0) append("; дополнительных ошибок: $failedOtherOptional")
                },
                errorMessage = when {
                    unreadableChannels.isNotEmpty() -> {
                        "EventSub не смог читать каналы: ${unreadableChannels.joinToString { "#${it.login}" }}"
                    }
                    noticeFailures.isNotEmpty() -> {
                        "Системные события не подключены: ${noticeFailures.keys.joinToString { "#$it" }}"
                    }
                    else -> current.errorMessage
                },
            )
        }
    }

    private suspend fun createSubscriptionAttempt(
        session: TwitchSession,
        accessToken: String,
        sessionId: String,
        channel: ChatChannel,
        type: String,
    ): Throwable? = try {
        createEventSubSubscriptionWithConflictRetry(
            session = session,
            accessToken = accessToken,
            sessionId = sessionId,
            channel = channel,
            type = type,
        )
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        failure
    }

    private suspend fun createEventSubSubscriptionWithConflictRetry(
        session: TwitchSession,
        accessToken: String,
        sessionId: String,
        channel: ChatChannel,
        type: String,
    ) {
        var conflictAttempt = 0
        while (true) {
            try {
                api.createEventSubSubscription(
                    clientId = session.clientId,
                    token = accessToken,
                    sessionId = sessionId,
                    broadcasterId = channel.id,
                    userId = session.userId,
                    type = type,
                    version = eventSubVersion(type),
                    identityConditionKey = eventSubIdentityConditionKey(type),
                )
                return
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (conflict: EventSubSubscriptionConflictException) {
                conflictAttempt += 1
                if (conflictAttempt >= EVENTSUB_CONFLICT_MAX_ATTEMPTS) throw conflict
                delay(EVENTSUB_CONFLICT_RETRY_DELAYS_MILLIS[conflictAttempt - 1])
            }
        }
    }

    private fun stopPinnedMessageRefreshes(clearSnapshots: Boolean) {
        pinnedMessageRequestGenerations.values.forEach { generation -> generation.incrementAndGet() }
        pinnedMessageRefreshJobs.values.forEach { job -> job.cancel() }
        pinnedMessageRefreshJobs.clear()
        pinnedMessageRequestGenerations.clear()
        if (clearSnapshots) {
            mutableState.update { state ->
                if (state.pinnedMessagesByChannel.isEmpty()) state
                else state.copy(pinnedMessagesByChannel = emptyMap())
            }
        }
    }

    private fun stopEventSub() {
        eventSubGeneration.incrementAndGet()
        val job = eventSubJob
        val client = eventSubClient
        eventSubJob = null
        eventSubClient = null
        client?.close()
        job?.cancel()
    }

    private fun startTokenValidation() {
        stopTokenValidation()
        tokenValidationJob = scope.launch {
            var nextDelaySeconds = TOKEN_LEASE_RENEW_INTERVAL_SECONDS
            while (true) {
                delay(nextDelaySeconds.seconds)
                val credential = backendCredential ?: tokenStore.load() ?: return@launch
                val previousLease = credentials
                try {
                    val activeLease = obtainAccessLease(credential, forceRefresh = false)
                    val activeSession = activeLease.session
                    val changed = previousLease?.accessToken != activeLease.accessToken ||
                        mutableState.value.session != activeSession
                    mutableState.update {
                        it.copy(
                            session = activeSession,
                            clientId = activeSession.clientId,
                            lastConnectionError = cachedLeaseWarningOrNull(),
                        )
                    }
                    if (changed) connectEventSub(activeSession, activeLease.accessToken)
                    nextDelaySeconds = TOKEN_LEASE_RENEW_INTERVAL_SECONDS
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (error.isPermanentAuthenticationFailure()) {
                        terminateSession("Сессия Ferventio завершена: ${error.userMessage()}")
                        return@launch
                    }
                    mutableState.update {
                        it.copy(lastConnectionError = "Обновление серверной сессии: ${error.userMessage()}")
                    }
                    nextDelaySeconds = TOKEN_LEASE_RETRY_SECONDS
                }
            }
        }
    }

    private fun stopTokenValidation() {
        tokenValidationJob?.cancel()
        tokenValidationJob = null
    }

    private fun cancelServerAuthorization(updateState: Boolean) {
        serverAuthorizationJob?.cancel()
        serverAuthorizationJob = null
        settingsStore.clearPendingAuth()
        if (updateState) {
            mutableState.update {
                it.copy(isAuthorizing = false, pendingExternalUri = null)
            }
        }
    }

    private fun terminateSession(message: String) {
        stopAllChatTransports()
        stopTokenValidation()
        stopAuthenticatedJobs()
        tokenStore.clear()
        settingsStore.clearPendingAuth()
        backendCredential = null
        credentials = null
        accessLeaseFallbackActive = false
        seenEventSubMessageIds.clear()
        clearEventQueue()
        onSessionEnded()
        scope.launch {
            restoreAnonymousChannelsAndConnect(
                warning = message,
                reauthorizationRequired = true,
            )
        }
    }

    private fun handleChatEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.Message -> appendMessage(event.message)
            is ChatEvent.AutoModHeld -> {
                val message = event.message
                mutableState.update { state ->
                    val updated = (listOf(message) + state.moderation.autoModQueue
                        .filterNot { it.messageId == message.messageId })
                        .take(MAX_AUTOMOD_QUEUE_ITEMS)
                    state.copy(moderation = state.moderation.copy(autoModQueue = updated))
                }
                if (settingsStore.autoModNotificationsEnabled) onAutoModHeld(message)
            }
            is ChatEvent.AutoModUpdated -> {
                val message = event.message
                mutableState.update { state ->
                    val existing = state.moderation.autoModQueue
                    val updated = if (existing.any { it.messageId == message.messageId }) {
                        existing.map { current ->
                            if (current.messageId != message.messageId) current
                            else current.copy(
                                status = message.status,
                                text = message.text.ifBlank { current.text },
                                fragments = message.fragments.ifEmpty { current.fragments },
                                reason = message.reason ?: current.reason,
                                category = message.category ?: current.category,
                                level = message.level ?: current.level,
                                boundaries = message.boundaries.ifEmpty { current.boundaries },
                                decidedByUserId = message.decidedByUserId,
                                decidedByUserLogin = message.decidedByUserLogin,
                                decidedByUserName = message.decidedByUserName,
                            )
                        }
                    } else {
                        (listOf(message) + existing).take(MAX_AUTOMOD_QUEUE_ITEMS)
                    }
                    state.copy(moderation = state.moderation.copy(autoModQueue = updated))
                }
            }
            is ChatEvent.ModerationPerformed -> {
                val action = event.action
                mutableState.update { state ->
                    val remote = (listOf(action) + state.moderation.remoteHistory
                        .filterNot { it.id == action.id })
                        .take(MAX_MODERATION_HISTORY_ITEMS)
                    state.copy(moderation = state.moderation.copy(remoteHistory = remote))
                }
                appendMessage(SystemChatMessages.moderation(action))
            }
            is ChatEvent.ChatSettingsUpdated -> {
                mutableState.update { state ->
                    if (state.moderation.selectedChannelId != event.settings.channelId) state
                    else state.copy(moderation = state.moderation.copy(chatSettings = event.settings))
                }
            }
            is ChatEvent.MessageDeleted -> {
                markMessageDeleted(event.channelId, event.messageId)
                if (event.channelId !in mutableState.value.moderatedChannelIds) {
                    val channelLogin = mutableState.value.channels
                        .firstOrNull { it.id == event.channelId }?.login.orEmpty()
                    appendMessage(
                        SystemChatMessages.messageDeleted(
                            channelId = event.channelId,
                            channelLogin = channelLogin,
                            messageId = event.messageId,
                            eventId = event.eventId,
                            createdAt = event.createdAt,
                        ),
                    )
                }
            }
            is ChatEvent.UserMessagesCleared -> {
                mutableState.update { state ->
                    val updated = state.messagesByChannel[event.channelId].orEmpty().map { message ->
                        if (message.userId == event.userId) {
                            message.copy(
                                flags = message.flags.copy(isDeleted = true),
                                moderation = ModerationState(
                                    action = ModerationAction.TIMEOUT,
                                    atMillis = System.currentTimeMillis(),
                                ),
                            )
                        } else {
                            message
                        }
                    }
                    state.copy(messagesByChannel = state.messagesByChannel + (event.channelId to updated))
                }
                scope.launch {
                    runCatching { historyRepository.markUserMessagesDeleted(event.channelId, event.userId) }
                }
                // Moderated channels also receive channel.moderate with richer actor/reason data.
                // Avoid showing two notices for the same timeout/ban there.
                if (event.channelId !in mutableState.value.moderatedChannelIds) {
                    val channelLogin = mutableState.value.channels
                        .firstOrNull { it.id == event.channelId }?.login.orEmpty()
                    appendMessage(
                        SystemChatMessages.userMessagesCleared(
                            channelId = event.channelId,
                            channelLogin = channelLogin,
                            userId = event.userId,
                            userLogin = event.userLogin,
                            durationSeconds = event.durationSeconds,
                            isPermanent = event.isPermanent,
                            eventId = event.eventId,
                            createdAt = event.createdAt,
                        ),
                    )
                }
            }

            is ChatEvent.ChatCleared -> {
                mutableState.update { state ->
                    state.copy(messagesByChannel = state.messagesByChannel + (event.channelId to emptyList()))
                }
                scope.launch { runCatching { historyRepository.clearChannel(event.channelId) } }
                if (event.channelId !in mutableState.value.moderatedChannelIds) {
                    val channelLogin = mutableState.value.channels
                        .firstOrNull { it.id == event.channelId }?.login.orEmpty()
                    appendMessage(
                        SystemChatMessages.chatCleared(
                            channelId = event.channelId,
                            channelLogin = channelLogin,
                            eventId = event.eventId,
                            createdAt = event.createdAt,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun restoreAttentionEntries() {
        val stored = runCatching { historyRepository.loadAttentionEntries(MAX_ATTENTION_ENTRIES) }
            .getOrElse { error ->
                mutableState.update { state ->
                    state.copy(historyErrorMessage = state.historyErrorMessage ?: "Mentions: ${error.userMessage()}")
                }
                emptyList()
            }
        val channels = mutableState.value.channels
        val remapped = stored.map { entry ->
            val channel = channels.firstOrNull { candidate ->
                candidate.id == entry.channelId || candidate.login.equals(entry.channelLogin, ignoreCase = true)
            }
            if (channel == null || channel.id == entry.channelId) entry else entry.copy(channelId = channel.id)
        }
        mutableState.update { state ->
            val restoredAttention = remapped.filterNot(AttentionEntry::isRead)
                .groupBy(AttentionEntry::channelId)
                .mapValues { (_, entries) ->
                    ChannelAttention(
                        unreadCount = entries.size.coerceAtMost(MAX_ATTENTION_COUNT),
                        mentionCount = entries.size.coerceAtMost(MAX_ATTENTION_COUNT),
                        firstUnreadMessageId = entries.minByOrNull(AttentionEntry::timestampMillis)?.messageId,
                    )
                }
            val mergedChannelAttention = restoredAttention.entries.fold(state.channelAttention) { accumulated, item ->
                val previous = accumulated[item.key]
                val restored = item.value
                accumulated + (
                    item.key to if (previous == null) {
                        restored
                    } else {
                        previous.copy(
                            unreadCount = maxOf(previous.unreadCount, restored.unreadCount),
                            mentionCount = maxOf(previous.mentionCount, restored.mentionCount),
                            firstUnreadMessageId = previous.firstUnreadMessageId ?: restored.firstUnreadMessageId,
                        )
                    }
                )
            }
            state.copy(
                attentionEntries = remapped,
                mentionUnreadCount = remapped.count { !it.isRead },
                channelAttention = mergedChannelAttention,
            )
        }
        if (remapped != stored) {
            runCatching { historyRepository.saveAttentionEntries(remapped) }
        }
        rebuildMessageRuleEvaluation()
    }

    private fun rebuildMessageRuleEvaluation() {
        messageRuleRebuildJob?.cancel()
        val snapshot = mutableState.value
        val evaluator = MessageRuleEvaluator.compile(
            highlights = snapshot.highlightRules,
            ignores = snapshot.ignoreRules,
            session = snapshot.session,
        )
        messageRuleEvaluator = evaluator
        val messages = snapshot.messagesByChannel.values.flatten()
        val existingAttention = snapshot.attentionEntries
        messageRuleRebuildJob = scope.launch(Dispatchers.Default) {
            val decorations = HashMap<String, MessageDecoration>(messages.size)
            val generatedAttention = ArrayList<AttentionEntry>()
            val existingById = existingAttention.associateBy(AttentionEntry::messageId)
            messages.forEachIndexed { index, message ->
                if (index % RULE_REBUILD_CANCELLATION_INTERVAL == 0) {
                    currentCoroutineContext().ensureActive()
                }
                if (message.isSystem) return@forEachIndexed
                val decoration = evaluator.evaluate(message)
                if (decoration.isHighlighted || decoration.isIgnored) decorations[message.id] = decoration
                if (!decoration.isIgnored) {
                    val directMention = evaluator.isDirectMention(message)
                    val highlightMention = decoration.isHighlighted && decoration.addToMentions
                    if ((directMention || highlightMention) && message.id !in existingById) {
                        generatedAttention += AttentionEntry(
                            messageId = message.id,
                            channelId = message.channelId,
                            channelLogin = message.channelLogin,
                            authorId = message.userId,
                            authorLogin = message.userLogin,
                            authorDisplayName = message.userDisplayName,
                            text = message.text,
                            timestamp = message.timestamp,
                            timestampMillis = message.timestampMillis,
                            isRead = true,
                            isDirectMention = directMention,
                            isHighlight = highlightMention,
                            highlightReasons = decoration.highlightReasons,
                            highlightColorArgb = decoration.highlightColorArgb,
                        )
                    }
                }
            }
            if (generatedAttention.isNotEmpty()) {
                runCatching { historyRepository.saveAttentionEntries(generatedAttention) }
            }
            val evaluatedMessageIds = messages.asSequence().map(ChatMessage::id).toSet()
            mutableState.update { state ->
                val combinedAttention = (generatedAttention + state.attentionEntries)
                    .distinctBy(AttentionEntry::messageId)
                    .sortedByDescending(AttentionEntry::timestampMillis)
                    .take(MAX_ATTENTION_ENTRIES)
                state.copy(
                    messageDecorationsById = state.messageDecorationsById
                        .filterKeys { it !in evaluatedMessageIds } + decorations,
                    attentionEntries = combinedAttention,
                    mentionUnreadCount = combinedAttention.count { !it.isRead },
                )
            }
        }
    }

    private fun appendMessage(message: ChatMessage) {
        if (message.id.isNotBlank() &&
            !seenMessageIds.addIfNew(message.id, System.currentTimeMillis())
        ) {
            return
        }

        val current = mutableState.value
        val enrichedMessage = enrichThirdPartyEmotes(message, current)
        val isSystemMessage = enrichedMessage.isSystem
        ChatMessageTextPreparation.warm(enrichedMessage)
        val decoration = if (isSystemMessage) MessageDecoration() else messageRuleEvaluator.evaluate(enrichedMessage)
        val directMention = !isSystemMessage && !decoration.isIgnored &&
            messageRuleEvaluator.isDirectMention(enrichedMessage)
        val activeScrollPosition = liveScrollPositions[enrichedMessage.channelId]
            ?: current.scrollPositionsByChannel[enrichedMessage.channelId]
        val isVisibleLive = ChannelReadPolicy.isLiveVisible(
            channelId = enrichedMessage.channelId,
            visibleChannelIds = current.visibleChannelIds,
            scrollPosition = activeScrollPosition,
        )
        val isOwnMessage = enrichedMessage.userId == current.session?.userId
        val addHighlightToMentions = decoration.isHighlighted && decoration.addToMentions
        val shouldRecordAttention = !isSystemMessage && !decoration.isIgnored &&
            (directMention || addHighlightToMentions)
        val attentionEntry = if (shouldRecordAttention) {
            AttentionEntry(
                messageId = enrichedMessage.id,
                channelId = enrichedMessage.channelId,
                channelLogin = enrichedMessage.channelLogin,
                authorId = enrichedMessage.userId,
                authorLogin = enrichedMessage.userLogin,
                authorDisplayName = enrichedMessage.userDisplayName,
                text = enrichedMessage.text,
                timestamp = enrichedMessage.timestamp,
                timestampMillis = enrichedMessage.timestampMillis,
                isRead = isVisibleLive || isOwnMessage,
                isDirectMention = directMention,
                isHighlight = addHighlightToMentions,
                highlightReasons = decoration.highlightReasons,
                highlightColorArgb = decoration.highlightColorArgb,
            )
        } else {
            null
        }

        mutableState.update { state ->
            val existing = state.messagesByChannel[enrichedMessage.channelId].orEmpty()
            val ownUserId = state.session?.userId
            // Only our own echoed messages can reconcile an optimistic row. Scanning the full
            // channel history for every message from every other chatter created avoidable work
            // and GC pressure in fast channels.
            val optimisticIndex = if (ownUserId != null && enrichedMessage.userId == ownUserId) {
                existing.indexOfLast { outgoing ->
                    outgoing.outgoingState != OutgoingMessageState.NONE &&
                        outgoing.userId == ownUserId &&
                        (
                            outgoing.serverMessageId == enrichedMessage.id ||
                                (
                                    outgoing.text == enrichedMessage.text &&
                                        outgoing.reply?.parentMessageId == enrichedMessage.reply?.parentMessageId &&
                                        kotlin.math.abs(outgoing.timestampMillis - enrichedMessage.timestampMillis) <=
                                        OPTIMISTIC_RECONCILE_WINDOW_MILLIS
                                    )
                            )
                }
            } else {
                -1
            }
            val mergedMessage = if (optimisticIndex >= 0) {
                val optimistic = existing[optimisticIndex]
                enrichedMessage.copy(
                    outgoingState = OutgoingMessageState.SENT,
                    clientNonce = optimistic.clientNonce,
                    serverMessageId = enrichedMessage.id,
                )
            } else enrichedMessage
            val memoryLimit = if (state.localHistoryEnabled) {
                minOf(MAX_MESSAGES_PER_CHANNEL, state.localHistoryLimit)
            } else {
                MAX_MESSAGES_PER_CHANNEL
            }
            val updated = if (optimisticIndex >= 0) {
                existing.toMutableList().apply { this[optimisticIndex] = mergedMessage }
            } else {
                appendBoundedMessage(existing, mergedMessage, memoryLimit)
            }
            val attention = if (isSystemMessage || isVisibleLive || isOwnMessage) {
                state.channelAttention
            } else {
                val previous = state.channelAttention[enrichedMessage.channelId] ?: ChannelAttention()
                state.channelAttention + (
                    enrichedMessage.channelId to previous.copy(
                        unreadCount = (previous.unreadCount + 1).coerceAtMost(MAX_ATTENTION_COUNT),
                        mentionCount = (previous.mentionCount + if (shouldRecordAttention) 1 else 0)
                            .coerceAtMost(MAX_ATTENTION_COUNT),
                        firstUnreadMessageId = previous.firstUnreadMessageId ?: enrichedMessage.id,
                    )
                    )
            }
            val nextAttentionEntries = if (attentionEntry == null) {
                state.attentionEntries
            } else {
                (listOf(attentionEntry) + state.attentionEntries.filterNot { it.messageId == attentionEntry.messageId })
                    .take(MAX_ATTENTION_ENTRIES)
            }
            val droppedMessageId = if (
                optimisticIndex < 0 && existing.size >= memoryLimit && existing.isNotEmpty()
            ) {
                existing.first().id
            } else {
                null
            }
            val retainedDecorations = droppedMessageId
                ?.let(state.messageDecorationsById::minus)
                ?: state.messageDecorationsById
            state.copy(
                messagesByChannel = state.messagesByChannel + (enrichedMessage.channelId to updated),
                channelAttention = attention,
                attentionEntries = nextAttentionEntries,
                mentionUnreadCount = state.mentionUnreadCount +
                    if (attentionEntry != null && !attentionEntry.isRead &&
                        state.attentionEntries.none { it.messageId == attentionEntry.messageId && !it.isRead }
                    ) 1 else 0,
                messageDecorationsById = when {
                    decoration.isHighlighted || decoration.isIgnored -> {
                        retainedDecorations + (enrichedMessage.id to decoration)
                    }
                    enrichedMessage.id in retainedDecorations -> retainedDecorations - enrichedMessage.id
                    else -> retainedDecorations
                },
                rateLimitsByChannel = if (isOwnMessage) {
                    state.rateLimitsByChannel - enrichedMessage.channelId
                } else {
                    state.rateLimitsByChannel
                },
            )
        }
        val session = current.session
        if (!isSystemMessage &&
            settingsStore.replyNotificationsEnabled &&
            session != null &&
            enrichedMessage.userId != session.userId &&
            enrichedMessage.reply?.parentUserId == session.userId
        ) {
            onReplyReceived(enrichedMessage)
        }
        if (!isSystemMessage && decoration.isHighlighted && !isOwnMessage &&
            (decoration.playSound || decoration.push)
        ) {
            onHighlightAlert(
                HighlightAlert(
                    message = enrichedMessage,
                    reasons = decoration.highlightReasons,
                    playSound = decoration.playSound,
                    push = decoration.push,
                ),
            )
        }
        if (!isSystemMessage && mutableState.value.isAuthenticated && mutableState.value.showAvatars) {
            queueUserProfileHydration(enrichedMessage.author.id)
        }
        if (!isSystemMessage) {
            historyWriteQueue.trySend(
                HistoryWriteRequest(
                    message = enrichedMessage,
                    attention = attentionEntry,
                ),
            )
        }
    }


    private fun appendBoundedMessage(
        existing: List<ChatMessage>,
        message: ChatMessage,
        limit: Int,
    ): List<ChatMessage> {
        if (limit <= 0) return emptyList()
        val keepExisting = minOf(existing.size, limit - 1)
        val fromIndex = existing.size - keepExisting
        return ArrayList<ChatMessage>(keepExisting + 1).apply {
            if (keepExisting > 0) addAll(existing.subList(fromIndex, existing.size))
            add(message)
        }
    }

    private fun refreshModeratedChannels(session: TwitchSession) {
        scope.launch {
            val result = runCatching {
                withAuthenticationRetry { context ->
                    api.getModeratedChannelIds(
                        clientId = context.session.clientId,
                        token = context.accessToken,
                        userId = context.session.userId,
                    )
                }
            }
            val moderated = result.getOrNull()?.plus(session.userId) ?: return@launch
            mutableState.update { state ->
                val currentModerationChannel = state.moderation.selectedChannelId
                    ?.takeIf(moderated::contains)
                    ?: state.selectedChannelId?.takeIf(moderated::contains)
                    ?: state.channels.firstOrNull { it.id in moderated }?.id
                state.copy(
                    moderatedChannelIds = moderated,
                    moderation = state.moderation.copy(selectedChannelId = currentModerationChannel),
                )
            }
            mutableState.value.selectedChannelId?.let(::refreshPinnedMessage)
        }
    }

    private fun queueUserProfileHydration(userId: String) {
        if (userId.isBlank() || userId.startsWith("anonymous:")) return
        val current = mutableState.value.userProfilesById[userId]
        if (current?.profileImageUrl?.isNotBlank() == true) return
        synchronized(pendingUserProfileIds) {
            pendingUserProfileIds += userId
            if (profileHydrationJob?.isActive == true) return
            profileHydrationJob = scope.launch {
                delay(350)
                while (true) {
                    val ids = synchronized(pendingUserProfileIds) {
                        pendingUserProfileIds.take(100).also { batch ->
                            pendingUserProfileIds.removeAll(batch.toSet())
                        }
                    }
                    if (ids.isEmpty()) break
                    val session = mutableState.value.session ?: break
                    val accessToken = credentials?.accessToken ?: break
                    runCatching {
                        val users = api.getUsersByIds(session.clientId, accessToken, ids)
                        val colors = api.getChatColors(session.clientId, accessToken, ids)
                        historyRepository.saveUsers(users, colors)
                        val profiles = users.associateBy(TwitchUser::id)
                        mutableState.update { state ->
                            state.copy(
                                userProfilesById = mergeUserProfiles(state.userProfilesById, profiles),
                                userColorsById = mergeUserColors(state.userColorsById, colors),
                            )
                        }
                    }
                }
                profileHydrationJob = null
            }
        }
    }

    private fun moderateChannel(
        channelId: String,
        action: suspend (ModerationContext) -> Unit,
    ) {
        if (channelId !in mutableState.value.moderatedChannelIds) {
            showError("У тебя нет прав модератора в этом канале")
            return
        }
        moderate(action)
    }

    private fun markMessageDeleted(channelId: String, messageId: String) {
        mutableState.update { state ->
            val updated = state.messagesByChannel[channelId].orEmpty().map { message ->
                if (message.id == messageId) {
                    message.copy(
                        flags = message.flags.copy(isDeleted = true),
                        moderation = ModerationState(
                            action = ModerationAction.DELETE,
                            atMillis = System.currentTimeMillis(),
                        ),
                    )
                } else {
                    message
                }
            }
            state.copy(messagesByChannel = state.messagesByChannel + (channelId to updated))
        }
        scope.launch { runCatching { historyRepository.markMessageDeleted(channelId, messageId) } }
    }

    private fun moderate(action: suspend (ModerationContext) -> Unit) {
        if (mutableState.value.session == null) return showError("Войди через Twitch для модерации")
        if (credentials == null) return showError("Нет OAuth-токена")
        scope.launch {
            runCatching { withAuthenticationRetry(action) }
                .onFailure { error ->
                    // A moderation endpoint may legitimately answer 400/403 for request-specific
                    // reasons. Do not destroy the account session outside the OAuth refresh path.
                    showError(error.userMessage())
                }
        }
    }

    private fun ensureRequiredScopes(session: TwitchSession) {
        val missing = BASE_REQUIRED_SCOPES.filterNot(session.scopes::contains)
        require(missing.isEmpty()) {
            "Twitch не выдал необходимые разрешения: ${missing.joinToString()}"
        }
    }

    private fun showNotice(message: String) {
        mutableState.update { it.copy(errorMessage = message) }
    }

    private fun showError(message: String) {
        mutableState.update { it.copy(errorMessage = message) }
    }

    private fun changedThirdPartyCatalogChannels(
        before: FerventioUiState,
        after: FerventioUiState,
        candidateChannelIds: Set<String>,
    ): Set<String> = candidateChannelIds.filterTo(linkedSetOf()) { channelId ->
        before.betterTtvEnabled != after.betterTtvEnabled ||
            before.frankerFaceZEnabled != after.frankerFaceZEnabled ||
            before.sevenTvEnabled != after.sevenTvEnabled ||
            before.betterTtvEmotesByChannel[channelId] != after.betterTtvEmotesByChannel[channelId] ||
            before.frankerFaceZEmotesByChannel[channelId] != after.frankerFaceZEmotesByChannel[channelId] ||
            before.sevenTvEmotesByChannel[channelId] != after.sevenTvEmotesByChannel[channelId]
    }

    private fun rebuildThirdPartyEmoteCache(
        state: FerventioUiState,
        channelIds: Collection<String>,
    ) {
        channelIds.forEach { channelId ->
            parsedThirdPartyEmotesByChannel[channelId] = ThirdPartyEmoteCatalogResolver.merge(
                betterTtv = if (state.betterTtvEnabled) {
                    state.betterTtvEmotesByChannel[channelId].orEmpty()
                } else emptyMap(),
                frankerFaceZ = if (state.frankerFaceZEnabled) {
                    state.frankerFaceZEmotesByChannel[channelId].orEmpty()
                } else emptyMap(),
                sevenTv = if (state.sevenTvEnabled) {
                    state.sevenTvEmotesByChannel[channelId].orEmpty()
                } else emptyMap(),
            )
        }
    }

    private fun reprocessThirdPartyEmotes(
        state: FerventioUiState,
        channelIds: Collection<String> = state.messagesByChannel.keys,
    ): Map<String, List<ChatMessage>> {
        if (channelIds.isEmpty()) return state.messagesByChannel
        rebuildThirdPartyEmoteCache(state, channelIds)
        var changed = false
        val result = state.messagesByChannel.toMutableMap()
        channelIds.forEach { channelId ->
            val messages = state.messagesByChannel[channelId] ?: return@forEach
            val updated = messages.map { message -> enrichThirdPartyEmotes(message, state) }
            if (updated != messages) {
                result[channelId] = updated
                changed = true
            }
        }
        return if (changed) result else state.messagesByChannel
    }

    private fun enrichThirdPartyEmotes(
        message: ChatMessage,
        state: FerventioUiState,
    ): ChatMessage {
        val baseMessage = removeThirdPartyEmoteFragments(message)
        val emotes = parsedThirdPartyEmotesByChannel[message.channelId] ?: run {
            rebuildThirdPartyEmoteCache(state, listOf(message.channelId))
            parsedThirdPartyEmotesByChannel[message.channelId].orEmpty()
        }
        return ThirdPartyEmoteParser.enrich(baseMessage, emotes)
    }

    private fun removeThirdPartyEmoteFragments(message: ChatMessage): ChatMessage {
        var changed = false
        var previousSourceWasEmote = false
        val fragments = buildList {
            fun appendText(value: String) {
                if (value.isEmpty()) return
                val previous = lastOrNull() as? ChatFragment.Text
                if (previous == null) add(ChatFragment.Text(value))
                else this[lastIndex] = previous.copy(text = previous.text + value)
            }

            message.fragments.forEach { fragment ->
                if (fragment is ChatFragment.ThirdPartyEmote) {
                    changed = true
                    // Parsing a composite removes the separator before the zero-width layer.
                    // Restore one while rebuilding the source text, otherwise a future catalog
                    // refresh would see "BaseOverlay" and could no longer resolve either token.
                    if (fragment.zeroWidth && previousSourceWasEmote) appendText(" ")
                    appendText(fragment.text)
                    previousSourceWasEmote = true
                } else {
                    add(fragment)
                    previousSourceWasEmote = when (fragment) {
                        is ChatFragment.TwitchEmote,
                        is ChatFragment.Gif,
                        is ChatFragment.Cheermote -> true
                        else -> false
                    }
                }
            }
        }
        return if (changed) message.copy(fragments = fragments) else message
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "кэш изображений"
        val units = listOf("Б", "КБ", "МБ", "ГБ")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex == 0) "${value.toLong()} ${units[unitIndex]}"
        else String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex])
    }

    private fun formatDuration(seconds: Int): String = when {
        seconds % 86_400 == 0 -> "${seconds / 86_400} д."
        seconds % 3_600 == 0 -> "${seconds / 3_600} ч."
        seconds % 60 == 0 -> "${seconds / 60} мин."
        else -> "$seconds сек."
    }

    private fun formatMinutes(minutes: Int): String = when {
        minutes % 43_200 == 0 -> "${minutes / 43_200} мес."
        minutes % 10_080 == 0 -> "${minutes / 10_080} нед."
        minutes % 1_440 == 0 -> "${minutes / 1_440} д."
        minutes % 60 == 0 -> "${minutes / 60} ч."
        else -> "$minutes мин."
    }

    private fun formatLongDuration(seconds: Long): String {
        val days = seconds / 86_400
        val hours = seconds % 86_400 / 3_600
        val minutes = seconds % 3_600 / 60
        return buildList {
            if (days > 0) add("$days д.")
            if (hours > 0 || days > 0) add("$hours ч.")
            add("$minutes мин.")
        }.joinToString(" ")
    }

    private fun commandHelpText(): String = buildString {
        append(CommandRegistry.builtIns.joinToString(" · ") { it.usage })
        val custom = mutableState.value.customCommands.filter(CustomCommand::enabled)
        if (custom.isNotEmpty()) {
            append("\nПользовательские: ")
            append(custom.joinToString(" · ") { "/${it.normalizedName}" })
        }
    }

    private data class EventSubSubscriptionAttempt(
        val channel: ChatChannel,
        val type: String,
        val error: Throwable?,
    )

    private data class ModerationContext(
        val session: TwitchSession,
        val accessToken: String,
    )

    private fun updateWorkspaceLayout(transform: (WorkspaceLayout) -> WorkspaceLayout) {
        mutableState.update { state ->
            val knownIds = state.channels.map(ChatChannel::id).toSet()
            val updated = transform(state.workspaceLayout).normalized(knownIds)
            val selectedFromLayout = updated.activeTab?.activeSplit?.channelId
                ?.takeIf(knownIds::contains)
            settingsStore.workspaceLayoutJson = WorkspaceLayoutCodec.encode(updated)
            selectedFromLayout
                ?.let { selectedId -> state.channels.firstOrNull { it.id == selectedId }?.login }
                ?.let { login -> settingsStore.selectedChannelLogin = login }
            state.copy(
                workspaceLayout = updated,
                selectedChannelId = selectedFromLayout ?: state.selectedChannelId?.takeIf(knownIds::contains),
            )
        }
    }

    private fun normalizeChannelPreferences(
        channels: List<ChatChannel>,
        selectedChannelId: String?,
    ) {
        val knownIds = channels.map(ChatChannel::id).toSet()
        liveScrollPositions.keys.retainAll(knownIds)
        val legacyFavoriteIds = settingsStore.favoriteChannelIds.intersect(knownIds)
        val pinnedIds = (settingsStore.pinnedChannelIds + legacyFavoriteIds)
            .filter(knownIds::contains)
            .distinct()
        val recentIds = settingsStore.recentChannelIds.filter(knownIds::contains).distinct().take(MAX_RECENT_CHANNELS)
        val channelTabTitles = settingsStore.channelTabTitles
            .filterKeys(knownIds::contains)
            .mapValues { (_, title) -> title.trim().take(32) }
            .filterValues(String::isNotEmpty)
        var layout = WorkspaceLayoutCodec.decodeOrDefault(
            raw = settingsStore.workspaceLayoutJson,
            fallbackChannelId = selectedChannelId ?: channels.firstOrNull()?.id,
        ).normalized(knownIds)
        if (layout.activeTab?.activeSplit?.channelId == null) {
            val fallback = selectedChannelId ?: channels.firstOrNull()?.id
            if (fallback != null) layout = layout.selectChannelInActiveSplit(fallback)
        }
        val navigationChannelId = mutableState.value.messageNavigationTargets.keys
            .lastOrNull()
            ?.takeIf(knownIds::contains)
        if (navigationChannelId != null) {
            layout = layout.selectChannelInActiveSplit(navigationChannelId)
        }
        val layoutChannelId = layout.activeTab?.activeSplit?.channelId?.takeIf(knownIds::contains)
        val resolvedSelectedId = navigationChannelId
            ?: selectedChannelId?.takeIf(knownIds::contains)
            ?: layoutChannelId
        resolvedSelectedId
            ?.let { selectedId -> channels.firstOrNull { it.id == selectedId }?.login }
            ?.let { login -> settingsStore.selectedChannelLogin = login }
        settingsStore.favoriteChannelIds = emptySet()
        settingsStore.pinnedChannelIds = pinnedIds
        settingsStore.recentChannelIds = recentIds
        settingsStore.channelTabTitles = channelTabTitles
        settingsStore.workspaceLayoutJson = WorkspaceLayoutCodec.encode(layout)
        val drafts = settingsStore.draftsByChannel.filterKeys(knownIds::contains)
        val sentHistory = settingsStore.sentMessageHistoryByChannel.filterKeys(knownIds::contains)
        settingsStore.draftsByChannel = drafts
        settingsStore.sentMessageHistoryByChannel = sentHistory
        mutableState.update { state ->
            state.copy(
                selectedChannelId = resolvedSelectedId,
                pinnedChannelIds = pinnedIds,
                recentChannelIds = recentIds,
                channelTabTitles = channelTabTitles,
                workspaceLayout = layout,
                visibleChannelIds = state.visibleChannelIds.intersect(knownIds),
                channelAttention = state.channelAttention.filterKeys(knownIds::contains),
                messageNavigationTargets = state.messageNavigationTargets.filterKeys(knownIds::contains),
                replyComposerTargets = state.replyComposerTargets.filterKeys(knownIds::contains),
                draftsByChannel = drafts,
                sentMessageHistoryByChannel = sentHistory,
                rateLimitsByChannel = state.rateLimitsByChannel.filterKeys(knownIds::contains),
            )
        }
    }

    private fun mergeUserProfiles(
        current: Map<String, TwitchUser>,
        incoming: Map<String, TwitchUser>,
    ): Map<String, TwitchUser> {
        userProfileCache.putAll(current)
        userProfileCache.putAll(incoming)
        return userProfileCache.snapshot()
    }

    private fun mergeUserColors(
        current: Map<String, String>,
        incoming: Map<String, String>,
    ): Map<String, String> {
        userColorCache.putAll(current)
        userColorCache.putAll(incoming.filterValues(String::isNotBlank))
        return userColorCache.snapshot()
    }

    fun startPerformanceScenario(
        initialMessageCount: Int,
        messagesPerSecond: Int = 0,
        durationSeconds: Int = 0,
    ) {
        performanceScenarioActive = true
        authRestoreJob?.cancel()
        authRestoreJob = null
        serverAuthorizationJob?.cancel()
        serverAuthorizationJob = null
        stopAllChatTransports()
        stopTokenValidation()
        settingsSyncJob?.cancel()
        settingsSyncJob = null
        performanceScenarioJob?.cancel()
        performanceScenarioJob = scope.launch {
            val channel = ChatChannel(
                id = PERFORMANCE_CHANNEL_ID,
                login = PERFORMANCE_CHANNEL_LOGIN,
                displayName = "Performance",
            )
            val safeInitialCount = initialMessageCount.coerceIn(1, PERFORMANCE_MAX_MESSAGES)
            val initialMessages = withContext(Dispatchers.Default) {
                buildPerformanceMessages(0, safeInitialCount)
            }
            ChatMessageTextPreparation.warm(initialMessages)
            mutableState.update { state ->
                state.copy(
                    isBootstrapping = false,
                    channels = listOf(channel),
                    selectedChannelId = channel.id,
                    visibleChannelIds = setOf(channel.id),
                    workspaceLayout = WorkspaceLayout.default(channel.id),
                    messagesByChannel = mapOf(channel.id to initialMessages),
                    historyPagingByChannel = mapOf(
                        channel.id to HistoryPagingState(
                            endReached = true,
                            loadedCount = initialMessages.size,
                        ),
                    ),
                    connectionStatus = ConnectionStatus.CONNECTED,
                    localHistoryEnabled = false,
                    isHistoryLoading = false,
                    restoredHistoryMessageCount = initialMessages.size,
                    errorMessage = null,
                )
            }

            val safeRate = messagesPerSecond.coerceIn(0, PERFORMANCE_MAX_MESSAGES_PER_SECOND)
            val safeDuration = durationSeconds.coerceIn(0, PERFORMANCE_MAX_DURATION_SECONDS)
            if (safeRate == 0 || safeDuration == 0) return@launch
            val intervalMillis = (1_000L / safeRate).coerceAtLeast(1L)
            val total = safeRate * safeDuration
            repeat(total) { offset ->
                val message = buildPerformanceMessage(safeInitialCount + offset)
                ChatMessageTextPreparation.warm(message)
                mutableState.update { state ->
                    val existing = state.messagesByChannel[channel.id].orEmpty()
                    state.copy(
                        messagesByChannel = state.messagesByChannel + (
                            channel.id to (existing + message).takeLast(PERFORMANCE_MAX_MESSAGES)
                        ),
                    )
                }
                delay(intervalMillis)
            }
        }
    }

    private fun buildPerformanceMessages(startIndex: Int, count: Int): List<ChatMessage> =
        List(count) { offset -> buildPerformanceMessage(startIndex + offset) }

    private fun buildPerformanceMessage(index: Int): ChatMessage {
        val timestampMillis = PERFORMANCE_BASE_TIMESTAMP_MILLIS + index
        val login = "user${index % PERFORMANCE_USER_POOL_SIZE}"
        val text = when {
            index % 37 == 0 -> "@dev_dive performance mention $index"
            index % 11 == 0 -> "Kappa performance emote message $index"
            else -> "Performance message $index with stable rendering payload"
        }
        return ChatMessage(
            id = "perf-$index",
            channelId = PERFORMANCE_CHANNEL_ID,
            channelLogin = PERFORMANCE_CHANNEL_LOGIN,
            author = ChatAuthor(
                id = "perf-user-${index % PERFORMANCE_USER_POOL_SIZE}",
                login = login,
                displayName = "User ${index % PERFORMANCE_USER_POOL_SIZE}",
                color = PERFORMANCE_USER_COLORS[index % PERFORMANCE_USER_COLORS.size],
            ),
            text = text,
            fragments = listOf(ChatFragment.Text(text)),
            timestamp = Instant.ofEpochMilli(timestampMillis).toString(),
            timestampMillis = timestampMillis,
        )
    }

    private fun freshUiState(
        isBootstrapping: Boolean,
        errorMessage: String? = null,
    ): FerventioUiState = FerventioUiState(
        clientId = "",
        isBootstrapping = isBootstrapping,
        pinnedChannelIds = (settingsStore.pinnedChannelIds + settingsStore.favoriteChannelIds).distinct(),
        recentChannelIds = settingsStore.recentChannelIds,
        channelTabTitles = settingsStore.channelTabTitles,
        workspaceLayout = WorkspaceLayoutCodec.decodeOrDefault(settingsStore.workspaceLayoutJson),
        recentEmoteKeys = settingsStore.recentEmoteKeys,
        favoriteEmoteKeys = settingsStore.favoriteEmoteKeys,
        draftsByChannel = settingsStore.draftsByChannel,
        sentMessageHistoryByChannel = settingsStore.sentMessageHistoryByChannel,
        customCommands = CustomCommandCodec.decode(settingsStore.customCommandsJson).getOrDefault(emptyList()),
        sendOnEnter = settingsStore.sendOnEnter,
        showComposerEmoteImages = settingsStore.showComposerEmoteImages,
        userCardTimeoutPresetsSeconds = settingsStore.userCardTimeoutPresetsSeconds,
        userCardShowBanAction = settingsStore.userCardShowBanAction,
        userCardModerationActionOrder = settingsStore.userCardModerationActionOrder,
        replyNotificationsEnabled = settingsStore.replyNotificationsEnabled,
        highlightRules = settingsStore.highlightRules,
        ignoreRules = settingsStore.ignoreRules,
        savedMessageFilters = settingsStore.savedMessageFilters,
        moderation = ModerationUiState(
            autoModNotificationsEnabled = settingsStore.autoModNotificationsEnabled,
        ),
        localHistoryEnabled = settingsStore.localHistoryEnabled,
        localHistoryLimit = settingsStore.localHistoryLimit,
        localHistoryRetentionDays = settingsStore.localHistoryRetentionDays,
        localHistoryMaxSizeMb = settingsStore.localHistoryMaxSizeMb,
        appLanguage = settingsStore.appLanguage,
        themeMode = settingsStore.themeMode,
        fontScalePercent = settingsStore.fontScalePercent,
        messageDensity = settingsStore.messageDensity,
        chatNameStyle = settingsStore.chatNameStyle,
        wrapMessageLines = settingsStore.wrapMessageLines,
        mentionColorArgb = settingsStore.mentionColorArgb,
        autoScrollEnabled = settingsStore.autoScrollEnabled,
        showAvatars = settingsStore.showAvatars,
        showBadges = settingsStore.showBadges,
        showTimestamps = settingsStore.showTimestamps,
        showDeletedMessageContent = settingsStore.showDeletedMessageContent,
        showSystemMessages = settingsStore.showSystemMessages,
        animateEmotes = settingsStore.animateEmotes,
        emoteScalePercent = settingsStore.emoteScalePercent,
        betterTtvEnabled = settingsStore.betterTtvEnabled,
        frankerFaceZEnabled = settingsStore.frankerFaceZEnabled,
        sevenTvEnabled = settingsStore.sevenTvEnabled,
        settingsSyncEnabled = settingsStore.settingsSyncEnabled,
        settingsSyncRevision = settingsStore.settingsSyncRevision,
        settingsSyncLastSyncedAtMillis = settingsStore.settingsSyncLastSyncedAtMillis,
        settingsSyncStatus = if (settingsStore.settingsSyncEnabled) SettingsSyncStatus.IDLE else SettingsSyncStatus.DISABLED,
        errorMessage = errorMessage,
    )

    private fun cachedLeaseWarningOrNull(): String? = if (accessLeaseFallbackActive) {
        "Сервер Ferventio временно недоступен. Используется сохранённый Twitch access token; " +
            "обновление продолжится автоматически."
    } else {
        null
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf(String::isNotBlank) ?: this::class.simpleName.orEmpty()

    private fun eventSubVersion(type: String): String = when (type) {
        "automod.message.hold", "automod.message.update", "channel.moderate" -> "2"
        else -> "1"
    }

    private fun eventSubIdentityConditionKey(type: String): String = when (type) {
        "automod.message.hold", "automod.message.update", "channel.moderate" -> "moderator_user_id"
        else -> "user_id"
    }

    private companion object {
        val PUSH_ATTENTION_TYPES = setOf("mention", "reply", "highlight", "selected_user")
        const val MAX_CACHED_USERS = 1_500
        const val MAX_CACHED_USER_COLORS = 3_000
        const val HISTORY_PAGE_SIZE = 200
        const val PERFORMANCE_CHANNEL_ID = "performance"
        const val PERFORMANCE_CHANNEL_LOGIN = "performance"
        const val PERFORMANCE_MAX_MESSAGES = 10_000
        const val PERFORMANCE_MAX_MESSAGES_PER_SECOND = 100
        const val PERFORMANCE_MAX_DURATION_SECONDS = 600
        const val PERFORMANCE_USER_POOL_SIZE = 500
        const val PERFORMANCE_BASE_TIMESTAMP_MILLIS = 1_750_000_000_000L
        val PERFORMANCE_USER_COLORS = listOf(
            "#B89CFF", "#FFB4A7", "#7CDBA3", "#FFC66F", "#80CBC4", "#90CAF9",
        )
        const val MAX_MESSAGES_PER_CHANNEL = 5_000
        const val MAX_SEEN_IDS = 4_000
        const val SEEN_ID_TTL_MILLIS = 10 * 60 * 1_000L
        const val MAX_CHANNELS = 20
        const val MAX_ATTENTION_COUNT = 9_999
        const val MAX_ATTENTION_ENTRIES = 1_000
        const val MAX_MESSAGE_RULES = 100
        const val MAX_RULE_PATTERN_LENGTH = 240
        const val RULE_REBUILD_CANCELLATION_INTERVAL = 128
        const val MAX_RECENT_EMOTE_USES = 160
        const val MAX_DRAFT_LENGTH = 500
        const val MAX_SENT_MESSAGE_HISTORY = 50
        const val MAX_CUSTOM_COMMAND_DEPTH = 5
        const val SETTINGS_SYNC_DEBOUNCE_MILLIS = 1_500L
        const val MAX_USER_CARD_TIMEOUT_PRESETS = 10
        const val MAX_AUTOMOD_QUEUE_ITEMS = 200
        const val MAX_MODERATION_HISTORY_ITEMS = 300
        const val MAX_OBSERVED_CHATTERS = 1_000
        const val OBSERVED_CHATTERS_NOTICE =
            "Показаны пользователи, замеченные в загруженном чате. Полный список Twitch доступен владельцу или модератору."
        const val FALLBACK_CHATTERS_NOTICE =
            "Twitch не вернул полный список. Показаны пользователи, замеченные в загруженном чате."
        val DEFAULT_USER_CARD_TIMEOUT_PRESETS = listOf(10, 60, 600, 3_600, 86_400)
        const val OPTIMISTIC_RECONCILE_WINDOW_MILLIS = 30_000L
        const val TOKEN_LEASE_RENEW_INTERVAL_SECONDS = 45L
        const val TOKEN_LEASE_RETRY_SECONDS = 15L
        const val ANONYMOUS_CHANNEL_ID_PREFIX = "irc:"
        const val EVENTSUB_SETUP_CONCURRENCY = 4
        const val EVENT_QUEUE_CAPACITY = 512
        const val EVENTSUB_ACTIVITY_PUBLISH_INTERVAL_MILLIS = 1_000L
        const val SCROLL_SAVE_DEBOUNCE_MILLIS = 250L
        const val DRAFT_SAVE_DEBOUNCE_MILLIS = 450L
        const val CHANNEL_ORDER_SAVE_DEBOUNCE_MILLIS = 250L
        const val CHANNEL_REFRESH_TIMEOUT_MILLIS = 10_000L
        const val HISTORY_WRITE_BATCH_WINDOW_MILLIS = 32L
        const val HISTORY_WRITE_BATCH_SIZE = 48
        const val CLEAR_CHAT_SCOPE = "moderator:manage:chat_messages"
        const val EVENTSUB_CONFLICT_MAX_ATTEMPTS = 4
        val EVENTSUB_CONFLICT_RETRY_DELAYS_MILLIS = longArrayOf(250L, 500L, 1_000L)

        val BASE_REQUIRED_SCOPES = listOf(
            "user:read:chat",
            "user:write:chat",
            CLEAR_CHAT_SCOPE,
        )


        const val PRIMARY_EVENT_TYPE = "channel.chat.message"
        const val NOTICE_EVENT_TYPE = "channel.chat.notification"

        val EVENT_TYPES = listOf(
            PRIMARY_EVENT_TYPE,
            NOTICE_EVENT_TYPE,
            "channel.chat.message_delete",
            "channel.chat.clear_user_messages",
            "channel.chat.clear",
            "channel.chat_settings.update",
            "automod.message.hold",
            "automod.message.update",
            "channel.moderate",
        )

        val MODERATOR_EVENT_TYPES = setOf(
            "automod.message.hold",
            "automod.message.update",
            "channel.moderate",
        )
    }
}

private data class AnonymousBadgeSnapshot(
    val globalAssets: Map<String, ChatBadgeAsset>?,
    val channelAssets: Map<String, Map<String, ChatBadgeAsset>>,
    val globalFfzBadges: Map<String, List<ChatBadgeAsset>>?,
    val channelFfzBadges: Map<String, Map<String, List<ChatBadgeAsset>>>,
)

private data class HistoryWriteRequest(
    val message: ChatMessage,
    val attention: AttentionEntry? = null,
)

private fun secureStateEquals(left: String, right: String): Boolean {
    if (left.isEmpty() || left.length != right.length) return false
    var difference = 0
    left.indices.forEach { index ->
        difference = difference or (left[index].code xor right[index].code)
    }
    return difference == 0
}

private fun Throwable.isPermanentAuthenticationFailure(): Boolean = when (this) {
    is FerventioBackendException -> statusCode == 401 || statusCode == 403
    // A generic Twitch 403 usually means that a user lacks a channel permission; it must not log the
    // whole account out. OAuth refresh/validation failures are reported as 400 or 401.
    is TwitchApiException -> statusCode == 400 || statusCode == 401
    is IllegalStateException, is IllegalArgumentException -> message.orEmpty().let { text ->
        text.contains("Client ID", ignoreCase = true) ||
            text.contains("refresh token", ignoreCase = true) ||
            text.contains("необходимые разрешения", ignoreCase = true) ||
            text.contains("Серверная сессия Ferventio истекла", ignoreCase = true) ||
            text.contains("Токен выдан", ignoreCase = true)
    }
    else -> false
}

private fun Throwable.isTransientBackendFailure(): Boolean = when (this) {
    is FerventioBackendException -> statusCode == 408 || statusCode == 429 || statusCode in 500..599
    is java.io.IOException -> true
    else -> cause?.isTransientBackendFailure() == true
}

private fun Throwable.hasUnauthorizedApiCause(): Boolean = when (this) {
    is TwitchApiException -> statusCode == 401
    is TwitchChatSendException -> statusCode == 401
    else -> cause?.hasUnauthorizedApiCause() == true
}

private fun WorkspaceLayout.remapChannelId(oldId: String, newId: String): WorkspaceLayout = copy(
    workspaces = workspaces.map { workspace ->
        workspace.copy(
            tabs = workspace.tabs.map { tab ->
                tab.copy(
                    splits = tab.splits.map { split ->
                        if (split.channelId == oldId) split.withChannelId(newId) else split
                    },
                )
            },
        )
    },
)

private fun WorkspaceLayout.selectChannelInActiveSplit(channelId: String): WorkspaceLayout =
    mapActiveTab { tab ->
        val targetSplitId = tab.activeSplitId ?: tab.splits.firstOrNull()?.id
        tab.copy(
            splits = tab.splits.map { split ->
                if (split.id == targetSplitId) split.withChannelId(channelId) else split
            },
            activeSplitId = targetSplitId,
        )
    }

private fun WorkspaceLayout.mapActiveWorkspace(
    transform: (Workspace) -> Workspace,
): WorkspaceLayout {
    val activeId = activeWorkspaceId ?: workspaces.firstOrNull()?.id ?: return this
    return copy(workspaces = workspaces.map { workspace ->
        if (workspace.id == activeId) transform(workspace) else workspace
    })
}

private fun WorkspaceLayout.mapActiveTab(
    transform: (WorkspaceTab) -> WorkspaceTab,
): WorkspaceLayout = mapActiveWorkspace { workspace ->
    val activeTabId = workspace.activeTabId ?: workspace.tabs.firstOrNull()?.id ?: return@mapActiveWorkspace workspace
    workspace.copy(tabs = workspace.tabs.map { tab ->
        if (tab.id == activeTabId) transform(tab) else tab
    })
}

private fun ChatMessage.mentionsUser(session: TwitchSession?): Boolean {
    val current = session ?: return false
    if (reply?.parentUserId == current.userId) return true
    if (fragments.any { fragment ->
            fragment is ChatFragment.Mention && (
                fragment.userId == current.userId || fragment.userLogin.equals(current.login, ignoreCase = true)
            )
        }
    ) return true
    return Regex("(^|\\s)@${Regex.escape(current.login)}(?=\\s|$|[.,!?;:])", RegexOption.IGNORE_CASE)
        .containsMatchIn(text)
}
