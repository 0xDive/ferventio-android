package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.account_action_failed
import io.ferventio.shared.generated.resources.account_authorization_summary
import io.ferventio.shared.generated.resources.account_cancel
import io.ferventio.shared.generated.resources.account_channels_summary
import io.ferventio.shared.generated.resources.account_confirm_revoke_all
import io.ferventio.shared.generated.resources.account_confirm_revoke_all_summary
import io.ferventio.shared.generated.resources.account_confirm_revoke_device
import io.ferventio.shared.generated.resources.account_confirm_revoke_device_summary
import io.ferventio.shared.generated.resources.account_reauthorize
import io.ferventio.shared.generated.resources.account_reauthorizing
import io.ferventio.shared.generated.resources.account_revoke_all
import io.ferventio.shared.generated.resources.account_revoke_all_summary
import io.ferventio.shared.generated.resources.account_revoke_device
import io.ferventio.shared.generated.resources.account_revoke_device_summary
import io.ferventio.shared.generated.resources.account_revoking_all
import io.ferventio.shared.generated.resources.account_revoking_device
import io.ferventio.shared.generated.resources.account_session_title
import io.ferventio.shared.generated.resources.account_sign_out
import io.ferventio.shared.generated.resources.account_twitch_title
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioAccountSettingsPage() {
    val runtime = LocalFerventioRuntimeState.current
    val actions = LocalFerventioAccountActions.current
    val authentication = runtime.authentication.state.authentication
    val session = authentication?.accessLease?.session
    val profile = rememberAccountTwitchProfile()
    val mutation = runtime.account.state
    var confirmRevokeDevice by rememberSaveable { mutableStateOf(false) }
    var confirmRevokeAll by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AccountSection(title = stringResource(Res.string.account_twitch_title)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    profile?.profileImageUrl?.takeIf(String::isNotBlank)?.let { imageUrl ->
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.extraLarge),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = profile?.displayName
                                ?.takeIf(String::isNotBlank)
                                ?: session?.login?.takeIf(String::isNotBlank)
                                ?: "—",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        session?.login?.takeIf(String::isNotBlank)?.let { login ->
                            Text(
                                text = "@$login",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        session?.userId?.takeIf(String::isNotBlank)?.let { userId ->
                            Text(
                                text = userId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(
                        Res.string.account_channels_summary,
                        runtime.workspace.channels.size,
                        runtime.workspace.moderatorChannelIds.count { moderatorId ->
                            runtime.workspace.channels.any { it.id == moderatorId }
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.account_authorization_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        val reauthorize = actions.onReauthorize
                        if (reauthorize != null && runtime.account.beginReauthorization()) {
                            reauthorize()
                        }
                    },
                    enabled = !mutation.mutationInFlight && actions.onReauthorize != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (mutation.isReauthorizing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(
                            if (mutation.isReauthorizing) {
                                Res.string.account_reauthorizing
                            } else {
                                Res.string.account_reauthorize
                            },
                        ),
                    )
                }
                OutlinedButton(
                    onClick = actions.onSignOut,
                    enabled = !mutation.mutationInFlight,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.account_sign_out))
                }
            }
        }

        AccountSection(title = stringResource(Res.string.account_session_title)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { confirmRevokeDevice = true },
                    enabled = !mutation.mutationInFlight && actions.onRevokeDevice != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (mutation.isRevokingDevice) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(
                            if (mutation.isRevokingDevice) {
                                Res.string.account_revoking_device
                            } else {
                                Res.string.account_revoke_device
                            },
                        ),
                    )
                }
                Text(
                    text = stringResource(Res.string.account_revoke_device_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedButton(
                    onClick = { confirmRevokeAll = true },
                    enabled = !mutation.mutationInFlight && actions.onRevokeAllSessions != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (mutation.isRevokingAllSessions) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(
                            if (mutation.isRevokingAllSessions) {
                                Res.string.account_revoking_all
                            } else {
                                Res.string.account_revoke_all
                            },
                        ),
                    )
                }
                Text(
                    text = stringResource(Res.string.account_revoke_all_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        mutation.errorMessage?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.account_action_failed),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    TextButton(onClick = runtime.account::clearError) {
                        Text("×")
                    }
                }
            }
        }
    }

    if (confirmRevokeDevice) {
        AlertDialog(
            onDismissRequest = { if (!mutation.mutationInFlight) confirmRevokeDevice = false },
            title = { Text(stringResource(Res.string.account_confirm_revoke_device)) },
            text = { Text(stringResource(Res.string.account_confirm_revoke_device_summary)) },
            confirmButton = {
                TextButton(
                    enabled = !mutation.mutationInFlight,
                    onClick = {
                        confirmRevokeDevice = false
                        val revokeDevice = actions.onRevokeDevice
                        if (revokeDevice != null && runtime.account.beginRevokeDevice()) {
                            revokeDevice()
                        }
                    },
                ) { Text(stringResource(Res.string.account_revoke_device)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutation.mutationInFlight,
                    onClick = { confirmRevokeDevice = false },
                ) { Text(stringResource(Res.string.account_cancel)) }
            },
        )
    }

    if (confirmRevokeAll) {
        AlertDialog(
            onDismissRequest = { if (!mutation.mutationInFlight) confirmRevokeAll = false },
            title = { Text(stringResource(Res.string.account_confirm_revoke_all)) },
            text = { Text(stringResource(Res.string.account_confirm_revoke_all_summary)) },
            confirmButton = {
                TextButton(
                    enabled = !mutation.mutationInFlight,
                    onClick = {
                        confirmRevokeAll = false
                        val revokeAllSessions = actions.onRevokeAllSessions
                        if (revokeAllSessions != null && runtime.account.beginRevokeAllSessions()) {
                            revokeAllSessions()
                        }
                    },
                ) { Text(stringResource(Res.string.account_revoke_all)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutation.mutationInFlight,
                    onClick = { confirmRevokeAll = false },
                ) { Text(stringResource(Res.string.account_cancel)) }
            },
        )
    }
}

@Composable
private fun AccountSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
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
            content()
        }
    }
}
