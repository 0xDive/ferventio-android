package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.PinnedChatMessage
import io.ferventio.app.network.FerventioServerTransportSecurity
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable

/**
 * Reads the public pinned-chat snapshot used by twitch.tv.
 *
 * This is an undocumented Twitch persisted GraphQL query. It intentionally uses no user OAuth
 * token, cookies, device identifier, or integrity token. A schema/hash change must fail softly in
 * the controller and must never affect the normal chat connection.
 */
class TwitchPinnedChatGqlClient : Closeable {
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            engine {
                FerventioServerTransportSecurity.configure(this)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun getPinnedChatMessage(channelId: String): PinnedChatMessage? {
        require(channelId.isNotBlank()) { "Не указан Twitch ID канала" }
        val response = client.post(TWITCH_GQL_URL) {
            header("Client-ID", TWITCH_WEB_CLIENT_ID)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(requestBody(channelId))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchPinnedChatGqlException(
                message = "Twitch GQL ${response.status.value}: ${body.take(300).ifBlank { "пустой ответ" }}",
                invalidatesSnapshot = response.status.value in 400..499 && response.status.value != 429,
            )
        }
        return TwitchPinnedChatGqlParser.parse(body, channelId)
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private fun requestBody(channelId: String): String = buildJsonArray {
        add(buildJsonObject {
            put("operationName", JsonPrimitive(GET_PINNED_CHAT_OPERATION))
            put("variables", buildJsonObject {
                put("channelID", JsonPrimitive(channelId))
                put("count", JsonPrimitive(1))
            })
            put("extensions", buildJsonObject {
                put("persistedQuery", buildJsonObject {
                    put("version", JsonPrimitive(1))
                    put("sha256Hash", JsonPrimitive(GET_PINNED_CHAT_HASH))
                })
            })
        })
    }.toString()

    private companion object {
        const val TWITCH_GQL_URL = "https://gql.twitch.tv/gql"
        const val TWITCH_WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val GET_PINNED_CHAT_OPERATION = "GetPinnedChat"
        const val GET_PINNED_CHAT_HASH =
            "450320a012e0f1704586e55755307ca3f8a4c611d678687cc3e202471a33e615"
    }
}

internal object TwitchPinnedChatGqlParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, requestedChannelId: String): PinnedChatMessage? {
        val envelope = (json.parseToJsonElement(body) as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: throw TwitchPinnedChatGqlException("Twitch GQL вернул неожиданный корневой объект")

        val errors = envelope.array("errors")
        if (!errors.isNullOrEmpty()) {
            val message = errors.mapNotNull { (it as? JsonObject)?.string("message") }
                .joinToString("; ")
                .ifBlank { "неизвестная GraphQL-ошибка" }
            throw TwitchPinnedChatGqlException(message)
        }

        val data = envelope.objectOrNull("data")
            ?: throw TwitchPinnedChatGqlException("Twitch GQL не вернул data")
        val channel = data.objectOrNull("channel") ?: return null
        val connection = channel.objectOrNull("pinnedChatMessages")
            ?: throw TwitchPinnedChatGqlException("Twitch GQL изменил pinnedChatMessages")
        val edges = connection.array("edges")
            ?: throw TwitchPinnedChatGqlException("Twitch GQL не вернул edges")
        val node = (edges.firstOrNull() as? JsonObject)?.objectOrNull("node") ?: return null
        val message = node.objectOrNull("pinnedMessage")
            ?: throw TwitchPinnedChatGqlException("Twitch GQL не вернул pinnedMessage")
        val content = message.objectOrNull("content") ?: JsonObject(emptyMap())
        val sender = message.objectOrNull("sender") ?: JsonObject(emptyMap())
        val pinnedBy = node.objectOrNull("pinnedBy")

        val fragmentTexts = content.array("fragments").orEmpty().mapNotNull { fragment ->
            (fragment as? JsonObject)?.string("text")
        }
        val text = content.string("text") ?: fragmentTexts.joinToString(separator = "")
        val fragments = if (fragmentTexts.isNotEmpty()) {
            fragmentTexts.map(ChatFragment::Text)
        } else {
            text.takeIf(String::isNotEmpty)?.let { listOf(ChatFragment.Text(it)) }.orEmpty()
        }
        val senderLogin = sender.string("login").orEmpty()
        val senderName = sender.string("displayName").orEmpty().ifBlank { senderLogin }

        return PinnedChatMessage(
            channelId = channel.string("id") ?: requestedChannelId,
            messageId = message.string("id") ?: node.string("id").orEmpty(),
            senderUserId = sender.string("id").orEmpty(),
            senderUserLogin = senderLogin,
            senderUserName = senderName,
            pinnedByUserName = pinnedBy?.string("displayName")
                ?: pinnedBy?.string("login"),
            text = text,
            fragments = fragments,
            startsAt = node.string("startsAt"),
            endsAt = node.string("endsAt"),
        )
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

class TwitchPinnedChatGqlException(
    message: String,
    val invalidatesSnapshot: Boolean = true,
) : IllegalStateException(message)
