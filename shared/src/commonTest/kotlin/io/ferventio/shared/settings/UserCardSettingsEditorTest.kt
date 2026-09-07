package io.ferventio.shared.settings

import io.ferventio.app.domain.UserCardModerationLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserCardSettingsEditorTest {
    @Test
    fun parsesSupportedTimeoutPresetSyntax() {
        assertEquals(10, UserCardSettingsEditor.parseTimeoutPreset("10s"))
        assertEquals(300, UserCardSettingsEditor.parseTimeoutPreset("5m"))
        assertEquals(7_200, UserCardSettingsEditor.parseTimeoutPreset("2h"))
        assertEquals(86_400, UserCardSettingsEditor.parseTimeoutPreset("1d"))
        assertNull(UserCardSettingsEditor.parseTimeoutPreset("0s"))
        assertNull(UserCardSettingsEditor.parseTimeoutPreset("15d"))
        assertNull(UserCardSettingsEditor.parseTimeoutPreset("later"))
    }

    @Test
    fun addTimeoutPresetRejectsDuplicatesAndMaximumSize() {
        val defaults = SharedAppPreferences()
        assertFalse(UserCardSettingsEditor.canAddTimeoutPreset(defaults, "10s"))
        assertEquals(defaults, UserCardSettingsEditor.addTimeoutPreset(defaults, "10s"))

        val full = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = (1..SharedAppPreferences.MAX_TIMEOUT_PRESETS).toList(),
            userCardModerationActionOrder = UserCardModerationLayout.defaultOrder(
                (1..SharedAppPreferences.MAX_TIMEOUT_PRESETS).toList(),
            ),
        )
        assertFalse(UserCardSettingsEditor.canAddTimeoutPreset(full, "11s"))
        assertEquals(full, UserCardSettingsEditor.addTimeoutPreset(full, "11s"))
    }

    @Test
    fun addTimeoutPresetAppendsPresetAndAction() {
        val initial = SharedAppPreferences()
        val updated = UserCardSettingsEditor.addTimeoutPreset(initial, "5m")

        assertTrue(300 in updated.userCardTimeoutPresetsSeconds)
        assertTrue(
            UserCardModerationLayout.timeoutActionId(300) in
                updated.userCardModerationActionOrder,
        )
    }

    @Test
    fun removeTimeoutPresetRemovesActionButKeepsLastPreset() {
        val presets = listOf(10, 60)
        val initial = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = presets,
            userCardModerationActionOrder = UserCardModerationLayout.defaultOrder(presets),
        )

        val reduced = UserCardSettingsEditor.removeTimeoutPreset(initial, 10)
        assertEquals(listOf(60), reduced.userCardTimeoutPresetsSeconds)
        assertFalse(
            UserCardModerationLayout.timeoutActionId(10) in reduced.userCardModerationActionOrder,
        )

        assertEquals(reduced, UserCardSettingsEditor.removeTimeoutPreset(reduced, 60))
    }

    @Test
    fun resetTimeoutPresetsRestoresDefaultsAndNormalizesOrder() {
        val customPresets = listOf(30, 90)
        val initial = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = customPresets,
            userCardModerationActionOrder = listOf(
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.timeoutActionId(90),
                UserCardModerationLayout.BAN,
                UserCardModerationLayout.UNBAN,
                UserCardModerationLayout.timeoutActionId(30),
            ),
        )

        val updated = UserCardSettingsEditor.resetTimeoutPresets(initial)

        assertEquals(SharedAppPreferences.DEFAULT_TIMEOUT_PRESETS, updated.userCardTimeoutPresetsSeconds)
        assertTrue(UserCardModerationLayout.WARN in updated.userCardModerationActionOrder)
        SharedAppPreferences.DEFAULT_TIMEOUT_PRESETS.forEach { seconds ->
            assertTrue(
                UserCardModerationLayout.timeoutActionId(seconds) in
                    updated.userCardModerationActionOrder,
            )
        }
        assertFalse(
            UserCardModerationLayout.timeoutActionId(30) in updated.userCardModerationActionOrder,
        )
        assertFalse(
            UserCardModerationLayout.timeoutActionId(90) in updated.userCardModerationActionOrder,
        )
    }

    @Test
    fun movingVisibleActionKeepsHiddenBanInPersistedOrder() {
        val initial = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = listOf(10),
            userCardShowBanAction = false,
            userCardModerationActionOrder = listOf(
                UserCardModerationLayout.timeoutActionId(10),
                UserCardModerationLayout.BAN,
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.UNBAN,
            ),
        )

        val updated = UserCardSettingsEditor.moveModerationAction(
            preferences = initial,
            actionId = UserCardModerationLayout.WARN,
            direction = -1,
        )

        assertEquals(
            listOf(
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.timeoutActionId(10),
                UserCardModerationLayout.UNBAN,
            ),
            UserCardSettingsEditor.visibleModerationActionIds(updated),
        )
        assertEquals(UserCardModerationLayout.BAN, updated.userCardModerationActionOrder.last())
    }

    @Test
    fun formatsTimeoutPresetsUsingCompactInputSyntax() {
        assertEquals("10s", UserCardSettingsEditor.formatTimeoutPreset(10))
        assertEquals("5m", UserCardSettingsEditor.formatTimeoutPreset(300))
        assertEquals("2h", UserCardSettingsEditor.formatTimeoutPreset(7_200))
        assertEquals("1d", UserCardSettingsEditor.formatTimeoutPreset(86_400))
    }
}
