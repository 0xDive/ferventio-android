package io.ferventio.shared.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createPlatformEventSubHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(WebSockets)
    expectSuccess = false
}
