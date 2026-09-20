package io.ferventio.shared.ui.user

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.user.UserCardRemoteEnrichment
import io.ferventio.shared.user.withRemoteEnrichment

@Composable
internal fun rememberRemoteUserCardData(
    localData: UserCardData,
): UserCardData {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val channelLogin = localData.recentMessages
        .asReversed()
        .firstOrNull { it.channelLogin.isNotBlank() }
        ?.channelLogin
        ?.takeIf(String::isNotBlank)
        ?: runtime.workspace.channels
            .firstOrNull { channel -> channel.id == localData.channelId }
            ?.login
            .orEmpty()
    val userCardRuntime = runtime.userCards
    var remote by remember(localData.channelId, localData.user.id, localData.user.login) {
        mutableStateOf<UserCardRemoteEnrichment?>(null)
    }

    LaunchedEffect(
        authentication,
        localData.user.id,
        localData.user.login,
        channelLogin,
        userCardRuntime,
    ) {
        remote = null
        if (authentication != null && channelLogin.isNotBlank()) {
            remote = userCardRuntime.enrich(
                authentication = authentication,
                userId = localData.user.id,
                userLogin = localData.user.login,
                channelLogin = channelLogin,
            )
        }
    }

    return remember(localData, remote) {
        localData.withRemoteEnrichment(remote)
    }
}
