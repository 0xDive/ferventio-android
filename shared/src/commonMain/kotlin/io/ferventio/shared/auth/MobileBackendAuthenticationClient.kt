package io.ferventio.shared.auth

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.BackendAuthorizationStart
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

class MobileBackendAuthenticationException(
    val statusCode: Int,
    val backendMessage: String,
) : IllegalStateException("Ferventio backend HTTP $statusCode: $backendMessage")

/** KMP client for the mobile-auth endpoints shared by Android and iOS. */
class MobileBackendAuthenticationClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
    private val nowEpochMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startAuthorization(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        appCallbackUri: String,
    ): BackendAuthorizationStart {
        require(installationId.isNotBlank()) { "Installation ID must not be blank" }
        require(deviceSecret.isNotBlank()) { "Device secret must not be blank" }
        require(appCallbackUri.isNotBlank()) { "OAuth callback URI must not be blank" }
        val baseUrl = validateServerUrl(serverUrl)
        val response = client.post("$baseUrl/v1/auth/mobile/start") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(
                buildJsonObject {
                    put("installationId", JsonPrimitive(installationId))
                    put("deviceSecret", JsonPrimitive(deviceSecret))
                    put("appCallbackUri", JsonPrimitive(appCallbackUri))
                }.toString(),
            )
        }
        val root = decodeSuccessfulObject(response.status.value, response.bodyAsText())
        return BackendAuthorizationStart(
            authorizationUrl = validateAuthorizationUrl(
                serverUrl = baseUrl,
                authorizationUrl = root.requiredString("authorizationUrl"),
            ),
            state = root.requiredString("state"),
            expiresAtEpochMillis = serverRelativeDeadline(
                serverTime = root.requiredString("serverTime"),
                timestamp = root.requiredString("expiresAt"),
            ),
        )
    }

    suspend fun completeAuthorization(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        code: String,
        state: String,
    ): StoredAuthentication {
        require(installationId.isNotBlank()) { "Installation ID must not be blank" }
        require(deviceSecret.isNotBlank()) { "Device secret must not be blank" }
        require(code.isNotBlank()) { "Authorization code must not be blank" }
        require(state.isNotBlank()) { "Authorization state must not be blank" }
        val baseUrl = validateServerUrl(serverUrl)
        val response = client.post("$baseUrl/v1/auth/mobile/complete") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(
                buildJsonObject {
                    put("installationId", JsonPrimitive(installationId))
                    put("deviceSecret", JsonPrimitive(deviceSecret))
                    put("code", JsonPrimitive(code))
                    put("state", JsonPrimitive(state))
                }.toString(),
            )
        }
        val root = decodeSuccessfulObject(response.status.value, response.bodyAsText())
        val leaseObject = root.requiredObject("lease")
        require(
            Instant.parse(root.requiredString("sessionExpiresAt")) ==
                Instant.parse(leaseObject.requiredString("sessionExpiresAt")),
        ) { "Backend returned inconsistent mobile-session expiry" }
        val lease = leaseObject.toAccessLease()
        val credential = BackendSessionCredential(
            serverUrl = baseUrl,
            token = root.requiredString("sessionToken"),
            expiresAtEpochMillis = lease.backendSessionExpiresAtEpochMillis,
        )
        AuthenticationPersistenceValidation.requireValid(credential, lease)
        return StoredAuthentication(
            backendCredential = credential,
            accessLease = lease,
        )
    }

    suspend fun leaseAccessToken(
        storedAuthentication: StoredAuthentication,
        installationId: String,
        deviceSecret: String,
        forceRefresh: Boolean = false,
    ): TwitchAccessLease {
        AuthenticationPersistenceValidation.requireValidBackendCredential(
            storedAuthentication.backendCredential,
        )
        require(installationId.isNotBlank()) { "Installation ID must not be blank" }
        require(deviceSecret.isNotBlank()) { "Device secret must not be blank" }
        val credential = storedAuthentication.backendCredential
        val suffix = if (forceRefresh) "?force_refresh=true" else ""
        val response = client.post(
            "${validateServerUrl(credential.serverUrl)}/v1/auth/token$suffix",
        ) {
            header(HttpHeaders.Authorization, "Bearer ${credential.token}")
            header(INSTALLATION_ID_HEADER, installationId)
            header(DEVICE_SECRET_HEADER, deviceSecret)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        val lease = decodeSuccessfulObject(
            response.status.value,
            response.bodyAsText(),
        ).toAccessLease()
        require(lease.backendSessionExpiresAtEpochMillis == credential.expiresAtEpochMillis) {
            "Access-token lease does not match the backend session"
        }
        AuthenticationPersistenceValidation.requireValidAccessLease(lease)
        return lease
    }

    private fun JsonObject.toAccessLease(): TwitchAccessLease {
        val now = nowEpochMillis()
        val serverTime = requiredString("serverTime")
        val leaseExpiresAtMillis = serverRelativeDeadline(
            serverTime,
            requiredString("leaseExpiresAt"),
            now,
        )
        val twitchExpiresAtMillis = serverRelativeDeadline(
            serverTime,
            requiredString("twitchExpiresAt"),
            now,
        )
        val twitchValidatedAtMillis = this["twitchValidatedAt"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.let { serverRelativeTimestamp(serverTime, it, now).coerceIn(1L, now) }
            ?: (now - LEGACY_TWITCH_VALIDATION_AGE_MILLIS).coerceAtLeast(1L)
        val backendSessionExpiresAtMillis = serverRelativeDeadline(
            serverTime,
            requiredString("sessionExpiresAt"),
            now,
        )
        val remainingSeconds = ((twitchExpiresAtMillis - now) / 1_000L).coerceAtLeast(0L)
        val lease = TwitchAccessLease(
            accessToken = requiredString("accessToken"),
            leaseExpiresAtEpochMillis = leaseExpiresAtMillis,
            twitchExpiresAtEpochMillis = twitchExpiresAtMillis,
            twitchValidatedAtEpochMillis = twitchValidatedAtMillis,
            backendSessionExpiresAtEpochMillis = backendSessionExpiresAtMillis,
            session = TwitchSession(
                clientId = requiredString("clientId"),
                userId = requiredString("userId"),
                login = requiredString("login"),
                scopes = requiredStringList("scopes").toSet(),
                expiresInSeconds = remainingSeconds,
            ),
        )
        AuthenticationPersistenceValidation.requireValidAccessLease(lease)
        return lease
    }

    private fun validateServerUrl(value: String): String {
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

    private fun validateAuthorizationUrl(
        serverUrl: String,
        authorizationUrl: String,
    ): String {
        val server = Url(serverUrl)
        val target = runCatching { Url(authorizationUrl.trim()) }
            .getOrElse { throw IllegalArgumentException("Invalid authorization URL", it) }
        require(target.user == null && target.password == null && target.fragment.isEmpty()) {
            "Authorization URL contains unsafe URL components"
        }
        require(
            server.protocol.name.equals(target.protocol.name, ignoreCase = true) &&
                server.host.equals(target.host, ignoreCase = true) &&
                server.port == target.port,
        ) { "Authorization URL must belong to the configured Ferventio server" }
        return target.toString()
    }

    private fun decodeSuccessfulObject(status: Int, body: String): JsonObject {
        if (status !in 200..299) {
            val message = runCatching {
                json.parseToJsonElement(body)
                    .jsonObject["error"]
                    ?.jsonPrimitive
                    ?.contentOrNull
            }.getOrNull().orEmpty().ifBlank {
                body.take(300).ifBlank { "unknown backend error" }
            }
            throw MobileBackendAuthenticationException(status, message)
        }
        return runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { error ->
                throw IllegalStateException("Backend returned malformed JSON", error)
            }
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Backend response is missing $name")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Backend response is missing object $name")

    private fun JsonObject.requiredStringList(name: String): List<String> =
        this[name]?.runCatching {
            jsonArray.mapNotNull { item ->
                item.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            }
        }?.getOrNull() ?: error("Backend response is missing list $name")

    private fun serverRelativeDeadline(
        serverTime: String,
        timestamp: String,
        localNowMillis: Long = nowEpochMillis(),
    ): Long {
        val remainingMillis =
            Instant.parse(timestamp).toEpochMilliseconds() -
                Instant.parse(serverTime).toEpochMilliseconds()
        require(remainingMillis > 0L) { "Backend returned an already-expired deadline" }
        return localNowMillis + remainingMillis
    }

    private fun serverRelativeTimestamp(
        serverTime: String,
        timestamp: String,
        localNowMillis: Long = nowEpochMillis(),
    ): Long = localNowMillis +
        (Instant.parse(timestamp).toEpochMilliseconds() - Instant.parse(serverTime).toEpochMilliseconds())

    private companion object {
        const val INSTALLATION_ID_HEADER = "X-Installation-ID"
        const val DEVICE_SECRET_HEADER = "X-Device-Secret"
        const val LEGACY_TWITCH_VALIDATION_AGE_MILLIS = 55L * 60L * 1_000L
    }
}

internal expect fun createPlatformMobileAuthenticationHttpClient(): HttpClient
