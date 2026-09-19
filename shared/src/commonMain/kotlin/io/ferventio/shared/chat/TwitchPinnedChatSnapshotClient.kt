package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.PinnedChatMessage
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class TwitchPinnedChatSnapshotException(
    message: String,
    val invalidatesSnapshot: Boolean = true,
) : IllegalStateException(message)

class TwitchPinnedChatSnapshotClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    suspend fun getPinnedChatMessage(channelId: String): PinnedChatMessage? {
        val normalizedChannelId = channelId.trim()
        require(normalizedChannelId.isNotEmpty()) { "Twitch pinned-chat channelId must not be blank" }
        val response = client.post(TWITCH_GQL_URL) {
            header("Client-ID", TWITCH_WEB_CLIENT_ID)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(requestBody(normalizedChannelId))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw TwitchPinnedChatSnapshotException(
                message = "Twitch pinned-chat snapshot HTTP ${response.status.value}",
                invalidatesSnapshot = response.status.value in 400..499 && response.status.value != 429,
            )
        }
        return TwitchPinnedChatSnapshotParser.parse(body, normalizedChannelId)
    }

    fun close() {
        client.close()
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

internal object TwitchPinnedChatSnapshotParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String, requestedChannelId: String): PinnedChatMessage? {
        val envelope = (json.parseToJsonElement(body) as? JsonArray)
            ?.firstOrNull() as? JsonObject
            ?: throw TwitchPinnedChatSnapshotException("Unexpected Twitch pinned-chat response")
        val errors = envelope["errors"] as? JsonArray
        if (!errors.isNullOrEmpty()) {
            throw TwitchPinnedChatSnapshotException(
                errors.mapNotNull { (it as? JsonObject)?.string("message") }
                    .joinToString("; ")
                    .ifBlank { "Twitch pinned-chat query failed" },
            )
        }
        val data = envelope.objectOrNull("data")
            ?: throw TwitchPinnedChatSnapshotException("Pinned-chat response did not contain data")
        val channel = data.objectOrNull("channel") ?: return null
        val connection = channel.objectOrNull("pinnedChatMessages")
            ?: throw TwitchPinnedChatSnapshotException("Pinned-chat schema changed")
        val edges = connection["edges"] as? JsonArray
            ?: throw TwitchPinnedChatSnapshotException("Pinned-chat response did not contain edges")
        val node = (edges.firstOrNull() as? JsonObject)?.objectOrNull("node") ?: return null
        val message = node.objectOrNull("pinnedMessage")
            ?: throw TwitchPinnedChatSnapshotException("Pinned-chat response did not contain message")
        val content = message.objectOrNull("content") ?: JsonObject(emptyMap())
        val sender = message.objectOrNull("sender") ?: JsonObject(emptyMap())
        val pinnedBy = node.objectOrNull("pinnedBy")
        val fragments = (content["fragments"] as? JsonArray)
            .orEmpty()
            .mapNotNull { item -> (item as? JsonObject)?.let(::parseFragment) }
        val text = content.string("text")
            ?: fragments.joinToString("") { fragment ->
                when (fragment) {
                    is ChatFragment.Text -> fragment.text
                    is ChatFragment.TwitchEmote -> fragment.text
                    is ChatFragment.Mention -> fragment.text
                    is ChatFragment.Cheermote -> fragment.text
                    else -> ""
                }
            }
        val senderLogin = sender.string("login").orEmpty()
        return PinnedChatMessage(
            channelId = channel.string("id") ?: requestedChannelId,
            messageId = message.string("id") ?: node.string("id").orEmpty(),
            senderUserId = sender.string("id").orEmpty(),
            senderUserLogin = senderLogin,
            senderUserName = sender.string("displayName").orEmpty().ifBlank { senderLogin },
            pinnedByUserName = pinnedBy?.string("displayName") ?: pinnedBy?.string("login"),
            text = text,
            fragments = fragments,
            startsAt = node.string("startsAt"),
            endsAt = node.string("endsAt"),
        )
    }

    private fun parseFragment(item: JsonObject): ChatFragment {
        val text = item.string("text").orEmpty()
        return when (item.string("type")) {
            "emote" -> {
                val emote = item.objectOrNull("emote")
                ChatFragment.TwitchEmote(
                    text = text,
                    emoteId = emote?.string("id").orEmpty(),
                    emoteSetId = emote?.string("emote_set_id"),
                    ownerId = emote?.string("owner_id"),
                    formats = (emote?.get("format") as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it.jsonPrimitive.contentOrNull }
                        .toSet(),
                )
            }
            "mention" -> {
                val mention = item.objectOrNull("mention")
                ChatFragment.Mention(
                    text = text,
                    userId = mention?.string("user_id").orEmpty(),
                    userLogin = mention?.string("user_login").orEmpty(),
                    userName = mention?.string("user_name").orEmpty(),
                )
            }
            "cheermote" -> {
                val cheermote = item.objectOrNull("cheermote")
                ChatFragment.Cheermote(
                    text = text,
                    prefix = cheermote?.string("prefix").orEmpty(),
                    bits = cheermote?.int("bits") ?: 0,
                    tier = cheermote?.int("tier") ?: 0,
                )
            }
            else -> ChatFragment.Text(text)
        }
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? = this[name] as? JsonObject
    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.intOrNull
}
