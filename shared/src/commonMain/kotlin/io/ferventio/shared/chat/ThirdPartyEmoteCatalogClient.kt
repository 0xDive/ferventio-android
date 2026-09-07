package io.ferventio.shared.chat

import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal data class ThirdPartyProviderCatalogs(
    val byProvider: Map<String, Map<String, ThirdPartyEmoteAsset>> = emptyMap(),
) {
    fun provider(id: String): Map<String, ThirdPartyEmoteAsset> = byProvider[id].orEmpty()
}

internal class ThirdPartyEmoteCatalogClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    suspend fun loadGlobals(): ThirdPartyProviderCatalogs = coroutineScope {
        val requests = listOf(
            SEVEN_TV to async { bestEffort { loadSevenTvGlobal() } },
            FRANKER_FACE_Z to async { bestEffort { loadFrankerFaceZGlobal() } },
            BETTER_TTV to async { bestEffort { loadBetterTtvGlobal() } },
        )
        ThirdPartyProviderCatalogs(
            byProvider = requests.map { (provider, deferred) -> provider to deferred.await() }.toMap(),
        )
    }

    suspend fun loadChannel(channelId: String): ThirdPartyProviderCatalogs = coroutineScope {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty()) return@coroutineScope ThirdPartyProviderCatalogs()
        val requests = listOf(
            SEVEN_TV to async { bestEffort { loadSevenTvChannel(normalizedChannelId) } },
            FRANKER_FACE_Z to async { bestEffort { loadFrankerFaceZChannel(normalizedChannelId) } },
            BETTER_TTV to async { bestEffort { loadBetterTtvChannel(normalizedChannelId) } },
        )
        ThirdPartyProviderCatalogs(
            byProvider = requests.map { (provider, deferred) -> provider to deferred.await() }.toMap(),
        )
    }

    fun mergeForChannel(
        global: ThirdPartyProviderCatalogs,
        channel: ThirdPartyProviderCatalogs,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        PROVIDER_MERGE_ORDER.forEach { provider ->
            putAll(global.provider(provider))
            putAll(channel.provider(provider))
        }
    }

    fun close() {
        client.close()
    }

    private suspend fun loadBetterTtvGlobal(): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.betterttv.net/3/cached/emotes/global") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        requireSuccess("BetterTTV", response.status.value, body)
        val data = runCatching { json.parseToJsonElement(body) as? JsonArray }
            .getOrNull() ?: return emptyMap()
        return parseBetterTtv(data, EmoteScope.GLOBAL)
    }

    private suspend fun loadBetterTtvChannel(
        channelId: String,
    ): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.betterttv.net/3/cached/users/twitch/$channelId") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        requireSuccess("BetterTTV", response.status.value, body)
        val root = parseObject(body) ?: return emptyMap()
        val shared = root["sharedEmotes"] as? JsonArray ?: JsonArray(emptyList())
        val channel = root["channelEmotes"] as? JsonArray ?: JsonArray(emptyList())
        val channelOwner = root.string("displayName") ?: root.string("name")
        return parseBetterTtv(shared, EmoteScope.CHANNEL, channelId) +
            parseBetterTtv(channel, EmoteScope.CHANNEL, channelId, channelOwner)
    }

    private fun parseBetterTtv(
        data: JsonArray,
        scope: EmoteScope,
        channelId: String? = null,
        ownerFallback: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        data.forEach { element ->
            val emote = element as? JsonObject ?: return@forEach
            val id = emote.string("id").orEmpty()
            val code = emote.string("code").orEmpty()
            if (id.isBlank() || code.isBlank()) return@forEach
            val imageType = emote.string("imageType").orEmpty()
            val animated = emote.string("animated")?.toBooleanStrictOrNull()
                ?: imageType.equals("gif", ignoreCase = true)
            val owner = emote["user"] as? JsonObject
            val ownerName = owner?.string("displayName")
                ?: owner?.string("name")
                ?: ownerFallback
            put(
                code,
                ThirdPartyEmoteAsset(
                    id = id,
                    code = code,
                    provider = BETTER_TTV,
                    imageType = imageType,
                    animated = animated,
                    imageUrl1x = betterTtvUrl(id, "1x"),
                    imageUrl2x = betterTtvUrl(id, "2x"),
                    imageUrl3x = betterTtvUrl(id, "3x"),
                    scope = scope,
                    channelId = channelId,
                    ownerName = ownerName,
                    sourceUrl = "https://betterttv.com/emotes/$id",
                ),
            )
        }
    }

    private suspend fun loadFrankerFaceZGlobal(): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.frankerfacez.com/v1/set/global") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        requireSuccess("FrankerFaceZ", response.status.value, body)
        val root = parseObject(body) ?: return emptyMap()
        val allowedSetIds = (root["default_sets"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        val sets = root["sets"] as? JsonObject ?: return emptyMap()
        return parseFrankerFaceZ(sets, allowedSetIds, EmoteScope.GLOBAL)
    }

    private suspend fun loadFrankerFaceZChannel(
        channelId: String,
    ): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.frankerfacez.com/v1/room/id/$channelId") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        requireSuccess("FrankerFaceZ", response.status.value, body)
        val root = parseObject(body) ?: return emptyMap()
        val room = root["room"] as? JsonObject
        val primarySetId = room?.get("set")?.jsonPrimitive?.contentOrNull
        val sets = root["sets"] as? JsonObject ?: return emptyMap()
        return parseFrankerFaceZ(
            sets = sets,
            allowedSetIds = primarySetId?.let(::setOf).orEmpty(),
            scope = EmoteScope.CHANNEL,
            channelId = channelId,
        )
    }

    private fun parseFrankerFaceZ(
        sets: JsonObject,
        allowedSetIds: Set<String>,
        scope: EmoteScope,
        channelId: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        if (allowedSetIds.isEmpty()) return@buildMap
        sets.forEach { (setId, setElement) ->
            if (setId !in allowedSetIds) return@forEach
            val set = setElement as? JsonObject ?: return@forEach
            val emoticons = set["emoticons"] as? JsonArray ?: return@forEach
            emoticons.forEach { element ->
                val emote = element as? JsonObject ?: return@forEach
                val id = emote.string("id").orEmpty()
                val code = emote.string("name").orEmpty()
                if (id.isBlank() || code.isBlank()) return@forEach
                val staticUrls = emote["urls"] as? JsonObject
                val animatedUrls = emote["animated"] as? JsonObject
                val animated = animatedUrls?.values
                    ?.any { value -> value.jsonPrimitive.contentOrNull?.isNotBlank() == true } == true
                val modifier = emote.string("modifier")?.toBooleanStrictOrNull() == true
                val modifierFlags = emote.int("modifier_flags") ?: 0
                if (modifier && modifierFlags and FFZ_EFFECT_ONLY_FLAG == FFZ_EFFECT_ONLY_FLAG) {
                    return@forEach
                }
                val owner = emote["owner"] as? JsonObject
                val ownerName = owner?.string("display_name") ?: owner?.string("name")
                val preferredUrls = if (animated) animatedUrls else staticUrls
                val image1x = preferredUrls.ffzUrl("1") ?: staticUrls.ffzUrl("1")
                    ?: return@forEach
                val image2x = preferredUrls.ffzUrl("2") ?: staticUrls.ffzUrl("2") ?: image1x
                val image3x = preferredUrls.ffzUrl("4") ?: staticUrls.ffzUrl("4") ?: image2x
                put(
                    code,
                    ThirdPartyEmoteAsset(
                        id = id,
                        code = code,
                        provider = FRANKER_FACE_Z,
                        imageType = if (animated) "webp" else "image",
                        animated = animated,
                        imageUrl1x = image1x,
                        imageUrl2x = image2x,
                        imageUrl3x = image3x,
                        scope = scope,
                        channelId = channelId,
                        ownerName = ownerName,
                        sourceUrl = "https://www.frankerfacez.com/emoticon/$id-$code",
                        zeroWidth = modifier,
                    ),
                )
            }
        }
    }

    private suspend fun loadSevenTvGlobal(): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://7tv.io/v3/emote-sets/global") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        requireSuccess("7TV", response.status.value, body)
        val root = parseObject(body) ?: return emptyMap()
        return parseSevenTv(root, EmoteScope.GLOBAL)
    }

    private suspend fun loadSevenTvChannel(
        channelId: String,
    ): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://7tv.io/v3/users/twitch/$channelId") {
            thirdPartyHeaders()
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        requireSuccess("7TV", response.status.value, body)
        val root = parseObject(body) ?: return emptyMap()
        val set = root["emote_set"] as? JsonObject ?: return emptyMap()
        return parseSevenTv(set, EmoteScope.CHANNEL, channelId)
    }

    private fun parseSevenTv(
        set: JsonObject,
        scope: EmoteScope,
        channelId: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        val emotes = set["emotes"] as? JsonArray ?: return@buildMap
        emotes.forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val data = entry["data"] as? JsonObject ?: entry
            val id = data.string("id") ?: entry.string("id") ?: return@forEach
            val code = entry.string("name") ?: data.string("name") ?: return@forEach
            if (id.isBlank() || code.isBlank()) return@forEach
            val host = data["host"] as? JsonObject
            val hostUrl = absoluteUrl(host?.string("url"))
            val files = host?.get("files") as? JsonArray ?: JsonArray(emptyList())
            val animated = data.string("animated")?.toBooleanStrictOrNull()
                ?: files.any { file ->
                    (file as? JsonObject)?.int("frame_count")?.let { it > 1 } == true
                }
            val image1x = sevenTvFileUrl(hostUrl, files, "1x") ?: sevenTvUrl(id, "1x")
            val image2x = sevenTvFileUrl(hostUrl, files, "2x") ?: sevenTvUrl(id, "2x")
            val image3x = sevenTvFileUrl(hostUrl, files, "3x")
                ?: sevenTvFileUrl(hostUrl, files, "4x")
                ?: sevenTvUrl(id, "3x")
            val imageType = image2x.substringAfterLast('.', "webp").substringBefore('?')
            val flags = entry.int("flags") ?: data.int("flags") ?: 0
            val owner = data["owner"] as? JsonObject
            val ownerName = owner?.string("display_name")
                ?: owner?.string("username")
                ?: owner?.string("name")
            put(
                code,
                ThirdPartyEmoteAsset(
                    id = id,
                    code = code,
                    provider = SEVEN_TV,
                    imageType = imageType,
                    animated = animated,
                    imageUrl1x = image1x,
                    imageUrl2x = image2x,
                    imageUrl3x = image3x,
                    scope = scope,
                    channelId = channelId,
                    ownerName = ownerName,
                    sourceUrl = "https://7tv.app/emotes/$id",
                    zeroWidth = flags and SEVEN_TV_ZERO_WIDTH_FLAG != 0,
                ),
            )
        }
    }

    private suspend fun <T> bestEffort(block: suspend () -> Map<String, T>): Map<String, T> = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        emptyMap()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.thirdPartyHeaders() {
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        header(HttpHeaders.UserAgent, "Ferventio")
    }

    private fun requireSuccess(source: String, status: Int, body: String) {
        if (status in 200..299) return
        error("$source emote request failed with HTTP $status: ${body.trim().take(200)}")
    }

    private fun parseObject(body: String): JsonObject? =
        runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull

    private fun JsonObject?.ffzUrl(scale: String): String? =
        absoluteUrl(this?.get(scale)?.jsonPrimitive?.contentOrNull)

    private fun absoluteUrl(value: String?): String? {
        val normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return when {
            normalized.startsWith("https://") || normalized.startsWith("http://") -> normalized
            normalized.startsWith("//") -> "https:$normalized"
            else -> null
        }
    }

    private fun betterTtvUrl(id: String, scale: String): String =
        "https://cdn.betterttv.net/emote/$id/$scale"

    private fun sevenTvUrl(id: String, scale: String): String =
        "https://cdn.7tv.app/emote/$id/$scale.webp"

    private fun sevenTvFileUrl(
        hostUrl: String?,
        files: JsonArray,
        scale: String,
    ): String? {
        val base = hostUrl?.trimEnd('/') ?: return null
        val candidates = files.mapNotNull { it as? JsonObject }
        val file = candidates.firstOrNull { it.string("name") == "$scale.webp" }
            ?: candidates.firstOrNull { it.string("name") == "$scale.avif" }
            ?: candidates.firstOrNull { it.string("name")?.startsWith("$scale.") == true }
            ?: return null
        return "$base/${file.string("name") ?: return null}"
    }

    private companion object {
        const val BETTER_TTV = "betterttv"
        const val FRANKER_FACE_Z = "frankerfacez"
        const val SEVEN_TV = "7tv"
        const val FFZ_EFFECT_ONLY_FLAG = 1
        const val SEVEN_TV_ZERO_WIDTH_FLAG = 1 shl 8
        val PROVIDER_MERGE_ORDER = listOf(SEVEN_TV, FRANKER_FACE_Z, BETTER_TTV)
    }
}
