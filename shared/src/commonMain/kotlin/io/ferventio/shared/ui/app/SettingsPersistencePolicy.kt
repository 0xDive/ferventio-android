package io.ferventio.shared.ui.app

import io.ferventio.shared.settings.SharedAppPreferences
import io.ferventio.shared.settings.SharedSettingsSaveStatus

internal fun shouldPersistSettings(
    current: SharedAppPreferences,
    lastRequested: SharedAppPreferences,
    saveStatus: SharedSettingsSaveStatus,
): Boolean =
    current != lastRequested || saveStatus == SharedSettingsSaveStatus.FAILED
