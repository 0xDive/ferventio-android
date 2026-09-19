package io.ferventio.shared.settings

/** Device-local History preferences used while there is no authenticated backend settings scope. */
data class AnonymousHistoryPreferences(
    val recentMessagesEnabled: Boolean = false,
    val localHistoryEnabled: Boolean = true,
    val localHistoryLimit: Int = 500,
    val localHistoryRetentionDays: Int = 7,
    val localHistoryMaxSizeMb: Int = 0,
) {
    fun normalized(): AnonymousHistoryPreferences = copy(
        localHistoryLimit = localHistoryLimit.coerceIn(100, 5_000),
        localHistoryRetentionDays = localHistoryRetentionDays.coerceIn(0, 365),
        localHistoryMaxSizeMb = localHistoryMaxSizeMb.coerceIn(0, 1_024),
    )

    fun applyTo(preferences: SharedAppPreferences): SharedAppPreferences = preferences.copy(
        recentMessagesEnabled = recentMessagesEnabled,
        localHistoryEnabled = localHistoryEnabled,
        localHistoryLimit = localHistoryLimit,
        localHistoryRetentionDays = localHistoryRetentionDays,
        localHistoryMaxSizeMb = localHistoryMaxSizeMb,
    ).normalized()

    companion object {
        fun from(preferences: SharedAppPreferences): AnonymousHistoryPreferences =
            AnonymousHistoryPreferences(
                recentMessagesEnabled = preferences.recentMessagesEnabled,
                localHistoryEnabled = preferences.localHistoryEnabled,
                localHistoryLimit = preferences.localHistoryLimit,
                localHistoryRetentionDays = preferences.localHistoryRetentionDays,
                localHistoryMaxSizeMb = preferences.localHistoryMaxSizeMb,
            ).normalized()
    }
}

interface AnonymousHistoryPreferencesStore {
    fun load(): AnonymousHistoryPreferences

    fun save(preferences: AnonymousHistoryPreferences)
}

/**
 * Projects only History fields into the shared settings holder.
 *
 * Authenticated settings remain backend-owned; guest mode never overwrites appearance, moderation,
 * notification or other account-scoped preferences when restoring its device-local snapshot.
 */
class AnonymousHistoryPreferencesCoordinator(
    private val store: AnonymousHistoryPreferencesStore = InMemoryAnonymousHistoryPreferencesStore(),
) {
    fun restore(state: SharedAppSettingsStateHolder): AnonymousHistoryPreferences {
        val restored = store.load().normalized()
        state.updateLocally { current -> restored.applyTo(current) }
        return restored
    }

    fun save(preferences: SharedAppPreferences): AnonymousHistoryPreferences {
        val snapshot = AnonymousHistoryPreferences.from(preferences)
        store.save(snapshot)
        return snapshot
    }
}

internal const val ANONYMOUS_RECENT_MESSAGES_ENABLED_KEY = "recent_messages_enabled"
internal const val ANONYMOUS_LOCAL_HISTORY_ENABLED_KEY = "local_history_enabled"
internal const val ANONYMOUS_LOCAL_HISTORY_LIMIT_KEY = "local_history_limit"
internal const val ANONYMOUS_LOCAL_HISTORY_RETENTION_DAYS_KEY = "local_history_retention_days"
internal const val ANONYMOUS_LOCAL_HISTORY_MAX_SIZE_MB_KEY = "local_history_max_size_mb"

private class InMemoryAnonymousHistoryPreferencesStore : AnonymousHistoryPreferencesStore {
    private var preferences = AnonymousHistoryPreferences()

    override fun load(): AnonymousHistoryPreferences = preferences

    override fun save(preferences: AnonymousHistoryPreferences) {
        this.preferences = preferences
    }
}
