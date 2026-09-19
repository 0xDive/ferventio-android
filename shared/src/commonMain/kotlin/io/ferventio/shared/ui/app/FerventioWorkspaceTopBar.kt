package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.attention_open
import io.ferventio.shared.generated.resources.chat_status_connected
import io.ferventio.shared.generated.resources.chat_status_connecting
import io.ferventio.shared.generated.resources.chat_status_creating_subscriptions
import io.ferventio.shared.generated.resources.chat_status_disconnected
import io.ferventio.shared.generated.resources.chat_status_failed
import io.ferventio.shared.generated.resources.chat_status_reconnecting
import io.ferventio.shared.generated.resources.chat_status_waiting_welcome
import io.ferventio.shared.generated.resources.history_search_open
import io.ferventio.shared.generated.resources.settings_open
import io.ferventio.shared.generated.resources.workspace_menu
import io.ferventio.shared.generated.resources.workspace_more
import org.jetbrains.compose.resources.stringResource

/**
 * Compact workspace chrome shared by signed-in and anonymous iOS flows.
 *
 * Its dimensions intentionally mirror the Android workspace bar so moving between platforms does
 * not change the product hierarchy or density.
 */
@Composable
internal fun FerventioWorkspaceTopBar(
    title: String,
    connectionStatus: ConnectionStatus,
    mentionUnreadCount: Int,
    onOpenChannels: () -> Unit,
    onOpenSearch: (() -> Unit)?,
    onOpenMentions: () -> Unit,
    onOpenSettings: () -> Unit,
    accountActionLabel: String? = null,
    onAccountAction: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(46.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenChannels,
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(Res.string.workspace_menu),
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(6.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.tertiary
                            ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    ) {}
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = workspaceConnectionStatusLabel(connectionStatus),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            onOpenSearch?.let { action ->
                IconButton(
                    onClick = action,
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = stringResource(Res.string.history_search_open),
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(Res.string.workspace_more),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(Res.string.attention_open),
                                    modifier = Modifier.weight(1f),
                                )
                                if (mentionUnreadCount > 0) {
                                    Spacer(Modifier.width(8.dp))
                                    Badge {
                                        Text(mentionUnreadCount.coerceAtMost(999).toString())
                                    }
                                }
                            }
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenMentions()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.settings_open)) },
                        onClick = {
                            menuExpanded = false
                            onOpenSettings()
                        },
                    )
                    if (accountActionLabel != null && onAccountAction != null) {
                        DropdownMenuItem(
                            text = { Text(accountActionLabel) },
                            onClick = {
                                menuExpanded = false
                                onAccountAction()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun workspaceConnectionStatusLabel(status: ConnectionStatus): String = when (status) {
    ConnectionStatus.CONNECTED -> stringResource(Res.string.chat_status_connected)
    ConnectionStatus.CONNECTING -> stringResource(Res.string.chat_status_connecting)
    ConnectionStatus.WAITING_WELCOME -> stringResource(Res.string.chat_status_waiting_welcome)
    ConnectionStatus.CREATING_SUBSCRIPTIONS ->
        stringResource(Res.string.chat_status_creating_subscriptions)
    ConnectionStatus.RECONNECTING -> stringResource(Res.string.chat_status_reconnecting)
    ConnectionStatus.FAILED -> stringResource(Res.string.chat_status_failed)
    ConnectionStatus.DISCONNECTED -> stringResource(Res.string.chat_status_disconnected)
}
