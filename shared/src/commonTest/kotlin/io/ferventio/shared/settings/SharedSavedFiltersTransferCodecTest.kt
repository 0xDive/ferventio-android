package io.ferventio.shared.settings

import io.ferventio.app.domain.SavedMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedSavedFiltersTransferCodecTest {
    @Test
    fun exportRoundTripsAndroidCompatibleJson() {
        val filters = listOf(
            SavedMessageFilter(
                id = "filter-1",
                name = "Urgent",
                expression = "message.content contains \"urgent\"",
            ),
        )

        val raw = SharedSavedFiltersTransferCodec.export(filters)
        val imported = SharedSavedFiltersTransferCodec.importAndMerge(raw, emptyList())

        assertEquals(filters, imported.filters)
    }

    @Test
    fun importMergesWithExistingFilters() {
        val existing = listOf(
            SavedMessageFilter(
                id = "existing",
                name = "Existing",
                expression = "message.length > 80",
            ),
        )
        val raw = SharedSavedFiltersTransferCodec.export(
            listOf(
                SavedMessageFilter(
                    id = "imported",
                    name = "Imported",
                    expression = "author.badges contains [\"moderator\"]",
                ),
            ),
        )

        val result = SharedSavedFiltersTransferCodec.importAndMerge(raw, existing)

        assertEquals(listOf("existing", "imported"), result.filters.map { it.id })
    }

    @Test
    fun invalidExpressionRejectsWholeImport() {
        val raw = SharedSavedFiltersTransferCodec.export(
            listOf(
                SavedMessageFilter(
                    id = "invalid",
                    name = "Invalid",
                    expression = "message.length >",
                ),
            ),
        )

        val error = assertFailsWith<IllegalArgumentException> {
            SharedSavedFiltersTransferCodec.importAndMerge(raw, emptyList())
        }

        assertTrue(error.message.orEmpty().contains("Invalid"))
    }

    @Test
    fun emptyImportIsRejected() {
        val raw = SharedSavedFiltersTransferCodec.export(emptyList())

        assertFailsWith<IllegalArgumentException> {
            SharedSavedFiltersTransferCodec.importAndMerge(raw, emptyList())
        }
    }
}
