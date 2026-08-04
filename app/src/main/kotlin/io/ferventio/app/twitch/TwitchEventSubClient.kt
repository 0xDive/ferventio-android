package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.AutoModBoundary
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ModerationChatSettings
import io.ferventio.app.domain.RemoteModerationAction
import io.ferventio.app.domain.ChatNotice
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ReplyContext
import io.ferventio.app.domain.ReplyTextNormalizer
import io.ferventio.app.domain.toEpochMillisOrNow
import io.ferventio.app.security.JsonInputGuard
import io.ferventio.app.security.SafeLog
import io.ferventio.app.security.SensitiveDataRedactor
import io.ferventio.app.domain.ConnectionStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.Closeable
import kotlin.math.min
import kotlin.random.Random

class TwitchEventSubClient(
    private val onStatusChanged: (EventSubConnectionUpdate) -> Unit,
    private val onSessionReady: suspend (sessionId: String) -> EventSubSessionSetup,
    private val onEvent: (ChatEvent) -> Unit,
    private val onActivity: (EventSubActivity) -> Boolean,
    private val onRevocation: (EventSubRevocation) -> Unit,
    private val onMalformedEnvelope: (Throwable) -> Unit,
    private val onError: (Throwable) -> Unit,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val jitterFraction: () -> Double = { Random.nextDouble() },
) : Closeable {
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            // OkHttp manages WebSocket frame limits internally. Setting maxFrameSize
            // through Ktor is unsupported by this engine and aborts the handshake.
            install(WebSockets)
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun run() {
        var socketUrl = DEFAULT_SOCKET_URL
        var createSubscriptions = true
        var reconnectAttempt = 0

        while (currentCoroutineContext().isActive) {
            var twitchReconnectUrl: String? = null
            var stopAfterRevocation = false
            try {
                publishStatus(
                    status = if (reconnectAttempt == 0) {
                        ConnectionStatus.CONNECTING
                    } else {
                        ConnectionStatus.RECONNECTING
                    },
                    detail = connectionAttemptDetail(reconnectAttempt),
                    attempt = reconnectAttempt,
                )
                SafeLog.i(TAG, "Opening EventSub WebSocket: ${SensitiveDataRedactor.urlForLog(socketUrl)}")

                client.webSocket(urlString = socketUrl) {
                    publishStatus(
                        status = ConnectionStatus.WAITING_WELCOME,
                        detail = "Ожидаем session_welcome от Twitch…",
                        attempt = reconnectAttempt,
                    )
                    val welcome = receiveEnvelope(WELCOME_TIMEOUT_MILLIS)
                    onActivity(
                        EventSubActivity(
                            type = welcome.type,
                            messageId = welcome.messageId,
                            messageTimestamp = welcome.messageTimestamp,
                            receivedAtMillis = clockMillis(),
                        ),
                    )
                    require(welcome.type == "session_welcome") {
                        "Первое сообщение Twitch EventSub — ${welcome.type.ifBlank { "неизвестное" }}, ожидалось session_welcome"
                    }

                    val sessionId = welcome.sessionId
                        ?: error("Twitch не прислал идентификатор EventSub-сессии")
                    val keepaliveTimeoutSeconds = welcome.keepaliveTimeoutSeconds
                        ?.coerceIn(MIN_KEEPALIVE_SECONDS, MAX_KEEPALIVE_SECONDS)
                        ?: DEFAULT_KEEPALIVE_SECONDS

                    if (createSubscriptions) {
                        publishStatus(
                            status = ConnectionStatus.CREATING_SUBSCRIPTIONS,
                            detail = "Создаём подписки Twitch…",
                            attempt = reconnectAttempt,
                        )
                        val setup = onSessionReady(sessionId)
                        require(setup.subscriptionCount > 0) {
                            "EventSub-сессия не получила ни одной подписки"
                        }
                        publishStatus(
                            status = ConnectionStatus.CONNECTED,
                            detail = setup.detail,
                            attempt = 0,
                        )
                    } else {
                        publishStatus(
                            status = ConnectionStatus.CONNECTED,
                            detail = "Соединение перенесено Twitch",
                            attempt = 0,
                        )
                    }

                    reconnectAttempt = 0
                    SafeLog.i(TAG, "EventSub session ready")

                    while (currentCoroutineContext().isActive) {
                        val envelope = receiveEnvelope(
                            timeoutMillis = (keepaliveTimeoutSeconds + KEEPALIVE_GRACE_SECONDS) * 1_000L,
                        )
                        val isNewEnvelope = onActivity(
                            EventSubActivity(
                                type = envelope.type,
                                messageId = envelope.messageId,
                                messageTimestamp = envelope.messageTimestamp,
                                receivedAtMillis = clockMillis(),
                            ),
                        )
                        if (!isNewEnvelope &&
                            (envelope.type == "notification" || envelope.type == "revocation")
                        ) {
                            SafeLog.d(TAG, "Ignoring duplicate EventSub message: ${envelope.messageId}")
                            continue
                        }
                        envelope.parseError?.let { message ->
                            onMalformedEnvelope(EventSubPayloadException(message))
                        }

                        when (envelope.type) {
                            "session_reconnect" -> {
                                twitchReconnectUrl = envelope.reconnectUrl
                                    ?: error("Twitch прислал reconnect без reconnect_url")
                                publishStatus(
                                    status = ConnectionStatus.RECONNECTING,
                                    detail = "Twitch переносит WebSocket-сессию…",
                                    attempt = 0,
                                )
                                break
                            }

                            "notification" -> envelope.event?.let(onEvent)
                            "revocation" -> {
                                val revocation = EventSubRevocation(
                                    subscriptionType = envelope.subscriptionType.orEmpty(),
                                    status = envelope.revocationStatus.orEmpty(),
                                )
                                SafeLog.w(
                                    TAG,
                                    "EventSub subscription revoked: ${revocation.subscriptionType} (${revocation.status})",
                                )
                                onRevocation(revocation)
                                if (revocation.status == AUTHORIZATION_REVOKED) {
                                    stopAfterRevocation = true
                                    break
                                }
                            }

                            "session_keepalive" -> SafeLog.v(TAG, "EventSub keepalive")
                            "session_welcome" -> SafeLog.d(TAG, "Ignoring duplicate session_welcome")
                            else -> SafeLog.d(TAG, "Ignoring EventSub message type: ${envelope.type}")
                        }
                    }
                }

                if (stopAfterRevocation) {
                    return
                }

                if (twitchReconnectUrl != null) {
                    socketUrl = requireNotNull(twitchReconnectUrl)
                    createSubscriptions = false
                    continue
                }

                error("Twitch EventSub закрыл WebSocket без команды reconnect")
            } catch (timeout: TimeoutCancellationException) {
                val error = EventSubConnectionException(
                    "Twitch EventSub не ответил вовремя. Проверь сеть, VPN и поддержку WebSocket.",
                    timeout,
                )
                SafeLog.w(TAG, error.message.orEmpty(), timeout)
                onError(error)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (setup: EventSubSetupException) {
                SafeLog.e(TAG, "EventSub subscription setup failed", setup)
                publishStatus(
                    status = ConnectionStatus.FAILED,
                    detail = setup.message,
                    attempt = reconnectAttempt,
                    error = setup.message,
                )
                onError(setup)
                return
            } catch (error: Throwable) {
                SafeLog.w(TAG, "EventSub connection failed", error)
                onError(error)
            }

            reconnectAttempt += 1
            createSubscriptions = true
            socketUrl = DEFAULT_SOCKET_URL
            if (reconnectAttempt >= MAX_AUTOMATIC_RECONNECT_ATTEMPTS) {
                val error = EventSubConnectionException(
                    "Не удалось подключиться к EventSub после $reconnectAttempt попыток. Нажми «Переподключить EventSub» после проверки сети.",
                )
                publishStatus(
                    status = ConnectionStatus.FAILED,
                    detail = error.message,
                    attempt = reconnectAttempt,
                    error = error.message,
                )
                onError(error)
                return
            }

            val delayMillis = EventSubReconnectPolicy.delayMillis(
                attempt = reconnectAttempt,
                jitterFraction = jitterFraction().coerceIn(0.0, 1.0),
            )
            val delaySeconds = (delayMillis + 999L) / 1_000L
            publishStatus(
                status = ConnectionStatus.RECONNECTING,
                detail = "Повтор через $delaySeconds с (попытка $reconnectAttempt из $MAX_AUTOMATIC_RECONNECT_ATTEMPTS)",
                attempt = reconnectAttempt,
            )
            delay(delayMillis)
        }
    }

    override fun close() {
        if (clientDelegate.isInitialized()) {
            client.close()
        }
    }

    private suspend fun DefaultClientWebSocketSession.receiveEnvelope(
        timeoutMillis: Long,
    ): EventSubEnvelope = try {
        withTimeout(timeoutMillis) {
            while (true) {
                val frame = incoming.receive()
                if (frame !is Frame.Text) continue

                val raw = frame.readText()
                val envelope = runCatching { EventSubParser.parseEnvelope(raw) }
                    .onFailure { error: Throwable ->
                        SafeLog.w(TAG, "Ignoring malformed EventSub envelope", error)
                        onMalformedEnvelope(error)
                    }
                    .getOrNull()
                if (envelope != null) return@withTimeout envelope
            }
            error("Недостижимый код EventSub")
        }
    } catch (closed: ClosedReceiveChannelException) {
        val reason = closeReason.await()
        throw EventSubConnectionException(
            buildString {
                append("Twitch закрыл EventSub WebSocket")
                reason?.let { append(": ${it.code} ${it.message}") }
            },
            closed,
        )
    }

    private fun publishStatus(
        status: ConnectionStatus,
        detail: String?,
        attempt: Int,
        error: String? = null,
    ) {
        onStatusChanged(
            EventSubConnectionUpdate(
                status = status,
                detail = detail,
                attempt = attempt,
                error = error,
            ),
        )
    }

    private fun connectionAttemptDetail(attempt: Int): String =
        if (attempt == 0) "Открываем EventSub WebSocket…" else "Попытка переподключения: $attempt"

    companion object {
        const val TAG = "FerventioEventSub"
        const val DEFAULT_SOCKET_URL =
            "wss://eventsub.wss.twitch.tv/ws?keepalive_timeout_seconds=30"
        const val WELCOME_TIMEOUT_MILLIS = 15_000L
        const val DEFAULT_KEEPALIVE_SECONDS = 30
        const val MIN_KEEPALIVE_SECONDS = 10
        const val MAX_KEEPALIVE_SECONDS = 600
        const val KEEPALIVE_GRACE_SECONDS = 10
        const val MAX_AUTOMATIC_RECONNECT_ATTEMPTS = 5
        const val AUTHORIZATION_REVOKED = "authorization_revoked"
    }
}

data class EventSubConnectionUpdate(
    val status: ConnectionStatus,
    val detail: String?,
    val attempt: Int,
    val error: String? = null,
)

data class EventSubActivity(
    val type: String,
    val messageId: String?,
    val messageTimestamp: String?,
    val receivedAtMillis: Long,
)

data class EventSubRevocation(
    val subscriptionType: String,
    val status: String,
)

data class EventSubSessionSetup(
    val subscriptionCount: Int,
    val detail: String,
)

class EventSubSetupException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class EventSubConnectionException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

class EventSubPayloadException(
    message: String,
) : IllegalArgumentException(message)

data class EventSubEnvelope(
    val type: String,
    val messageId: String? = null,
    val messageTimestamp: String? = null,
    val sessionId: String? = null,
    val reconnectUrl: String? = null,
    val keepaliveTimeoutSeconds: Int? = null,
    val subscriptionType: String? = null,
    val revocationStatus: String? = null,
    val event: ChatEvent? = null,
    val parseError: String? = null,
)

object EventSubParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseEnvelope(raw: String): EventSubEnvelope {
        JsonInputGuard.requireWithinLimits(
            raw = raw,
            maxChars = MAX_EVENTSUB_ENVELOPE_CHARS,
            maxNestingDepth = MAX_EVENTSUB_JSON_DEPTH,
            inputName = "EventSub envelope",
        )
        val root = json.parseToJsonElement(raw) as? JsonObject
            ?: throw IllegalArgumentException("Корень сообщения EventSub не является JSON-объектом")
        val metadata = root.objectOrEmpty("metadata")
        val payload = root.objectOrEmpty("payload")
        val type = metadata.string("message_type").orEmpty()
        val messageId = metadata.string("message_id")
        val timestamp = metadata.string("message_timestamp")

        return when (type) {
            "session_welcome", "session_reconnect" -> {
                val session = payload.objectOrEmpty("session")
                EventSubEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    sessionId = session.string("id"),
                    reconnectUrl = session.string("reconnect_url"),
                    keepaliveTimeoutSeconds = session.int("keepalive_timeout_seconds"),
                )
            }

            "notification" -> {
                val subscription = payload.objectOrEmpty("subscription")
                val subscriptionType = subscription.string("type").orEmpty()
                val event = payload.objectOrEmpty("event")
                var parseFailure: Throwable? = null
                val parsedEvent = runCatching {
                    parseNotification(subscriptionType, event, timestamp.orEmpty(), messageId.orEmpty())
                }.onFailure { error ->
                    parseFailure = error
                    SafeLog.w(
                        TwitchEventSubClient.TAG,
                        "Ignoring malformed EventSub notification: $subscriptionType",
                        error,
                    )
                }.getOrNull()
                val eventWithMetadata = when (parsedEvent) {
                    is ChatEvent.Message -> ChatEvent.Message(
                        parsedEvent.message.copy(eventSubMessageId = messageId),
                    )
                    else -> parsedEvent
                }
                EventSubEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    subscriptionType = subscriptionType,
                    event = eventWithMetadata,
                    parseError = parseFailure?.message,
                )
            }

            "revocation" -> {
                val subscription = payload.objectOrEmpty("subscription")
                EventSubEnvelope(
                    type = type,
                    messageId = messageId,
                    messageTimestamp = timestamp,
                    subscriptionType = subscription.string("type")
                        ?: metadata.string("subscription_type"),
                    revocationStatus = subscription.string("status"),
                )
            }

            else -> EventSubEnvelope(
                type = type,
                messageId = messageId,
                messageTimestamp = timestamp,
            )
        }
    }

    private fun parseNotification(
        subscriptionType: String,
        event: JsonObject,
        timestamp: String,
        eventSubMessageId: String,
    ): ChatEvent? = when (subscriptionType) {
        "channel.chat.message" -> ChatEvent.Message(parseMessage(event, timestamp))
        "channel.chat.notification" -> ChatEvent.Message(parseChatNotification(event, timestamp))
        "channel.chat.message_delete" -> ChatEvent.MessageDeleted(
            channelId = event.string("broadcaster_user_id").orEmpty(),
            messageId = event.string("message_id").orEmpty(),
            eventId = eventSubMessageId.takeIf(String::isNotBlank),
            createdAt = timestamp.takeIf(String::isNotBlank),
        )

        "channel.chat.clear_user_messages" -> ChatEvent.UserMessagesCleared(
            channelId = event.string("broadcaster_user_id").orEmpty(),
            userId = event.string("target_user_id").orEmpty(),
            userLogin = event.string("target_user_login"),
            eventId = eventSubMessageId.takeIf(String::isNotBlank),
            createdAt = timestamp.takeIf(String::isNotBlank),
        )

        "channel.chat.clear" -> ChatEvent.ChatCleared(
            channelId = event.string("broadcaster_user_id").orEmpty(),
            eventId = eventSubMessageId.takeIf(String::isNotBlank),
            createdAt = timestamp.takeIf(String::isNotBlank),
        )

        "automod.message.hold" -> ChatEvent.AutoModHeld(
            parseAutoModMessage(event, timestamp, AutoModMessageStatus.HELD),
        )

        "automod.message.update" -> ChatEvent.AutoModUpdated(
            parseAutoModMessage(
                event = event,
                timestamp = timestamp,
                status = when (event.string("status")?.lowercase()) {
                    "approved" -> AutoModMessageStatus.APPROVED
                    "denied" -> AutoModMessageStatus.DENIED
                    else -> AutoModMessageStatus.HELD
                },
            ),
        )

        "channel.moderate" -> ChatEvent.ModerationPerformed(
            parseModerationAction(event, timestamp, eventSubMessageId),
        )

        "channel.chat_settings.update" -> ChatEvent.ChatSettingsUpdated(
            ModerationChatSettings(
                channelId = event.string("broadcaster_user_id").orEmpty(),
                slowMode = event.boolean("slow_mode") ?: false,
                slowModeWaitSeconds = event.int("slow_mode_wait_time_seconds")
                    ?: event.int("slow_mode_wait_time") ?: 30,
                followerMode = event.boolean("follower_mode") ?: false,
                followerModeDurationMinutes = event.int("follower_mode_duration_minutes")
                    ?: event.int("follower_mode_duration") ?: 0,
                subscriberMode = event.boolean("subscriber_mode") ?: false,
                emoteMode = event.boolean("emote_mode") ?: false,
                uniqueChatMode = event.boolean("unique_chat_mode") ?: false,
            ),
        )

        else -> null
    }

    private fun parseAutoModMessage(
        event: JsonObject,
        timestamp: String,
        status: AutoModMessageStatus,
    ): AutoModHeldMessage {
        val messageObject = event.objectOrNull("message")
        val text = messageObject?.string("text") ?: event.string("message").orEmpty()
        val fragments = messageObject?.let { parseFragments(it, text) }
            ?: listOf(ChatFragment.Text(text))
        val automod = event.objectOrEmpty("automod")
        val boundaries = buildList {
            event.collectBoundaries(this)
        }.distinct()
        return AutoModHeldMessage(
            channelId = event.string("broadcaster_user_id").orEmpty(),
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            channelName = event.string("broadcaster_user_name")
                ?: event.string("broadcaster_user_login").orEmpty(),
            userId = event.string("user_id").orEmpty(),
            userLogin = event.string("user_login").orEmpty(),
            userName = event.string("user_name") ?: event.string("user_login").orEmpty(),
            messageId = event.string("message_id").orEmpty(),
            text = text,
            fragments = fragments,
            reason = event.string("reason"),
            category = automod.string("category") ?: event.string("category"),
            level = automod.int("level") ?: event.int("level"),
            boundaries = boundaries,
            heldAt = event.string("held_at") ?: timestamp,
            status = status,
            decidedByUserId = event.string("moderator_user_id"),
            decidedByUserLogin = event.string("moderator_user_login"),
            decidedByUserName = event.string("moderator_user_name"),
        )
    }

    private fun parseModerationAction(
        event: JsonObject,
        timestamp: String,
        eventSubMessageId: String,
    ): RemoteModerationAction {
        val action = event.string("action").orEmpty()
        val details = event.objectOrEmpty(action)
        return RemoteModerationAction(
            id = eventSubMessageId.ifBlank { "$action:${event.string("broadcaster_user_id")}:$timestamp" },
            channelId = event.string("broadcaster_user_id").orEmpty(),
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            channelName = event.string("broadcaster_user_name")
                ?: event.string("broadcaster_user_login").orEmpty(),
            moderatorId = event.string("moderator_user_id").orEmpty(),
            moderatorLogin = event.string("moderator_user_login").orEmpty(),
            moderatorName = event.string("moderator_user_name")
                ?: event.string("moderator_user_login").orEmpty(),
            action = action,
            targetUserId = details.string("user_id") ?: details.string("target_user_id"),
            targetUserLogin = details.string("user_login") ?: details.string("target_user_login"),
            targetUserName = details.string("user_name") ?: details.string("target_user_name"),
            messageId = details.string("message_id"),
            reason = details.string("reason"),
            durationSeconds = moderationDurationSeconds(action, details, timestamp),
            createdAt = timestamp,
        )
    }

    private fun moderationDurationSeconds(
        action: String,
        details: JsonObject,
        timestamp: String,
    ): Int? {
        details.int("duration_seconds")?.let { return it }
        details.int("duration")?.let { return it }
        details.int("wait_time_seconds")?.let { return it }
        details.int("follow_duration_minutes")?.let { return it * 60 }
        if (action == "timeout" || action == "shared_chat_timeout") {
            val expiresAt = details.string("expires_at") ?: return null
            return runCatching {
                val started = java.time.Instant.parse(timestamp)
                val expires = java.time.Instant.parse(expiresAt)
                java.time.Duration.between(started, expires).seconds
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            }.getOrNull()
        }
        return null
    }

    private fun JsonElement.collectBoundaries(target: MutableList<AutoModBoundary>) {
        when (this) {
            is JsonObject -> {
                val start = int("start_pos") ?: int("start")
                val end = int("end_pos") ?: int("end")
                if (start != null && end != null && end >= start) {
                    target += AutoModBoundary(start = start, endInclusive = end)
                }
                values.forEach { it.collectBoundaries(target) }
            }
            is JsonArray -> forEach { it.collectBoundaries(target) }
            else -> Unit
        }
    }

    private fun parseMessage(event: JsonObject, timestamp: String): ChatMessage {
        val messageObject = event.objectOrEmpty("message")
        val replyObject = event.objectOrNull("reply")
        val badges = event.arrayOrEmpty("badges")
        val messageId = event.string("message_id").orEmpty()
        val channelId = event.string("broadcaster_user_id").orEmpty()
        val userId = event.string("chatter_user_id").orEmpty()
        require(messageId.isNotBlank()) { "channel.chat.message не содержит message_id" }
        require(channelId.isNotBlank()) { "channel.chat.message не содержит broadcaster_user_id" }
        require(userId.isNotBlank()) { "channel.chat.message не содержит chatter_user_id" }

        val parsedBadges = parseBadges(badges)
        val reply = replyObject?.string("parent_message_id")?.let { parentMessageId ->
            ReplyContext(
                parentMessageId = parentMessageId,
                parentMessageBody = replyObject.string("parent_message_body"),
                parentUserId = replyObject.string("parent_user_id"),
                parentUserLogin = replyObject.string("parent_user_login"),
                parentUserName = replyObject.string("parent_user_name"),
                threadMessageId = replyObject.string("thread_message_id"),
                threadUserId = replyObject.string("thread_user_id"),
                threadUserLogin = replyObject.string("thread_user_login"),
                threadUserName = replyObject.string("thread_user_name"),
            )
        }
        val rawText = messageObject.string("text").orEmpty()
        val rawFragments = parseFragments(messageObject, rawText)
        val normalizedContent = ReplyTextNormalizer.normalize(
            text = rawText,
            fragments = rawFragments,
            reply = reply,
        )
        val rawMessageType = event.string("message_type").orEmpty()
        val rewardObject = event.objectOrNull("reward") ?: messageObject.objectOrNull("reward")
        val rewardId = event.string("channel_points_custom_reward_id") ?: rewardObject?.string("id")
        val reward = if (rewardId != null || rewardObject != null) {
            ChatReward(
                id = rewardId,
                title = rewardObject?.string("title"),
                cost = (rewardObject?.int("cost") ?: rewardObject?.int("channel_points"))?.toLong(),
            )
        } else {
            null
        }
        val domainType = when {
            rawMessageType == "action" -> ChatMessageType.ACTION
            rewardId != null || rewardObject != null -> ChatMessageType.REWARD
            normalizedContent.fragments.any { it is ChatFragment.Cheermote } -> ChatMessageType.CHEER
            rawMessageType in setOf(
                "channel_points_highlighted",
                "channel_points_sub_only",
                "power_ups_message_effect",
                "power_ups_gigantified_emote",
            ) -> ChatMessageType.REWARD
            rawMessageType.isBlank() || rawMessageType == "text" || rawMessageType == "user_intro" -> {
                ChatMessageType.CHAT
            }
            else -> ChatMessageType.UNKNOWN
        }

        return ChatMessage(
            id = messageId,
            channelId = channelId,
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            author = ChatAuthor(
                id = userId,
                login = event.string("chatter_user_login").orEmpty(),
                displayName = event.string("chatter_user_name")
                    ?: event.string("chatter_user_login").orEmpty(),
                color = event.string("color"),
                badges = parsedBadges,
            ),
            text = normalizedContent.text,
            fragments = normalizedContent.fragments,
            timestamp = timestamp,
            timestampMillis = timestamp.toEpochMillisOrNow(),
            reply = reply,
            reward = reward,
            type = domainType,
            flags = MessageFlags(
                isAction = domainType == ChatMessageType.ACTION,
                isFirstMessage = event.boolean("first_message") ?: false,
                isReturningChatter = event.boolean("returning_chatter") ?: false,
            ),
        )
    }


    private fun parseChatNotification(event: JsonObject, timestamp: String): ChatMessage {
        val messageId = event.string("message_id").orEmpty()
        val channelId = event.string("broadcaster_user_id").orEmpty()
        require(messageId.isNotBlank()) { "channel.chat.notification не содержит message_id" }
        require(channelId.isNotBlank()) { "channel.chat.notification не содержит broadcaster_user_id" }

        val rawNoticeType = event.string("notice_type").orEmpty().ifBlank { "unknown" }
        val noticeType = rawNoticeType.removePrefix("shared_chat_")
        val isAnonymous = event.boolean("chatter_is_anonymous") ?: false
        val messageObject = event.objectOrEmpty("message")
        val systemMessage = event.string("system_message").orEmpty()
        val userMessage = messageObject.string("text").orEmpty()
        val text = if (noticeType == "announcement") {
            userMessage.ifBlank { systemMessage }.ifBlank { rawNoticeType }
        } else {
            listOf(systemMessage, userMessage)
                .filter(String::isNotBlank)
                .distinct()
                .joinToString("\n")
                .ifBlank { rawNoticeType }
        }
        val authorId = event.string("chatter_user_id").orEmpty().ifBlank {
            if (isAnonymous) "anonymous:$messageId" else "twitch:$channelId"
        }
        val authorLogin = event.string("chatter_user_login").orEmpty().ifBlank {
            if (isAnonymous) "anonymous" else "twitch"
        }
        val authorName = event.string("chatter_user_name")
            ?.takeIf(String::isNotBlank)
            ?: if (isAnonymous) "Аноним" else authorLogin
        val fragments = if (userMessage.isNotBlank()) {
            parseFragments(messageObject, userMessage)
        } else {
            listOf(ChatFragment.Text(text))
        }
        val details = event.objectOrNull(rawNoticeType)
            ?: event.objectOrNull(noticeType)
            ?: EMPTY_OBJECT

        return ChatMessage(
            id = messageId,
            channelId = channelId,
            channelLogin = event.string("broadcaster_user_login").orEmpty(),
            author = ChatAuthor(
                id = authorId,
                login = authorLogin,
                displayName = authorName,
                color = event.string("color"),
                badges = parseBadges(event.arrayOrEmpty("badges")),
                profileImageUrl = details.string("profile_image_url"),
            ),
            text = text,
            fragments = fragments,
            timestamp = timestamp,
            timestampMillis = timestamp.toEpochMillisOrNow(),
            notice = ChatNotice(
                type = rawNoticeType,
                systemMessage = systemMessage.takeIf(String::isNotBlank),
                userMessage = userMessage.takeIf(String::isNotBlank),
                subTier = details.string("sub_tier")
                    ?: details.string("sub_plan")
                    ?: details.string("tier"),
                isPrime = details.boolean("is_prime"),
                durationMonths = details.int("duration_months"),
                cumulativeMonths = details.int("cumulative_months"),
                streakMonths = details.int("streak_months"),
                isGift = details.boolean("is_gift"),
                giftTotal = details.int("total"),
                cumulativeGiftTotal = details.int("cumulative_total"),
                communityGiftId = details.string("community_gift_id") ?: details.string("id"),
                gifterIsAnonymous = details.boolean("gifter_is_anonymous"),
                gifterUserId = details.string("gifter_user_id"),
                gifterUserLogin = details.string("gifter_user_login"),
                gifterUserName = details.string("gifter_user_name"),
                recipientUserId = details.string("recipient_user_id"),
                recipientUserLogin = details.string("recipient_user_login"),
                recipientUserName = details.string("recipient_user_name"),
                raidUserId = details.string("user_id"),
                raidUserLogin = details.string("user_login"),
                raidUserName = details.string("user_name"),
                raidViewerCount = details.int("viewer_count"),
                raidProfileImageUrl = details.string("profile_image_url"),
                announcementColor = details.string("color"),
                isAnonymous = isAnonymous,
            ),
            type = when (noticeType) {
                "sub" -> ChatMessageType.SUBSCRIPTION
                "resub" -> ChatMessageType.RESUBSCRIPTION
                "sub_gift", "community_sub_gift", "gift_paid_upgrade",
                "prime_paid_upgrade", "pay_it_forward" -> ChatMessageType.GIFT_SUBSCRIPTION
                "raid" -> ChatMessageType.RAID
                "unraid" -> ChatMessageType.SYSTEM
                "announcement" -> ChatMessageType.ANNOUNCEMENT
                else -> ChatMessageType.SYSTEM
            },
            flags = MessageFlags(isSystem = noticeType != "announcement"),
        )
    }

    private fun parseBadges(badges: JsonArray): List<ChatBadge> =
        badges.mapNotNull { badgeElement ->
            val badge = badgeElement as? JsonObject ?: return@mapNotNull null
            ChatBadge(
                setId = badge.string("set_id").orEmpty(),
                id = badge.string("id").orEmpty(),
                info = badge.string("info"),
            )
        }

    private fun parseFragments(message: JsonObject, fallbackText: String): List<ChatFragment> {
        val parsed = message.arrayOrEmpty("fragments").mapNotNull { element ->
            val fragment = element as? JsonObject ?: return@mapNotNull null
            val type = fragment.string("type").orEmpty()
            val text = fragment.string("text").orEmpty()
            when (type) {
                // Twitch may place a URL and the remaining sentence in one text fragment.
                // Link ranges are detected by the renderer, so never mark the whole fragment as a link.
                "text" -> ChatFragment.Text(text)

                "emote" -> {
                    val emote = fragment.objectOrEmpty("emote")
                    ChatFragment.TwitchEmote(
                        text = text,
                        emoteId = emote.string("id").orEmpty(),
                        emoteSetId = emote.string("emote_set_id"),
                        ownerId = emote.string("owner_id"),
                        formats = emote.arrayOrEmpty("format")
                            .mapNotNull { value -> (value as? JsonPrimitive)?.contentOrNull }
                            .toSet(),
                    )
                }

                "mention" -> {
                    val mention = fragment.objectOrEmpty("mention")
                    ChatFragment.Mention(
                        text = text,
                        userId = mention.string("user_id").orEmpty(),
                        userLogin = mention.string("user_login").orEmpty(),
                        userName = mention.string("user_name").orEmpty(),
                    )
                }

                "cheermote" -> {
                    val cheermote = fragment.objectOrEmpty("cheermote")
                    ChatFragment.Cheermote(
                        text = text,
                        prefix = cheermote.string("prefix").orEmpty(),
                        bits = cheermote.int("bits") ?: 0,
                        tier = cheermote.int("tier") ?: 0,
                    )
                }

                "gif" -> {
                    val gif = fragment.objectOrEmpty("gif")
                    ChatFragment.Gif(
                        text = text,
                        gifId = gif.string("gif_id").orEmpty(),
                        url = gif.string("url").orEmpty(),
                    )
                }

                else -> ChatFragment.Unknown(text = text, rawType = type.ifBlank { "unknown" })
            }
        }
        return parsed.ifEmpty { listOf(ChatFragment.Text(fallbackText)) }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        this[key] as? JsonObject

    private fun JsonObject.objectOrEmpty(key: String): JsonObject =
        objectOrNull(key) ?: EMPTY_OBJECT

    private fun JsonObject.arrayOrEmpty(key: String): JsonArray =
        this[key] as? JsonArray ?: EMPTY_ARRAY

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private val EMPTY_OBJECT = JsonObject(emptyMap())
    private val EMPTY_ARRAY = JsonArray(emptyList())
    internal const val MAX_EVENTSUB_ENVELOPE_CHARS = 256 * 1024
    internal const val MAX_EVENTSUB_JSON_DEPTH = 64
}
