package io.ferventio.app.emote

import io.ferventio.app.security.SafeLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

/** Best-effort live invalidation. REST remains the source of truth after every event. */
class EmoteLiveUpdateClient {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun run(
        betterTtvChannelIds: Set<String>,
        sevenTvSetIds: Map<String, String?>,
        onInvalidated: (providerId: String, channelId: String?) -> Unit,
    ) = coroutineScope {
        if (betterTtvChannelIds.isNotEmpty()) {
            launch { runBetterTtv(betterTtvChannelIds, onInvalidated) }
        }
        if (sevenTvSetIds.isNotEmpty()) {
            launch { runSevenTv(sevenTvSetIds, onInvalidated) }
        }
    }

    private suspend fun runBetterTtv(
        channelIds: Set<String>,
        onInvalidated: (String, String?) -> Unit,
    ) {
        reconnectingSocket("BetterTTV") { client ->
            client.webSocket(urlString = BETTER_TTV_SOCKET_URL) {
                channelIds.forEach { channelId ->
                    send(
                        Frame.Text(
                            buildJsonObject {
                                put("name", JsonPrimitive("join_channel"))
                                put("data", buildJsonObject {
                                    put("name", JsonPrimitive("twitch:$channelId"))
                                })
                            }.toString(),
                        ),
                    )
                }
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        ?: continue
                    val eventName = root.string("name").orEmpty()
                    if (eventName !in BETTER_TTV_EMOTE_EVENTS) continue
                    val data = root["data"] as? JsonObject
                    val channelId = data?.string("channel")
                        ?.removePrefix("twitch:")
                        ?.takeIf { it in channelIds }
                    onInvalidated(EmoteRepository.BETTER_TTV, channelId)
                }
            }
        }
    }

    private suspend fun runSevenTv(
        setIds: Map<String, String?>,
        onInvalidated: (String, String?) -> Unit,
    ) {
        reconnectingSocket("7TV") { client ->
            client.webSocket(urlString = SEVEN_TV_SOCKET_URL) {
                var subscribed = false
                for (frame in incoming) {
                    val text = (frame as? Frame.Text)?.readText() ?: continue
                    val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
                        ?: continue
                    when (root["op"]?.jsonPrimitive?.intOrNull) {
                        1 -> if (!subscribed) {
                            setIds.keys.forEach { setId ->
                                send(
                                    Frame.Text(
                                        buildJsonObject {
                                            put("op", JsonPrimitive(35))
                                            put("d", buildJsonObject {
                                                put("type", JsonPrimitive("emote_set.update"))
                                                put("condition", buildJsonObject {
                                                    put("object_id", JsonPrimitive(setId))
                                                })
                                            })
                                        }.toString(),
                                    ),
                                )
                            }
                            subscribed = true
                        }

                        0 -> {
                            val data = root["d"] as? JsonObject ?: continue
                            if (data.string("type") != "emote_set.update") continue
                            val body = data["body"] as? JsonObject
                            val setId = body?.string("id") ?: continue
                            if (setId in setIds) {
                                onInvalidated(EmoteRepository.SEVEN_TV, setIds[setId])
                            }
                        }

                        4, 7 -> break
                    }
                }
            }
        }
    }

    private suspend fun reconnectingSocket(
        name: String,
        block: suspend (HttpClient) -> Unit,
    ) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val client = HttpClient(OkHttp) {
                install(WebSockets)
                expectSuccess = false
            }
            try {
                block(client)
                attempt = 0
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                SafeLog.w(TAG, "$name emote socket disconnected", error)
                attempt++
            } finally {
                client.close()
            }
            val delayMillis = min(30_000L, 1_000L * (1L shl attempt.coerceIn(0, 5)))
            delay(delayMillis)
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        const val TAG = "EmoteLiveUpdates"
        const val BETTER_TTV_SOCKET_URL = "wss://sockets.betterttv.net/ws"
        const val SEVEN_TV_SOCKET_URL = "wss://events.7tv.io/v3"
        val BETTER_TTV_EMOTE_EVENTS = setOf("emote_create", "emote_update", "emote_delete")
    }
}
