package io.ferventio.shared.ui.user

import io.ferventio.app.domain.UserCardModerationLayout
import io.ferventio.shared.settings.SharedAppPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class UserCardModerationActionPolicyTest {
    @Test
    fun defaultActionsExposeConfiguredTimeoutsWarnAndBan() {
        val actions = UserCardModerationActionPolicy.visibleActions(
            preferences = SharedAppPreferences(),
            isPermanentlyBanned = false,
        )

        assertEquals(
            listOf(
                UserCardRuntimeModerationAction.Timeout(10),
                UserCardRuntimeModerationAction.Timeout(60),
                UserCardRuntimeModerationAction.Timeout(600),
                UserCardRuntimeModerationAction.Timeout(3_600),
                UserCardRuntimeModerationAction.Timeout(86_400),
                UserCardRuntimeModerationAction.Warn,
                UserCardRuntimeModerationAction.Ban,
            ),
            actions,
        )
    }

    @Test
    fun customPersistedOrderControlsAllSupportedRuntimeActions() {
        val preferences = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = listOf(600, 60),
            userCardShowBanAction = true,
            userCardModerationActionOrder = listOf(
                UserCardModerationLayout.BAN,
                UserCardModerationLayout.timeoutActionId(60),
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.timeoutActionId(600),
                UserCardModerationLayout.UNBAN,
            ),
        )

        assertEquals(
            listOf(
                UserCardRuntimeModerationAction.Ban,
                UserCardRuntimeModerationAction.Timeout(60),
                UserCardRuntimeModerationAction.Warn,
                UserCardRuntimeModerationAction.Timeout(600),
            ),
            UserCardModerationActionPolicy.visibleActions(
                preferences = preferences,
                isPermanentlyBanned = false,
            ),
        )
    }

    @Test
    fun permanentBanReplacesBanWithConfiguredUnbanAction() {
        val preferences = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = listOf(600),
            userCardShowBanAction = true,
            userCardModerationActionOrder = listOf(
                UserCardModerationLayout.UNBAN,
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.BAN,
                UserCardModerationLayout.timeoutActionId(600),
            ),
        )

        assertEquals(
            listOf(
                UserCardRuntimeModerationAction.Unban,
                UserCardRuntimeModerationAction.Warn,
                UserCardRuntimeModerationAction.Timeout(600),
            ),
            UserCardModerationActionPolicy.visibleActions(
                preferences = preferences,
                isPermanentlyBanned = true,
            ),
        )
    }

    @Test
    fun hiddenBanStillKeepsTimeoutsAndWarn() {
        val preferences = SharedAppPreferences(
            userCardTimeoutPresetsSeconds = listOf(600),
            userCardShowBanAction = false,
            userCardModerationActionOrder = listOf(
                UserCardModerationLayout.BAN,
                UserCardModerationLayout.timeoutActionId(600),
                UserCardModerationLayout.WARN,
                UserCardModerationLayout.UNBAN,
            ),
        )

        val actions = UserCardModerationActionPolicy.visibleActions(
            preferences = preferences,
            isPermanentlyBanned = false,
        )

        assertEquals(
            listOf(
                UserCardRuntimeModerationAction.Timeout(600),
                UserCardRuntimeModerationAction.Warn,
            ),
            actions,
        )
        assertFalse(UserCardRuntimeModerationAction.Ban in actions)
        assertFalse(UserCardRuntimeModerationAction.Unban in actions)
    }
}
