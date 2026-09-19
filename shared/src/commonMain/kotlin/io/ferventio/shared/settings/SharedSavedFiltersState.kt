package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.MAX_FILTER_EXPRESSION_LENGTH
import io.ferventio.app.domain.MAX_SAVED_FILTERS
import io.ferventio.app.domain.SavedMessageFilter

class SharedSavedFiltersStateHolder(
    initial: SharedSavedFiltersSnapshot = SharedSavedFiltersSnapshot(),
) {
    var filters by mutableStateOf(normalize(initial.filters))
        private set

    var saveStatus by mutableStateOf(SharedSettingsSaveStatus.IDLE)
        private set

    var saveErrorMessage by mutableStateOf<String?>(null)
        private set

    val snapshot: SharedSavedFiltersSnapshot
        get() = SharedSavedFiltersSnapshot(filters = filters)

    fun restore(snapshot: SharedSavedFiltersSnapshot) {
        filters = normalize(snapshot.filters)
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }

    fun upsert(filter: SavedMessageFilter): SavedMessageFilter {
        val normalized = normalizeFilter(filter)
        val index = filters.indexOfFirst { it.id == normalized.id }
        if (index < 0 && filters.size >= MAX_SAVED_FILTERS) {
            throw IllegalStateException("Saved message filter limit reached")
        }
        filters = if (index < 0) {
            filters + normalized
        } else {
            filters.toMutableList().apply { this[index] = normalized }
        }
        saveErrorMessage = null
        return normalized
    }

    fun delete(filterId: String) {
        val id = requireFilterId(filterId)
        filters = filters.filterNot { it.id == id }
        saveErrorMessage = null
    }

    fun markSaveStarted() {
        saveStatus = SharedSettingsSaveStatus.SAVING
        saveErrorMessage = null
    }

    fun markSaveSucceeded(snapshot: SharedSavedFiltersSnapshot) {
        restore(snapshot)
    }

    fun markSaveFailed(message: String?) {
        saveStatus = SharedSettingsSaveStatus.FAILED
        saveErrorMessage = message?.trim()?.takeIf(String::isNotEmpty)
            ?: "Failed to save message filters"
    }

    fun clear() {
        restore(SharedSavedFiltersSnapshot())
    }

    private fun normalize(values: List<SavedMessageFilter>): List<SavedMessageFilter> = values
        .mapNotNull { value -> runCatching { normalizeFilter(value) }.getOrNull() }
        .distinctBy(SavedMessageFilter::id)
        .take(MAX_SAVED_FILTERS)

    private fun normalizeFilter(filter: SavedMessageFilter): SavedMessageFilter {
        val id = requireFilterId(filter.id)
        val name = filter.name.trim().take(MAX_FILTER_NAME_LENGTH)
        require(name.isNotEmpty()) { "Saved message filter name must not be blank" }
        val expression = filter.expression.trim().take(MAX_FILTER_EXPRESSION_LENGTH)
        require(expression.isNotEmpty()) { "Saved message filter expression must not be blank" }
        return filter.copy(id = id, name = name, expression = expression)
    }

    private fun requireFilterId(value: String): String = value.trim().takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("Saved message filter id must not be blank")

    private companion object {
        const val MAX_FILTER_NAME_LENGTH = 80
    }
}
