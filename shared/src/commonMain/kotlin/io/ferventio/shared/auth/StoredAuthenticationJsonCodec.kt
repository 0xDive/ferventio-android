package io.ferventio.shared.auth

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.BackendSessionCredential
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.TwitchAccessLease
import io.ferventio.app.domain.TwitchSession
import kotlin.Throws
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Versioned portable envelope for secure stores on platforms that persist an opaque UTF-8 value.
 * Android keeps its established binary FERVAUTH v2 codec; this codec exists for KMP/native stores.
 */
class StoredAuthenticationJsonCodec {
    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    fun encode(authentication: StoredAuthentication): String {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        return buildJsonObject {
            put("version", JsonPrimitive(VERSION))
            put(
                "backendCredential",
                buildJsonObject {
                    put("serverUrl", JsonPrimitive(authentication.backendCredential.serverUrl))
                    put("token", JsonPrimitive(authentication.backendCredential.token))
                    put(
                        "expiresAtEpochMillis",
                        JsonPrimitive(authentication.backendCredential.expiresAtEpochMillis),
                    )
                },
            )
            val lease = authentication.accessLease
            if (lease == null) {
                put("accessLease", JsonNull)
            } else {
                put(
                    "accessLease",
                    buildJsonObject {
                        put("accessToken", JsonPrimitive(lease.accessToken))
                        put("leaseExpiresAtEpochMillis", JsonPrimitive(lease.leaseExpiresAtEpochMillis))
                        put("twitchExpiresAtEpochMillis", JsonPrimitive(lease.twitchExpiresAtEpochMillis))
                        put("twitchValidatedAtEpochMillis", JsonPrimitive(lease.twitchValidatedAtEpochMillis))
                        put(
                            "backendSessionExpiresAtEpochMillis",
                            JsonPrimitive(lease.backendSessionExpiresAtEpochMillis),
                        )
                        put(
                            "session",
                            buildJsonObject {
                                put("clientId", JsonPrimitive(lease.session.clientId))
                                put("userId", JsonPrimitive(lease.session.userId))
                                put("login", JsonPrimitive(lease.session.login))
                                put(
                                    "scopes",
                                    kotlinx.serialization.json.JsonArray(
                                        lease.session.scopes
                                            .toList()
                                            .sorted()
                                            .map(::JsonPrimitive),
                                    ),
                                )
                                put(
                                    "expiresInSeconds",
                                    JsonPrimitive(lease.session.expiresInSeconds),
                                )
                            },
                        )
                    },
                )
            }
        }.toString()
    }

    @Throws(Exception::class)
    fun decode(payload: String): StoredAuthentication {
        val root = json.parseToJsonElement(payload).jsonObject
        require(root.requiredLong("version") == VERSION.toLong()) {
            "Unsupported authentication envelope version"
        }
        val credentialObject = root.requiredObject("backendCredential")
        val credential = BackendSessionCredential(
            serverUrl = credentialObject.requiredString("serverUrl"),
            token = credentialObject.requiredString("token"),
            expiresAtEpochMillis = credentialObject.requiredLong("expiresAtEpochMillis"),
        )
        val leaseElement = root["accessLease"]
        val lease = if (leaseElement == null || leaseElement is JsonNull) {
            null
        } else {
            val leaseObject = leaseElement.jsonObject
            val sessionObject = leaseObject.requiredObject("session")
            TwitchAccessLease(
                accessToken = leaseObject.requiredString("accessToken"),
                leaseExpiresAtEpochMillis = leaseObject.requiredLong("leaseExpiresAtEpochMillis"),
                twitchExpiresAtEpochMillis = leaseObject.requiredLong("twitchExpiresAtEpochMillis"),
                twitchValidatedAtEpochMillis = leaseObject.requiredLong("twitchValidatedAtEpochMillis"),
                backendSessionExpiresAtEpochMillis = leaseObject.requiredLong(
                    "backendSessionExpiresAtEpochMillis",
                ),
                session = TwitchSession(
                    clientId = sessionObject.requiredString("clientId"),
                    userId = sessionObject.requiredString("userId"),
                    login = sessionObject.requiredString("login"),
                    scopes = sessionObject.requiredStringList("scopes").toSet(),
                    expiresInSeconds = sessionObject.requiredLong("expiresInSeconds"),
                ),
            )
        }
        AuthenticationPersistenceValidation.requireValid(credential, lease)
        return StoredAuthentication(
            backendCredential = credential,
            accessLease = lease,
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
            ?: error("Authentication envelope is missing $name")

    private fun JsonObject.requiredLong(name: String): Long =
        this[name]?.jsonPrimitive?.longOrNull
            ?: error("Authentication envelope is missing numeric field $name")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        this[name]?.runCatching { jsonObject }?.getOrNull()
            ?: error("Authentication envelope is missing object $name")

    private fun JsonObject.requiredStringList(name: String): List<String> =
        this[name]?.runCatching {
            jsonArray.map { item ->
                item.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
                    ?: error("Authentication envelope contains a blank $name entry")
            }
        }?.getOrNull() ?: error("Authentication envelope is missing list $name")

    private companion object {
        const val VERSION = 1
    }
}
