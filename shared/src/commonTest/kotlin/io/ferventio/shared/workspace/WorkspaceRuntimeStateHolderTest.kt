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
                pushContextRevision = 7L,
            ),
        )

        assertEquals(listOf("1", "2"), holder.channelIds)
        assertEquals("2", holder.selectedChannelId)
        assertEquals(listOf("2", "1"), holder.pinnedChannelIds)
        assertEquals(setOf("2"), holder.moderatorChannelIds)
        assertEquals(7L, holder.pushContextRevision)
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
        val revision = holder.pushContextRevision

        holder.addOrReplaceChannel(
            alpha.copy(displayName = "Alpha Live"),
        )

        assertEquals(listOf("1", "2"), holder.channelIds)
        assertEquals("Alpha Live", holder.channels.first().displayName)
        assertEquals(revision, holder.pushContextRevision)
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
    fun pushContextRevisionChangesOnlyForBackendRelevantWorkspaceChanges() {
        val holder = WorkspaceRuntimeStateHolder()
        assertEquals(0L, holder.pushContextRevision)

        holder.replaceChannels(listOf(alpha, beta))
        val membershipRevision = holder.pushContextRevision
        assertEquals(1L, membershipRevision)

        holder.selectChannel("2")
        holder.moveChannel("2", 0)
        holder.updatePinnedChannelIds(listOf("2"))
        holder.addOrReplaceChannel(beta.copy(displayName = "Beta Live"))
        assertEquals(membershipRevision, holder.pushContextRevision)

        holder.updateModeratorChannelIds(listOf("2"))
        val moderatorRevision = holder.pushContextRevision
        assertEquals(membershipRevision + 1L, moderatorRevision)

        holder.updateModeratorChannelIds(listOf(" 2 ", "2"))
        assertEquals(moderatorRevision, holder.pushContextRevision)

        holder.addOrReplaceChannel(gamma)
        assertEquals(moderatorRevision + 1L, holder.pushContextRevision)

        holder.removeChannel("1")
        assertEquals(moderatorRevision + 2L, holder.pushContextRevision)
    }

    @Test
    fun replacingSameMembershipInDifferentOrderDoesNotBumpPushRevision() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.replaceChannels(listOf(alpha, beta, gamma))
        val revision = holder.pushContextRevision

        holder.replaceChannels(listOf(gamma, beta, alpha))

        assertEquals(listOf("3", "2", "1"), holder.channelIds)
        assertEquals(revision, holder.pushContextRevision)
    }

    @Test
    fun clearResetsWorkspaceIdentityStateAndInvalidatesPushContext() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha),
                selectedChannelId = "1",
                pinnedChannelIds = listOf("1"),
                moderatorChannelIds = setOf("1"),
            ),
        )
        val revision = holder.pushContextRevision

        holder.clear()

        assertEquals(emptyList(), holder.channels)
        assertNull(holder.selectedChannelId)
        assertEquals(emptyList(), holder.pinnedChannelIds)
        assertEquals(emptySet(), holder.moderatorChannelIds)
        assertEquals(revision + 1L, holder.pushContextRevision)
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
