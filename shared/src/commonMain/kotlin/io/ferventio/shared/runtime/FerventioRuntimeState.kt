package io.ferventio.shared.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.ferventio.shared.auth.MobileAuthenticationStateHolder
import io.ferventio.shared.push.PushNavigationInbox
import io.ferventio.shared.push.PushRegistrationStateHolder

class FerventioRuntimeState(
    val lifecycle: AppLifecycleStateHolder,
    val authentication: MobileAuthenticationStateHolder,
    val pushRegistration: PushRegistrationStateHolder,
    val pushNavigation: PushNavigationInbox,
) {
    constructor() : this(
        lifecycle = AppLifecycleStateHolder(),
        authentication = MobileAuthenticationStateHolder(),
        pushRegistration = PushRegistrationStateHolder(),
        pushNavigation = PushNavigationInbox(),
    )
}

val LocalFerventioRuntimeState = staticCompositionLocalOf<FerventioRuntimeState> {
    error("Ferventio runtime state was not provided")
}

@Composable
fun ProvideFerventioRuntimeState(
    runtimeState: FerventioRuntimeState,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFerventioRuntimeState provides runtimeState,
        content = content,
    )
}
