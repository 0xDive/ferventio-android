package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class HistorySettingsPresetsTest {
    @Test
    fun sharedHistoryChoicesMatchAndroidSettingsUi() {
        assertEquals(listOf(250, 500, 1_000), HistorySettingsPresets.messageLimits)
        assertEquals(listOf(1, 7, 30, 0), HistorySettingsPresets.retentionDays)
        assertEquals(listOf(50, 100, 250, 0), HistorySettingsPresets.maxSizeMb)
    }
}
