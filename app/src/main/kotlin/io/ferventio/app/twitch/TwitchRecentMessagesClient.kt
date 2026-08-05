package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Loads the public IRC snapshot used by desktop Twitch chat clients to fill the gap before the
 * live socket starts producing messages. The service is optional: failures must never interrupt
 * EventSub/IRC or prevent a channel from opening.
 */
class TwitchRecentMessagesClient(
    baseUrl: String = DEFAULT_BASE_URL,
) : Closeable {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/').also { value ->
        require(value.startsWith("https://")) { "Recent Messages endpoint must use HTTPS" }
    }
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
            }
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun load(
        channel: ChatChannel,
        limit: Int = DEFAULT_LIMIT,
    ): TwitchRecentMessagesResult {
        val login = channel.login.trim().lowercase()
        require(LOGIN_PATTERN.matches(login)) { "Некорректное имя Twitch-канала" }
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val response = client.get("$normalizedBaseUrl/$login") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            parameter("limit", safeLimit)
        }
        val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (declaredLength != null && declaredLength > MAX_RESPONSE_BYTES) {
            throw TwitchRecentMessagesException("Сервис истории вернул слишком большой ответ")
        }
        val body = response.bodyAsText()
        if (body.length > MAX_RESPONSE_CHARS) {
            throw TwitchRecentMessagesException("Сервис истории вернул слишком большой ответ")
        }
        if (response.status.value !in 200..299) {
            throw TwitchRecentMessagesException(
                "Recent Messages request failed with HTTP ${response.status.value}",
            )
        }
        return TwitchRecentMessagesParser.parse(body, channel, safeLimit)
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://recent-messages.robotty.de/api/v2/recent-messages"
        const val DEFAULT_LIMIT = 100
        const val MAX_LIMIT = 500
        const val REQUEST_TIMEOUT_MILLIS = 12_000L
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val SOCKET_TIMEOUT_MILLIS = 15_000L
        const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
        const val MAX_RESPONSE_CHARS = 2 * 1024 * 1024
        val LOGIN_PATTERN = Regex("[a-z0-9_]{1,25}")
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
            ?: throw TwitchRecentMessagesException("Сервис истории вернул неожиданный JSON")
        val rawMessages = root["messages"] as? JsonArray
            ?: throw TwitchRecentMessagesException("Сервис истории не вернул messages")
        val messagesById = LinkedHashMap<String, ChatMessage>()
        rawMessages.asSequence()
            .mapNotNull { element -> runCatching { element.jsonPrimitive.contentOrNull }.getOrNull() }
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
                            messagesById.replaceAll { _, message ->
                                if (message.userId != event.userId) {
                                    message
                                } else {
                                    message.copy(
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
        val parsed = messagesById.values.sortedWith(
            compareBy<ChatMessage>(ChatMessage::timestampMillis)
                .thenBy(ChatMessage::id),
        ).takeLast(limit.coerceAtLeast(0))

        return TwitchRecentMessagesResult(
            messages = parsed,
            errorCode = root.string("error_code"),
        )
    }

    private fun parseTimestampMillis(value: String): Long? =
        runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

class TwitchRecentMessagesException(message: String) : IllegalStateException(message)
