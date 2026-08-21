package io.ferventio.shared.workspace

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.MobileDeviceIdentityValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedMessageRulesPayloadCodec
import io.ferventio.shared.settings.SharedMessageRulesSnapshot
import io.ferventio.shared.settings.SharedSavedFiltersPayloadCodec
import io.ferventio.shared.settings.SharedSavedFiltersSnapshot
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
    val messageRules: SharedMessageRulesSnapshot,
    val savedFilters: SharedSavedFiltersSnapshot,
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
    ): WorkspaceSettingsSnapshot = updateSnapshot(
        identity = identity,
        authentication = authentication,
    ) { snapshot ->
        SharedSettingsPayloadCodec.replacePreferences(
            payload = snapshot.payload,
            preferences = preferences,
        )
    }

    /**
     * Applies a channel mutation to the freshest available snapshot. If another device wins the
     * optimistic revision race, the operation is recomputed against that remote channel state once.
     */
    @Throws(Exception::class)
    suspend fun updateChannels(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        mutate: (PersistedWorkspaceChannels) -> PersistedWorkspaceChannels,
    ): WorkspaceSettingsSnapshot = updateSnapshot(
        identity = identity,
        authentication = authentication,
    ) { snapshot ->
        val updated = mutate(snapshot.channels)
        SharedSettingsPayloadCodec.replaceChannels(
            payload = snapshot.payload,
            logins = updated.logins,
            selectedLogin = updated.selectedLogin,
            pinnedChannelIds = updated.pinnedChannelIds,
            tabTitles = updated.tabTitles,
        )
    }

    /**
     * Applies a highlight/ignore mutation to the freshest snapshot so concurrent Android/iOS edits
     * are rebased by rule id instead of replacing a stale local list after a 409 conflict.
     */
    @Throws(Exception::class)
    suspend fun updateMessageRules(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        mutate: (SharedMessageRulesSnapshot) -> SharedMessageRulesSnapshot,
    ): WorkspaceSettingsSnapshot = updateSnapshot(
        identity = identity,
        authentication = authentication,
    ) { snapshot ->
        SharedMessageRulesPayloadCodec.replace(
            payload = snapshot.payload,
            rules = mutate(snapshot.messageRules),
        )
    }

    /**
     * Applies a saved-filter mutation to the freshest snapshot so concurrent device edits are
     * rebased by filter id instead of replacing a stale list after a revision conflict.
     */
    @Throws(Exception::class)
    suspend fun updateSavedFilters(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        mutate: (SharedSavedFiltersSnapshot) -> SharedSavedFiltersSnapshot,
    ): WorkspaceSettingsSnapshot = updateSnapshot(
        identity = identity,
        authentication = authentication,
    ) { snapshot ->
        SharedSavedFiltersPayloadCodec.replace(
            payload = snapshot.payload,
            snapshot = mutate(snapshot.savedFilters),
        )
    }

    /**
     * Applies a workspace-layout mutation to the freshest remote layout. Conflict retries re-run
     * the operation against that remote layout so unrelated tabs/splits created elsewhere survive.
     */
    @Throws(Exception::class)
    suspend fun updateWorkspaceLayout(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        fallbackChannelId: String? = null,
        mutate: (WorkspaceLayout) -> WorkspaceLayout,
    ): WorkspaceSettingsSnapshot = updateSnapshot(
        identity = identity,
        authentication = authentication,
    ) { snapshot ->
        val remote = SharedWorkspaceLayoutPayloadCodec.parse(
            payload = snapshot.payload,
            fallbackChannelId = fallbackChannelId,
        )
        SharedWorkspaceLayoutPayloadCodec.replace(
            payload = snapshot.payload,
            layout = mutate(remote),
        )
    }

    /** Atomically changes a split channel and the legacy selectedLogin projection. */
    @Throws(Exception::class)
    suspend fun updateWorkspaceLayoutAndSelectedChannel(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        selectedLogin: String,
        fallbackChannelId: String? = null,
        mutate: (WorkspaceLayout) -> WorkspaceLayout,
    ): WorkspaceSettingsSnapshot {
        val login = selectedLogin.trim().removePrefix("#").lowercase()
            .takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Selected channel login must not be blank")
        return updateSnapshot(
            identity = identity,
            authentication = authentication,
        ) { snapshot ->
            require(login in snapshot.channels.logins) {
                "Channel is not in the synced workspace"
            }
            val remote = SharedWorkspaceLayoutPayloadCodec.parse(
                payload = snapshot.payload,
                fallbackChannelId = fallbackChannelId,
            )
            val withLayout = SharedWorkspaceLayoutPayloadCodec.replace(
                payload = snapshot.payload,
                layout = mutate(remote),
            )
            SharedSettingsPayloadCodec.replaceChannels(
                payload = withLayout,
                logins = snapshot.channels.logins,
                selectedLogin = login,
                pinnedChannelIds = snapshot.channels.pinnedChannelIds,
                tabTitles = snapshot.channels.tabTitles,
            )
        }
    }

    private suspend fun updateSnapshot(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        mutatePayload: (WorkspaceSettingsSnapshot) -> String,
    ): WorkspaceSettingsSnapshot {
        val baseUrl = validateAndResolveBaseUrl(identity, authentication)
        val credential = authentication.backendCredential
        var snapshot = fetch(identity, authentication)
            ?: error("Remote settings are unavailable")

        repeat(MAX_CONFLICT_ATTEMPTS + 1) { attempt ->
            val updatedPayload = mutatePayload(snapshot)
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
            messageRules = SharedMessageRulesPayloadCodec.parse(payloadText),
            savedFilters = SharedSavedFiltersPayloadCodec.parse(payloadText),
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
