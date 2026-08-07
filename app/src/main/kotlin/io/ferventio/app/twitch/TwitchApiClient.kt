package io.ferventio.app.twitch

import io.ferventio.app.network.FerventioServerUrlPolicy

import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatSendResult
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.EmoteProviderCatalog
import io.ferventio.app.domain.EmoteScope
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.chatBadgeAssetKey
import io.ferventio.app.domain.PinnedChatMessage
import io.ferventio.app.domain.PublicChannelRelationship
import io.ferventio.app.domain.TwitchSession
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.TwitchChannelInfo
import io.ferventio.app.domain.TwitchStreamInfo
import io.ferventio.app.domain.TwitchCategory
import io.ferventio.app.domain.TwitchClipResult
import io.ferventio.app.domain.TwitchMarkerResult
import io.ferventio.app.domain.TwitchChatter
import io.ferventio.app.domain.TwitchChattersResult
import io.ferventio.app.domain.ModerationChatSettings
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.BannedChatUser
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.app.domain.ChatAssetResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap


class TwitchApiClient : Closeable {
    private val json = Json { ignoreUnknownKeys = true }
    private val userMetadataCache = ConcurrentHashMap<String, TwitchUser>()

    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    suspend fun validateAccessToken(token: String): TwitchSession {
        require(token.isNotBlank()) { "Пустой Twitch access token" }
        val response = client.get("https://id.twitch.tv/oauth2/validate") {
            header(HttpHeaders.Authorization, "OAuth $token")
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val root = json.parseToJsonElement(body).jsonObject
        return TwitchSession(
            clientId = root.string("client_id") ?: error("Twitch validate не вернул client_id"),
            userId = root.string("user_id") ?: error("Twitch validate не вернул user_id"),
            login = root.string("login") ?: error("Twitch validate не вернул login"),
            scopes = root["scopes"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
                ?.toSet()
                ?: error("Twitch validate не вернул scopes"),
            expiresInSeconds = root.string("expires_in")?.toLongOrNull()
                ?: error("Twitch validate не вернул expires_in"),
        )
    }

    suspend fun getCurrentUser(clientId: String, token: String): TwitchUser {
        val users = getUsers(clientId, token, ids = emptyList(), logins = emptyList())
        return users.firstOrNull() ?: error("Не удалось получить пользователя Twitch")
    }

    suspend fun getUserByLogin(clientId: String, token: String, login: String): TwitchUser {
        val normalizedLogin = login.trim().removePrefix("@").lowercase()
        if (normalizedLogin.isBlank()) error("Не указан пользователь Twitch")
        return getUsers(clientId, token, ids = emptyList(), logins = listOf(normalizedLogin)).firstOrNull()
            ?: error("Пользователь $normalizedLogin не найден")
    }

    suspend fun getUserById(clientId: String, token: String, userId: String): TwitchUser {
        if (userId.isBlank()) error("Не указан Twitch ID пользователя")
        return getUsers(clientId, token, ids = listOf(userId), logins = emptyList()).firstOrNull()
            ?: error("Пользователь Twitch не найден")
    }

    suspend fun getUsersByIds(
        clientId: String,
        token: String,
        userIds: List<String>,
    ): List<TwitchUser> = getUsers(
        clientId = clientId,
        token = token,
        ids = userIds.filter(String::isNotBlank).distinct().take(100),
        logins = emptyList(),
    )

    suspend fun getChannel(clientId: String, token: String, login: String): ChatChannel {
        val users = getUsers(clientId, token, ids = emptyList(), logins = listOf(login.lowercase()))
        val user = users.firstOrNull() ?: error("Канал $login не найден")
        return ChatChannel(
            id = user.id,
            login = user.login,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
        )
    }

    suspend fun getChannelsByLogins(
        clientId: String,
        token: String,
        logins: List<String>,
    ): List<ChatChannel> {
        val normalized = logins
            .map { it.trim().removePrefix("#").lowercase() }
            .filter(String::isNotBlank)
            .distinct()
            .take(100)
        if (normalized.isEmpty()) return emptyList()
        return getUsers(clientId, token, ids = emptyList(), logins = normalized).map { user ->
            ChatChannel(
                id = user.id,
                login = user.login,
                displayName = user.displayName,
                profileImageUrl = user.profileImageUrl,
            )
        }
    }

    private suspend fun getUsers(
        clientId: String,
        token: String,
        ids: List<String>,
        logins: List<String>,
    ): List<TwitchUser> {
        val response = client.get("https://api.twitch.tv/helix/users") {
            twitchHeaders(clientId, token)
            ids.forEach { parameter("id", it) }
            logins.forEach { parameter("login", it) }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.map { element ->
            val user = element.jsonObject
            TwitchUser(
                id = user.string("id") ?: error("В ответе Twitch отсутствует id"),
                login = user.string("login") ?: "",
                displayName = user.string("display_name") ?: user.string("login").orEmpty(),
                profileImageUrl = user.string("profile_image_url"),
                createdAt = user.string("created_at"),
                broadcasterType = user.string("broadcaster_type"),
                description = user.string("description"),
            )
        }
    }

    suspend fun getChatColors(
        clientId: String,
        token: String,
        userIds: List<String>,
    ): Map<String, String> {
        val ids = userIds.filter(String::isNotBlank).distinct().take(100)
        if (ids.isEmpty()) return emptyMap()
        val response = client.get("https://api.twitch.tv/helix/chat/color") {
            twitchHeaders(clientId, token)
            ids.forEach { parameter("user_id", it) }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.mapNotNull { item ->
            val value = item.jsonObject
            val userId = value.string("user_id") ?: return@mapNotNull null
            userId to value.string("color").orEmpty()
        }.toMap()
    }

    suspend fun getGlobalChatBadges(
        clientId: String,
        token: String,
    ): Map<String, ChatBadgeAsset> = getChatBadges(
        clientId = clientId,
        token = token,
        url = "https://api.twitch.tv/helix/chat/badges/global",
        broadcasterId = null,
    )

    suspend fun getChannelChatBadges(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): Map<String, ChatBadgeAsset> = getChatBadges(
        clientId = clientId,
        token = token,
        url = "https://api.twitch.tv/helix/chat/badges",
        broadcasterId = broadcasterId,
    )

    private suspend fun getChatBadges(
        clientId: String,
        token: String,
        url: String,
        broadcasterId: String?,
    ): Map<String, ChatBadgeAsset> {
        val response = client.get(url) {
            twitchHeaders(clientId, token)
            broadcasterId?.let { parameter("broadcaster_id", it) }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        return parseChatBadges(body)
    }

    suspend fun getPublicGlobalChatBadges(serverUrl: String): Map<String, ChatBadgeAsset> {
        val baseUrl = FerventioServerUrlPolicy.validate(serverUrl).baseUrl
        return getPublicChatBadges("$baseUrl/v1/twitch/badges/global")
    }

    suspend fun getPublicChannelChatBadges(
        serverUrl: String,
        broadcasterId: String,
    ): Map<String, ChatBadgeAsset> {
        if (broadcasterId.isBlank()) return emptyMap()
        val baseUrl = FerventioServerUrlPolicy.validate(serverUrl).baseUrl
        return getPublicChatBadges("$baseUrl/v1/twitch/badges/$broadcasterId")
    }

    private suspend fun getPublicChatBadges(url: String): Map<String, ChatBadgeAsset> {
        val response = client.get(url) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("Ferventio metadata", response.status.value, body)
        return parseChatBadges(body)
    }

    private fun parseChatBadges(body: String): Map<String, ChatBadgeAsset> {
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        return buildMap {
            for (setElement in data) {
                val set = setElement.jsonObject
                val setId = set.string("set_id").orEmpty()
                if (setId.isBlank()) continue
                val versions = set["versions"] as? JsonArray ?: continue
                for (versionElement in versions) {
                    val version = versionElement.jsonObject
                    val id = version.string("id").orEmpty()
                    if (id.isBlank()) continue
                    val asset = ChatBadgeAsset(
                        setId = setId,
                        id = id,
                        imageUrl1x = version.string("image_url_1x").orEmpty(),
                        imageUrl2x = version.string("image_url_2x").orEmpty(),
                        imageUrl4x = version.string("image_url_4x").orEmpty(),
                        title = version.string("title").orEmpty(),
                        description = version.string("description").orEmpty(),
                    )
                    put(chatBadgeAssetKey(setId, id), asset)
                }
            }
        }
    }

    suspend fun getTwitchUserEmotes(
        clientId: String,
        token: String,
        userId: String,
        broadcasterId: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> {
        if (userId.isBlank()) return emptyMap()

        val emoteObjects = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val response = client.get("https://api.twitch.tv/helix/chat/emotes/user") {
                twitchHeaders(clientId, token)
                parameter("user_id", userId)
                broadcasterId?.takeIf(String::isNotBlank)?.let { parameter("broadcaster_id", it) }
                cursor?.takeIf(String::isNotBlank)?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            ensureSuccess(response.status.value, body)
            val root = json.parseToJsonElement(body).jsonObject
            val page = root["data"] as? JsonArray ?: JsonArray(emptyList())
            emoteObjects += page.mapNotNull { it as? JsonObject }
            cursor = (root["pagination"] as? JsonObject)?.string("cursor")
                ?.takeIf(String::isNotBlank)
        } while (cursor != null && emoteObjects.size < MAX_USER_EMOTES_ITEMS)

        val ownerIds = emoteObjects.asSequence()
            .mapNotNull { it.string("owner_id") }
            .filter { it.isNotBlank() && it != "0" }
            .distinct()
            .toList()
        val missingOwnerIds = ownerIds.filterNot(userMetadataCache::containsKey)
        if (missingOwnerIds.isNotEmpty()) {
            runCatching {
                missingOwnerIds.chunked(100)
                    .flatMap { ids -> getUsersByIds(clientId, token, ids) }
            }.getOrDefault(emptyList()).forEach { owner ->
                userMetadataCache[owner.id] = owner
            }
        }
        val owners = ownerIds.mapNotNull { ownerId ->
            userMetadataCache[ownerId]?.let { ownerId to it }
        }.toMap()

        return buildMap {
            for (emote in emoteObjects) {
                val id = emote.string("id").orEmpty()
                val code = emote.string("name").orEmpty()
                if (id.isBlank() || code.isBlank()) continue

                val formats = (emote["format"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                val animated = "animated" in formats
                val images = emote["images"] as? JsonObject
                val image1x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "1.0",
                    animatedAvailable = animated,
                ) ?: images?.string("url_1x") ?: continue
                val image2x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "2.0",
                    animatedAvailable = animated,
                ) ?: images?.string("url_2x") ?: image1x
                val image3x = ChatAssetResolver.twitchEmoteUrl(
                    emoteId = id,
                    animate = animated,
                    scale = "3.0",
                    animatedAvailable = animated,
                ) ?: images?.string("url_4x") ?: image2x

                val emoteType = emote.string("emote_type")
                val ownerId = emote.string("owner_id")?.takeIf { it.isNotBlank() && it != "0" }
                val owner = ownerId?.let(owners::get)
                val global = emoteType in TWITCH_GLOBAL_EMOTE_TYPES || ownerId == null
                put(
                    "twitch:$id",
                    ThirdPartyEmoteAsset(
                        id = id,
                        code = code,
                        provider = "twitch",
                        imageType = if (animated) "animated" else "image",
                        animated = animated,
                        imageUrl1x = image1x,
                        imageUrl2x = image2x,
                        imageUrl3x = image3x,
                        scope = if (global) EmoteScope.GLOBAL else EmoteScope.CHANNEL,
                        channelId = ownerId?.takeUnless { global },
                        ownerId = ownerId,
                        ownerName = owner?.displayName ?: owner?.login,
                        emoteType = emoteType,
                        emoteSetId = emote.string("emote_set_id"),
                        sourceUrl = owner?.login?.takeIf(String::isNotBlank)
                            ?.let { "https://www.twitch.tv/$it" }
                            ?: "https://www.twitch.tv/",
                        textResolvable = true,
                    ),
                )
            }
        }
    }

    suspend fun getTwitchGlobalEmotes(
        clientId: String,
        token: String,
    ): Map<String, ThirdPartyEmoteAsset> = getTwitchEmotes(
        clientId = clientId,
        token = token,
        broadcasterId = null,
    )

    suspend fun getTwitchChannelEmotes(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): Map<String, ThirdPartyEmoteAsset> = getTwitchEmotes(
        clientId = clientId,
        token = token,
        broadcasterId = broadcasterId,
    )

    private suspend fun getTwitchEmotes(
        clientId: String,
        token: String,
        broadcasterId: String?,
    ): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get(
            if (broadcasterId == null) {
                "https://api.twitch.tv/helix/chat/emotes/global"
            } else {
                "https://api.twitch.tv/helix/chat/emotes"
            },
        ) {
            twitchHeaders(clientId, token)
            broadcasterId?.let { parameter("broadcaster_id", it) }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray
            ?: return emptyMap()
        return buildMap {
            for (element in data) {
                val emote = element as? JsonObject ?: continue
                val id = emote.string("id").orEmpty()
                val code = emote.string("name").orEmpty()
                if (id.isBlank() || code.isBlank()) continue
                val images = emote["images"] as? JsonObject
                val image1x = images?.string("url_1x")
                    ?: ChatAssetResolver.twitchEmoteUrl(id, false, "1.0")
                    ?: continue
                val image2x = images?.string("url_2x")
                    ?: ChatAssetResolver.twitchEmoteUrl(id, false, "2.0")
                    ?: image1x
                val image3x = images?.string("url_4x")
                    ?: ChatAssetResolver.twitchEmoteUrl(id, false, "3.0")
                    ?: image2x
                val formats = (emote["format"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                put(
                    code,
                    ThirdPartyEmoteAsset(
                        id = id,
                        code = code,
                        provider = "twitch",
                        imageType = "image",
                        animated = "animated" in formats,
                        imageUrl1x = image1x,
                        imageUrl2x = image2x,
                        imageUrl3x = image3x,
                        scope = if (broadcasterId == null) EmoteScope.GLOBAL else EmoteScope.CHANNEL,
                        channelId = broadcasterId,
                        sourceUrl = "https://www.twitch.tv/",
                        textResolvable = true,
                    ),
                )
            }
        }
    }

    suspend fun getCheermotes(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): Map<String, List<CheermoteAsset>> {
        val response = client.get("https://api.twitch.tv/helix/bits/cheermotes") {
            twitchHeaders(clientId, token)
            if (broadcasterId.isNotBlank()) parameter("broadcaster_id", broadcasterId)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray
            ?: return emptyMap()
        return buildMap {
            for (element in data) {
                val cheermote = element as? JsonObject ?: continue
                val prefix = cheermote.string("prefix").orEmpty()
                if (prefix.isBlank()) continue
                val tiers = (cheermote["tiers"] as? JsonArray).orEmpty()
                    .mapNotNull { tierElement ->
                        val tier = tierElement as? JsonObject ?: return@mapNotNull null
                        val tierId = tier.int("id") ?: tier.string("id")?.toIntOrNull() ?: 0
                        val minBits = tier.int("min_bits") ?: tierId
                        val images = tier["images"] as? JsonObject
                        val dark = images?.get("dark") as? JsonObject
                        val animated = dark?.get("animated") as? JsonObject
                        val static = dark?.get("static") as? JsonObject
                        CheermoteAsset(
                            prefix = prefix,
                            minBits = minBits,
                            tier = tierId,
                            color = tier.string("color").orEmpty(),
                            animatedImageUrl = animated?.string("2")
                                ?: animated?.string("1")
                                ?: animated?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull,
                            staticImageUrl = static?.string("2")
                                ?: static?.string("1")
                                ?: static?.values?.firstOrNull()?.jsonPrimitive?.contentOrNull,
                        )
                    }
                    .sortedBy(CheermoteAsset::minBits)
                if (tiers.isNotEmpty()) put(prefix.lowercase(), tiers)
            }
        }
    }

    suspend fun getFrankerFaceZBadgesByUserId(): Map<String, List<ChatBadgeAsset>> {
        val response = client.get("https://api.frankerfacez.com/v1/badges/ids") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("FrankerFaceZ", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val badges = root["badges"] as? JsonArray ?: JsonArray(emptyList())
        val byKey = buildMap<String, ChatBadgeAsset> {
            for (element in badges) {
                val badge = element as? JsonObject ?: continue
                val asset = parseFrankerFaceZBadgeAsset(badge) ?: continue
                put(asset.id, asset)
                badge.string("name")?.takeIf(String::isNotBlank)?.let { put(it, asset) }
            }
        }
        val users = root["users"] as? JsonObject ?: return emptyMap()
        return buildMap {
            for ((badgeKey, userElement) in users) {
                val asset = byKey[badgeKey] ?: continue
                val userIds = when (userElement) {
                    is JsonArray -> userElement.mapNotNull { it.jsonPrimitive.contentOrNull }
                    else -> emptyList()
                }
                for (userId in userIds) {
                    if (userId.isBlank()) continue
                    put(userId, get(userId).orEmpty() + asset)
                }
            }
        }
    }

    suspend fun getFrankerFaceZChannelBadgesByUserId(
        broadcasterId: String,
    ): Map<String, List<ChatBadgeAsset>> {
        if (broadcasterId.isBlank()) return emptyMap()
        val response = client.get("https://api.frankerfacez.com/v1/room/id/$broadcasterId") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        ensureThirdPartySuccess("FrankerFaceZ", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val room = root["room"] as? JsonObject ?: return emptyMap()
        val assignments = room["user_badge_ids"] as? JsonObject ?: return emptyMap()
        if (assignments.isEmpty()) return emptyMap()

        val assetsById = buildMap<String, ChatBadgeAsset> {
            for (badgeId in assignments.keys) {
                val asset = runCatching { getFrankerFaceZBadgeAsset(badgeId) }.getOrNull() ?: continue
                put(badgeId, asset)
            }
        }
        return buildMap {
            for ((badgeId, usersElement) in assignments) {
                val asset = assetsById[badgeId] ?: continue
                val userIds = (usersElement as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    .orEmpty()
                for (userId in userIds) {
                    if (userId.isBlank()) continue
                    val merged = (get(userId).orEmpty() + asset).distinctBy(ChatBadgeAsset::key)
                    put(userId, merged)
                }
            }
        }
    }

    private suspend fun getFrankerFaceZBadgeAsset(badgeId: String): ChatBadgeAsset? {
        val response = client.get("https://api.frankerfacez.com/v1/_badge/$badgeId") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return null
        ensureThirdPartySuccess("FrankerFaceZ", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return null
        val badge = root["badge"] as? JsonObject ?: return null
        return parseFrankerFaceZBadgeAsset(badge)
    }

    private fun parseFrankerFaceZBadgeAsset(badge: JsonObject): ChatBadgeAsset? {
        val id = badge.string("id").orEmpty()
        val name = badge.string("name") ?: badge.string("title") ?: id
        if (id.isBlank() || name.isBlank()) return null
        val urls = badge["urls"] as? JsonObject
        val legacyImage = badge.string("alpha_image") ?: badge.string("image")
        val url1 = urls.ffzUrl("1")
            ?: ChatAssetResolver.absoluteImageUrl(legacyImage)
            ?: return null
        val url2 = urls.ffzUrl("2") ?: url1
        val url4 = urls.ffzUrl("4") ?: url2
        return ChatBadgeAsset(
            setId = "ffz:$name",
            id = id,
            imageUrl1x = url1,
            imageUrl2x = url2,
            imageUrl4x = url4,
            title = badge.string("title") ?: name,
            description = badge.string("description") ?: badge.string("title") ?: name,
        )
    }

    suspend fun getBetterTtvGlobalEmotes(): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.betterttv.net/3/cached/emotes/global") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("BetterTTV", response.status.value, body)
        val data = json.parseToJsonElement(body) as? JsonArray ?: JsonArray(emptyList())
        return parseBetterTtvEmotes(data, scope = EmoteScope.GLOBAL)
    }

    suspend fun getBetterTtvChannelEmotes(
        broadcasterId: String,
    ): Map<String, ThirdPartyEmoteAsset> {
        if (broadcasterId.isBlank()) return emptyMap()
        val response = client.get("https://api.betterttv.net/3/cached/users/twitch/$broadcasterId") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        ensureThirdPartySuccess("BetterTTV", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val shared = root["sharedEmotes"] as? JsonArray ?: JsonArray(emptyList())
        val channel = root["channelEmotes"] as? JsonArray ?: JsonArray(emptyList())
        val channelOwner = root.string("displayName") ?: root.string("name")
        return parseBetterTtvEmotes(shared, EmoteScope.CHANNEL, broadcasterId) +
            parseBetterTtvEmotes(channel, EmoteScope.CHANNEL, broadcasterId, channelOwner)
    }

    private fun parseBetterTtvEmotes(
        data: JsonArray,
        scope: EmoteScope,
        channelId: String? = null,
        ownerFallback: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        for (element in data) {
            val emote = element as? JsonObject ?: continue
            val id = emote.string("id").orEmpty()
            val code = emote.string("code").orEmpty()
            if (id.isBlank() || code.isBlank()) continue
            val imageType = emote.string("imageType").orEmpty()
            val animated = emote["animated"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
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
                    provider = "betterttv",
                    imageType = imageType,
                    animated = animated,
                    imageUrl1x = ChatAssetResolver.betterTtvEmoteUrl(id, "1x").orEmpty(),
                    imageUrl2x = ChatAssetResolver.betterTtvEmoteUrl(id, "2x").orEmpty(),
                    imageUrl3x = ChatAssetResolver.betterTtvEmoteUrl(id, "3x").orEmpty(),
                    scope = scope,
                    channelId = channelId,
                    ownerName = ownerName,
                    sourceUrl = "https://betterttv.com/emotes/$id",
                ),
            )
        }
    }

    suspend fun getFrankerFaceZGlobalEmotes(): Map<String, ThirdPartyEmoteAsset> {
        val response = client.get("https://api.frankerfacez.com/v1/set/global") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("FrankerFaceZ", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val defaultSetIds = (root["default_sets"] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.toSet()
            .orEmpty()
        val sets = root["sets"] as? JsonObject ?: return emptyMap()
        return parseFrankerFaceZSets(sets, defaultSetIds, EmoteScope.GLOBAL)
    }

    suspend fun getFrankerFaceZChannelEmotes(
        broadcasterId: String,
    ): Map<String, ThirdPartyEmoteAsset> {
        if (broadcasterId.isBlank()) return emptyMap()
        val response = client.get("https://api.frankerfacez.com/v1/room/id/$broadcasterId") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return emptyMap()
        ensureThirdPartySuccess("FrankerFaceZ", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        val room = root["room"] as? JsonObject
        val primarySetId = room?.get("set")?.jsonPrimitive?.contentOrNull
        val sets = root["sets"] as? JsonObject ?: return emptyMap()
        return parseFrankerFaceZSets(
            sets = sets,
            allowedSetIds = primarySetId?.let { setOf(it) }.orEmpty(),
            scope = EmoteScope.CHANNEL,
            channelId = broadcasterId,
        )
    }

    private fun parseFrankerFaceZSets(
        sets: JsonObject,
        allowedSetIds: Set<String>,
        scope: EmoteScope,
        channelId: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        if (allowedSetIds.isEmpty()) return@buildMap
        for ((setId, setElement) in sets) {
            if (setId !in allowedSetIds) continue
            val set = setElement as? JsonObject ?: continue
            val emoticons = set["emoticons"] as? JsonArray ?: continue
            for (element in emoticons) {
                val emote = element as? JsonObject ?: continue
                val id = emote.string("id").orEmpty()
                val code = emote.string("name").orEmpty()
                if (id.isBlank() || code.isBlank()) continue
                val staticUrls = emote["urls"] as? JsonObject
                val animatedUrls = emote["animated"] as? JsonObject
                val animated = animatedUrls?.values
                    ?.any { value -> value.jsonPrimitive.contentOrNull?.isNotBlank() == true } == true
                val modifier = emote["modifier"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
                val modifierFlags = emote.int("modifier_flags") ?: 0
                // FFZ effect-only modifiers are hidden and require CSS effects which the native
                // renderer cannot reproduce. Visible modifier images are supported as overlays.
                if (modifier && modifierFlags and 1 == 1) continue
                val owner = emote["owner"] as? JsonObject
                val ownerName = owner?.string("display_name") ?: owner?.string("name")
                val preferredUrls = if (animated) animatedUrls else staticUrls
                val image1x = preferredUrls.ffzUrl("1")
                    ?: staticUrls.ffzUrl("1")
                    ?: continue
                val image2x = preferredUrls.ffzUrl("2")
                    ?: staticUrls.ffzUrl("2")
                    ?: image1x
                val image4x = preferredUrls.ffzUrl("4")
                    ?: staticUrls.ffzUrl("4")
                    ?: image2x
                put(
                    code,
                    ThirdPartyEmoteAsset(
                        id = id,
                        code = code,
                        provider = "frankerfacez",
                        imageType = if (animated) "webp" else "image",
                        animated = animated,
                        imageUrl1x = image1x,
                        imageUrl2x = image2x,
                        imageUrl3x = image4x,
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

    private fun JsonObject?.ffzUrl(scale: String): String? =
        ChatAssetResolver.absoluteImageUrl(this?.get(scale)?.jsonPrimitive?.contentOrNull)

    suspend fun getSevenTvGlobalEmotes(): Map<String, ThirdPartyEmoteAsset> =
        getSevenTvGlobalCatalog().emotes

    suspend fun getSevenTvGlobalCatalog(): EmoteProviderCatalog {
        val response = client.get("https://7tv.io/v3/emote-sets/global") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("7TV", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return EmoteProviderCatalog()
        val setId = root.string("id")
        return EmoteProviderCatalog(
            emotes = parseSevenTvEmoteSet(root, EmoteScope.GLOBAL),
            subscriptionIds = setOfNotNull(setId),
        )
    }

    suspend fun getSevenTvChannelEmotes(
        broadcasterId: String,
    ): Map<String, ThirdPartyEmoteAsset> = getSevenTvChannelCatalog(broadcasterId).emotes

    suspend fun getSevenTvChannelCatalog(
        broadcasterId: String,
    ): EmoteProviderCatalog {
        if (broadcasterId.isBlank()) return EmoteProviderCatalog()
        val response = client.get("https://7tv.io/v3/users/twitch/$broadcasterId") {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        if (response.status.value == 404) return EmoteProviderCatalog()
        ensureThirdPartySuccess("7TV", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: return EmoteProviderCatalog()
        val set = root["emote_set"] as? JsonObject ?: return EmoteProviderCatalog()
        return EmoteProviderCatalog(
            emotes = parseSevenTvEmoteSet(set, EmoteScope.CHANNEL, broadcasterId),
            subscriptionIds = setOfNotNull(set.string("id")),
        )
    }

    private fun parseSevenTvEmoteSet(
        set: JsonObject,
        scope: EmoteScope,
        channelId: String? = null,
    ): Map<String, ThirdPartyEmoteAsset> = buildMap {
        val emotes = set["emotes"] as? JsonArray ?: return@buildMap
        for (element in emotes) {
            val entry = element as? JsonObject ?: continue
            val data = entry["data"] as? JsonObject ?: entry
            val id = data.string("id") ?: entry.string("id") ?: continue
            val code = entry.string("name") ?: data.string("name") ?: continue
            if (id.isBlank() || code.isBlank()) continue

            val host = data["host"] as? JsonObject
            val hostUrl = ChatAssetResolver.absoluteImageUrl(host?.string("url"))
            val files = host?.get("files") as? JsonArray ?: JsonArray(emptyList())
            val animated = data["animated"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                ?: files.any { file ->
                    (file as? JsonObject)?.int("frame_count")?.let { it > 1 } == true
                }
            val image1x = sevenTvFileUrl(hostUrl, files, "1x")
                ?: ChatAssetResolver.sevenTvEmoteUrl(id, "1x")
                ?: continue
            val image2x = sevenTvFileUrl(hostUrl, files, "2x")
                ?: ChatAssetResolver.sevenTvEmoteUrl(id, "2x")
                ?: image1x
            val image3x = sevenTvFileUrl(hostUrl, files, "3x")
                ?: fourOrThreeSevenTvFileUrl(hostUrl, files)
                ?: ChatAssetResolver.sevenTvEmoteUrl(id, "3x")
                ?: image2x
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
                    provider = "7tv",
                    imageType = imageType,
                    animated = animated,
                    imageUrl1x = image1x,
                    imageUrl2x = image2x,
                    imageUrl3x = image3x,
                    scope = scope,
                    channelId = channelId,
                    ownerName = ownerName,
                    sourceUrl = "https://7tv.app/emotes/$id",
                    zeroWidth = flags and 1 == 1,
                ),
            )
        }
    }

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

    private fun fourOrThreeSevenTvFileUrl(
        hostUrl: String?,
        files: JsonArray,
    ): String? = sevenTvFileUrl(hostUrl, files, "4x")
        ?: sevenTvFileUrl(hostUrl, files, "3x")

    suspend fun getModeratedChannelIds(
        clientId: String,
        token: String,
        userId: String,
    ): Set<String> {
        val response = client.get("https://api.twitch.tv/helix/moderation/channels") {
            twitchHeaders(clientId, token)
            parameter("user_id", userId)
            parameter("first", 100)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.mapNotNull { it.jsonObject.string("broadcaster_id") }.toSet()
    }

    suspend fun getPublicChannelRelationship(
        userLogin: String,
        channelLogin: String,
    ): PublicChannelRelationship {
        val normalizedUser = userLogin.trim().removePrefix("@").lowercase()
        val normalizedChannel = channelLogin.trim().removePrefix("#").lowercase()
        require(TWITCH_LOGIN_REGEX.matches(normalizedUser)) { "Некорректный Twitch login пользователя" }
        require(TWITCH_LOGIN_REGEX.matches(normalizedChannel)) { "Некорректный Twitch login канала" }
        val response = client.get(
            "https://api.ivr.fi/v2/twitch/subage/$normalizedUser/$normalizedChannel",
        ) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            header(HttpHeaders.UserAgent, "Ferventio Android")
        }
        val body = response.bodyAsText()
        ensureThirdPartySuccess("IVR", response.status.value, body)
        val root = json.parseToJsonElement(body) as? JsonObject ?: JsonObject(emptyMap())
        val meta = root["meta"] as? JsonObject
        val cumulative = root["cumulative"] as? JsonObject
        return PublicChannelRelationship(
            followedAt = root.string("followedAt")?.takeIf(String::isNotBlank),
            subscriptionStatusHidden = root.boolean("statusHidden"),
            isCurrentlySubscribed = if (root.containsKey("meta")) meta != null else null,
            subscriberMonths = cumulative?.int("months")?.takeIf { it > 0 },
            subscriberTier = meta?.string("tier")?.takeIf(String::isNotBlank),
        )
    }

    suspend fun getFollowedAt(
        clientId: String,
        token: String,
        broadcasterId: String,
        userId: String,
    ): String? {
        val response = client.get("https://api.twitch.tv/helix/channels/followers") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("user_id", userId)
            parameter("first", 1)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val data = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
        return data.firstOrNull()?.jsonObject?.string("followed_at")
    }

    suspend fun createEventSubSubscription(
        clientId: String,
        token: String,
        sessionId: String,
        broadcasterId: String,
        userId: String,
        type: String,
        version: String = "1",
        identityConditionKey: String? = "user_id",
    ) {
        val payload = buildJsonObject {
            put("type", JsonPrimitive(type))
            put("version", JsonPrimitive(version))
            put("condition", buildJsonObject {
                put("broadcaster_user_id", JsonPrimitive(broadcasterId))
                identityConditionKey?.let { key -> put(key, JsonPrimitive(userId)) }
            })
            put("transport", buildJsonObject {
                put("method", JsonPrimitive("websocket"))
                put("session_id", JsonPrimitive(sessionId))
            })
        }

        val response = client.post("https://api.twitch.tv/helix/eventsub/subscriptions") {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        if (response.status.value == 409) {
            val existingSubscriptionId = runCatching {
                json.parseToJsonElement(body).jsonObject.string("id")
            }.getOrNull()
            throw EventSubSubscriptionConflictException(
                type = type,
                broadcasterId = broadcasterId,
                existingSubscriptionId = existingSubscriptionId,
            )
        }
        ensureSuccess(response.status.value, body)
    }

    suspend fun getPinnedChatMessage(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
    ): PinnedChatMessage? {
        val response = client.get("https://api.twitch.tv/helix/chat/pins") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: return null
        val message = item["message"]?.jsonObject ?: JsonObject(emptyMap())
        val fragments = message["fragments"]?.jsonArray.orEmpty().mapNotNull { element ->
            runCatching { parsePinnedFragment(element.jsonObject) }.getOrNull()
        }
        return PinnedChatMessage(
            channelId = item.string("broadcaster_id") ?: broadcasterId,
            messageId = item.string("message_id").orEmpty(),
            senderUserId = item.string("sender_user_id").orEmpty(),
            senderUserLogin = item.string("sender_user_login").orEmpty(),
            senderUserName = item.string("sender_user_name").orEmpty(),
            pinnedByUserName = item.string("pinned_by_user_name"),
            text = message.string("text").orEmpty(),
            fragments = fragments,
            startsAt = item.string("starts_at"),
            endsAt = item.string("ends_at"),
        )
    }

    suspend fun pinChatMessage(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        messageId: String,
        durationSeconds: Int? = null,
    ) {
        val response = client.put("https://api.twitch.tv/helix/chat/pins") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            parameter("message_id", messageId)
            durationSeconds?.let { parameter("duration_seconds", it.coerceIn(30, 1_800)) }
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun unpinChatMessage(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        messageId: String,
    ) {
        val response = client.delete("https://api.twitch.tv/helix/chat/pins") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            parameter("message_id", messageId)
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun getChatSettings(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
    ): ModerationChatSettings {
        val response = client.get("https://api.twitch.tv/helix/chat/settings") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: JsonObject(emptyMap())
        return ModerationChatSettings(
            channelId = broadcasterId,
            slowMode = item.boolean("slow_mode"),
            slowModeWaitSeconds = item.int("slow_mode_wait_time") ?: 30,
            followerMode = item.boolean("follower_mode"),
            followerModeDurationMinutes = item.int("follower_mode_duration") ?: 0,
            subscriberMode = item.boolean("subscriber_mode"),
            emoteMode = item.boolean("emote_mode"),
            uniqueChatMode = item.boolean("unique_chat_mode"),
        )
    }

    suspend fun getModerators(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): List<ModerationUser> = getModerationUsers(
        clientId = clientId,
        token = token,
        url = "https://api.twitch.tv/helix/moderation/moderators",
        broadcasterId = broadcasterId,
    )

    suspend fun getVips(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): List<ModerationUser> = getModerationUsers(
        clientId = clientId,
        token = token,
        url = "https://api.twitch.tv/helix/channels/vips",
        broadcasterId = broadcasterId,
    )

    private suspend fun getModerationUsers(
        clientId: String,
        token: String,
        url: String,
        broadcasterId: String,
    ): List<ModerationUser> {
        val users = mutableListOf<ModerationUser>()
        var cursor: String? = null
        do {
            val response = client.get(url) {
                twitchHeaders(clientId, token)
                parameter("broadcaster_id", broadcasterId)
                parameter("first", 100)
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            ensureSuccess(response.status.value, body)
            val root = json.parseToJsonElement(body).jsonObject
            users += root["data"]?.jsonArray.orEmpty().map { element ->
                val item = element.jsonObject
                ModerationUser(
                    id = item.string("user_id").orEmpty(),
                    login = item.string("user_login").orEmpty(),
                    displayName = item.string("user_name").orEmpty(),
                )
            }
            cursor = root["pagination"]?.jsonObject?.string("cursor")?.takeIf(String::isNotBlank)
        } while (cursor != null && users.size < MAX_MODERATION_LIST_ITEMS)
        return users.distinctBy(ModerationUser::id).take(MAX_MODERATION_LIST_ITEMS)
    }

    suspend fun getBannedUsers(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): List<BannedChatUser> {
        val users = mutableListOf<BannedChatUser>()
        var cursor: String? = null
        do {
            val response = client.get("https://api.twitch.tv/helix/moderation/banned") {
                twitchHeaders(clientId, token)
                parameter("broadcaster_id", broadcasterId)
                parameter("first", 100)
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            ensureSuccess(response.status.value, body)
            val root = json.parseToJsonElement(body).jsonObject
            users += root["data"]?.jsonArray.orEmpty().map { element ->
                val item = element.jsonObject
                BannedChatUser(
                    id = item.string("user_id").orEmpty(),
                    login = item.string("user_login").orEmpty(),
                    displayName = item.string("user_name").orEmpty(),
                    expiresAt = item.string("expires_at"),
                    createdAt = item.string("created_at"),
                    reason = item.string("reason"),
                    moderatorId = item.string("moderator_id"),
                    moderatorLogin = item.string("moderator_login"),
                    moderatorName = item.string("moderator_name"),
                )
            }
            cursor = root["pagination"]?.jsonObject?.string("cursor")?.takeIf(String::isNotBlank)
        } while (cursor != null && users.size < MAX_MODERATION_LIST_ITEMS)
        return users.distinctBy(BannedChatUser::id).take(MAX_MODERATION_LIST_ITEMS)
    }

    suspend fun manageHeldAutoModMessage(
        clientId: String,
        token: String,
        moderatorId: String,
        messageId: String,
        allow: Boolean,
    ) {
        val payload = buildJsonObject {
            put("user_id", JsonPrimitive(moderatorId))
            put("msg_id", JsonPrimitive(messageId))
            put("action", JsonPrimitive(if (allow) "ALLOW" else "DENY"))
        }
        val response = client.post("https://api.twitch.tv/helix/moderation/automod/message") {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun updateChatSettings(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        slowMode: Boolean? = null,
        slowModeWaitSeconds: Int? = null,
        followerMode: Boolean? = null,
        followerModeDurationMinutes: Int? = null,
        subscriberMode: Boolean? = null,
        emoteMode: Boolean? = null,
        uniqueChatMode: Boolean? = null,
    ) {
        val payload = buildJsonObject {
            slowMode?.let { put("slow_mode", JsonPrimitive(it)) }
            slowModeWaitSeconds?.let { put("slow_mode_wait_time", JsonPrimitive(it)) }
            followerMode?.let { put("follower_mode", JsonPrimitive(it)) }
            followerModeDurationMinutes?.let { put("follower_mode_duration", JsonPrimitive(it)) }
            subscriberMode?.let { put("subscriber_mode", JsonPrimitive(it)) }
            emoteMode?.let { put("emote_mode", JsonPrimitive(it)) }
            uniqueChatMode?.let { put("unique_chat_mode", JsonPrimitive(it)) }
        }
        val response = client.patch("https://api.twitch.tv/helix/chat/settings") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun getChannelInformation(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): TwitchChannelInfo {
        val response = client.get("https://api.twitch.tv/helix/channels") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?: error("Канал Twitch не найден")
        return TwitchChannelInfo(
            broadcasterId = item.string("broadcaster_id") ?: broadcasterId,
            title = item.string("title").orEmpty(),
            gameId = item.string("game_id").orEmpty(),
            gameName = item.string("game_name").orEmpty(),
        )
    }

    suspend fun modifyChannelInformation(
        clientId: String,
        token: String,
        broadcasterId: String,
        title: String? = null,
        gameId: String? = null,
    ) {
        val payload = buildJsonObject {
            title?.let { put("title", JsonPrimitive(it.take(140))) }
            gameId?.let { put("game_id", JsonPrimitive(it)) }
        }
        val response = client.patch("https://api.twitch.tv/helix/channels") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun searchCategories(
        clientId: String,
        token: String,
        query: String,
        first: Int = 20,
    ): List<TwitchCategory> {
        val response = client.get("https://api.twitch.tv/helix/search/categories") {
            twitchHeaders(clientId, token)
            parameter("query", query)
            parameter("first", first.coerceIn(1, 100))
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        return json.parseToJsonElement(body).jsonObject["data"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            TwitchCategory(
                id = item.string("id").orEmpty(),
                name = item.string("name").orEmpty(),
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() }
    }

    suspend fun getStream(
        clientId: String,
        token: String,
        broadcasterId: String,
    ): TwitchStreamInfo? {
        val response = client.get("https://api.twitch.tv/helix/streams") {
            twitchHeaders(clientId, token)
            parameter("user_id", broadcasterId)
            parameter("first", 1)
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: return null
        return TwitchStreamInfo(
            id = item.string("id").orEmpty(),
            startedAt = item.string("started_at").orEmpty(),
            viewerCount = item.int("viewer_count") ?: 0,
            title = item.string("title").orEmpty(),
            gameName = item.string("game_name").orEmpty(),
        )
    }

    suspend fun createClip(
        clientId: String,
        token: String,
        broadcasterId: String,
        title: String? = null,
    ): TwitchClipResult {
        val response = client.post("https://api.twitch.tv/helix/clips") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            title?.takeIf(String::isNotBlank)?.let { parameter("title", it.take(140)) }
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: error("Twitch не вернул созданный clip")
        return TwitchClipResult(
            id = item.string("id").orEmpty(),
            editUrl = item.string("edit_url").orEmpty(),
        )
    }

    suspend fun createStreamMarker(
        clientId: String,
        token: String,
        broadcasterId: String,
        description: String? = null,
    ): TwitchMarkerResult {
        val payload = buildJsonObject {
            put("user_id", JsonPrimitive(broadcasterId))
            description?.takeIf(String::isNotBlank)?.let { put("description", JsonPrimitive(it.take(140))) }
        }
        val response = client.post("https://api.twitch.tv/helix/streams/markers") {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        ensureSuccess(response.status.value, body)
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject ?: error("Twitch не вернул созданный marker")
        return TwitchMarkerResult(
            id = item.string("id").orEmpty(),
            positionSeconds = item.int("position_seconds") ?: 0,
            description = item.string("description").orEmpty(),
        )
    }

    suspend fun getChatters(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        first: Int = 100,
    ): TwitchChattersResult {
        val requested = first.coerceIn(1, MAX_CHATTERS_LIST_ITEMS)
        val users = mutableListOf<TwitchChatter>()
        var total = 0
        var cursor: String? = null
        do {
            val response = client.get("https://api.twitch.tv/helix/chat/chatters") {
                twitchHeaders(clientId, token)
                parameter("broadcaster_id", broadcasterId)
                parameter("moderator_id", moderatorId)
                parameter("first", minOf(1_000, requested - users.size).coerceAtLeast(1))
                cursor?.let { parameter("after", it) }
            }
            val body = response.bodyAsText()
            ensureSuccess(response.status.value, body)
            val root = json.parseToJsonElement(body).jsonObject
            total = root.int("total") ?: total
            users += root["data"]?.jsonArray.orEmpty().map { element ->
                val item = element.jsonObject
                TwitchChatter(
                    id = item.string("user_id").orEmpty(),
                    login = item.string("user_login").orEmpty(),
                    displayName = item.string("user_name").orEmpty(),
                )
            }
            cursor = root["pagination"]?.jsonObject?.string("cursor")?.takeIf(String::isNotBlank)
        } while (cursor != null && users.size < requested)
        return TwitchChattersResult(
            total = total,
            users = users.distinctBy(TwitchChatter::id).take(requested),
        )
    }

    suspend fun sendMessage(
        clientId: String,
        token: String,
        broadcasterId: String,
        senderId: String,
        message: String,
        replyParentMessageId: String? = null,
    ): ChatSendResult {
        val payload = buildJsonObject {
            put("broadcaster_id", JsonPrimitive(broadcasterId))
            put("sender_id", JsonPrimitive(senderId))
            put("message", JsonPrimitive(message))
            replyParentMessageId?.let { put("reply_parent_message_id", JsonPrimitive(it)) }
        }

        val response = client.post("https://api.twitch.tv/helix/chat/messages") {
            twitchHeaders(clientId, token)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            val retryAtMillis = response.headers["Ratelimit-Reset"]
                ?.toLongOrNull()
                ?.times(1_000L)
                ?: response.headers["Retry-After"]
                    ?.toLongOrNull()
                    ?.let { seconds -> System.currentTimeMillis() + seconds * 1_000L }
            throw TwitchChatSendException(
                statusCode = response.status.value,
                apiMessage = parseApiMessage(body) ?: body.take(300).ifBlank { "неизвестная ошибка" },
                retryAtMillis = retryAtMillis,
            )
        }

        val result = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray
            ?.firstOrNull()?.jsonObject
        val isSent = result?.get("is_sent")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        if (!isSent) {
            val reason = result.get("drop_reason")?.jsonObject?.string("message")
            throw TwitchChatSendException(
                statusCode = response.status.value,
                apiMessage = reason ?: "Twitch отклонил сообщение",
                retryAtMillis = null,
            )
        }
        return ChatSendResult(messageId = result?.string("message_id"))
    }

    suspend fun deleteMessage(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        messageId: String,
    ) {
        val response = client.delete("https://api.twitch.tv/helix/moderation/chat") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            parameter("message_id", messageId)
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun timeoutUser(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        targetUserId: String,
        durationSeconds: Int,
        reason: String = "Ferventio moderation",
    ) {
        banOrTimeout(
            clientId = clientId,
            token = token,
            broadcasterId = broadcasterId,
            moderatorId = moderatorId,
            targetUserId = targetUserId,
            durationSeconds = durationSeconds,
            reason = reason,
        )
    }

    suspend fun banUser(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        targetUserId: String,
        reason: String = "Ferventio moderation",
    ) {
        banOrTimeout(
            clientId = clientId,
            token = token,
            broadcasterId = broadcasterId,
            moderatorId = moderatorId,
            targetUserId = targetUserId,
            durationSeconds = null,
            reason = reason,
        )
    }

    suspend fun unbanUser(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        targetUserId: String,
    ) {
        val response = client.delete("https://api.twitch.tv/helix/moderation/bans") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            parameter("user_id", targetUserId)
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun warnUser(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        targetUserId: String,
        reason: String,
    ) {
        val payload = buildJsonObject {
            put("data", buildJsonObject {
                put("user_id", JsonPrimitive(targetUserId))
                put("reason", JsonPrimitive(reason.trim().take(500)))
            })
        }
        val response = client.post("https://api.twitch.tv/helix/moderation/warnings") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun blockUser(
        clientId: String,
        token: String,
        targetUserId: String,
    ) {
        val response = client.put("https://api.twitch.tv/helix/users/blocks") {
            twitchHeaders(clientId, token)
            parameter("target_user_id", targetUserId)
            parameter("source_context", "chat")
            parameter("reason", "other")
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    suspend fun clearChat(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
    ) {
        val response = client.delete("https://api.twitch.tv/helix/moderation/chat") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    private suspend fun banOrTimeout(
        clientId: String,
        token: String,
        broadcasterId: String,
        moderatorId: String,
        targetUserId: String,
        durationSeconds: Int?,
        reason: String,
    ) {
        val payload = buildJsonObject {
            put("data", buildJsonObject {
                put("user_id", JsonPrimitive(targetUserId))
                durationSeconds?.let { put("duration", JsonPrimitive(it)) }
                put("reason", JsonPrimitive(reason.take(500)))
            })
        }
        val response = client.post("https://api.twitch.tv/helix/moderation/bans") {
            twitchHeaders(clientId, token)
            parameter("broadcaster_id", broadcasterId)
            parameter("moderator_id", moderatorId)
            contentType(ContentType.Application.Json)
            setBody(payload.toString())
        }
        ensureSuccess(response.status.value, response.bodyAsText())
    }

    override fun close() {
        client.close()
    }

    private fun io.ktor.client.request.HttpRequestBuilder.twitchHeaders(clientId: String, token: String) {
        header("Client-Id", clientId)
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private fun ensureSuccess(status: Int, body: String) {
        if (status in 200..299) return
        throw TwitchApiException(
            statusCode = status,
            apiMessage = parseApiMessage(body) ?: body.take(300).ifBlank { "неизвестная ошибка" },
        )
    }

    private fun ensureThirdPartySuccess(service: String, status: Int, body: String) {
        if (status in 200..299) return
        error("$service API $status: ${body.take(300).ifBlank { "неизвестная ошибка" }}")
    }

    private fun parseApiMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root.string("message") ?: root.string("error")
    }.getOrNull()

    private fun parsePinnedFragment(item: JsonObject): ChatFragment {
        val text = item.string("text").orEmpty()
        return when (item.string("type")) {
            "emote" -> {
                val emote = item["emote"]?.jsonObject
                ChatFragment.TwitchEmote(
                    text = text,
                    emoteId = emote?.string("id").orEmpty(),
                    emoteSetId = emote?.string("emote_set_id"),
                    ownerId = emote?.string("owner_id"),
                    formats = emote?.get("format")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                        ?.toSet()
                        .orEmpty(),
                )
            }

            "mention" -> {
                val mention = item["mention"]?.jsonObject
                ChatFragment.Mention(
                    text = text,
                    userId = mention?.string("user_id").orEmpty(),
                    userLogin = mention?.string("user_login").orEmpty(),
                    userName = mention?.string("user_name").orEmpty(),
                )
            }

            "cheermote" -> {
                val cheermote = item["cheermote"]?.jsonObject
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

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.boolean(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private companion object {
        const val MAX_MODERATION_LIST_ITEMS = 2_000
        const val MAX_CHATTERS_LIST_ITEMS = 5_000
        const val MAX_USER_EMOTES_ITEMS = 10_000
        val TWITCH_LOGIN_REGEX = Regex("^[a-z0-9_]{1,25}$")
        val TWITCH_GLOBAL_EMOTE_TYPES = setOf(
            "none",
            "globals",
            "smilies",
            "prime",
            "turbo",
            "owl2019",
            "twofactor",
            "limitedtime",
        )
    }
}

class TwitchApiException(
    val statusCode: Int,
    val apiMessage: String,
) : IllegalStateException("Twitch API $statusCode: $apiMessage")

class EventSubSubscriptionConflictException(
    val type: String,
    val broadcasterId: String,
    val existingSubscriptionId: String?,
) : IllegalStateException(
    buildString {
        append("Подписка $type для канала $broadcasterId уже активна в другой EventSub-сессии")
        existingSubscriptionId?.let { append(" (id: $it)") }
    },
)

class TwitchChatSendException(
    val statusCode: Int,
    val apiMessage: String,
    val retryAtMillis: Long?,
) : IllegalStateException(
    if (statusCode == 429) "Лимит отправки Twitch: $apiMessage" else "Twitch API $statusCode: $apiMessage",
)
