package io.ferventio.shared.settings

import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.SavedMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnonymousSavedFiltersCoordinatorTest {
    @Test
    fun restoreReadsAndroidCompatibleMessageFilterJson() {
        val stored = listOf(filter("one", "Long", "message.length > 80"))
        val store = RecordingStore(MessageFilterCodec.encode(stored))
        val state = SharedSavedFiltersStateHolder()

        AnonymousSavedFiltersCoordinator(store).restore(state)

        assertEquals(stored, state.filters)
    }

    @Test
    fun mutationsPersistBeforePublishingNextState() {
        val store = RecordingStore()
        val state = SharedSavedFiltersStateHolder()
        val coordinator = AnonymousSavedFiltersCoordinator(store)

        coordinator.upsert(filter("one", "Long", "message.length > 80"), state)
        coordinator.upsert(filter("two", "Hello", "message.content contains \"hello\""), state)
        coordinator.delete("one", state)

        assertEquals(listOf("two"), state.filters.map(SavedMessageFilter::id))
        assertEquals(
            listOf("two"),
            MessageFilterCodec.decode(store.raw).getOrThrow().map(SavedMessageFilter::id),
        )
    }

    @Test
    fun failedDurableWriteLeavesPublishedStateUnchanged() {
        val original = filter("one", "Long", "message.length > 80")
        val store = RecordingStore(MessageFilterCodec.encode(listOf(original)))
        val state = SharedSavedFiltersStateHolder(SharedSavedFiltersSnapshot(listOf(original)))
        val coordinator = AnonymousSavedFiltersCoordinator(store)
        store.failOnSave = true

        assertFailsWith<IllegalStateException> {
            coordinator.upsert(filter("two", "Hello", "message.content contains \"hello\""), state)
        }

        assertEquals(listOf(original), state.filters)
        assertEquals(SharedSettingsSaveStatus.FAILED, state.saveStatus)
    }

    @Test
    fun importUsesExistingMergeSemanticsAndPersistsResult() {
        val existing = filter("same", "Existing", "message.length > 10")
        val imported = filter("same", "Imported", "message.length > 20")
        val store = RecordingStore(MessageFilterCodec.encode(listOf(existing)))
        val state = SharedSavedFiltersStateHolder(SharedSavedFiltersSnapshot(listOf(existing)))
        val coordinator = AnonymousSavedFiltersCoordinator(store)

        coordinator.importAndMerge(MessageFilterCodec.encode(listOf(imported)), state)

        assertEquals(2, state.filters.size)
        assertEquals(2, MessageFilterCodec.decode(store.raw).getOrThrow().size)
    }

    private fun filter(id: String, name: String, expression: String) = SavedMessageFilter(
        id = id,
        name = name,
        expression = expression,
    )

    private class RecordingStore(
        var raw: String? = null,
    ) : AnonymousSavedFiltersStore {
        var failOnSave = false

        override fun load(): String? = raw

        override fun save(raw: String) {
            if (failOnSave) error("disk full")
            this.raw = raw
        }
    }
}
