package io.ferventio.shared.push

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlin.Throws
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PushBackendRegistrationException(
    val statusCode: Int,
    val backendMessage: String,
) : IllegalStateException("Ferventio backend HTTP $statusCode: $backendMessage")

/** KMP transport for the backend push-registration endpoint. */
class PushBackendRegistrationClient(
    private val client: HttpClient = createPlatformPushHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Throws(Exception::class)
    suspend fun register(
        serverUrl: String,
        request: PushRegistrationRequest,
    ) {
        PushRegistrationValidation.requireValid(request)
        val baseUrl = validateServerUrl(serverUrl)
        val response = client.put(
            "$baseUrl/v1/push/registrations/${request.installationId}",
        ) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            setBody(json.encodeToString(request))
        }
        val body = response.bodyAsText()
        if (response.status.value !in 200..299) {
            throw PushBackendRegistrationException(
                statusCode = response.status.value,
                backendMessage = decodeBackendError(body),
            )
        }
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

    private fun decodeBackendError(body: String): String = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull().orEmpty().ifBlank {
        body.take(300).ifBlank { "unknown backend error" }
    }
}

/** High-level APNs registration operation intended for the native iOS adapter. */
class ApnsPushRegistrationCoordinator(
    private val backend: PushBackendRegistrationClient = PushBackendRegistrationClient(),
    private val requestFactory: PushRegistrationRequestFactory = PushRegistrationRequestFactory(),
) {
    @Throws(Exception::class)
    suspend fun register(
        serverUrl: String,
        identity: MobileDeviceIdentity,
        apnsDeviceToken: String,
        appVersion: String,
    ): PushRegistrationRequest = register(
        serverUrl = serverUrl,
        identity = identity,
        apnsDeviceToken = apnsDeviceToken,
        appVersion = appVersion,
        context = PushRegistrationContext(),
    )

    @Throws(Exception::class)
    suspend fun register(
        serverUrl: String,
        identity: MobileDeviceIdentity,
        apnsDeviceToken: String,
        appVersion: String,
        context: PushRegistrationContext,
    ): PushRegistrationRequest {
        val request = requestFactory.apns(
            identity = identity,
            apnsDeviceToken = apnsDeviceToken,
            appVersion = appVersion,
            context = context,
        )
        backend.register(serverUrl, request)
        return request
    }
}

internal expect fun createPlatformPushHttpClient(): HttpClient
