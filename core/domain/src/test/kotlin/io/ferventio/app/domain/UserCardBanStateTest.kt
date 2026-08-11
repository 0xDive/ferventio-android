package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserCardBanStateTest {
    @Test
    fun `known remote permanent ban is preserved without local override`() {
        assertTrue(UserCardBanState.resolve(knownPermanentlyBanned = true, localActions = emptyList()))
    }

    @Test
    fun `latest persisted unban overrides an older ban`() {
        val actions = listOf(
            action("BAN", 100),
            action("UNBAN", 200),
        )

        assertFalse(UserCardBanState.resolve(knownPermanentlyBanned = true, localActions = actions))
    }

    @Test
    fun `latest persisted ban overrides an older unban`() {
        val actions = listOf(
            action("UNBAN", 100),
            action("BAN", 200),
        )

        assertTrue(UserCardBanState.resolve(knownPermanentlyBanned = false, localActions = actions))
    }

    @Test
    fun `legacy lowercase actions remain compatible`() {
        val actions = listOf(action("ban", 100))

        assertTrue(UserCardBanState.resolve(knownPermanentlyBanned = false, localActions = actions))
    }

    private fun action(type: String, createdAtMillis: Long) = LocalModerationAction(
        id = "$type-$createdAtMillis",
        channelId = "channel",
        targetUserId = "target",
        targetUserLogin = "target",
        messageId = null,
        action = type,
        durationSeconds = null,
        reason = null,
        createdAtMillis = createdAtMillis,
    )
}
