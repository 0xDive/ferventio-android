package io.ferventio.shared.user

import io.ferventio.app.domain.AuthenticationPersistenceValidation
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.auth.createPlatformMobileAuthenticationHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class TwitchUserSafetyScopeException(
    val requiredScope: String,
) : IllegalStateException("Twitch user safety requires OAuth scope $requiredScope")

class TwitchUserSafetyMutationException(
    val statusCode: Int,
    val twitchMessage: String?,
) : IllegalStateException(
    buildString {
        append("Twitch user block failed with HTTP ")
        append(statusCode)
        twitchMessage?.takeIf(String::isNotBlank)?.let { message ->
            append(": ")
            append(message)
        }
    },
)

interface TwitchUserSafetyGateway {
    suspend fun blockUser(
        authentication: StoredAuthentication,
        targetUserId: String,
    )
}

class TwitchUserSafetyClient(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TwitchUserSafetyGateway {
    constructor() : this(createPlatformMobileAuthenticationHttpClient())

    override suspend fun blockUser(
        authentication: StoredAuthentication,
        targetUserId: String,
    ) {
        AuthenticationPersistenceValidation.requireValid(
            authentication.backendCredential,
            authentication.accessLease,
        )
        val lease = requireNotNull(authentication.accessLease) {
            "Twitch access lease is required for user blocking"
        }
        if (BLOCKED_USERS_SCOPE !in lease.session.scopes) {
            throw TwitchUserSafetyScopeException(BLOCKED_USERS_SCOPE)
        }
        val normalizedTargetUserId = targetUserId.trim()
        val ownUserId = lease.session.userId.trim()
        require(normalizedTargetUserId.isNotEmpty()) { "Twitch target user id must not be blank" }
        require(normalizedTargetUserId != ownUserId) { "The authenticated Twitch user cannot block itself" }

        val response = client.put(USER_BLOCKS_URL) {
            header("Client-Id", lease.session.clientId)
            header(HttpHeaders.Authorization, "Bearer ${lease.accessToken}")
            parameter("target_user_id", normalizedTargetUserId)
            parameter("source_context", "chat")
            parameter("reason", "other")
        }
        requireSuccess(response)
    }

    private suspend fun requireSuccess(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val body = response.bodyAsText()
        val twitchMessage = runCatching {
            json.decodeFromString(TwitchUserSafetyErrorPayload.serializer(), body).message
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.getOrNull() ?: body.trim().take(300).takeIf(String::isNotEmpty)
        throw TwitchUserSafetyMutationException(
            statusCode = response.status.value,
            twitchMessage = twitchMessage,
        )
    }

    private companion object {
        const val USER_BLOCKS_URL = "https://api.twitch.tv/helix/users/blocks"
        const val BLOCKED_USERS_SCOPE = "user:manage:blocked_users"
    }
}

@Serializable
private data class TwitchUserSafetyErrorPayload(
    val message: String? = null,
)
