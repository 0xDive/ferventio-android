package io.ferventio.shared.push

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun createPlatformPushHttpClient(): HttpClient =
    HttpClient(OkHttp) {
        expectSuccess = false
    }
