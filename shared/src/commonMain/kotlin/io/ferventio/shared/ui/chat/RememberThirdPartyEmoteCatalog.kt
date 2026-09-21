package io.ferventio.shared.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.runtime.LocalFerventioRuntimeState

@Composable
internal fun rememberThirdPartyEmoteCatalog(
    channelId: String,
    betterTtvEnabled: Boolean = true,
    frankerFaceZEnabled: Boolean = true,
    sevenTvEnabled: Boolean = true,
): Map<String, ThirdPartyEmoteAsset> {
    val runtime = LocalFerventioRuntimeState.current
    val catalogRuntime = runtime.thirdPartyEmotes
    var catalog by remember(channelId) {
        mutableStateOf<Map<String, ThirdPartyEmoteAsset>>(emptyMap())
    }

    LaunchedEffect(channelId, catalogRuntime) {
        val normalizedChannelId = channelId.trim()
        catalog = if (normalizedChannelId.isEmpty()) {
            emptyMap()
        } else {
            runCatching {
                catalogRuntime.load(normalizedChannelId)
            }.getOrDefault(emptyMap())
        }
    }

    if (betterTtvEnabled && frankerFaceZEnabled && sevenTvEnabled) {
        return catalog
    }

    return remember(catalog, betterTtvEnabled, frankerFaceZEnabled, sevenTvEnabled) {
        catalog.filterValues { asset ->
            when (asset.provider.lowercase()) {
                "betterttv" -> betterTtvEnabled
                "frankerfacez" -> frankerFaceZEnabled
                "7tv" -> sevenTvEnabled
                else -> true
            }
        }
    }
}
