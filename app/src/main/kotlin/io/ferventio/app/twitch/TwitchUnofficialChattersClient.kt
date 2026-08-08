package io.ferventio.app.twitch

import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        if (integrityRejected) {
            Log.d(LOG_TAG, "CommunityTab skipped channel=$login reason=integrity-rejected")
            return emptyList()
        }

        return requestMutexes.computeIfAbsent(login) { Mutex() }.withLock {
            cachedChatters(login)?.let { cached ->
                Log.d(LOG_TAG, "CommunityTab cache hit channel=$login count=${cached.size}")
                return@withLock cached
            }
            if (integrityRejected) {
                Log.d(LOG_TAG, "CommunityTab skipped channel=$login reason=integrity-rejected")
                return@withLock emptyList()
            }

            Log.d(LOG_TAG, "CommunityTab request channel=$login")
            try {
                val response = client.post(TWITCH_GQL_URL) {
                    header("Client-ID", TWITCH_WEB_CLIENT_ID)
                    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    contentType(ContentType.Application.Json)
                    setBody(requestBody(login))
                }
                val body = response.bodyAsText()
                if (response.status.value !in 200..299) {
                    Log.w(
                        LOG_TAG,
                        "CommunityTab HTTP ${response.status.value} channel=$login body=${body.safeLogPrefix()}",
                    )
                    throw TwitchUnofficialChattersException(
                        "Twitch GQL ${response.status.value}: ${body.take(300).ifBlank { "empty response" }}",
                    )
                }
                val chatters = TwitchUnofficialChattersParser.parse(body)
                if (chatters.isEmpty()) {
                    Log.w(LOG_TAG, "CommunityTab empty channel=$login body=${body.safeLogPrefix()}")
                } else {
                    val groups = chatters
                        .groupingBy(TwitchUnofficialChatter::group)
                        .eachCount()
                        .entries
                        .sortedBy { it.key.ordinal }
                        .joinToString(prefix = "{", postfix = "}") { (group, count) -> "$group=$count" }
                    Log.d(LOG_TAG, "CommunityTab success channel=$login count=${chatters.size} groups=$groups")
                }
                chatterCache[login] = CachedChatters(
                    chatters = chatters,
                    expiresAtMillis = System.currentTimeMillis() + CACHE_TTL_MILLIS,
                )
                chatters
            } catch (cancelled: CancellationException) {
                Log.d(LOG_TAG, "CommunityTab cancelled channel=$login")
                throw cancelled
            } catch (error: Throwable) {
                if (error is TwitchUnofficialChattersException && error.message.orEmpty().contains("integrity", ignoreCase = true)) {
                    integrityRejected = true
                    Log.w(LOG_TAG, "CommunityTab disabled for this process after Twitch integrity rejection")
                }
                Log.w(LOG_TAG, "CommunityTab failed channel=$login: ${error.message}", error)
                throw error
            }
        }
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
        const val LOG_TAG = "FerventioChatters"
        const val TWITCH_GQL_URL = "https://gql.twitch.tv/gql"
        const val TWITCH_WEB_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        const val COMMUNITY_TAB_HASH =
            "92168b4434c8f4d32df14510052131c3544b929723d5f8b69bb96c96207e483e"
        const val CACHE_TTL_MILLIS = 30_000L

        @Volatile
        var integrityRejected = false

        val chatterCache = ConcurrentHashMap<String, CachedChatters>()
        val requestMutexes = ConcurrentHashMap<String, Mutex>()

        fun cachedChatters(login: String): List<TwitchUnofficialChatter>? {
            val cached = chatterCache[login] ?: return null
            if (cached.expiresAtMillis <= System.currentTimeMillis()) {
                chatterCache.remove(login, cached)
                return null
            }
            return cached.chatters
        }
    }
}

private fun String.safeLogPrefix(): String =
    replace(Regex("[\\r\\n]+"), " ").take(300).ifBlank { "<empty>" }

private data class CachedChatters(
    val chatters: List<TwitchUnofficialChatter>,
    val expiresAtMillis: Long,
)

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
        "moderators" to TwitchUnofficialChatterGroup.MODERATOR,
        "vips" to TwitchUnofficialChatterGroup.VIP,
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
