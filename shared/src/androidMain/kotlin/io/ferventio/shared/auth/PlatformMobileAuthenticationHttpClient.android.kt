package io.ferventio.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformMobileAuthenticationHttpClient(): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
}
