package io.ferventio.shared.ui.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_backup_conflict
import io.ferventio.shared.generated.resources.settings_backup_error
import io.ferventio.shared.generated.resources.settings_backup_keep_local
import io.ferventio.shared.generated.resources.settings_backup_unresolved_channels
import io.ferventio.shared.generated.resources.settings_backup_use_server
import io.ferventio.shared.generated.resources.settings_close
import io.ferventio.shared.generated.resources.settings_export_sync
import org.jetbrains.compose.resources.stringResource

enum class SharedSettingsBackupStatus {
    IDLE,
    EXPORTING,
    IMPORTING,
    RESOLVING,
    SYNCED,
    CONFLICT,
    FAILED,
}

class SharedSettingsBackupStateHolder {
    var status by mutableStateOf(SharedSettingsBackupStatus.IDLE)
        private set

    var conflictRevision by mutableStateOf<Long?>(null)
        private set

    var unresolvedLogins by mutableStateOf<List<String>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun markExporting() = update(SharedSettingsBackupStatus.EXPORTING)

    fun markImporting() = update(SharedSettingsBackupStatus.IMPORTING)

    fun markResolving() = update(SharedSettingsBackupStatus.RESOLVING)

    fun markSynced(unresolvedLogins: List<String> = emptyList()) {
        status = SharedSettingsBackupStatus.SYNCED
        conflictRevision = null
        this.unresolvedLogins = unresolvedLogins.normalizeLogins()
        errorMessage = null
    }

    fun markConflict(revision: Long, unresolvedLogins: List<String> = emptyList()) {
        require(revision > 0L) { "Settings conflict revision must be positive" }
        status = SharedSettingsBackupStatus.CONFLICT
        conflictRevision = revision
        this.unresolvedLogins = unresolvedLogins.normalizeLogins()
        errorMessage = null
    }

    fun markFailed(message: String?) {
        status = SharedSettingsBackupStatus.FAILED
        conflictRevision = null
        unresolvedLogins = emptyList()
        errorMessage = message?.trim()?.takeIf(String::isNotEmpty) ?: "Settings backup operation failed"
    }

    fun markIdle() {
        status = SharedSettingsBackupStatus.IDLE
        conflictRevision = null
        unresolvedLogins = emptyList()
        errorMessage = null
    }

    private fun update(nextStatus: SharedSettingsBackupStatus) {
        status = nextStatus
        conflictRevision = null
        unresolvedLogins = emptyList()
        errorMessage = null
    }

    private fun List<String>.normalizeLogins(): List<String> =
        map { it.trim().removePrefix("#").lowercase() }
            .filter(String::isNotEmpty)
            .distinct()
}

data class FerventioSettingsBackupActions(
    val state: SharedSettingsBackupStateHolder = SharedSettingsBackupStateHolder(),
    val onExport: (() -> Unit)? = null,
    val onImport: (() -> Unit)? = null,
    val onKeepLocal: (() -> Unit)? = null,
    val onUseServer: (() -> Unit)? = null,
) {
    val fileTransferAvailable: Boolean
        get() = onExport != null || onImport != null

    val conflictResolutionAvailable: Boolean
        get() = onKeepLocal != null && onUseServer != null
}

internal val LocalFerventioSettingsBackupActions = staticCompositionLocalOf {
    FerventioSettingsBackupActions()
}

@Composable
internal fun ProvideFerventioSettingsBackupActions(
    actions: FerventioSettingsBackupActions,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalFerventioSettingsBackupActions provides actions,
        content = content,
    )
}

@Composable
internal fun FerventioSettingsBackupOperationFeedback(actions: FerventioSettingsBackupActions) {
    val state = actions.state
    when {
        state.status == SharedSettingsBackupStatus.CONFLICT && actions.conflictResolutionAvailable -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(Res.string.settings_export_sync)) },
                text = { Text(stringResource(Res.string.settings_backup_conflict)) },
                confirmButton = {
                    TextButton(onClick = { actions.onKeepLocal?.invoke() }) {
                        Text(stringResource(Res.string.settings_backup_keep_local))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { actions.onUseServer?.invoke() }) {
                        Text(stringResource(Res.string.settings_backup_use_server))
                    }
                },
            )
        }
        state.status == SharedSettingsBackupStatus.FAILED -> {
            AlertDialog(
                onDismissRequest = state::markIdle,
                title = { Text(stringResource(Res.string.settings_export_sync)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.settings_backup_error,
                            state.errorMessage.orEmpty(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = state::markIdle) {
                        Text(stringResource(Res.string.settings_close))
                    }
                },
            )
        }
        state.status == SharedSettingsBackupStatus.SYNCED && state.unresolvedLogins.isNotEmpty() -> {
            AlertDialog(
                onDismissRequest = state::markIdle,
                title = { Text(stringResource(Res.string.settings_export_sync)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.settings_backup_unresolved_channels,
                            state.unresolvedLogins.joinToString(", ") { "#$it" },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = state::markIdle) {
                        Text(stringResource(Res.string.settings_close))
                    }
                },
            )
        }
    }
}
