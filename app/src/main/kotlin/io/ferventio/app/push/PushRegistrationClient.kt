package io.ferventio.app.push

import io.ferventio.app.network.FerventioServerUrlPolicy

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PushRegistrationClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val http: HttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HttpClient(OkHttp) {
        }
    }

    suspend fun register(serverUrl: String, request: PushRegistrationRequest) {
        val response = http.put(
            "${serverUrl.normalized()}/v1/push/registrations/${request.installationId}",
        ) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(request))
        }
        require(response.status.value in 200..299) {
            "Не удалось сохранить push-регистрацию: HTTP ${response.status.value} ${response.bodyAsText()}"
        }
    }

    suspend fun unregister(serverUrl: String, installationId: String, deviceSecret: String) {
        val response = http.delete(
            "${serverUrl.normalized()}/v1/push/registrations/$installationId",
        ) {
            header(DEVICE_SECRET_HEADER, deviceSecret)
        }
        require(response.status == HttpStatusCode.NoContent || response.status == HttpStatusCode.NotFound) {
            "Не удалось удалить push-регистрацию: HTTP ${response.status.value}"
        }
    }

    suspend fun sendSelfTest(serverUrl: String, installationId: String, deviceSecret: String) {
        val response = http.post(
            "${serverUrl.normalized()}/v1/push/registrations/$installationId/self-test",
        ) {
            header(DEVICE_SECRET_HEADER, deviceSecret)
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        require(response.status.value in 200..299) {
            "Тестовый push не отправлен: HTTP ${response.status.value} ${response.bodyAsText()}"
        }
    }

    private fun String.normalized(): String =
        FerventioServerUrlPolicy.validate(this).baseUrl

    private companion object {
        const val DEVICE_SECRET_HEADER = "X-Device-Secret"
    }
}
