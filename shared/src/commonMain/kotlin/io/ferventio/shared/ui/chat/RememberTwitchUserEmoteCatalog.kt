package io.ferventio.shared.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.TwitchUserEmoteCatalogClient

@Composable
internal fun rememberTwitchUserEmoteCatalog(
    authentication: StoredAuthentication?,
    broadcasterId: String,
): List<ThirdPartyEmoteAsset> {
    val client = remember { TwitchUserEmoteCatalogClient() }
    val lease = authentication?.accessLease
    val accessToken = lease?.accessToken.orEmpty()
    val userId = lease?.session?.userId.orEmpty()
    val hasScope = lease?.session?.scopes?.contains(USER_EMOTES_SCOPE) == true
    var emotes by remember(userId, accessToken, broadcasterId) {
        mutableStateOf(emptyList<ThirdPartyEmoteAsset>())
    }

    DisposableEffect(client) {
        onDispose { client.close() }
    }
    LaunchedEffect(authentication, accessToken, userId, broadcasterId, hasScope) {
        emotes = if (authentication == null || !hasScope) {
            emptyList()
        } else {
            runCatching {
                client.load(
                    authentication = authentication,
                    broadcasterId = broadcasterId,
                )
            }.getOrDefault(emptyList())
        }
    }
    return emotes
}

private const val USER_EMOTES_SCOPE = "user:read:emotes"
