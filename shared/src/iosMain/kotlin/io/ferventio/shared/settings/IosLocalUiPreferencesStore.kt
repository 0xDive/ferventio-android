package io.ferventio.shared.settings

import platform.Foundation.NSUserDefaults

class IosLocalUiPreferencesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SharedLocalUiPreferencesStore {
    override fun load(): SharedLocalUiPreferences = SharedLocalUiPreferences(
        showQuickBan = defaults.boolForKey(QUICK_BAN_BUTTON_KEY),
        showQuickDelete = defaults.boolForKey(QUICK_DELETE_BUTTON_KEY),
        confirmModerationActions = defaults.objectForKey(CONFIRM_MODERATION_ACTIONS_KEY)
            ?.let { defaults.boolForKey(CONFIRM_MODERATION_ACTIONS_KEY) }
            ?: true,
    )

    override fun save(preferences: SharedLocalUiPreferences) {
        defaults.setBool(preferences.showQuickBan, forKey = QUICK_BAN_BUTTON_KEY)
        defaults.setBool(preferences.showQuickDelete, forKey = QUICK_DELETE_BUTTON_KEY)
        defaults.setBool(
            preferences.confirmModerationActions,
            forKey = CONFIRM_MODERATION_ACTIONS_KEY,
        )
    }
}
