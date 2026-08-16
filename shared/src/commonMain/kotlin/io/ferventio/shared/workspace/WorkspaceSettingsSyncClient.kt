package io.ferventio.shared.workspace

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class WorkspaceSettingsSnapshot(
    val revision: Long,
    val channels: PersistedWorkspaceChannels,
)

class WorkspaceSettingsSyncException(
    val statusCode: Int,
    val backendMessage: String,
) : IllegalStateException("Ferventio backend HTTP $statusCode: $backendMessage")

/** Fetches only the workspace projection from the existing settings-sync snapshot. */
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
        MobileDeviceIdentityValidation.requireValid(identity)
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val credential = authentication.backendCredential
        val baseUrl = credential.serverUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("https://", ignoreCase = true)) {
            "Ferventio server must use HTTPS"
        }

        val response = client.get("$baseUrl/v1/sync/settings") {
            header(HttpHeaders.Authorization, "Bearer ${credential.token}")
            header(INSTALLATION_ID_HEADER, identity.installationId)
            header(DEVICE_SECRET_HEADER, identity.deviceSecret)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        if (response.status.value == 204) return null

        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw WorkspaceSettingsSyncException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
        val root = runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IllegalStateException("Backend returned malformed settings JSON", it) }
        val revision = root["revision"]?.jsonPrimitive?.longOrNull
            ?: error("Backend settings snapshot does not contain revision")
        require(revision > 0L) { "Backend settings revision must be positive" }
        val payload = root["payload"]
            ?: error("Backend settings snapshot does not contain payload")
        return WorkspaceSettingsSnapshot(
            revision = revision,
            channels = WorkspaceSettingsPayloadParser.parse(payload.toString()),
        )
    }

    private fun decodeBackendError(body: String): String = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull().orEmpty().ifBlank {
        body.take(300).ifBlank { "unknown backend error" }
    }

    private companion object {
        const val INSTALLATION_ID_HEADER = "X-Installation-ID"
        const val DEVICE_SECRET_HEADER = "X-Device-Secret"
    }
}
