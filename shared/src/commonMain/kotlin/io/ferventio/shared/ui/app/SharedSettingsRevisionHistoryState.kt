package io.ferventio.shared.ui.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.shared.workspace.WorkspaceSettingsHistoryEntry

enum class SharedSettingsRevisionHistoryStatus {
    IDLE,
    LOADING,
    RESTORING,
    FAILED,
}

class SharedSettingsRevisionHistoryStateHolder {
    var status by mutableStateOf(SharedSettingsRevisionHistoryStatus.IDLE)
        private set

    var entries by mutableStateOf<List<WorkspaceSettingsHistoryEntry>>(emptyList())
        private set

    var restoringRevision by mutableStateOf<Long?>(null)
        private set

    var lastRestoredRevision by mutableStateOf<Long?>(null)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun markLoading() {
        status = SharedSettingsRevisionHistoryStatus.LOADING
        restoringRevision = null
        errorMessage = null
    }

    fun markLoaded(entries: List<WorkspaceSettingsHistoryEntry>) {
        status = SharedSettingsRevisionHistoryStatus.IDLE
        this.entries = entries
            .distinctBy(WorkspaceSettingsHistoryEntry::revision)
            .sortedByDescending(WorkspaceSettingsHistoryEntry::revision)
        restoringRevision = null
        errorMessage = null
    }

    fun markRestoring(revision: Long) {
        require(revision > 0L) { "Settings revision must be positive" }
        status = SharedSettingsRevisionHistoryStatus.RESTORING
        restoringRevision = revision
        errorMessage = null
    }

    fun markRestored(revision: Long) {
        require(revision > 0L) { "Settings revision must be positive" }
        status = SharedSettingsRevisionHistoryStatus.IDLE
        restoringRevision = null
        lastRestoredRevision = revision
        errorMessage = null
    }

    fun markFailed(message: String?) {
        status = SharedSettingsRevisionHistoryStatus.FAILED
        restoringRevision = null
        errorMessage = message?.trim()?.takeIf(String::isNotEmpty)
            ?: "Settings revision history operation failed"
    }

    fun clearError() {
        if (status == SharedSettingsRevisionHistoryStatus.FAILED) {
            status = SharedSettingsRevisionHistoryStatus.IDLE
            errorMessage = null
        }
    }
}
