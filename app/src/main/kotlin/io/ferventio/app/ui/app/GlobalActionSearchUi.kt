package io.ferventio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import io.ferventio.app.R
import io.ferventio.app.domain.ActionSearchIndex
import io.ferventio.app.domain.CommandRegistry
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.SearchableAction
import io.ferventio.app.domain.SearchableActionFactory
import io.ferventio.app.domain.SearchableActionKind

internal data class GlobalActionCatalogStrings(
    val settingsTitle: String,
    val settingsSubtitle: String,
    val addChannelTitle: String,
    val addChannelSubtitle: String,
    val reconnectTitle: String,
    val reconnectSubtitle: String,
)

private data class GlobalActionUiStrings(
    val catalog: GlobalActionCatalogStrings,
    val title: String,
    val placeholder: String,
    val requiresPreview: String,
    val requiresConfirmation: String,
)

internal object GlobalActionCatalog {
    fun build(
        state: FerventioUiState,
        strings: GlobalActionCatalogStrings,
    ): List<SearchableAction> = buildList {
        add(
            SearchableAction(
                id = "navigation:settings",
                title = strings.settingsTitle,
                subtitle = strings.settingsSubtitle,
                keywords = setOf("settings", "preferences", "параметры"),
                kind = SearchableActionKind.NAVIGATION,
            ),
        )
        add(
            SearchableAction(
                id = "navigation:add-channel",
                title = strings.addChannelTitle,
                subtitle = strings.addChannelSubtitle,
                keywords = setOf("channel", "twitch", "канал"),
                kind = SearchableActionKind.NAVIGATION,
            ),
        )
        add(
            SearchableAction(
                id = "navigation:reconnect",
                title = strings.reconnectTitle,
                subtitle = strings.reconnectSubtitle,
                keywords = setOf("reconnect", "eventsub", "connection"),
                kind = SearchableActionKind.NAVIGATION,
            ),
        )

        state.channels.forEach { channel ->
            add(
                SearchableAction(
                    id = "channel:${channel.id}",
                    title = "#${channel.displayName}",
                    subtitle = channel.login,
                    keywords = setOf(channel.login, channel.displayName, "channel", "канал"),
                    kind = SearchableActionKind.CHANNEL,
                ),
            )
        }

        CommandRegistry.builtIns.forEach { definition ->
            add(SearchableActionFactory.fromCommandDefinition(definition))
        }
        state.customCommands
            .filter { it.enabled }
            .forEach { command -> add(SearchableActionFactory.fromCustomCommand(command)) }
    }.distinctBy(SearchableAction::id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlobalActionSearchSheet(
    state: FerventioUiState,
    onDismiss: () -> Unit,
    onAction: (SearchableAction) -> Unit,
) {
    val resourceStrings = rememberAppResourceStrings(state.appLanguage)
    val strings = GlobalActionUiStrings(
        catalog = GlobalActionCatalogStrings(
            settingsTitle = resourceStrings.string(R.string.ferventio_action_search_settings_title),
            settingsSubtitle = resourceStrings.string(R.string.ferventio_action_search_settings_subtitle),
            addChannelTitle = resourceStrings.string(R.string.ferventio_action_search_add_channel_title),
            addChannelSubtitle = resourceStrings.string(R.string.ferventio_action_search_add_channel_subtitle),
            reconnectTitle = resourceStrings.string(R.string.ferventio_action_search_reconnect_title),
            reconnectSubtitle = resourceStrings.string(R.string.ferventio_action_search_reconnect_subtitle),
        ),
        title = resourceStrings.string(R.string.ferventio_action_search_title),
        placeholder = resourceStrings.string(R.string.ferventio_action_search_placeholder),
        requiresPreview = resourceStrings.string(R.string.ferventio_action_search_requires_preview),
        requiresConfirmation = resourceStrings.string(R.string.ferventio_action_search_requires_confirmation),
    )
    var query by remember { mutableStateOf("") }
    val actions = remember(state.channels, state.customCommands, strings.catalog) {
        GlobalActionCatalog.build(state, strings.catalog)
    }
    val matches = remember(query, actions) { ActionSearchIndex.search(query, actions) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LocalizedText(
                strings.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { LocalizedText(strings.placeholder) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = matches,
                    key = { match -> match.action.id },
                    contentType = { match -> match.action.kind.name },
                ) { match ->
                    GlobalActionRow(
                        action = match.action,
                        requiresPreviewText = strings.requiresPreview,
                        requiresConfirmationText = strings.requiresConfirmation,
                        onClick = {
                            onAction(match.action)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalActionRow(
    action: SearchableAction,
    requiresPreviewText: String,
    requiresConfirmationText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = when (action.kind) {
                    SearchableActionKind.NAVIGATION -> Icons.Default.Tune
                    SearchableActionKind.SETTING -> Icons.Default.Settings
                    SearchableActionKind.COMMAND -> Icons.Default.Code
                    SearchableActionKind.MODERATION -> Icons.Default.Block
                    SearchableActionKind.CHANNEL -> Icons.AutoMirrored.Filled.Chat
                    SearchableActionKind.USER -> Icons.Default.Person
                },
                contentDescription = null,
                tint = if (action.requiresPreview || action.requiresConfirmation) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                VerbatimText(
                    action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (action.subtitle.isNotBlank()) {
                    VerbatimText(
                        action.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    action.requiresPreview -> LocalizedText(
                        requiresPreviewText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    action.requiresConfirmation -> LocalizedText(
                        requiresConfirmationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}
