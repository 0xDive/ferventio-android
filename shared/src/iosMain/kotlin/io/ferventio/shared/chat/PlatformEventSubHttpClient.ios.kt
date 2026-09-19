package io.ferventio.shared.chat

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.WebSockets

internal actual fun createPlatformEventSubHttpClient(): HttpClient = HttpClient(Darwin) {
    install(WebSockets)
    expectSuccess = false
}
