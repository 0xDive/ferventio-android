package io.ferventio.shared.settings

import platform.Foundation.NSUserDefaults

/** iOS device-local History preferences for read-only signed-out chat. */
class IosAnonymousHistoryPreferencesStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : AnonymousHistoryPreferencesStore {
    override fun load(): AnonymousHistoryPreferences = AnonymousHistoryPreferences(
        recentMessagesEnabled = defaults.boolOrDefault(
            ANONYMOUS_RECENT_MESSAGES_ENABLED_KEY,
            false,
        ),
        localHistoryEnabled = defaults.boolOrDefault(
            ANONYMOUS_LOCAL_HISTORY_ENABLED_KEY,
            true,
        ),
        localHistoryLimit = defaults.intOrDefault(ANONYMOUS_LOCAL_HISTORY_LIMIT_KEY, 500),
        localHistoryRetentionDays = defaults.intOrDefault(
            ANONYMOUS_LOCAL_HISTORY_RETENTION_DAYS_KEY,
            7,
        ),
        localHistoryMaxSizeMb = defaults.intOrDefault(
            ANONYMOUS_LOCAL_HISTORY_MAX_SIZE_MB_KEY,
            0,
        ),
    ).normalized()

    override fun save(preferences: AnonymousHistoryPreferences) {
        val normalized = preferences.normalized()
        defaults.setBool(
            normalized.recentMessagesEnabled,
            forKey = ANONYMOUS_RECENT_MESSAGES_ENABLED_KEY,
        )
        defaults.setBool(
            normalized.localHistoryEnabled,
            forKey = ANONYMOUS_LOCAL_HISTORY_ENABLED_KEY,
        )
        defaults.setInteger(
            normalized.localHistoryLimit.toLong(),
            forKey = ANONYMOUS_LOCAL_HISTORY_LIMIT_KEY,
        )
        defaults.setInteger(
            normalized.localHistoryRetentionDays.toLong(),
            forKey = ANONYMOUS_LOCAL_HISTORY_RETENTION_DAYS_KEY,
        )
        defaults.setInteger(
            normalized.localHistoryMaxSizeMb.toLong(),
            forKey = ANONYMOUS_LOCAL_HISTORY_MAX_SIZE_MB_KEY,
        )
    }

    private fun NSUserDefaults.boolOrDefault(key: String, fallback: Boolean): Boolean =
        objectForKey(key)?.let { boolForKey(key) } ?: fallback

    private fun NSUserDefaults.intOrDefault(key: String, fallback: Int): Int =
        objectForKey(key)?.let { integerForKey(key).toInt() } ?: fallback
}

fun iosAnonymousHistoryPreferencesCoordinator(): AnonymousHistoryPreferencesCoordinator =
    AnonymousHistoryPreferencesCoordinator(IosAnonymousHistoryPreferencesStore())
