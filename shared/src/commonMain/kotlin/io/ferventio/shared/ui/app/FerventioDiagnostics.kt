package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.chat.TwitchEventSubTransportRecoverySnapshot
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.runtime.FerventioRuntimeState
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal data class FerventioDiagnosticsSnapshot(
    val versionName: String,
    val connectionStatus: String,
    val connectionAttempt: Int,
    val authenticationRequired: Boolean,
    val workspaceLoadStatus: String,
    val workspaceChannels: Int,
    val moderatorChannels: Int,
    val settingsRevision: Long,
    val liveChannels: Int,
    val liveMessages: Int,
    val historyAvailable: Boolean,
    val eventSubTransportLimitReached: Boolean,
)

internal fun FerventioRuntimeState.diagnosticsSnapshot(versionName: String): FerventioDiagnosticsSnapshot =
    FerventioDiagnosticsSnapshot(
        versionName = versionName.trim().ifEmpty { "unknown" },
        connectionStatus = chat.connectionStatus.name,
        connectionAttempt = chat.connectionAttempt,
        authenticationRequired = chat.authenticationRequired,
        workspaceLoadStatus = workspace.loadStatus.name,
        workspaceChannels = workspace.channels.size,
        moderatorChannels = workspace.moderatorChannelIds.size,
        settingsRevision = workspace.settingsRevision,
        liveChannels = chat.messagesByChannel.values.count { messages -> messages.isNotEmpty() },
        liveMessages = chat.messagesByChannel.values.sumOf { messages -> messages.size },
        historyAvailable = history != null,
        eventSubTransportLimitReached = chat.eventSubTransportLimitReached,
    )

internal fun FerventioDiagnosticsSnapshot.toDiagnosticReport(): String = buildString {
    appendLine("Ferventio diagnostics")
    appendLine("version=$versionName")
    appendLine("eventsub.status=$connectionStatus")
    appendLine("eventsub.attempt=$connectionAttempt")
    appendLine("eventsub.authentication_required=$authenticationRequired")
    appendLine("eventsub.transport_limit=$eventSubTransportLimitReached")
    appendLine("workspace.status=$workspaceLoadStatus")
    appendLine("workspace.channels=$workspaceChannels")
    appendLine("workspace.moderator_channels=$moderatorChannels")
    appendLine("workspace.settings_revision=$settingsRevision")
    appendLine("chat.live_channels=$liveChannels")
    appendLine("chat.live_messages=$liveMessages")
    append("history.available=$historyAvailable")
}

