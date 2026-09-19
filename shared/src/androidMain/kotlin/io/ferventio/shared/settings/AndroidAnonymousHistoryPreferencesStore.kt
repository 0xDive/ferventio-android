package io.ferventio.shared.settings

import android.content.Context

/** Android guest History persistence compatible with the legacy production preference keys. */
class AndroidAnonymousHistoryPreferencesStore(
    context: Context,
    fileName: String = FERVENTIO_ANDROID_SETTINGS_FILE_NAME,
) : AnonymousHistoryPreferencesStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        fileName,
        Context.MODE_PRIVATE,
    )

    override fun load(): AnonymousHistoryPreferences = AnonymousHistoryPreferences(
        recentMessagesEnabled = preferences.getBoolean(ANONYMOUS_RECENT_MESSAGES_ENABLED_KEY, false),
        localHistoryEnabled = preferences.getBoolean(ANONYMOUS_LOCAL_HISTORY_ENABLED_KEY, true),
        localHistoryLimit = preferences.getInt(ANONYMOUS_LOCAL_HISTORY_LIMIT_KEY, 500),
        localHistoryRetentionDays = preferences.getInt(
            ANONYMOUS_LOCAL_HISTORY_RETENTION_DAYS_KEY,
            7,
        ),
        localHistoryMaxSizeMb = preferences.getInt(ANONYMOUS_LOCAL_HISTORY_MAX_SIZE_MB_KEY, 0),
    ).normalized()

    override fun save(preferences: AnonymousHistoryPreferences) {
        val normalized = preferences.normalized()
        this.preferences.edit()
            .putBoolean(ANONYMOUS_RECENT_MESSAGES_ENABLED_KEY, normalized.recentMessagesEnabled)
            .putBoolean(ANONYMOUS_LOCAL_HISTORY_ENABLED_KEY, normalized.localHistoryEnabled)
            .putInt(ANONYMOUS_LOCAL_HISTORY_LIMIT_KEY, normalized.localHistoryLimit)
            .putInt(
                ANONYMOUS_LOCAL_HISTORY_RETENTION_DAYS_KEY,
                normalized.localHistoryRetentionDays,
            )
            .putInt(ANONYMOUS_LOCAL_HISTORY_MAX_SIZE_MB_KEY, normalized.localHistoryMaxSizeMb)
            .apply()
    }
}

fun androidAnonymousHistoryPreferencesCoordinator(
    context: Context,
): AnonymousHistoryPreferencesCoordinator = AnonymousHistoryPreferencesCoordinator(
    store = AndroidAnonymousHistoryPreferencesStore(context),
)
