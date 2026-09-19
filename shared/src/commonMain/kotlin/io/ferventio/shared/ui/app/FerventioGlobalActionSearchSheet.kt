package io.ferventio.shared.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import io.ferventio.app.domain.ActionSearchIndex
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.CommandRegistry
import io.ferventio.app.domain.SearchableAction
import io.ferventio.app.domain.SearchableActionFactory
import io.ferventio.app.domain.SearchableActionKind
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.action_search_add_channel_subtitle
import io.ferventio.shared.generated.resources.action_search_add_channel_title
import io.ferventio.shared.generated.resources.action_search_placeholder
import io.ferventio.shared.generated.resources.action_search_reconnect_subtitle
import io.ferventio.shared.generated.resources.action_search_reconnect_title
import io.ferventio.shared.generated.resources.action_search_requires_confirmation
import io.ferventio.shared.generated.resources.action_search_requires_preview
import io.ferventio.shared.generated.resources.action_search_settings_subtitle
import io.ferventio.shared.generated.resources.action_search_settings_title
import io.ferventio.shared.generated.resources.action_search_title
import org.jetbrains.compose.resources.stringResource

internal const val SHARED_ACTION_ID_SETTINGS = "navigation:settings"
internal const val SHARED_ACTION_ID_ADD_CHANNEL = "navigation:add-channel"
internal const val SHARED_ACTION_ID_RECONNECT = "navigation:reconnect"
internal const val SHARED_ACTION_CHANNEL_PREFIX = "channel:"
internal const val SHARED_ACTION_COMMAND_PREFIX = "command:"

internal data class SharedGlobalActionCatalogStrings(
    val settingsTitle: String,
    val settingsSubtitle: String,
    val addChannelTitle: String,
    val addChannelSubtitle: String,
    val reconnectTitle: String,
    val reconnectSubtitle: String,
)

internal fun buildSharedGlobalActionCatalog(
    channels: List<ChatChannel>,
    moderatorChannelIds: Set<String>,
    activeChannelId: String?,
    strings: SharedGlobalActionCatalogStrings,
    canAddChannel: Boolean,
    reconnectAvailable: Boolean,
): List<SearchableAction> = buildList {
    add(
        SearchableAction(
            id = SHARED_ACTION_ID_SETTINGS,
            title = strings.settingsTitle,
            subtitle = strings.settingsSubtitle,
            keywords = setOf("settings", "preferences", "параметры"),
            kind = SearchableActionKind.NAVIGATION,
        ),
    )

    if (canAddChannel) {
        add(
            SearchableAction(
                id = SHARED_ACTION_ID_ADD_CHANNEL,
                title = strings.addChannelTitle,
                subtitle = strings.addChannelSubtitle,
                keywords = setOf("channel", "twitch", "add", "канал"),
                kind = SearchableActionKind.NAVIGATION,
            ),
        )
    }

    if (reconnectAvailable) {
        add(
            SearchableAction(
                id = SHARED_ACTION_ID_RECONNECT,
                title = strings.reconnectTitle,
                subtitle = strings.reconnectSubtitle,
                keywords = setOf("reconnect", "eventsub", "connection"),
                kind = SearchableActionKind.NAVIGATION,
            ),
        )
    }

    channels.forEach { channel ->
        add(
            SearchableAction(
                id = SHARED_ACTION_CHANNEL_PREFIX + channel.id,
                title = "#${channel.displayName}",
                subtitle = channel.login,
                keywords = setOf(channel.login, channel.displayName, "channel", "канал"),
                kind = SearchableActionKind.CHANNEL,
            ),
        )
    }

    if (activeChannelId != null) {
        val canModerate = activeChannelId in moderatorChannelIds
        CommandRegistry.builtIns
            .asSequence()
            .filter { definition -> definition.name in SHARED_ACTION_PALETTE_COMMANDS }
            .map(SearchableActionFactory::fromCommandDefinition)
            .filter { action -> action.kind != SearchableActionKind.MODERATION || canModerate }
            .forEach(::add)
    }
}.distinctBy(SearchableAction::id)

internal fun visibleSharedGlobalActions(
    query: String,
    actions: List<SearchableAction>,
) = ActionSearchIndex.search(
    query = query,
    actions = if (query.trimStart().startsWith('/')) {
        actions
    } else {
        actions.filterNot { action ->
            action.kind == SearchableActionKind.COMMAND ||
                action.kind == SearchableActionKind.MODERATION
        }
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioGlobalActionSearchSheet(
    channels: List<ChatChannel>,
    moderatorChannelIds: Set<String>,
    activeChannelId: String?,
    canAddChannel: Boolean,
    reconnectAvailable: Boolean,
    onDismiss: () -> Unit,
    onAction: (SearchableAction) -> Unit,
) {
    val strings = SharedGlobalActionCatalogStrings(
        settingsTitle = stringResource(Res.string.action_search_settings_title),
        settingsSubtitle = stringResource(Res.string.action_search_settings_subtitle),
        addChannelTitle = stringResource(Res.string.action_search_add_channel_title),
        addChannelSubtitle = stringResource(Res.string.action_search_add_channel_subtitle),
        reconnectTitle = stringResource(Res.string.action_search_reconnect_title),
        reconnectSubtitle = stringResource(Res.string.action_search_reconnect_subtitle),
    )
    val requiresPreviewText = stringResource(Res.string.action_search_requires_preview)
    val requiresConfirmationText = stringResource(Res.string.action_search_requires_confirmation)
    var query by remember { mutableStateOf("") }
    val actions = remember(
        channels,
        moderatorChannelIds,
        activeChannelId,
        strings,
        canAddChannel,
        reconnectAvailable,
    ) {
        buildSharedGlobalActionCatalog(
            channels = channels,
            moderatorChannelIds = moderatorChannelIds,
            activeChannelId = activeChannelId,
            strings = strings,
            canAddChannel = canAddChannel,
            reconnectAvailable = reconnectAvailable,
        )
    }
    val matches = remember(query, actions) { visibleSharedGlobalActions(query, actions) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.action_search_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(120) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text(stringResource(Res.string.action_search_placeholder)) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(
                    items = matches,
                    key = { match -> match.action.id },
                    contentType = { match -> match.action.kind.name },
                ) { match ->
                    SharedGlobalActionRow(
                        action = match.action,
                        requiresPreviewText = requiresPreviewText,
                        requiresConfirmationText = requiresConfirmationText,
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
private fun SharedGlobalActionRow(
    action: SearchableAction,
    requiresPreviewText: String,
    requiresConfirmationText: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
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
                Text(
                    text = action.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (action.subtitle.isNotBlank()) {
                    Text(
                        text = action.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when {
                    action.requiresPreview -> Text(
                        text = requiresPreviewText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    action.requiresConfirmation -> Text(
                        text = requiresConfirmationText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

private val SHARED_ACTION_PALETTE_COMMANDS = setOf(
    "me",
    "user",
    "nuke",
    "ban",
    "unban",
    "timeout",
    "delete",
    "clear",
    "slow",
    "slowoff",
    "followers",
    "followersoff",
    "subscribers",
    "subscribersoff",
    "emoteonly",
    "emoteonlyoff",
)
