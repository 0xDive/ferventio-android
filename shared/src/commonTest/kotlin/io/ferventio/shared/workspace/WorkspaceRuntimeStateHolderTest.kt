package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkspaceRuntimeStateHolderTest {
    private val alpha = ChatChannel(
        id = "1",
        login = "alpha",
        displayName = "Alpha",
    )
    private val beta = ChatChannel(
        id = "2",
        login = "beta",
        displayName = "Beta",
    )
    private val gamma = ChatChannel(
        id = "3",
        login = "gamma",
        displayName = "Gamma",
    )

    @Test
    fun initialSnapshotNormalizesMembershipAndSelection() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha, beta, alpha),
                selectedChannelId = "2",
                pinnedChannelIds = listOf("2", "missing", "2", "1"),
                moderatorChannelIds = setOf("3", "2"),
            ),
        )

        assertEquals(listOf("1", "2"), holder.channelIds)
        assertEquals("2", holder.selectedChannelId)
        assertEquals(listOf("2", "1"), holder.pinnedChannelIds)
        assertEquals(setOf("2"), holder.moderatorChannelIds)
    }

    @Test
    fun replacingChannelsPreservesSelectionWhenPossibleAndFallsBackToFirst() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.replaceChannels(listOf(alpha, beta))
        holder.selectChannel("2")

        holder.replaceChannels(listOf(beta, gamma))
        assertEquals("2", holder.selectedChannelId)

        holder.replaceChannels(listOf(gamma))
        assertEquals("3", holder.selectedChannelId)
    }

    @Test
    fun addRemoveAndMoveUseStableWorkspaceOrder() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.addOrReplaceChannel(alpha)
        holder.addOrReplaceChannel(beta)
        holder.addOrReplaceChannel(gamma)

        holder.moveChannel("3", 0)
        assertEquals(listOf("3", "1", "2"), holder.channelIds)

        holder.removeChannel("3")
        assertEquals(listOf("1", "2"), holder.channelIds)
        assertEquals("1", holder.selectedChannelId)
    }

    @Test
    fun replacingExistingChannelDoesNotChangeItsPosition() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.replaceChannels(listOf(alpha, beta))

        holder.addOrReplaceChannel(
            alpha.copy(displayName = "Alpha Live"),
        )

        assertEquals(listOf("1", "2"), holder.channelIds)
        assertEquals("Alpha Live", holder.channels.first().displayName)
    }

    @Test
    fun roleAndPinSetsAreTrimmedToWorkspaceMembership() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.replaceChannels(listOf(alpha, beta, gamma))

        holder.updatePinnedChannelIds(listOf(" 2 ", "3", "2", "missing"))
        holder.updateModeratorChannelIds(listOf(" 1 ", "1", "missing"))

        assertEquals(listOf("2", "3"), holder.pinnedChannelIds)
        assertEquals(setOf("1"), holder.moderatorChannelIds)

        holder.removeChannel("1")
        assertEquals(emptySet(), holder.moderatorChannelIds)
    }

    @Test
    fun clearResetsWorkspaceIdentityState() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha),
                selectedChannelId = "1",
                pinnedChannelIds = listOf("1"),
                moderatorChannelIds = setOf("1"),
            ),
        )

        holder.clear()

        assertEquals(emptyList(), holder.channels)
        assertNull(holder.selectedChannelId)
        assertEquals(emptyList(), holder.pinnedChannelIds)
        assertEquals(emptySet(), holder.moderatorChannelIds)
    }

    @Test
    fun selectingUnknownChannelIsRejected() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(channels = listOf(alpha)),
        )

        assertFailsWith<IllegalArgumentException> {
            holder.selectChannel("missing")
        }
    }

    @Test
    fun invalidChannelIdentityIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceRuntimeStateHolder().replaceChannels(
                listOf(alpha.copy(id = " ")),
            )
        }
    }
}
