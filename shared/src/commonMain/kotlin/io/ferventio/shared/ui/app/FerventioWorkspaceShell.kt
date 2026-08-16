package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.workspace_channels
import io.ferventio.shared.generated.resources.workspace_chats
import io.ferventio.shared.generated.resources.workspace_load_failed
import io.ferventio.shared.generated.resources.workspace_loading
import io.ferventio.shared.generated.resources.workspace_menu
import io.ferventio.shared.generated.resources.workspace_no_channels
import io.ferventio.shared.generated.resources.workspace_no_channels_summary
import io.ferventio.shared.generated.resources.workspace_signed_in_as
import io.ferventio.shared.workspace.WorkspaceLoadStatus
import io.ferventio.shared.workspace.WorkspaceRuntimeStateHolder
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FerventioWorkspaceShell(
    state: WorkspaceRuntimeStateHolder,
    login: String?,
    modifier: Modifier = Modifier,
    content: @Composable (ChatChannel) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val selectedChannel = state.channels.firstOrNull { it.id == state.selectedChannelId }
        ?: state.channels.firstOrNull()
    val menuDescription = stringResource(Res.string.workspace_menu)

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        gesturesEnabled = state.channels.isNotEmpty(),
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(Res.string.workspace_channels),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                HorizontalDivider()
                state.channels.forEach { channel ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                text = "#${channel.displayName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        selected = channel.id == selectedChannel?.id,
                        onClick = {
                            state.selectChannel(channel.id)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    navigationIcon = {
                        TextButton(
                            onClick = { scope.launch { drawerState.open() } },
                            enabled = state.channels.isNotEmpty(),
                            modifier = Modifier.semantics {
                                contentDescription = menuDescription
                            },
                        ) {
                            Text(
                                text = "☰",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = selectedChannel?.let { "#${it.displayName}" }
                                    ?: stringResource(Res.string.workspace_chats),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            login?.takeIf(String::isNotBlank)?.let { value ->
                                Text(
                                    text = stringResource(
                                        Res.string.workspace_signed_in_as,
                                        value,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (state.loadStatus == WorkspaceLoadStatus.FAILED && state.channels.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = stringResource(Res.string.workspace_load_failed),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        state.loadStatus == WorkspaceLoadStatus.IDLE ||
                            state.loadStatus == WorkspaceLoadStatus.LOADING -> WorkspaceLoadingState()

                        state.loadStatus == WorkspaceLoadStatus.FAILED && state.channels.isEmpty() ->
                            WorkspaceFailureState()

                        selectedChannel == null -> WorkspaceEmptyState()

                        else -> content(selectedChannel)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceLoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(Res.string.workspace_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkspaceFailureState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.workspace_load_failed),
            modifier = Modifier.padding(24.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun WorkspaceEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(Res.string.workspace_no_channels),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.workspace_no_channels_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
