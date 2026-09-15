package io.ferventio.shared.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformMobileAuthenticationHttpClient(): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
}
