package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.twitch.TwitchIrcEvent
import io.ferventio.app.domain.twitch.TwitchIrcParser
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlin.time.Instant
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Best-effort third-party snapshot used to fill chat before live EventSub rows arrive. */
class TwitchRecentMessagesClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
    baseUrl: String = DEFAULT_BASE_URL,
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient(), DEFAULT_BASE_URL)

    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        require(value.startsWith("https://")) { "Recent Messages endpoint must use HTTPS" }
    }

    @Throws(Exception::class)
    suspend fun load(
        channel: ChatChannel,
        limit: Int = DEFAULT_LIMIT,
    ): TwitchRecentMessagesResult {
        val login = channel.login.trim().lowercase()
        require(LOGIN_PATTERN.matches(login)) { "Invalid Twitch channel login" }
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val payload = withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
            val response = client.get("$normalizedBaseUrl/$login") {
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                url { parameters.append("limit", safeLimit.toString()) }
            }
            val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
            if (declaredLength != null && declaredLength > MAX_RESPONSE_BYTES) {
                throw TwitchRecentMessagesException("Recent Messages response is too large")
            }
            ResponsePayload(
                statusCode = response.status.value,
                body = response.bodyAsText(),
            )
        } ?: throw TwitchRecentMessagesException("Recent Messages request timed out")

        if (payload.body.length > MAX_RESPONSE_CHARS) {
            throw TwitchRecentMessagesException("Recent Messages response is too large")
        }
        if (payload.statusCode !in 200..299) {
            throw TwitchRecentMessagesException(
                "Recent Messages request failed with HTTP ${payload.statusCode}",
            )
        }
        return TwitchRecentMessagesParser.parse(payload.body, channel, safeLimit)
    }

    private data class ResponsePayload(
        val statusCode: Int,
        val body: String,
    )

    companion object {
        const val DEFAULT_BASE_URL = "https://recent-messages.robotty.de/api/v2/recent-messages"
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 500
        private const val REQUEST_TIMEOUT_MILLIS = 12_000L
        private const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
        private const val MAX_RESPONSE_CHARS = 2 * 1024 * 1024
        private val LOGIN_PATTERN = Regex("[a-z0-9_]{1,25}")
    }
}

data class TwitchRecentMessagesResult(
    val messages: List<ChatMessage>,
    val errorCode: String? = null,
)

internal object TwitchRecentMessagesParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        body: String,
        channel: ChatChannel,
        limit: Int,
    ): TwitchRecentMessagesResult {
        val root = json.parseToJsonElement(body) as? JsonObject
            ?: throw TwitchRecentMessagesException("Recent Messages returned unexpected JSON")
        val rawMessages = root["messages"] as? JsonArray
            ?: throw TwitchRecentMessagesException("Recent Messages response is missing messages")
        val messagesById = LinkedHashMap<String, ChatMessage>()

        rawMessages.asSequence()
            .mapNotNull { element ->
                runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()
            }
            .forEach { rawLine ->
                TwitchIrcParser.parse(rawLine) { channel.id }.forEach { parsedEvent ->
                    when (val event = (parsedEvent as? TwitchIrcEvent.Chat)?.event) {
                        is ChatEvent.Message -> {
                            val message = event.message.copy(
                                channelId = channel.id,
                                channelLogin = channel.login.lowercase(),
                            )
                            if (message.id.isNotBlank()) messagesById[message.id] = message
                        }
                        is ChatEvent.MessageDeleted -> {
                            messagesById[event.messageId]?.let { message ->
                                messagesById[event.messageId] = message.copy(
                                    flags = message.flags.copy(isDeleted = true),
                                    moderation = ModerationState(
                                        action = ModerationAction.DELETE,
                                        atMillis = event.createdAt?.let(::parseTimestampMillis),
                                    ),
                                )
                            }
                        }
                        is ChatEvent.UserMessagesCleared -> {
                            messagesById.keys.toList().forEach { messageId ->
                                val message = messagesById[messageId] ?: return@forEach
                                if (message.userId == event.userId) {
                                    messagesById[messageId] = message.copy(
                                        flags = message.flags.copy(isDeleted = true),
                                        moderation = ModerationState(
                                            action = if (event.isPermanent == true) {
                                                ModerationAction.BAN
                                            } else {
                                                ModerationAction.TIMEOUT
                                            },
                                            atMillis = event.createdAt?.let(::parseTimestampMillis),
                                        ),
                                    )
                                }
                            }
                        }
                        is ChatEvent.ChatCleared -> messagesById.clear()
                        else -> Unit
                    }
                }
            }

        val parsed = messagesById.values
            .sortedWith(
                compareBy<ChatMessage>(ChatMessage::timestampMillis)
                    .thenBy(ChatMessage::id),
            )
            .takeLast(limit.coerceAtLeast(0))
        return TwitchRecentMessagesResult(
            messages = parsed,
            errorCode = root.string("error_code"),
        )
    }

    private fun parseTimestampMillis(value: String): Long? =
        runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

class TwitchRecentMessagesException(message: String) : IllegalStateException(message)
