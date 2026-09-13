package io.ferventio.shared.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AnonymousWorkspaceCoordinatorTest {
    @Test
    fun restoreNormalizesLegacyLoginStorageAndSelectsStoredChannel() {
        val store = RecordingStore(
            AnonymousWorkspaceSnapshot(
                channelLogins = listOf(" #Alpha ", "beta", "ALPHA", "bad login"),
                selectedChannelLogin = "#BETA",
                pinnedChannelLogins = listOf("BETA", "missing", "beta"),
                channelTitlesByLogin = mapOf(
                    "ALPHA" to " Alpha tab ",
                    "missing" to "Missing",
                ),
            ),
        )
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)

        val restored = coordinator.restore(state)

        assertEquals(listOf("alpha", "beta"), restored.channelLogins)
        assertEquals("beta", restored.selectedChannelLogin)
        assertEquals(listOf("beta"), restored.pinnedChannelLogins)
        assertEquals(mapOf("alpha" to "Alpha tab"), restored.channelTitlesByLogin)
        assertEquals(listOf("anonymous:alpha", "anonymous:beta"), state.channelIds)
        assertEquals("anonymous:beta", state.selectedChannelId)
        assertEquals(listOf("anonymous:beta"), state.pinnedChannelIds)
        assertEquals(
            mapOf("anonymous:alpha" to "Alpha tab"),
            state.channelTabTitles,
        )
        assertEquals(WorkspaceLoadStatus.READY, state.loadStatus)
        assertEquals(restored, store.snapshot)
    }

    @Test
    fun localMutationsPersistLoginOrderSelectionPinsAndTitles() {
        val store = RecordingStore()
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)
        coordinator.restore(state)

        coordinator.addChannel("Alpha", state)
        coordinator.addChannel("#beta", state)
        coordinator.addChannel("ALPHA", state)

        assertEquals(listOf("alpha", "beta"), store.snapshot.channelLogins)
        assertEquals("alpha", store.snapshot.selectedChannelLogin)

        coordinator.moveChannel("anonymous:beta", 0, state)
        assertEquals(listOf("beta", "alpha"), store.snapshot.channelLogins)

        coordinator.selectChannel("anonymous:beta", state)
        coordinator.setChannelPinned("anonymous:beta", true, state)
        coordinator.renameChannel("anonymous:beta", " Beta local ", state)
        assertEquals("beta", store.snapshot.selectedChannelLogin)
        assertEquals(listOf("beta"), store.snapshot.pinnedChannelLogins)
        assertEquals(mapOf("beta" to "Beta local"), store.snapshot.channelTitlesByLogin)

        coordinator.removeChannel("anonymous:beta", state)
        assertEquals(listOf("alpha"), store.snapshot.channelLogins)
        assertEquals("alpha", store.snapshot.selectedChannelLogin)
        assertEquals(emptyList(), store.snapshot.pinnedChannelLogins)
        assertEquals(emptyMap(), store.snapshot.channelTitlesByLogin)
        assertFalse(state.mutationInFlight)
    }

    @Test
    fun roomResolutionRemapsRuntimeIdentityWithoutChangingLoginBasedMetadata() {
        val store = RecordingStore(
            AnonymousWorkspaceSnapshot(
                channelLogins = listOf("alpha"),
                selectedChannelLogin = "alpha",
                pinnedChannelLogins = listOf("alpha"),
                channelTitlesByLogin = mapOf("alpha" to "Alpha tab"),
            ),
        )
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)
        coordinator.restore(state)

        assertTrue(coordinator.onRoomResolved("alpha", "1234", state))

        assertEquals(listOf("1234"), state.channelIds)
        assertEquals("1234", state.selectedChannelId)
        assertEquals(listOf("1234"), state.pinnedChannelIds)
        assertEquals(mapOf("1234" to "Alpha tab"), state.channelTabTitles)
        assertEquals("1234", state.workspaceLayout.activeTab?.activeSplit?.channelId)
        assertEquals(
            AnonymousWorkspaceSnapshot(
                channelLogins = listOf("alpha"),
                selectedChannelLogin = "alpha",
                pinnedChannelLogins = listOf("alpha"),
                channelTitlesByLogin = mapOf("alpha" to "Alpha tab"),
            ),
            store.snapshot,
        )
    }

    @Test
    fun layoutPersistsWithStableLoginIdsAcrossRoomResolution() {
        val store = RecordingStore()
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)
        coordinator.restore(state)
        coordinator.addChannel("alpha", state)
        coordinator.addChannel("beta", state)

        val firstSplitId = assertNotNull(state.workspaceLayout.activeTab?.activeSplit?.id)
        coordinator.addSplit(state)
        val secondSplitId = assertNotNull(state.workspaceLayout.activeTab?.activeSplit?.id)
        coordinator.setSplitChannel(secondSplitId, "anonymous:beta", state)
        coordinator.setSplitFilterQuery(
            secondSplitId,
            "message.content contains \"hello\"",
            state,
        )

        assertTrue(coordinator.onRoomResolved("alpha", "111", state))
        assertTrue(coordinator.onRoomResolved("beta", "222", state))
        coordinator.setPrimaryFraction(0.65f, state)

        val persistedLayout = assertNotNull(store.snapshot.workspaceLayoutJson)
        assertTrue("anonymous:alpha" in persistedLayout)
        assertTrue("anonymous:beta" in persistedLayout)
        assertFalse("\"111\"" in persistedLayout)
        assertFalse("\"222\"" in persistedLayout)

        val restoredState = WorkspaceRuntimeStateHolder()
        coordinator.restore(restoredState)
        val restoredTab = assertNotNull(restoredState.workspaceLayout.activeTab)
        assertEquals(2, restoredTab.splits.size)
        assertEquals(firstSplitId, restoredTab.splits.first().id)
        val restoredSecond = restoredTab.splits.first { split -> split.id == secondSplitId }
        assertEquals("anonymous:beta", restoredSecond.channelId)
        assertEquals("message.content contains \"hello\"", restoredSecond.filterQuery)
        assertEquals(0.65f, restoredTab.primaryFraction)
    }

    @Test
    fun invalidLoginDoesNotMutatePersistedWorkspace() {
        val store = RecordingStore()
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)
        coordinator.restore(state)

        assertFailsWith<AnonymousWorkspaceMutationException> {
            coordinator.addChannel("not a valid login", state)
        }

        assertEquals(AnonymousWorkspaceSnapshot(), store.snapshot)
        assertFalse(state.mutationInFlight)
        assertTrue(state.mutationErrorMessage?.isNotBlank() == true)
    }

    private class RecordingStore(
        var snapshot: AnonymousWorkspaceSnapshot = AnonymousWorkspaceSnapshot(),
    ) : AnonymousWorkspaceStore {
        override fun load(): AnonymousWorkspaceSnapshot = snapshot

        override fun save(snapshot: AnonymousWorkspaceSnapshot) {
            this.snapshot = snapshot
        }
    }
}
