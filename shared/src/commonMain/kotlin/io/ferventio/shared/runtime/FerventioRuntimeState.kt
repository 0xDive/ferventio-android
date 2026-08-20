package io.ferventio.shared.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.shared.auth.MobileAuthenticationStateHolder
import io.ferventio.shared.chat.ChatAttentionStateHolder
import io.ferventio.shared.chat.ChatRuntimeStateHolder
import io.ferventio.shared.chat.TwitchChatMessageRuntime
import io.ferventio.shared.chat.TwitchInteractiveRuntime
import io.ferventio.shared.moderation.TwitchModerationRuntime
import io.ferventio.shared.push.PushNavigationInbox
import io.ferventio.shared.push.PushRegistrationStateHolder
import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import io.ferventio.shared.settings.SharedLocalUiPreferencesStateHolder
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder

class FerventioRuntimeState(
    val lifecycle: AppLifecycleStateHolder,
    val authentication: MobileAuthenticationStateHolder,
    val workspace: WorkspaceRuntimeStateHolder,
    val settings: SharedAppSettingsStateHolder,
    val messageRules: SharedMessageRulesStateHolder,
    val savedFilters: SharedSavedFiltersStateHolder,
    val chat: ChatRuntimeStateHolder,
    val attention: ChatAttentionStateHolder,
    val pushRegistration: PushRegistrationStateHolder,
    val pushNavigation: PushNavigationInbox,
    val history: ChatHistoryStore? = null,
    val localUiPreferences: SharedLocalUiPreferencesStateHolder = SharedLocalUiPreferencesStateHolder(),
) {
    val moderation: TwitchModerationRuntime by lazy { TwitchModerationRuntime(chat) }
    val interactive: TwitchInteractiveRuntime by lazy { TwitchInteractiveRuntime(chat) }
    val chatMessages: TwitchChatMessageRuntime by lazy { TwitchChatMessageRuntime(chat) }

    constructor(
        history: ChatHistoryStore? = null,
        localUiPreferences: SharedLocalUiPreferencesStateHolder = SharedLocalUiPreferencesStateHolder(),
    ) : this(
        lifecycle = AppLifecycleStateHolder(),
        authentication = MobileAuthenticationStateHolder(),
        workspace = WorkspaceRuntimeStateHolder(),
        settings = SharedAppSettingsStateHolder(),
        messageRules = SharedMessageRulesStateHolder(),
        savedFilters = SharedSavedFiltersStateHolder(),
        chat = ChatRuntimeStateHolder(),
        attention = ChatAttentionStateHolder(),
        pushRegistration = PushRegistrationStateHolder(),
        pushNavigation = PushNavigationInbox(),
        history = history,
        localUiPreferences = localUiPreferences,
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
