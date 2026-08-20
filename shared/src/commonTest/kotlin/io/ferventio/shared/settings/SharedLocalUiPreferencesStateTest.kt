package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLocalUiPreferencesStateTest {
    @Test
    fun defaultsMatchAndroidQuickModerationPreferences() {
        val state = SharedLocalUiPreferencesStateHolder()

        assertFalse(state.preferences.showQuickBan)
        assertFalse(state.preferences.showQuickDelete)
        assertTrue(state.preferences.confirmModerationActions)
    }

    @Test
    fun updatesPersistThroughConfiguredStore() {
        val store = RecordingStore()
        val state = SharedLocalUiPreferencesStateHolder(store)

        state.setShowQuickBan(true)
        state.setShowQuickDelete(true)
        state.setConfirmModerationActions(false)

        assertEquals(
            SharedLocalUiPreferences(
                showQuickBan = true,
                showQuickDelete = true,
                confirmModerationActions = false,
            ),
            store.load(),
        )
        assertEquals(store.load(), state.preferences)
    }

    private class RecordingStore : SharedLocalUiPreferencesStore {
        private var value = SharedLocalUiPreferences()

        override fun load(): SharedLocalUiPreferences = value

        override fun save(preferences: SharedLocalUiPreferences) {
            value = preferences
        }
    }
}
