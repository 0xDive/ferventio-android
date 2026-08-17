package io.ferventio.shared.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.ThirdPartyEmoteCatalogClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
internal fun rememberThirdPartyEmoteCatalog(
    channelId: String,
): Map<String, ThirdPartyEmoteAsset> {
    val client = remember { ThirdPartyEmoteCatalogClient() }
    var catalog by remember(channelId) {
        mutableStateOf<Map<String, ThirdPartyEmoteAsset>>(emptyMap())
    }

    DisposableEffect(client) {
        onDispose { client.close() }
    }
    LaunchedEffect(channelId) {
        val normalizedChannelId = channelId.trim()
        catalog = emptyMap()
        if (normalizedChannelId.isEmpty()) return@LaunchedEffect
        coroutineScope {
            val global = async { client.loadGlobals() }
            val channel = async { client.loadChannel(normalizedChannelId) }
            catalog = client.mergeForChannel(global.await(), channel.await())
        }
    }

    return catalog
}
