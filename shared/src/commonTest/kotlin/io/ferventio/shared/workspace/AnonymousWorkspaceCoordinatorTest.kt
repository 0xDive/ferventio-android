package io.ferventio.shared.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymousWorkspaceCoordinatorTest {
    @Test
    fun restoreNormalizesLegacyLoginStorageAndSelectsStoredChannel() {
        val store = RecordingStore(
            AnonymousWorkspaceSnapshot(
                channelLogins = listOf(" #Alpha ", "beta", "ALPHA", "bad login"),
                selectedChannelLogin = "#BETA",
            ),
        )
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)

        val restored = coordinator.restore(state)

        assertEquals(listOf("alpha", "beta"), restored.channelLogins)
        assertEquals("beta", restored.selectedChannelLogin)
        assertEquals(listOf("anonymous:alpha", "anonymous:beta"), state.channelIds)
        assertEquals("anonymous:beta", state.selectedChannelId)
        assertEquals(WorkspaceLoadStatus.READY, state.loadStatus)
        assertEquals(restored, store.snapshot)
    }

    @Test
    fun localMutationsPersistLoginOrderAndSelection() {
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
        assertEquals("beta", store.snapshot.selectedChannelLogin)

        coordinator.removeChannel("anonymous:beta", state)
        assertEquals(listOf("alpha"), store.snapshot.channelLogins)
        assertEquals("alpha", store.snapshot.selectedChannelLogin)
        assertFalse(state.mutationInFlight)
    }

    @Test
    fun roomResolutionRemapsRuntimeIdentityWithoutPersistingRoomId() {
        val store = RecordingStore(
            AnonymousWorkspaceSnapshot(
                channelLogins = listOf("alpha"),
                selectedChannelLogin = "alpha",
            ),
        )
        val state = WorkspaceRuntimeStateHolder()
        val coordinator = AnonymousWorkspaceCoordinator(store)
        coordinator.restore(state)
        state.updatePinnedChannelIds(listOf("anonymous:alpha"))
        state.updateChannelTabTitles(mapOf("anonymous:alpha" to "Alpha tab"))

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
            ),
            store.snapshot,
        )
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
