package io.ferventio.shared.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.ferventio.shared.push.PushRegistrationStateHolder

class FerventioRuntimeState(
    val lifecycle: AppLifecycleStateHolder,
    val pushRegistration: PushRegistrationStateHolder,
) {
    constructor() : this(
        lifecycle = AppLifecycleStateHolder(),
        pushRegistration = PushRegistrationStateHolder(),
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
