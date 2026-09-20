package io.ferventio.shared.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLocalUiPreferencesStateTest {
    @Test
    fun defaultsMatchAndroidQuickModerationPreferences() {
        val state = SharedLocalUiPreferencesStateHolder()

        assertFalse(state.preferences.showQuickBan)
        assertFalse(state.preferences.showQuickDelete)
        assertTrue(state.preferences.confirmModerationActions)
    }

    @Test
    fun updatesPersistThroughConfiguredStore() {
        val store = RecordingStore()
        val state = SharedLocalUiPreferencesStateHolder(store)

        state.setShowQuickBan(true)
        state.setShowQuickDelete(true)
        state.setConfirmModerationActions(false)

        assertEquals(
            SharedLocalUiPreferences(
                showQuickBan = true,
                showQuickDelete = true,
                confirmModerationActions = false,
            ),
            store.load(),
        )
        assertEquals(store.load(), state.preferences)
    }

    @Test
    fun identicalUpdatesDoNotWriteLocalStoreAgain() {
        val store = RecordingStore()
        val state = SharedLocalUiPreferencesStateHolder(store)

        state.setDraft("channel-1", "hello")
        val writesAfterFirstDraft = store.saveCount
        state.setDraft("channel-1", "hello")

        state.recordSentMessage("channel-1", "message")
        val writesAfterFirstMessage = store.saveCount
        state.recordSentMessage("channel-1", "message")

        assertEquals(1, writesAfterFirstDraft)
        assertEquals(2, writesAfterFirstMessage)
        assertEquals(2, store.saveCount)
    }

    @Test
    fun repeatedTopSentMessageDoesNotWriteStoreAgain() {
        val store = RecordingStore()
        val state = SharedLocalUiPreferencesStateHolder(store)

        state.recordSentMessage("channel-1", "same")
        val writes = store.saveCount
        state.recordSentMessage("channel-1", "same")

        assertEquals(writes, store.saveCount)
        assertEquals(listOf("same"), state.sentMessageHistory("channel-1"))
    }

    @Test
    fun composerDraftsAndHistoryPersistLocally() {
        val store = RecordingStore()
        val state = SharedLocalUiPreferencesStateHolder(store)

        state.setDraft("channel-1", "hello ")
        state.recordSentMessage("channel-1", "first")
        state.recordSentMessage("channel-1", "second")
        state.recordSentMessage("channel-1", "first")

        assertEquals("hello ", state.draft("channel-1"))
        assertEquals(listOf("first", "second"), state.sentMessageHistory("channel-1"))
        assertEquals(state.preferences, store.load())

        state.setDraft("channel-1", "")
        assertEquals("", state.draft("channel-1"))
        assertFalse("channel-1" in state.preferences.draftsByChannel)
    }

    private class RecordingStore : SharedLocalUiPreferencesStore {
        private var value = SharedLocalUiPreferences()
        var saveCount: Int = 0
            private set

        override fun load(): SharedLocalUiPreferences = value

        override fun save(preferences: SharedLocalUiPreferences) {
            value = preferences
            saveCount += 1
        }
    }
}
