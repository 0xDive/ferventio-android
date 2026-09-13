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
