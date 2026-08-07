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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Isolated best-effort client for viewer Channel Points operations that Twitch does not expose in
 * Helix. It deliberately uses only the Ferventio OAuth lease supplied by the controller; it never
 * accepts browser cookies or first-party credentials.
 */
class TwitchChannelPointsGqlClient : Closeable {
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

    suspend fun getContext(
        clientId: String,
        accessToken: String,
        channelLogin: String,
    ): TwitchChannelPointsContext {
        val response = post(
            clientId = clientId,
            accessToken = accessToken,
            body = buildJsonObject {
                put("operationName", JsonPrimitive("ChannelPointsContext"))
                put("variables", buildJsonObject {
                    put("channelLogin", JsonPrimitive(channelLogin.trim().lowercase()))
                })
                put("extensions", buildJsonObject {
                    put("persistedQuery", buildJsonObject {
                        put("version", JsonPrimitive(1))
                        put("sha256Hash", JsonPrimitive(CHANNEL_POINTS_CONTEXT_HASH))
                    })
                })
            }.toString(),
        )
        return TwitchChannelPointsGqlParser.parseContext(response)
    }

    suspend fun redeem(
        clientId: String,
        accessToken: String,
        channelId: String,
        reward: TwitchChannelPointsReward,
        transactionId: String,
        textInput: String?,
    ): TwitchChannelPointsRedemption {
        val input = buildJsonObject {
            put("channelID", JsonPrimitive(channelId))
            put("rewardID", JsonPrimitive(reward.id))
            put("cost", JsonPrimitive(reward.cost))
            put("title", JsonPrimitive(reward.title))
            if (reward.prompt.isBlank()) put("prompt", JsonNull) else put("prompt", JsonPrimitive(reward.prompt))
            val normalizedInput = textInput?.trim()?.takeIf(String::isNotEmpty)
            if (normalizedInput == null) put("textInput", JsonNull) else put("textInput", JsonPrimitive(normalizedInput))
            put("transactionID", JsonPrimitive(transactionId))
        }
        val response = post(
            clientId = clientId,
            accessToken = accessToken,
            body = buildJsonObject {
                put("operationName", JsonPrimitive("FerventioRedeemCommunityPointsCustomReward"))
                put("query", JsonPrimitive(REDEEM_MUTATION))
                put("variables", buildJsonObject { put("input", input) })
            }.toString(),
        )
        return TwitchChannelPointsGqlParser.parseRedemption(response)
    }

    private suspend fun post(clientId: String, accessToken: String, body: String): String {
        require(clientId.isNotBlank()) { "Twitch client id is required" }
        require(accessToken.isNotBlank()) { "Twitch access token is required" }
        val response = client.post(TWITCH_GQL_URL) {
            header("Client-ID", clientId)
            header(HttpHeaders.Authorization, "OAuth $accessToken")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val responseBody = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchChannelPointsGqlException(
                "Twitch GQL ${response.status.value}: ${responseBody.take(300).ifBlank { "empty response" }}",
            )
        }
        return responseBody
    }

    override fun close() {
        if (clientDelegate.isInitialized()) client.close()
    }

    private companion object {
        const val TWITCH_GQL_URL = "https://gql.twitch.tv/gql"
        const val CHANNEL_POINTS_CONTEXT_HASH =
            "374314de591e69925fce3ddc2bcf085796f56ebb8cad67a0daa3165c03adc345"
        const val REDEEM_MUTATION = """
            mutation FerventioRedeemCommunityPointsCustomReward(${'$'}input: RedeemCommunityPointsCustomRewardInput!) {
              redeemCommunityPointsCustomReward(input: ${'$'}input) {
                error { code }
                redemption { id }
              }
            }
        """
    }
}

data class TwitchChannelPointsContext(
    val balance: Int?,
    val rewards: List<TwitchChannelPointsReward>,
)

data class TwitchChannelPointsReward(
    val id: String,
    val title: String,
    val prompt: String,
    val cost: Int,
    val enabled: Boolean,
    val userInputRequired: Boolean,
    val imageUrl: String?,
)

data class TwitchChannelPointsRedemption(val id: String)

internal object TwitchChannelPointsGqlParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseContext(body: String): TwitchChannelPointsContext {
        val root = parseRoot(body)
        throwGraphQlErrors(root)
        val settings = findObjectWithKey(root, "customRewards")
        val rewards = (settings?.get("customRewards") as? JsonArray).orEmpty().mapNotNull(::parseReward)
        val balance = findIntByKey(root, "balance")
        return TwitchChannelPointsContext(balance = balance, rewards = rewards)
    }

    fun parseRedemption(body: String): TwitchChannelPointsRedemption {
        val root = parseRoot(body)
        throwGraphQlErrors(root)
        val payload = findObjectWithKey(root, "redemption")
            ?: throw TwitchChannelPointsGqlException("Twitch did not return a Channel Points redemption payload")
        val error = payload["error"] as? JsonObject
        val code = error?.string("code")
        if (!code.isNullOrBlank()) throw TwitchChannelPointsRedeemException(code)
        val redemption = payload["redemption"] as? JsonObject
            ?: throw TwitchChannelPointsGqlException("Twitch did not create the Channel Points redemption")
        val id = redemption.string("id").orEmpty()
        if (id.isBlank()) throw TwitchChannelPointsGqlException("Twitch returned a redemption without an id")
        return TwitchChannelPointsRedemption(id)
    }

    private fun parseReward(element: JsonElement): TwitchChannelPointsReward? {
        val item = element as? JsonObject ?: return null
        val id = item.string("id").orEmpty()
        val title = item.string("title").orEmpty()
        val cost = item.int("cost") ?: return null
        if (id.isBlank() || title.isBlank()) return null
        return TwitchChannelPointsReward(
            id = id,
            title = title,
            prompt = item.string("prompt").orEmpty(),
            cost = cost,
            enabled = item.boolean("isEnabled") ?: item.boolean("enabled") ?: true,
            userInputRequired = item.boolean("isUserInputRequired") ?: item.boolean("userInputRequired") ?: false,
            imageUrl = imageUrl(item["image"]) ?: imageUrl(item["defaultImage"]),
        )
    }

    private fun imageUrl(element: JsonElement?): String? {
        val image = element as? JsonObject ?: return null
        return sequenceOf("url", "url4x", "url2x", "url1x")
            .mapNotNull { key -> image.string(key) }
            .firstOrNull(String::isNotBlank)
    }

    private fun throwGraphQlErrors(root: JsonObject) {
        val errors = root["errors"] as? JsonArray ?: return
        if (errors.isEmpty()) return
        val message = errors.mapNotNull { (it as? JsonObject)?.string("message") }
            .joinToString("; ")
            .ifBlank { "unknown GraphQL error" }
        throw TwitchChannelPointsGqlException(message)
    }

    private fun parseRoot(body: String): JsonObject {
        val parsed = json.parseToJsonElement(body)
        return when (parsed) {
            is JsonObject -> parsed
            is JsonArray -> parsed.firstOrNull() as? JsonObject
            else -> null
        } ?: throw TwitchChannelPointsGqlException("Twitch GQL returned an unexpected root value")
    }

    private fun findObjectWithKey(element: JsonElement, key: String): JsonObject? {
        when (element) {
            is JsonObject -> {
                if (key in element) return element
                element.values.forEach { child -> findObjectWithKey(child, key)?.let { return it } }
            }
            is JsonArray -> element.forEach { child -> findObjectWithKey(child, key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun findIntByKey(element: JsonElement, key: String): Int? {
        when (element) {
            is JsonObject -> {
                element[key]?.let { value -> runCatching { value.jsonPrimitive.intOrNull }.getOrNull()?.let { return it } }
                element.values.forEach { child -> findIntByKey(child, key)?.let { return it } }
            }
            is JsonArray -> element.forEach { child -> findIntByKey(child, key)?.let { return it } }
            else -> Unit
        }
        return null
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()
    private fun JsonObject.int(key: String): Int? =
        runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()
    private fun JsonObject.boolean(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()
}

open class TwitchChannelPointsGqlException(message: String) : IllegalStateException(message)
class TwitchChannelPointsRedeemException(val code: String) :
    TwitchChannelPointsGqlException("Channel Points redemption failed: $code")
