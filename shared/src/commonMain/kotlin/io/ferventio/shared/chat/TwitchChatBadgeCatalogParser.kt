package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.chatBadgeAssetKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses the Helix chat-badge envelope returned by Twitch and the Ferventio metadata relay. */
internal object TwitchChatBadgeCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): Map<String, ChatBadgeAsset> {
        val data = runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        }.getOrElse {
            throw IllegalStateException("Twitch returned malformed chat-badges JSON", it)
        }
        return buildMap {
            for (setElement in data) {
                val set = setElement.runCatching { jsonObject }.getOrNull() ?: continue
                val setId = set.string("set_id") ?: continue
                val versions = set["versions"]?.runCatching { jsonArray }?.getOrNull() ?: continue
                for (versionElement in versions) {
                    val version = versionElement.runCatching { jsonObject }.getOrNull() ?: continue
                    val id = version.string("id") ?: continue
                    val imageUrl1x = version.string("image_url_1x") ?: continue
                    val imageUrl2x = version.string("image_url_2x") ?: imageUrl1x
                    val imageUrl4x = version.string("image_url_4x") ?: imageUrl2x
                    val asset = ChatBadgeAsset(
                        setId = setId,
                        id = id,
                        imageUrl1x = imageUrl1x,
                        imageUrl2x = imageUrl2x,
                        imageUrl4x = imageUrl4x,
                        title = version.string("title").orEmpty(),
                        description = version.string("description").orEmpty(),
                    )
                    put(chatBadgeAssetKey(setId, id), asset)
                }
            }
        }
    }

    private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
}
