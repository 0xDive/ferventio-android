package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistorySearchScope
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.history_search_action
import io.ferventio.shared.generated.resources.history_search_all_channels
import io.ferventio.shared.generated.resources.history_search_channel
import io.ferventio.shared.generated.resources.history_search_current_channel
import io.ferventio.shared.generated.resources.history_search_empty
import io.ferventio.shared.generated.resources.history_search_initial
import io.ferventio.shared.generated.resources.history_search_open_message
import io.ferventio.shared.generated.resources.history_search_query_hint
import io.ferventio.shared.generated.resources.history_search_query_label
import io.ferventio.shared.generated.resources.history_search_results
import io.ferventio.shared.generated.resources.history_search_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FerventioHistorySearchSheet(
    history: ChatHistoryStore,
    currentChannelId: String?,
    navigableChannelIds: Set<String>,
    onOpenMessage: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var scope by remember(currentChannelId) {
        mutableStateOf(
            if (currentChannelId.isNullOrBlank()) {
                ChatHistorySearchScope.ALL_CHANNELS
            } else {
                ChatHistorySearchScope.CURRENT_CHANNEL
            },
        )
    }
    var results by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val runSearch = {
        val value = query.trim()
        if (value.isNotEmpty() && !isSearching) {
            isSearching = true
            errorMessage = null
            coroutineScope.launch {
                history.searchMessages(
                    ChatHistorySearchRequest(
                        rawQuery = value,
                        scope = scope,
                        currentChannelId = currentChannelId,
                    ),
                ).onSuccess { messages ->
                    results = filterNavigableHistorySearchResults(messages, navigableChannelIds)
                    hasSearched = true
                }.onFailure { error ->
                    results = emptyList()
                    hasSearched = true
                    errorMessage = error.message
                }
                isSearching = false
            }
        }
        Unit
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.history_search_title),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                label = { Text(stringResource(Res.string.history_search_query_label)) },
                supportingText = { Text(stringResource(Res.string.history_search_query_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = scope == ChatHistorySearchScope.CURRENT_CHANNEL,
                    onClick = { scope = ChatHistorySearchScope.CURRENT_CHANNEL },
                    enabled = !currentChannelId.isNullOrBlank(),
                    label = { Text(stringResource(Res.string.history_search_current_channel)) },
                )
                FilterChip(
                    selected = scope == ChatHistorySearchScope.ALL_CHANNELS,
                    onClick = { scope = ChatHistorySearchScope.ALL_CHANNELS },
                    label = { Text(stringResource(Res.string.history_search_all_channels)) },
                )
                Button(
                    onClick = { runSearch() },
                    enabled = query.isNotBlank() && !isSearching,
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(Res.string.history_search_action))
                    }
                }
            }

            errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            when {
                !hasSearched -> SearchStatusText(stringResource(Res.string.history_search_initial))
                results.isEmpty() -> SearchStatusText(stringResource(Res.string.history_search_empty))
                else -> {
                    Text(
                        text = stringResource(Res.string.history_search_results, results.size),
                        modifier = Modifier.padding(horizontal = 20.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    ) {
                        items(
                            items = results,
                            key = { message -> "${message.channelId}:${message.id}" },
                        ) { message ->
                            HistorySearchResultRow(
                                message = message,
                                onClick = { onOpenMessage(message) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

internal fun filterNavigableHistorySearchResults(
    messages: List<ChatMessage>,
    navigableChannelIds: Set<String>,
): List<ChatMessage> {
    if (messages.isEmpty() || navigableChannelIds.isEmpty()) return emptyList()
    val normalized = navigableChannelIds
        .asSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .toHashSet()
    if (normalized.isEmpty()) return emptyList()
    return messages.filter { it.channelId in normalized }
}

@Composable
private fun SearchStatusText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HistorySearchResultRow(
    message: ChatMessage,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.history_search_channel, message.channelLogin),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = message.author.displayName.ifBlank { message.author.login },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(Res.string.history_search_open_message),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = message.text,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.flags.isDeleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
