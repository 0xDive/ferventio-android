package io.ferventio.shared.settings

import io.ferventio.app.domain.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnonymousHistoryPreferencesCoordinatorTest {
    @Test
    fun restoreProjectsOnlyHistoryFieldsIntoSharedSettings() {
        val store = RecordingStore(
            AnonymousHistoryPreferences(
                recentMessagesEnabled = true,
                localHistoryEnabled = false,
                localHistoryLimit = 1_200,
                localHistoryRetentionDays = 30,
                localHistoryMaxSizeMb = 256,
            ),
        )
        val state = SharedAppSettingsStateHolder(
            SharedAppPreferences(
                themeMode = AppThemeMode.AMOLED,
                showBadges = false,
            ),
        )

        AnonymousHistoryPreferencesCoordinator(store).restore(state)

        assertEquals(AppThemeMode.AMOLED, state.preferences.themeMode)
        assertEquals(false, state.preferences.showBadges)
        assertTrue(state.preferences.recentMessagesEnabled)
        assertEquals(false, state.preferences.localHistoryEnabled)
        assertEquals(1_200, state.preferences.localHistoryLimit)
        assertEquals(30, state.preferences.localHistoryRetentionDays)
        assertEquals(256, state.preferences.localHistoryMaxSizeMb)
    }

    @Test
    fun saveExtractsAndNormalizesOnlyHistoryPreferences() {
        val store = RecordingStore()
        val coordinator = AnonymousHistoryPreferencesCoordinator(store)
        val saved = coordinator.save(
            SharedAppPreferences(
                recentMessagesEnabled = true,
                localHistoryEnabled = true,
                localHistoryLimit = 9_999,
                localHistoryRetentionDays = 999,
                localHistoryMaxSizeMb = 9_999,
            ),
        )

        assertEquals(
            AnonymousHistoryPreferences(
                recentMessagesEnabled = true,
                localHistoryEnabled = true,
                localHistoryLimit = 5_000,
                localHistoryRetentionDays = 365,
                localHistoryMaxSizeMb = 1_024,
            ),
            saved,
        )
        assertEquals(saved, store.preferences)
    }

    private class RecordingStore(
        var preferences: AnonymousHistoryPreferences = AnonymousHistoryPreferences(),
    ) : AnonymousHistoryPreferencesStore {
        override fun load(): AnonymousHistoryPreferences = preferences

        override fun save(preferences: AnonymousHistoryPreferences) {
            this.preferences = preferences
        }
    }
}
