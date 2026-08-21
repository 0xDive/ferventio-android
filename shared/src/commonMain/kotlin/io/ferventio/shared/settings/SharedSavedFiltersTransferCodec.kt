package io.ferventio.shared.settings

import io.ferventio.app.domain.FilterDiagnosticSeverity
import io.ferventio.app.domain.MessageFilterCodec
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.SavedMessageFilter

/** Android-compatible import/export contract for saved message filters. */
object SharedSavedFiltersTransferCodec {
    fun export(filters: List<SavedMessageFilter>): String = MessageFilterCodec.encode(filters)

    fun importAndMerge(
        raw: String,
        existing: List<SavedMessageFilter>,
    ): SharedSavedFiltersSnapshot {
        val imported = MessageFilterCodec.decode(raw).getOrElse { error ->
            throw IllegalArgumentException("Invalid saved filters JSON", error)
        }
        require(imported.isNotEmpty()) { "Saved filters import is empty" }
        imported.forEach { filter ->
            val error = MessageFilterLanguage.compile(filter.expression).diagnostics
                .firstOrNull { it.severity == FilterDiagnosticSeverity.ERROR }
            require(error == null) {
                "Filter '${filter.name}': ${error?.message.orEmpty()}"
            }
        }
        val merged = MessageFilterCodec.merge(existing, imported)
        return SharedSavedFiltersStateHolder(
            SharedSavedFiltersSnapshot(filters = merged),
        ).snapshot
    }
}
