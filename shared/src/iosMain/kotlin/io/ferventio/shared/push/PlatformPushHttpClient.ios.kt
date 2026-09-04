package io.ferventio.shared.push

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal actual fun createPlatformPushHttpClient(): HttpClient =
    HttpClient(Darwin) {
        expectSuccess = false
    }
