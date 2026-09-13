package io.ferventio.shared.workspace

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ferventio.shared.settings.SharedMessageRulesPayloadCodec
import io.ferventio.shared.settings.SharedSavedFiltersPayloadCodec
import io.ferventio.shared.settings.SharedSettingsPayloadCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class WorkspaceSettingsHistoryEntry(
    val revision: Long,
    val updatedAt: String,
    val updatedByInstallationId: String,
    val appVersion: String?,
    val contentHash: String,
)

/** Reads revision history and restores Android-compatible settings snapshots through shared KMP transport. */
class WorkspaceSettingsHistoryClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    suspend fun fetchHistory(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
    ): List<WorkspaceSettingsHistoryEntry> {
        val baseUrl = validateAndResolveBaseUrl(identity, authentication)
        val response = client.get("$baseUrl/v1/sync/settings/history") {
            authenticatedHeaders(identity, authentication.backendCredential.token)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw WorkspaceSettingsSyncException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
        val root = parseObject(body, "Backend returned malformed settings history JSON")
        return root["data"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            WorkspaceSettingsHistoryEntry(
                revision = item.requiredPositiveLong("revision"),
                updatedAt = item.requiredString("updatedAt"),
                updatedByInstallationId = item.requiredString("updatedByInstallationId"),
                appVersion = item["appVersion"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf(String::isNotEmpty),
                contentHash = item.requiredString("contentHash"),
            )
        }
    }

    @Throws(Exception::class)
    suspend fun restoreRevision(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        revision: Long,
    ): WorkspaceSettingsSnapshot {
        require(revision > 0L) { "Settings revision must be positive" }
        val baseUrl = validateAndResolveBaseUrl(identity, authentication)
        val response = client.post("$baseUrl/v1/sync/settings/restore/$revision") {
            authenticatedHeaders(identity, authentication.backendCredential.token)
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw WorkspaceSettingsSyncException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
        return decodeSnapshot(parseObject(body, "Backend returned malformed restored settings JSON"))
    }

    private fun decodeSnapshot(root: JsonObject): WorkspaceSettingsSnapshot {
        val revision = root.requiredPositiveLong("revision")
        val payload = root["payload"]
            ?: error("Backend settings snapshot does not contain payload")
        val payloadText = payload.toString()
        return WorkspaceSettingsSnapshot(
            revision = revision,
            channels = WorkspaceSettingsPayloadParser.parse(payloadText),
            preferences = SharedSettingsPayloadCodec.parsePreferences(payloadText),
            messageRules = SharedMessageRulesPayloadCodec.parse(payloadText),
            savedFilters = SharedSavedFiltersPayloadCodec.parse(payloadText),
            payload = payloadText,
        )
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

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Backend settings history does not contain $name")

    private fun JsonObject.requiredPositiveLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull
            ?.takeIf { it > 0L }
            ?: error("Backend settings history does not contain positive $name")

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
    }
}
