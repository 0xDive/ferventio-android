package io.ferventio.shared.workspace

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedSettingsPayloadCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class WorkspaceSettingsSnapshot(
    val revision: Long,
    val channels: PersistedWorkspaceChannels,
    val preferences: SharedAppPreferences,
    val payload: String,
)

class WorkspaceSettingsSyncException(
    val statusCode: Int,
    val backendMessage: String,
) : IllegalStateException("Ferventio backend HTTP $statusCode: $backendMessage")

/** Reads and updates the shared projection of Android-compatible settings-sync snapshots. */
class WorkspaceSettingsSyncClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    suspend fun fetch(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): WorkspaceSettingsSnapshot? {
        val baseUrl = validateAndResolveBaseUrl(identity, authentication)
        val credential = authentication.backendCredential
        val response = client.get("$baseUrl/v1/sync/settings") {
            authenticatedHeaders(identity, credential.token)
        }
        if (response.status.value == 204) return null

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw WorkspaceSettingsSyncException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
        return decodeSnapshot(body)
    }

    @Throws(Exception::class)
    suspend fun updatePreferences(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        preferences: SharedAppPreferences,
    ): WorkspaceSettingsSnapshot {
        val baseUrl = validateAndResolveBaseUrl(identity, authentication)
        val credential = authentication.backendCredential
        var snapshot = fetch(identity, authentication)
            ?: error("Remote settings are unavailable")

        repeat(MAX_CONFLICT_ATTEMPTS + 1) { attempt ->
            val updatedPayload = SharedSettingsPayloadCodec.replacePreferences(
                payload = snapshot.payload,
                preferences = preferences,
            )
            val response = client.put("$baseUrl/v1/sync/settings") {
                authenticatedHeaders(identity, credential.token)
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("baseRevision", JsonPrimitive(snapshot.revision))
                        put("force", JsonPrimitive(false))
                        put("payload", json.parseToJsonElement(updatedPayload))
                    }.toString(),
                )
            }
            val body = response.bodyAsText()
            if (response.status.value == 409) {
                if (attempt >= MAX_CONFLICT_ATTEMPTS) {
                    throw WorkspaceSettingsSyncException(
                        statusCode = 409,
                        backendMessage = "Settings changed on another device",
                    )
                }
                val root = parseObject(body, "Backend returned malformed settings conflict JSON")
                val remote = root["snapshot"]?.runCatching { jsonObject }?.getOrNull()
                    ?: error("Backend settings conflict does not contain snapshot")
                snapshot = decodeSnapshot(remote)
                return@repeat
            }
            if (response.status.value !in 200..299) {
                throw WorkspaceSettingsSyncException(
                    statusCode = response.status.value,
                    backendMessage = decodeBackendError(body),
                )
            }
            return decodeSnapshot(body)
        }
        error("Settings update retry loop terminated unexpectedly")
    }

    private fun validateAndResolveBaseUrl(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): String {
        MobileDeviceIdentityValidation.requireValid(identity)
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        return authentication.backendCredential.serverUrl.trim().trimEnd('/').also { baseUrl ->
            require(baseUrl.startsWith("https://", ignoreCase = true)) {
                "Ferventio server must use HTTPS"
            }
        }
    }

    private fun decodeSnapshot(body: String): WorkspaceSettingsSnapshot =
        decodeSnapshot(parseObject(body, "Backend returned malformed settings JSON"))

    private fun decodeSnapshot(root: JsonObject): WorkspaceSettingsSnapshot {
        val revision = root["revision"]?.jsonPrimitive?.longOrNull
            ?: error("Backend settings snapshot does not contain revision")
        require(revision > 0L) { "Backend settings revision must be positive" }
        val payload = root["payload"]
            ?: error("Backend settings snapshot does not contain payload")
        val payloadText = payload.toString()
        return WorkspaceSettingsSnapshot(
            revision = revision,
            channels = WorkspaceSettingsPayloadParser.parse(payloadText),
            preferences = SharedSettingsPayloadCodec.parsePreferences(payloadText),
            payload = payloadText,
        )
    }

    private fun parseObject(body: String, message: String): JsonObject = runCatching {
        json.parseToJsonElement(body).jsonObject
    }.getOrElse { throw IllegalStateException(message, it) }

    private fun decodeBackendError(body: String): String = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull().orEmpty().ifBlank {
        body.take(300).ifBlank { "unknown backend error" }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authenticatedHeaders(
        identity: MobileDeviceIdentity,
        token: String,
    ) {
        header(HttpHeaders.Authorization, "Bearer $token")
        header(INSTALLATION_ID_HEADER, identity.installationId)
        header(DEVICE_SECRET_HEADER, identity.deviceSecret)
        header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    }

    private companion object {
        const val INSTALLATION_ID_HEADER = "X-Installation-ID"
        const val DEVICE_SECRET_HEADER = "X-Device-Secret"
        const val MAX_CONFLICT_ATTEMPTS = 1
    }
}
