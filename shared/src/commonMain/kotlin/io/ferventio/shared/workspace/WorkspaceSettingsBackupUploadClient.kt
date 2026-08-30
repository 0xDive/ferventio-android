package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ferventio.shared.settings.SharedSettingsBackupCodec
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal sealed interface WorkspaceSettingsBackupUploadResult {
    data class Success(
        val revision: Long,
        val payload: String,
    ) : WorkspaceSettingsBackupUploadResult

    data class Conflict(
        val serverRevision: Long,
        val serverPayload: String,
    ) : WorkspaceSettingsBackupUploadResult
}

/**
 * Uploads an explicitly imported backup without silently resolving revision conflicts.
 *
 * Android re-captures imported settings with the current app/version envelope before ordinary sync,
 * then surfaces a local/server choice on conflict. Shared clients mirror that: imported content is
 * promoted to the current backup format, uploaded once with force=false, and HTTP 409 is returned
 * as data rather than retried or forced.
 */
internal class WorkspaceSettingsBackupUploadClient(
    private val client: HttpClient = createPlatformMobileAuthenticationHttpClient(),
) {
    private val snapshots = WorkspaceSettingsSyncClient(client)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun upload(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        importedPayload: String,
        currentAppVersion: String,
        createdAt: Instant,
    ): WorkspaceSettingsBackupUploadResult {
        val syncPayload = SharedSettingsBackupCodec.promoteForSync(
            raw = importedPayload,
            currentAppVersion = currentAppVersion,
            createdAt = createdAt,
        )
        val current = snapshots.fetch(identity, authentication)
        val baseRevision = current?.revision ?: 0L
        val credential = authentication.backendCredential
        val baseUrl = credential.serverUrl.trim().trimEnd('/')
        val response = client.put("$baseUrl/v1/sync/settings") {
            header(HttpHeaders.Authorization, "Bearer ${credential.token}")
            header("X-Installation-ID", identity.installationId)
            header("X-Device-Secret", identity.deviceSecret)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            contentType(ContentType.Application.Json)
            setBody(
                buildJsonObject {
                    put("baseRevision", JsonPrimitive(baseRevision))
                    put("force", JsonPrimitive(false))
                    put("payload", json.parseToJsonElement(syncPayload))
                }.toString(),
            )
        }
        val body = response.bodyAsText()
        if (response.status.value == 409) {
            val snapshot = parseObject(body, "Backend returned malformed settings conflict JSON")
                .get("snapshot")
                ?.runCatching { jsonObject }
                ?.getOrNull()
                ?: error("Backend settings conflict does not contain snapshot")
            return WorkspaceSettingsBackupUploadResult.Conflict(
                serverRevision = requireRevision(snapshot),
                serverPayload = requirePayload(snapshot),
            )
        }
        if (response.status.value !in 200..299) {
            throw WorkspaceSettingsSyncException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
        val snapshot = parseObject(body, "Backend returned malformed settings JSON")
        return WorkspaceSettingsBackupUploadResult.Success(
            revision = requireRevision(snapshot),
            payload = requirePayload(snapshot),
        )
    }

    private fun requireRevision(snapshot: JsonObject): Long = snapshot["revision"]
        ?.jsonPrimitive
        ?.longOrNull
        ?.also { revision -> require(revision > 0L) { "Backend settings revision must be positive" } }
        ?: error("Backend settings snapshot does not contain revision")

    private fun requirePayload(snapshot: JsonObject): String = snapshot["payload"]
        ?.toString()
        ?: error("Backend settings snapshot does not contain payload")

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
}
