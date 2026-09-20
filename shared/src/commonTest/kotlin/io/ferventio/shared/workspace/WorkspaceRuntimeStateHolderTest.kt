package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.WorkspaceLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun channelIdentityProjectionReusesStableInstancesUntilIdsChange() {
        val holder = WorkspaceRuntimeStateHolder()
        holder.replaceChannels(listOf(alpha, beta))
        val ids = holder.channelIds
        val idSet = holder.channelIdSet

        assertTrue(ids === holder.channelIds)
        assertTrue(idSet === holder.channelIdSet)

        holder.addOrReplaceChannel(alpha.copy(displayName = "Alpha Live"))

        assertTrue(ids === holder.channelIds)
        assertTrue(idSet === holder.channelIdSet)

        holder.moveChannel("2", 0)

        assertFalse(ids === holder.channelIds)
        assertFalse(idSet === holder.channelIdSet)
        assertEquals(listOf("2", "1"), holder.channelIds)
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
    fun remappingChannelIdPreservesWorkspacePresentationMembership() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha, beta),
                selectedChannelId = "1",
                pinnedChannelIds = listOf("1"),
                channelTabTitles = mapOf("1" to "Alpha tab"),
                moderatorChannelIds = setOf("1"),
                workspaceLayout = WorkspaceLayout.default("1"),
            ),
        )
        val revision = holder.pushContextRevision

        assertTrue(holder.remapChannelId("1", "101"))

        assertEquals(listOf("101", "2"), holder.channelIds)
        assertEquals("101", holder.selectedChannelId)
        assertEquals(listOf("101"), holder.pinnedChannelIds)
        assertEquals(mapOf("101" to "Alpha tab"), holder.channelTabTitles)
        assertEquals(setOf("101"), holder.moderatorChannelIds)
        assertEquals("101", holder.workspaceLayout.activeTab?.activeSplit?.channelId)
        assertEquals(revision + 1L, holder.pushContextRevision)
        assertFalse(holder.remapChannelId("missing", "102"))
    }

    @Test
    fun repeatedTabTitleUpdateReusesPresentationMap() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha, beta),
                channelTabTitles = mapOf("1" to "Alpha tab"),
            ),
        )
        val before = holder.channelTabTitles

        holder.setChannelTabTitle("1", " Alpha tab ")

        assertTrue(holder.channelTabTitles === before)
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
    fun loadStateGatesPushRegistrationWithoutChangingPushContextRevision() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(channels = listOf(alpha)),
        )
        val revision = holder.pushContextRevision

        assertEquals(WorkspaceLoadStatus.IDLE, holder.loadStatus)
        assertFalse(holder.isReadyForPushRegistration)

        holder.markLoadStarted()
        assertEquals(WorkspaceLoadStatus.LOADING, holder.loadStatus)
        assertFalse(holder.isReadyForPushRegistration)
        assertNull(holder.loadErrorMessage)

        holder.markLoadFailed(" temporary failure ")
        assertEquals(WorkspaceLoadStatus.FAILED, holder.loadStatus)
        assertEquals("temporary failure", holder.loadErrorMessage)
        assertFalse(holder.isReadyForPushRegistration)
        assertEquals(listOf("1"), holder.channelIds)
        assertEquals(revision, holder.pushContextRevision)

        holder.markLoadStarted()
        holder.markLoadReady(settingsRevision = 42L)
        assertEquals(WorkspaceLoadStatus.READY, holder.loadStatus)
        assertEquals(42L, holder.settingsRevision)
        assertTrue(holder.isReadyForPushRegistration)
        assertNull(holder.loadErrorMessage)
        assertEquals(revision, holder.pushContextRevision)
    }

    @Test
    fun clearResetsWorkspaceIdentityAndLoadStateAndInvalidatesPushContext() {
        val holder = WorkspaceRuntimeStateHolder(
            WorkspaceRuntimeSnapshot(
                channels = listOf(alpha),
                selectedChannelId = "1",
                pinnedChannelIds = listOf("1"),
                moderatorChannelIds = setOf("1"),
            ),
        )
        holder.markLoadReady(settingsRevision = 9L)
        val revision = holder.pushContextRevision

        holder.clear()

        assertEquals(emptyList(), holder.channels)
        assertNull(holder.selectedChannelId)
        assertEquals(emptyList(), holder.pinnedChannelIds)
        assertEquals(emptySet(), holder.moderatorChannelIds)
        assertEquals(WorkspaceLoadStatus.IDLE, holder.loadStatus)
        assertEquals(0L, holder.settingsRevision)
        assertNull(holder.loadErrorMessage)
        assertFalse(holder.isReadyForPushRegistration)
        assertEquals(revision + 1L, holder.pushContextRevision)
    }

    @Test
    fun negativeSettingsRevisionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceRuntimeStateHolder().markLoadReady(settingsRevision = -1L)
        }
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
