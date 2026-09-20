package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.TwitchUser
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.CancellationException

@Composable
internal fun rememberAccountTwitchProfile(): TwitchUser? {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val session = authentication?.accessLease?.session
    val userCardRuntime = runtime.userCards
    var profile by remember(session?.userId, session?.login) {
        mutableStateOf<TwitchUser?>(null)
    }

    LaunchedEffect(authentication, session?.userId, session?.login, userCardRuntime) {
        profile = null
        if (authentication == null || session == null) return@LaunchedEffect
        profile = try {
            userCardRuntime.loadUser(
                authentication = authentication,
                userId = session.userId,
                userLogin = session.login,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    return profile
}
