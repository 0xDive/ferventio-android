package io.ferventio.shared.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.ferventio.shared.auth.MobileAuthenticationStateHolder
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.moderation.TwitchModerationRuntime
import io.ferventio.shared.push.PushNavigationInbox
import io.ferventio.shared.push.PushRegistrationStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder

class FerventioRuntimeState(
    val lifecycle: AppLifecycleStateHolder,
    val authentication: MobileAuthenticationStateHolder,
    val workspace: WorkspaceRuntimeStateHolder,
    val chat: ChatRuntimeStateHolder,
    val pushRegistration: PushRegistrationStateHolder,
    val pushNavigation: PushNavigationInbox,
) {
    val moderation: TwitchModerationRuntime by lazy { TwitchModerationRuntime(chat) }

    constructor() : this(
        lifecycle = AppLifecycleStateHolder(),
        authentication = MobileAuthenticationStateHolder(),
        workspace = WorkspaceRuntimeStateHolder(),
        chat = ChatRuntimeStateHolder(),
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
