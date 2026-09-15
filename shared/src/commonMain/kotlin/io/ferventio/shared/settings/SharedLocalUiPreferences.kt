package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val QUICK_BAN_BUTTON_KEY = "quick_ban_button_enabled"
const val QUICK_DELETE_BUTTON_KEY = "quick_delete_button_enabled"
const val CONFIRM_MODERATION_ACTIONS_KEY = "confirm_moderation_actions_enabled"

data class SharedLocalUiPreferences(
    val showQuickBan: Boolean = false,
    val showQuickDelete: Boolean = false,
    val confirmModerationActions: Boolean = true,
)

/** Device-local UI preferences that intentionally do not participate in backend settings sync. */
interface SharedLocalUiPreferencesStore {
    fun load(): SharedLocalUiPreferences

    fun save(preferences: SharedLocalUiPreferences)
}

class SharedLocalUiPreferencesStateHolder(
    private val store: SharedLocalUiPreferencesStore = InMemorySharedLocalUiPreferencesStore(),
) {
    var preferences by mutableStateOf(store.load())
        private set

    fun update(transform: (SharedLocalUiPreferences) -> SharedLocalUiPreferences): SharedLocalUiPreferences {
        val updated = transform(preferences)
        store.save(updated)
        preferences = updated
        return updated
    }

    fun setShowQuickBan(value: Boolean) {
        update { it.copy(showQuickBan = value) }
    }

    fun setShowQuickDelete(value: Boolean) {
        update { it.copy(showQuickDelete = value) }
    }

    fun setConfirmModerationActions(value: Boolean) {
        update { it.copy(confirmModerationActions = value) }
    }
}

private class InMemorySharedLocalUiPreferencesStore : SharedLocalUiPreferencesStore {
    private var value = SharedLocalUiPreferences()

    override fun load(): SharedLocalUiPreferences = value

    override fun save(preferences: SharedLocalUiPreferences) {
        value = preferences
    }
}
