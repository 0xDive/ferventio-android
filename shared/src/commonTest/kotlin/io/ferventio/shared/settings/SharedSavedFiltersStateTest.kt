package io.ferventio.shared.settings

import io.ferventio.app.domain.MAX_SAVED_FILTERS
import io.ferventio.app.domain.SavedMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SharedSavedFiltersStateTest {
    @Test
    fun upsertNormalizesAndReplacesById() {
        val state = SharedSavedFiltersStateHolder()

        val inserted = state.upsert(
            SavedMessageFilter(
                id = " filter-1 ",
                name = "  Moderator messages  ",
                expression = "  badge.mod == true  ",
            ),
        )
        assertEquals("filter-1", inserted.id)
        assertEquals("Moderator messages", inserted.name)
        assertEquals("badge.mod == true", inserted.expression)

        state.upsert(
            SavedMessageFilter(
                id = "filter-1",
                name = "Updated",
                expression = "badge.vip == true",
            ),
        )

        assertEquals(1, state.filters.size)
        assertEquals("Updated", state.filters.single().name)
        assertEquals("badge.vip == true", state.filters.single().expression)
    }

    @Test
    fun invalidFiltersAndOverflowAreRejected() {
        val state = SharedSavedFiltersStateHolder(
            SharedSavedFiltersSnapshot(
                filters = List(MAX_SAVED_FILTERS) { index ->
                    SavedMessageFilter(
                        id = "filter-$index",
                        name = "Filter $index",
                        expression = "text contains \"$index\"",
                    )
                },
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            state.upsert(
                SavedMessageFilter(
                    id = "invalid",
                    name = " ",
                    expression = "text contains \"x\"",
                ),
            )
        }
        assertFailsWith<IllegalStateException> {
            state.upsert(
                SavedMessageFilter(
                    id = "overflow",
                    name = "Overflow",
                    expression = "text contains \"overflow\"",
                ),
            )
        }
    }

    @Test
    fun saveStateAndDeleteFollowSharedSettingsSemantics() {
        val state = SharedSavedFiltersStateHolder(
            SharedSavedFiltersSnapshot(
                filters = listOf(
                    SavedMessageFilter("filter-1", "First", "text contains \"one\""),
                ),
            ),
        )

        state.markSaveStarted()
        assertEquals(SharedSettingsSaveStatus.SAVING, state.saveStatus)

        state.markSaveFailed(" backend error ")
        assertEquals(SharedSettingsSaveStatus.FAILED, state.saveStatus)
        assertEquals("backend error", state.saveErrorMessage)

        state.delete("filter-1")
        assertEquals(emptyList(), state.filters)
        assertEquals(null, state.saveErrorMessage)

        state.markSaveSucceeded(
            SharedSavedFiltersSnapshot(
                filters = listOf(
                    SavedMessageFilter("filter-2", "Second", "text contains \"two\""),
                ),
            ),
        )
        assertEquals(SharedSettingsSaveStatus.IDLE, state.saveStatus)
        assertEquals("filter-2", state.filters.single().id)
    }
}