@Suppress("DEPRECATION")
@Composable
internal fun FerventioDiagnosticsSettingsSection(versionName: String) {
    val runtime = LocalFerventioRuntimeState.current
    val actions = currentFerventioDiagnosticsActions()
    val clipboardManager = LocalClipboardManager.current
    val snapshot = runtime.diagnosticsSnapshot(versionName)
    val report = snapshot.toDiagnosticReport()
    var copied by remember(report) { mutableStateOf(false) }
    val yes = stringResource(Res.string.diagnostics_yes)
    val no = stringResource(Res.string.diagnostics_no)

    val scope = rememberCoroutineScope()
    val authentication = runtime.authentication.state.authentication
    val authenticationUserId = authentication?.accessLease?.session?.userId
    var transportSnapshot by remember(authenticationUserId) {
        mutableStateOf<TwitchEventSubTransportRecoverySnapshot?>(null)
    }
    var transportBusy by remember(authenticationUserId) { mutableStateOf(false) }
    var transportError by remember(authenticationUserId) { mutableStateOf<String?>(null) }
    var cleanupFeedback by remember(authenticationUserId) {
        mutableStateOf<EventSubCleanupFeedback?>(null)
    }
    var confirmTransportCleanup by remember(authenticationUserId) { mutableStateOf(false) }

    fun refreshTransportSessions() {
        val currentAuthentication = runtime.authentication.state.authentication ?: return
        if (transportBusy) return
        transportBusy = true
        transportError = null
        cleanupFeedback = null
        scope.launch {
            try {
                transportSnapshot = runtime.eventSubMaintenance.load(currentAuthentication)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                transportError = error.message?.trim()?.takeIf(String::isNotEmpty)
                    ?: "Unknown EventSub maintenance error"
            } finally {
                transportBusy = false
            }
        }
    }

    fun cleanupOtherTransportSessions() {
        val currentAuthentication = runtime.authentication.state.authentication ?: return
        if (transportBusy) return
        transportBusy = true
        transportError = null
        cleanupFeedback = null
        confirmTransportCleanup = false
        scope.launch {
            try {
                val result = runtime.eventSubMaintenance.clearOtherSessions(
                    authentication = currentAuthentication,
                    protectedSessionId = runtime.chat.eventSubSessionId,
                )
                cleanupFeedback = EventSubCleanupFeedback(
                    deletedSubscriptions = result.deletedSubscriptionCount,
                    targetedSessions = result.targetedSessionIds.size,
                )
                transportSnapshot = runtime.eventSubMaintenance.load(currentAuthentication)
                actions.onReconnect?.invoke()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                transportError = error.message?.trim()?.takeIf(String::isNotEmpty)
                    ?: "Unknown EventSub maintenance error"
            } finally {
                transportBusy = false
            }
        }
    }

    val protectedSessionId = runtime.chat.eventSubSessionId
    val transportSessions = transportSnapshot?.sessions.orEmpty()
    val protectedSessionPresent = protectedSessionId != null &&
        transportSessions.any { it.sessionId == protectedSessionId }
    val cleanupTargetCount = transportSessions.count { it.sessionId != protectedSessionId }

    if (confirmTransportCleanup) {
        AlertDialog(
            onDismissRequest = {
                if (!transportBusy) confirmTransportCleanup = false
            },
            title = {
                Text(stringResource(Res.string.diagnostics_eventsub_cleanup_confirm_title))
            },
            text = {
                Text(
                    stringResource(
                        if (protectedSessionPresent) {
                            Res.string.diagnostics_eventsub_cleanup_confirm_body
                        } else {
                            Res.string.diagnostics_eventsub_cleanup_confirm_all_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !transportBusy,
                    onClick = ::cleanupOtherTransportSessions,
                ) {
                    Text(stringResource(Res.string.diagnostics_eventsub_cleanup_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !transportBusy,
                    onClick = { confirmTransportCleanup = false },
                ) {
                    Text(stringResource(Res.string.diagnostics_eventsub_cleanup_cancel))
                }
            },
        )
    }

    Column {
        Text(
            text = stringResource(Res.string.diagnostics_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(Res.string.diagnostics_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                DiagnosticGroup(title = stringResource(Res.string.diagnostics_eventsub_title)) {
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_status),
                        value = snapshot.connectionStatus,
                    )
                    runtime.chat.connectionDetail?.let { detail ->
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_connection_detail),
                            value = detail,
                        )
                    }
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_attempt),
                        value = snapshot.connectionAttempt.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_auth_required),
                        value = if (snapshot.authenticationRequired) yes else no,
                    )
                    runtime.chat.connectionErrorMessage?.let { error ->
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_last_error),
                            value = error,
                            error = true,
                        )
                    }
                    if (runtime.chat.eventSubTransportLimitReached) {
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_transport_limit),
                            value = stringResource(Res.string.diagnostics_transport_limit_reached),
                            error = true,
                        )
                    }
                    runtime.chat.eventSubSessionId?.let { sessionId ->
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_eventsub_session),
                            value = compactEventSubSessionId(sessionId),
                        )
                    }
                    transportSnapshot?.let { transport ->
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_eventsub_sessions),
                            value = transport.sessionCount.toString(),
                            error = transport.sessionCount >= 3,
                        )
                        DiagnosticRow(
                            label = stringResource(Res.string.diagnostics_eventsub_subscriptions),
                            value = transport.subscriptionCount.toString(),
                        )
                        transport.sessions.forEach { session ->
                            DiagnosticRow(
                                label = compactEventSubSessionId(session.sessionId),
                                value = stringResource(
                                    Res.string.diagnostics_eventsub_session_row,
                                    stringResource(
                                        if (session.sessionId == protectedSessionId) {
                                            Res.string.diagnostics_eventsub_session_current
                                        } else {
                                            Res.string.diagnostics_eventsub_session_other
                                        },
                                    ),
                                    session.subscriptionCount,
                                ),
                            )
                        }
                    }
                    if (transportBusy) {
                        Text(
                            text = stringResource(Res.string.diagnostics_eventsub_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    transportError?.let { error ->
                        Text(
                            text = stringResource(
                                Res.string.diagnostics_eventsub_error,
                                error,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    cleanupFeedback?.let { feedback ->
                        Text(
                            text = stringResource(
                                Res.string.diagnostics_eventsub_cleanup_result,
                                feedback.deletedSubscriptions,
                                feedback.targetedSessions,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (authentication != null) {
                    OutlinedButton(
                        enabled = !transportBusy,
                        onClick = ::refreshTransportSessions,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (transportSnapshot == null) {
                                    Res.string.diagnostics_eventsub_inspect
                                } else {
                                    Res.string.diagnostics_eventsub_refresh
                                },
                            ),
                        )
                    }
                    if (cleanupTargetCount > 0) {
                        OutlinedButton(
                            enabled = !transportBusy,
                            onClick = { confirmTransportCleanup = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(
                                    Res.string.diagnostics_eventsub_cleanup,
                                ),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                actions.onReconnect?.let { reconnect ->
                    OutlinedButton(
                        onClick = reconnect,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.diagnostics_reconnect))
                    }
                }

                DiagnosticGroup(title = stringResource(Res.string.diagnostics_runtime_title)) {
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_workspace_state),
                        value = snapshot.workspaceLoadStatus,
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_channels),
                        value = snapshot.workspaceChannels.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_moderator_channels),
                        value = snapshot.moderatorChannels.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_settings_revision),
                        value = snapshot.settingsRevision.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_live_channels),
                        value = snapshot.liveChannels.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_live_messages),
                        value = snapshot.liveMessages.toString(),
                    )
                    DiagnosticRow(
                        label = stringResource(Res.string.diagnostics_history),
                        value = if (snapshot.historyAvailable) {
                            stringResource(Res.string.diagnostics_available)
                        } else {
                            stringResource(Res.string.diagnostics_unavailable)
                        },
                    )
                }

                Text(
                    text = stringResource(Res.string.diagnostics_report_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = report,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(report))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.diagnostics_copy))
                }
                if (copied) {
                    Text(
                        text = stringResource(Res.string.diagnostics_copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    error: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1.25f),
            style = MaterialTheme.typography.bodySmall,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}


private data class EventSubCleanupFeedback(
    val deletedSubscriptions: Int,
    val targetedSessions: Int,
)

internal fun compactEventSubSessionId(value: String): String {
    val normalized = value.trim()
    if (normalized.length <= 16) return normalized
    return normalized.take(8) + "…" + normalized.takeLast(6)
}
