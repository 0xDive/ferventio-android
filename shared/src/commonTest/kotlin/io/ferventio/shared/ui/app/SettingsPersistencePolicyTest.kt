package io.ferventio.shared.ui.app

import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedSettingsSaveStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsPersistencePolicyTest {
    @Test
    fun unchangedSuccessfulPreferencesDoNotRequestAnotherSave() {
        val preferences = SharedAppPreferences()

        assertFalse(
            shouldPersistSettings(
                current = preferences,
                lastRequested = preferences,
                saveStatus = SharedSettingsSaveStatus.IDLE,
            ),
        )
    }

    @Test
    fun changedPreferencesRequestSave() {
        val previous = SharedAppPreferences()
        val current = previous.copy(showAvatars = !previous.showAvatars)

        assertTrue(
            shouldPersistSettings(
                current = current,
                lastRequested = previous,
                saveStatus = SharedSettingsSaveStatus.IDLE,
            ),
        )
    }

    @Test
    fun failedSaveRetriesEvenWhenPreferencesAreUnchanged() {
        val preferences = SharedAppPreferences()

        assertTrue(
            shouldPersistSettings(
                current = preferences,
                lastRequested = preferences,
                saveStatus = SharedSettingsSaveStatus.FAILED,
            ),
        )
    }
}
