package io.ferventio.shared.chat

import io.ferventio.shared.settings.SharedAppSettingsStateHolder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoModAlertPolicyTest {
    @Test
    fun missingOrEnabledSettingsAllowAutoModAlert() {
        assertTrue(shouldEmitAutoModAlert(null))
        assertTrue(shouldEmitAutoModAlert(SharedAppSettingsStateHolder()))
    }

    @Test
    fun disabledPreferenceSuppressesPlatformAlert() {
        val settings = SharedAppSettingsStateHolder()
        settings.updateLocally { preferences ->
            preferences.copy(autoModNotificationsEnabled = false)
        }

        assertFalse(shouldEmitAutoModAlert(settings))
    }
}
