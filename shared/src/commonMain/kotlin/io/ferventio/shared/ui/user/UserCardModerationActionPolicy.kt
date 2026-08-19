package io.ferventio.shared.ui.user

import io.ferventio.app.domain.UserCardModerationLayout
import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.UserCardSettingsEditor

internal sealed interface UserCardRuntimeModerationAction {
    data class Timeout(val durationSeconds: Int) : UserCardRuntimeModerationAction
    data object Ban : UserCardRuntimeModerationAction
}

internal object UserCardModerationActionPolicy {
    fun visibleActions(preferences: SharedAppPreferences): List<UserCardRuntimeModerationAction> {
        val normalized = preferences.normalized()
        return UserCardSettingsEditor.visibleModerationActionIds(normalized).mapNotNull { actionId ->
            when {
                actionId == UserCardModerationLayout.BAN -> UserCardRuntimeModerationAction.Ban
                else -> UserCardSettingsEditor.timeoutSeconds(actionId)
                    ?.takeIf(normalized.userCardTimeoutPresetsSeconds::contains)
                    ?.let(UserCardRuntimeModerationAction::Timeout)
            }
        }
    }
}
