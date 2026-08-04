package io.ferventio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UserCardModerationLayoutTest {
    @Test
    fun normalize_keepsSavedOrderAndAppendsNewActions() {
        val result = UserCardModerationLayout.normalize(
            storedOrder = listOf("ban", "timeout:60", "removed", "ban"),
            timeoutPresetsSeconds = listOf(10, 60),
        )

        assertEquals(
            listOf("ban", "timeout:60", "timeout:10", "warn", "unban"),
            result,
        )
    }

    @Test
    fun move_reordersOnlyOneStepAndClampsAtEdges() {
        val presets = listOf(10, 60)
        val moved = UserCardModerationLayout.move(
            storedOrder = UserCardModerationLayout.defaultOrder(presets),
            timeoutPresetsSeconds = presets,
            actionId = "ban",
            direction = -1,
        )

        assertEquals(
            listOf("timeout:10", "timeout:60", "ban", "warn", "unban"),
            moved,
        )
        assertEquals(
            moved,
            UserCardModerationLayout.move(moved, presets, "timeout:10", -1),
        )
    }
    @Test
    fun move_ignoresHiddenActions() {
        val presets = listOf(10, 60)
        val result = UserCardModerationLayout.move(
            storedOrder = listOf("timeout:10", "ban", "warn", "timeout:60", "unban"),
            timeoutPresetsSeconds = presets,
            actionId = "warn",
            direction = -1,
            hiddenActionIds = setOf("ban"),
        )

        assertEquals(
            listOf("warn", "timeout:10", "timeout:60", "unban", "ban"),
            result,
        )
    }

}
