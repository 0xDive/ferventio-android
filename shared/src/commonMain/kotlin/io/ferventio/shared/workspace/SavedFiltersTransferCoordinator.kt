package io.ferventio.shared.workspace

import io.ferventio.app.domain.MobileDeviceIdentity
import io.ferventio.app.domain.StoredAuthentication
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ferventio.shared.settings.SharedSavedFiltersTransferCodec
import kotlin.Throws

/** Imports Android-compatible saved-filter JSON through optimistic settings sync. */
class SavedFiltersTransferCoordinator(
    private val settings: WorkspaceSettingsSyncClient = WorkspaceSettingsSyncClient(),
) {
    constructor() : this(WorkspaceSettingsSyncClient())

    @Throws(Exception::class)
    suspend fun importFilters(
        identity: MobileDeviceIdentity,
        authentication: StoredAuthentication,
        raw: String,
        filtersState: SharedSavedFiltersStateHolder,
    ): WorkspaceSettingsSnapshot {
        filtersState.markSaveStarted()
        return try {
            settings.updateSavedFilters(identity, authentication) { remote ->
                SharedSavedFiltersTransferCodec.importAndMerge(
                    raw = raw,
                    existing = remote.filters,
                )
            }.also { snapshot ->
                filtersState.markSaveSucceeded(snapshot.savedFilters)
            }
        } catch (error: Throwable) {
            filtersState.markSaveFailed(error.message)
            throw error
        }
    }
}
