package io.ferventio.app.network

import io.ferventio.app.domain.BackendAuthorizationStart
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.time.Instant

class FerventioBackendClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
            engine {
                FerventioServerTransportSecurity.configure(this)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 20_000
            }
            expectSuccess = false
        }
    }

    suspend fun startAuthorization(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        appCallbackUri: String,
    ): BackendAuthorizationStart {
        val response = client.post("${serverUrl.normalized()}/v1/auth/mobile/start") {
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
        val serverTime = root.requiredString("serverTime")
        val expiresAtMillis = serverRelativeDeadline(serverTime, root.requiredString("expiresAt"))
        return BackendAuthorizationStart(
            authorizationUrl = validateAuthorizationUrl(
                serverUrl = serverUrl,
                authorizationUrl = root.requiredString("authorizationUrl"),
            ),
            state = root.requiredString("state"),
            expiresAtEpochMillis = expiresAtMillis,
        )
    }

    suspend fun completeAuthorization(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        code: String,
        state: String,
    ): Pair<BackendSessionCredential, TwitchAccessLease> {
        val response = client.post("${serverUrl.normalized()}/v1/auth/mobile/complete") {
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
        val lease = root.requiredObject("lease").toDomain()
        val declaredSessionExpiry = Instant.parse(root.requiredString("sessionExpiresAt"))
        val leaseSessionExpiry = Instant.parse(root.requiredObject("lease").requiredString("sessionExpiresAt"))
        require(declaredSessionExpiry == leaseSessionExpiry) {
            "Сервер вернул несогласованный срок mobile session"
        }
        val credential = BackendSessionCredential(
            serverUrl = serverUrl.normalized(),
            token = root.requiredString("sessionToken"),
            expiresAtEpochMillis = lease.backendSessionExpiresAtEpochMillis,
        )
        return credential to lease
    }

    suspend fun leaseAccessToken(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
        forceRefresh: Boolean = false,
    ): TwitchAccessLease {
        val suffix = if (forceRefresh) "?force_refresh=true" else ""
        val response = client.post("${serverUrl.normalized()}/v1/auth/token$suffix") {
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            header(INSTALLATION_ID_HEADER, installationId)
            header(DEVICE_SECRET_HEADER, deviceSecret)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        return decodeSuccessfulObject(response.status.value, response.bodyAsText()).toDomain()
    }

    suspend fun logout(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ) {
        val response = client.delete("${serverUrl.normalized()}/v1/auth/session") {
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            header(INSTALLATION_ID_HEADER, installationId)
            header(DEVICE_SECRET_HEADER, deviceSecret)
        }
        if (response.status.value != 204 && response.status.value != 401) {
            decodeSuccessfulObject(response.status.value, response.bodyAsText())
        }
    }

    suspend fun revokeDevice(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ) {
        val response = client.delete("${serverUrl.normalized()}/v1/auth/device") {
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            header(INSTALLATION_ID_HEADER, installationId)
            header(DEVICE_SECRET_HEADER, deviceSecret)
        }
        if (response.status.value != 204) {
            decodeSuccessfulObject(response.status.value, response.bodyAsText())
        }
    }

    suspend fun revokeAllSessions(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ) {
        val response = client.delete("${serverUrl.normalized()}/v1/auth/sessions") {
            header(HttpHeaders.Authorization, "Bearer $sessionToken")
            header(INSTALLATION_ID_HEADER, installationId)
            header(DEVICE_SECRET_HEADER, deviceSecret)
        }
        if (response.status.value != 204) {
            decodeSuccessfulObject(response.status.value, response.bodyAsText())
        }
    }

    suspend fun getSettingsSnapshot(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ): BackendSettingsSnapshot? {
        val response = client.get("${serverUrl.normalized()}/v1/sync/settings") {
            authenticatedHeaders(installationId, deviceSecret, sessionToken)
        }
        if (response.status.value == 204) return null
        return decodeSuccessfulObject(response.status.value, response.bodyAsText()).toSettingsSnapshot()
    }

    suspend fun putSettingsSnapshot(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
        baseRevision: Long,
        force: Boolean,
        payload: String,
    ): BackendSettingsPutResult {
        val payloadElement = runCatching { json.parseToJsonElement(payload) }
            .getOrElse { throw IllegalArgumentException("Некорректный JSON настроек", it) }
        val response = client.put("${serverUrl.normalized()}/v1/sync/settings") {
            authenticatedHeaders(installationId, deviceSecret, sessionToken)
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("baseRevision", JsonPrimitive(baseRevision))
                    put("force", JsonPrimitive(force))
                    put("payload", payloadElement)
                }.toString(),
            )
        }
        val body = response.bodyAsText()
        if (response.status.value == 409) {
            val root = runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw IllegalStateException("Сервер вернул некорректный конфликт настроек", it) }
            val snapshot = root["snapshot"]?.jsonObject?.toSettingsSnapshot()
                ?: error("Сервер не вернул текущую ревизию")
            return BackendSettingsPutResult.Conflict(snapshot)
        }
        return BackendSettingsPutResult.Success(
            decodeSuccessfulObject(response.status.value, body).toSettingsSnapshot(),
        )
    }

    suspend fun getSettingsHistory(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ): List<BackendSettingsHistoryEntry> {
        val response = client.get("${serverUrl.normalized()}/v1/sync/settings/history") {
            authenticatedHeaders(installationId, deviceSecret, sessionToken)
        }
        val root = decodeSuccessfulObject(response.status.value, response.bodyAsText())
        return root["data"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            BackendSettingsHistoryEntry(
                revision = item.requiredLong("revision"),
                updatedAt = item.requiredString("updatedAt"),
                updatedByInstallationId = item.requiredString("updatedByInstallationId"),
                appVersion = item["appVersion"]?.jsonPrimitive?.contentOrNull,
                contentHash = item.requiredString("contentHash"),
            )
        }
    }

    suspend fun restoreSettingsRevision(
        serverUrl: String,
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
        revision: Long,
    ): BackendSettingsSnapshot {
        require(revision > 0L) { "Некорректная ревизия настроек" }
        val response = client.post("${serverUrl.normalized()}/v1/sync/settings/restore/$revision") {
            authenticatedHeaders(installationId, deviceSecret, sessionToken)
        }
        return decodeSuccessfulObject(response.status.value, response.bodyAsText()).toSettingsSnapshot()
    }

    private fun JsonObject.toDomain(): TwitchAccessLease {
        val now = System.currentTimeMillis()
        val serverTime = requiredString("serverTime")
        val leaseExpiresAtMillis = serverRelativeDeadline(serverTime, requiredString("leaseExpiresAt"), now)
        val twitchExpiresAtMillis = serverRelativeDeadline(serverTime, requiredString("twitchExpiresAt"), now)
        val twitchValidatedAtMillis = this["twitchValidatedAt"]?.jsonPrimitive?.contentOrNull
            ?.let { serverRelativeTimestamp(serverTime, it, now).coerceIn(1L, now) }
            ?: (now - LEGACY_TWITCH_VALIDATION_AGE_MILLIS).coerceAtLeast(1L)
        val backendSessionExpiresAtMillis = serverRelativeDeadline(serverTime, requiredString("sessionExpiresAt"), now)
        val remainingSeconds = ((twitchExpiresAtMillis - now) / 1_000L).coerceAtLeast(0L)
        return TwitchAccessLease(
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
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authenticatedHeaders(
        installationId: String,
        deviceSecret: String,
        sessionToken: String,
    ) {
        header(HttpHeaders.Authorization, "Bearer $sessionToken")
        header(INSTALLATION_ID_HEADER, installationId)
        header(DEVICE_SECRET_HEADER, deviceSecret)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private fun JsonObject.toSettingsSnapshot(): BackendSettingsSnapshot = BackendSettingsSnapshot(
        revision = requiredLong("revision"),
        updatedAt = requiredString("updatedAt"),
        updatedByInstallationId = requiredString("updatedByInstallationId"),
        appVersion = this["appVersion"]?.jsonPrimitive?.contentOrNull,
        contentHash = requiredString("contentHash"),
        payload = this["payload"]?.toString() ?: error("Сервер не вернул payload настроек"),
    )

    private fun decodeSuccessfulObject(status: Int, body: String): JsonObject {
        if (status !in 200..299) {
            val message = runCatching {
                json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
            }.getOrNull().orEmpty().ifBlank {
                body.take(300).ifBlank { "unknown backend error" }
            }
            throw FerventioBackendException(status, message)
        }
        return runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { error -> throw IllegalStateException("Сервер вернул некорректный JSON", error) }
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Сервер не вернул поле $name")

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull
            ?: error("Сервер не вернул числовое поле $name")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Сервер не вернул объект $name")

    private fun JsonObject.requiredStringList(name: String): List<String> =
        this[name]?.runCatching {
            jsonArray.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
        }?.getOrNull() ?: error("Сервер не вернул список $name")

    private fun serverRelativeDeadline(
        serverTime: String,
        expiresAt: String,
        localNowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val remainingMillis = Instant.parse(expiresAt).toEpochMilli() - Instant.parse(serverTime).toEpochMilli()
        require(remainingMillis > 0L) { "Сервер вернул уже истёкший срок авторизации" }
        return localNowMillis + remainingMillis
    }

    private fun serverRelativeTimestamp(
        serverTime: String,
        timestamp: String,
        localNowMillis: Long = System.currentTimeMillis(),
    ): Long = localNowMillis + (Instant.parse(timestamp).toEpochMilli() - Instant.parse(serverTime).toEpochMilli())

    private fun validateAuthorizationUrl(serverUrl: String, authorizationUrl: String): String {
        val server = URI(serverUrl.normalized())
        val target = runCatching { URI(authorizationUrl.trim()) }.getOrNull()
            ?: error("Сервер вернул некорректный URL авторизации")
        require(target.isAbsolute && target.userInfo == null && target.fragment == null) {
            "Сервер вернул небезопасный URL авторизации"
        }
        require(
            server.scheme.equals(target.scheme, ignoreCase = true) &&
                server.host.equals(target.host, ignoreCase = true) &&
                effectivePort(server) == effectivePort(target),
        ) { "URL авторизации должен принадлежать настроенному серверу Ferventio" }
        return target.toString()
    }

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", ignoreCase = true) -> 443
        uri.scheme.equals("http", ignoreCase = true) -> 80
        else -> -1
    }

    private fun String.normalized(): String =
        FerventioServerTransportSecurity.validateServerUrl(this).baseUrl

    private companion object {
        const val INSTALLATION_ID_HEADER = "X-Installation-ID"
        const val DEVICE_SECRET_HEADER = "X-Device-Secret"
        const val LEGACY_TWITCH_VALIDATION_AGE_MILLIS = 55L * 60L * 1_000L
    }
}

class FerventioBackendException(
    val statusCode: Int,
    val backendMessage: String,
) : IllegalStateException("Ferventio backend $statusCode: $backendMessage")

data class BackendSettingsSnapshot(
    val revision: Long,
    val updatedAt: String,
    val updatedByInstallationId: String,
    val appVersion: String?,
    val contentHash: String,
    val payload: String,
)

data class BackendSettingsHistoryEntry(
    val revision: Long,
    val updatedAt: String,
    val updatedByInstallationId: String,
    val appVersion: String?,
    val contentHash: String,
)

sealed interface BackendSettingsPutResult {
    data class Success(val snapshot: BackendSettingsSnapshot) : BackendSettingsPutResult
    data class Conflict(val snapshot: BackendSettingsSnapshot) : BackendSettingsPutResult
}
