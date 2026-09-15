package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.workspace.WorkspaceRuntimeSnapshot
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnonymousRecentMessagesPolicyTest {
    @Test
    fun recentMessagesWaitForSelectedCanonicalRoomId() {
        val workspace = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(channel("anonymous:alpha", "alpha")),
                selectedChannelId = "anonymous:alpha",
            ),
        )

        assertNull(workspace.anonymousRecentMessagesTarget(enabled = true).channel)

        workspace.remapChannelId("anonymous:alpha", "1234")

        val target = workspace.anonymousRecentMessagesTarget(enabled = true)
        assertTrue(target.enabled)
        assertEquals("1234", target.channel?.id)
        assertEquals("alpha", target.channel?.login)
    }

    @Test
    fun recentMessagesTargetTracksSelectedChannelOnly() {
        val workspace = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(
                    channel("1", "alpha"),
                    channel("2", "beta"),
                ),
                selectedChannelId = "1",
            ),
        )

        assertEquals("1", workspace.anonymousRecentMessagesTarget(true).channel?.id)
        workspace.selectChannel("2")
        assertEquals("2", workspace.anonymousRecentMessagesTarget(true).channel?.id)
    }

    @Test
    fun retryPolicyMatchesAndroidMinuteCooldownPerLogin() {
        val policy = AnonymousRecentMessagesAttemptPolicy()
        val alpha = channel("1", "alpha")
        val beta = channel("2", "beta")

        assertTrue(policy.shouldAttempt(alpha, nowMillis = 1_000L))
        assertFalse(policy.shouldAttempt(alpha, nowMillis = 60_999L))
        assertTrue(policy.shouldAttempt(alpha, nowMillis = 61_000L))
        assertTrue(policy.shouldAttempt(beta, nowMillis = 61_000L))
    }

    private fun channel(id: String, login: String) = ChatChannel(
        id = id,
        login = login,
        displayName = login,
    )
}
