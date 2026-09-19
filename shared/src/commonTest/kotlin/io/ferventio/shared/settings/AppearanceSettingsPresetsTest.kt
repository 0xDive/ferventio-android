package io.ferventio.shared.settings

import io.ferventio.app.domain.AppThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class AppearanceSettingsPresetsTest {
    @Test
    fun presetsMatchAndroidAppearanceChoices() {
        assertEquals(listOf(85, 100, 115, 130), AppearanceSettingsPresets.FONT_SCALE_PERCENT)
        assertEquals(listOf(90, 100, 125, 150), AppearanceSettingsPresets.EMOTE_SCALE_PERCENT)
    }

    @Test
    fun localAppearanceChangesSnapToAndroidPresets() {
        val state = SharedAppSettingsStateHolder()

        state.updateLocally { it.copy(fontScalePercent = 89) }
        assertEquals(85, state.preferences.fontScalePercent)

        state.updateLocally { it.copy(fontScalePercent = 93) }
        assertEquals(100, state.preferences.fontScalePercent)

        state.updateLocally { it.copy(emoteScalePercent = 112) }
        assertEquals(100, state.preferences.emoteScalePercent)

        state.updateLocally { it.copy(emoteScalePercent = 118) }
        assertEquals(125, state.preferences.emoteScalePercent)
    }

    @Test
    fun restoredCustomValuesSurviveUnrelatedLocalEdits() {
        val state = SharedAppSettingsStateHolder()
        state.restore(
            SharedAppPreferences(
                fontScalePercent = 93,
                emoteScalePercent = 117,
            ),
            revision = 4,
        )

        state.updateLocally { it.copy(themeMode = AppThemeMode.LIGHT) }

        assertEquals(93, state.preferences.fontScalePercent)
        assertEquals(117, state.preferences.emoteScalePercent)
    }
}
