package io.ferventio.app.twitch

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
import java.io.Closeable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Best-effort reader for twitch.tv's undocumented CommunityTab chatter data.
 *
 * The persisted query is intentionally isolated from the normal chat transport. Twitch may change
 * or remove it without notice; callers must always provide a local/official fallback.
 */
class TwitchUnofficialChattersClient : Closeable {
    private val clientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }
    private val client: HttpClient by clientDelegate

    suspend fun getChatters(channelLogin: String): List<TwitchUnofficialChatter> {
        val login = channelLogin.trim().lowercase()
        require(login.isNotBlank()) { "Channel login is required" }

        val response = client.post(TWITCH_GQL_URL) {
            header("Client-ID", TWITCH_WEB_CLIENT_ID)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(requestBody(login))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchUnofficialChattersException(
                "Twitch GQL ${response.status.value}: ${body.take(300).ifBlank { "empty response" }}",
            )
        }
        return TwitchUnofficialChattersParser.parse(body)
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private fun requestBody(channelLogin: String): String = buildJsonArray {
        add(buildJsonObject {
            put("operationName", JsonPrimitive("CommunityTab"))
            put("variables", buildJsonObject {
                put("login", JsonPrimitive(channelLogin))
            })
            put("extensions", buildJsonObject {
                put("persistedQuery", buildJsonObject {
                    put("version", JsonPrimitive(1))
                    put("sha256Hash", JsonPrimitive(COMMUNITY_TAB_HASH))
                })
            })
        })
    }.toString()

    private companion object {
        const val TWITCH_GQL_URL = "https://gql.twitch.tv/gql"
        const val TWITCH_WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val COMMUNITY_TAB_HASH =
            "92168b4434c8f4d32df14510052131c3544b929723d5f8b69bb96c96207e483e"
    }
}

enum class TwitchUnofficialChatterGroup {
    BROADCASTER,
    STAFF,
    VIP,
    MODERATOR,
    CHATBOT,
    VIEWER,
}

data class TwitchUnofficialChatter(
    val id: String,
    val login: String,
    val group: TwitchUnofficialChatterGroup = TwitchUnofficialChatterGroup.VIEWER,
)

internal object TwitchUnofficialChattersParser {
    private val groups = linkedMapOf(
        "broadcasters" to TwitchUnofficialChatterGroup.BROADCASTER,
        "staff" to TwitchUnofficialChatterGroup.STAFF,
        "vips" to TwitchUnofficialChatterGroup.VIP,
        "moderators" to TwitchUnofficialChatterGroup.MODERATOR,
        "chatbots" to TwitchUnofficialChatterGroup.CHATBOT,
        "viewers" to TwitchUnofficialChatterGroup.VIEWER,
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<TwitchUnofficialChatter> {
        val parsed = json.parseToJsonElement(body)
        val root = when (parsed) {
            is JsonObject -> parsed
            is JsonArray -> parsed.firstOrNull() as? JsonObject
            else -> null
        } ?: throw TwitchUnofficialChattersException("Twitch GQL returned an unexpected root object")

        val errors = root["errors"] as? JsonArray
        if (!errors.isNullOrEmpty()) {
            val message = errors.mapNotNull { element ->
                (element as? JsonObject)?.string("message")
            }.joinToString("; ").ifBlank { "unknown GraphQL error" }
            throw TwitchUnofficialChattersException(message)
        }

        val chatters = root.objectOrNull("data")
            ?.objectOrNull("user")
            ?.objectOrNull("channel")
            ?.objectOrNull("chatters")
            ?: return emptyList()

        val byLogin = LinkedHashMap<String, TwitchUnofficialChatter>()
        groups.forEach { (groupKey, group) ->
            chatters.array(groupKey).orEmpty().forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val login = item.string("login").orEmpty().trim()
                if (login.isBlank()) return@forEach
                val id = item.string("id").orEmpty().trim()
                val key = login.lowercase()
                val existing = byLogin[key]
                if (existing == null) {
                    byLogin[key] = TwitchUnofficialChatter(
                        id = id,
                        login = login,
                        group = group,
                    )
                } else if (existing.id.isBlank() && id.isNotBlank()) {
                    // Role buckets are visited from highest to lowest priority. Keep the first role
                    // while still accepting a canonical id from a duplicate entry in a later bucket.
                    byLogin[key] = existing.copy(id = id)
                }
            }
        }
        return byLogin.values.toList()
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

class TwitchUnofficialChattersException(message: String) : IllegalStateException(message)
