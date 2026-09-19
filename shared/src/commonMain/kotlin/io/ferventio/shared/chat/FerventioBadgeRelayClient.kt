package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlin.Throws

class FerventioBadgeRelayException(
    val statusCode: Int,
    val responseBody: String,
) : IllegalStateException("Ferventio badge relay HTTP $statusCode")

/** Public backend metadata relay used by signed-out chat without a Twitch user token. */
class FerventioBadgeRelayClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    @Throws(Exception::class)
    suspend fun loadGlobal(serverUrl: String): Map<String, ChatBadgeAsset> = load(
        endpoint = "${normalizeFerventioMetadataServerUrl(serverUrl)}/v1/twitch/badges/global",
    )

    @Throws(Exception::class)
    suspend fun loadChannel(
        serverUrl: String,
        broadcasterId: String,
    ): Map<String, ChatBadgeAsset> = load(
        endpoint = "${normalizeFerventioMetadataServerUrl(serverUrl)}/v1/twitch/badges/${requireTwitchBroadcasterId(broadcasterId)}",
    )

    private suspend fun load(endpoint: String): Map<String, ChatBadgeAsset> {
        val response = client.get(endpoint) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw FerventioBadgeRelayException(
                statusCode = response.status.value,
                responseBody = body.take(300),
            )
        }
        return TwitchChatBadgeCatalogParser.parse(body)
    }
}

internal fun normalizeFerventioMetadataServerUrl(value: String): String {
    val normalized = value.trim().trimEnd('/')
    require(normalized.isNotEmpty()) { "Ferventio server URL must not be blank" }
    val url = runCatching { Url(normalized) }
        .getOrElse { throw IllegalArgumentException("Invalid Ferventio server URL", it) }
    require(url.protocol.name.equals("https", ignoreCase = true)) {
        "Ferventio server must use HTTPS"
    }
    require(
        url.host.isNotBlank() &&
            url.user == null &&
            url.password == null &&
            url.parameters.isEmpty() &&
            url.fragment.isEmpty(),
    ) { "Ferventio server URL must be a base HTTPS URL without credentials, query or fragment" }
    return normalized
}

internal fun requireTwitchBroadcasterId(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotEmpty() && normalized.length <= 32 && normalized.all(Char::isDigit)) {
        "Twitch broadcaster ID must contain 1..32 digits"
    }
    return normalized
}
