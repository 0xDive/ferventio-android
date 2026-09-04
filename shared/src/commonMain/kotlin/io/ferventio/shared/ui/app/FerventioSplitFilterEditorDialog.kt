package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.MAX_FILTER_EXPRESSION_LENGTH
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.savedFilterIdFromReference
import io.ferventio.app.domain.savedFilterReference
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.workspace_split_filter_apply
import io.ferventio.shared.generated.resources.workspace_split_filter_cancel
import io.ferventio.shared.generated.resources.workspace_split_filter_choose_saved
import io.ferventio.shared.generated.resources.workspace_split_filter_expression
import io.ferventio.shared.generated.resources.workspace_split_filter_highlights
import io.ferventio.shared.generated.resources.workspace_split_filter_invalid
import io.ferventio.shared.generated.resources.workspace_split_filter_linked
import io.ferventio.shared.generated.resources.workspace_split_filter_no_matches
import io.ferventio.shared.generated.resources.workspace_split_filter_preview
import io.ferventio.shared.generated.resources.workspace_split_filter_reset
import io.ferventio.shared.generated.resources.workspace_split_filter_title
import org.jetbrains.compose.resources.stringResource

private data class SplitFilterPreview(
    val checked: Int,
    val matched: Int,
    val messages: List<ChatMessage>,
)

@Composable
internal fun FerventioSplitFilterEditorDialog(
    initialExpression: String,
    savedFilters: List<SavedMessageFilter>,
    messages: List<ChatMessage>,
    decorations: Map<String, MessageDecoration>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val initialLinkedFilterId = remember(initialExpression, savedFilters) {
        savedFilterIdFromReference(initialExpression)
            ?.takeIf { filterId -> savedFilters.any { it.id == filterId } }
    }
    val initialEditorExpression = remember(initialExpression, initialLinkedFilterId, savedFilters) {
        initialLinkedFilterId
            ?.let { filterId -> savedFilters.firstOrNull { it.id == filterId }?.expression }
            ?: initialExpression
    }
    var expression by rememberSaveable(initialExpression) { mutableStateOf(initialEditorExpression) }
    var linkedFilterId by rememberSaveable(initialExpression) { mutableStateOf(initialLinkedFilterId) }
    var savedMenuVisible by remember { mutableStateOf(false) }
    val normalizedExpression = expression.trim()
    val isHighlights = normalizedExpression == HIGHLIGHTS_FILTER_QUERY
    val compiled = remember(normalizedExpression) {
        if (normalizedExpression.isEmpty() || isHighlights) null
        else MessageFilterLanguage.compileForSplit(normalizedExpression)
    }
    val valid = normalizedExpression.isEmpty() || isHighlights || compiled?.isValid == true
    val preview = remember(normalizedExpression, isHighlights, compiled, messages, decorations) {
        val matched = when {
            normalizedExpression.isEmpty() -> messages
            isHighlights -> messages.filter { message ->
                decorations[message.id]?.filteredSplit == true
            }
            compiled != null -> messages.filter(compiled::matches)
            else -> emptyList()
        }
        SplitFilterPreview(
            checked = messages.size,
            matched = matched.size,
            messages = matched.take(MAX_PREVIEW_MESSAGES),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.workspace_split_filter_title)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (savedFilters.isNotEmpty()) {
                    item {
                        Box {
                            OutlinedButton(
                                onClick = { savedMenuVisible = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(Res.string.workspace_split_filter_choose_saved))
                            }
                            DropdownMenu(
                                expanded = savedMenuVisible,
                                onDismissRequest = { savedMenuVisible = false },
                            ) {
                                savedFilters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.name) },
                                        onClick = {
                                            linkedFilterId = filter.id
                                            expression = filter.expression
                                            savedMenuVisible = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                linkedFilterId?.let { filterId ->
                    savedFilters.firstOrNull { it.id == filterId }?.let { filter ->
                        item {
                            Text(
                                text = stringResource(
                                    Res.string.workspace_split_filter_linked,
                                    filter.name,
                                ),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = expression,
                        onValueChange = { value ->
                            linkedFilterId = null
                            expression = value.take(MAX_FILTER_EXPRESSION_LENGTH)
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        label = { Text(stringResource(Res.string.workspace_split_filter_expression)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        minLines = 4,
                        maxLines = 9,
                        isError = !valid,
                    )
                }

                if (isHighlights) {
                    item {
                        Text(
                            text = stringResource(Res.string.workspace_split_filter_highlights),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (!valid) {
                    item {
                        Text(
                            text = stringResource(Res.string.workspace_split_filter_invalid),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                item {
                    Text(
                        text = stringResource(
                            Res.string.workspace_split_filter_preview,
                            preview.matched,
                            preview.checked,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (preview.messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.workspace_split_filter_no_matches),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(preview.messages, key = ChatMessage::id) { message ->
                        SplitFilterPreviewMessage(message)
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SPLIT_FILTER_EXAMPLES.forEach { example ->
                            TextButton(
                                onClick = {
                                    linkedFilterId = null
                                    expression = example
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = example,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        linkedFilterId?.let(::savedFilterReference)
                            ?: normalizedExpression,
                    )
                },
            ) {
                Text(stringResource(Res.string.workspace_split_filter_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        linkedFilterId = null
                        expression = ""
                    },
                ) {
                    Text(stringResource(Res.string.workspace_split_filter_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.workspace_split_filter_cancel))
                }
            }
        },
    )
}

@Composable
private fun SplitFilterPreviewMessage(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(
            text = message.userDisplayName.ifBlank { message.userLogin },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private const val MAX_PREVIEW_MESSAGES = 8

private val SPLIT_FILTER_EXAMPLES = listOf(
    "message.length > 80",
    "author.badges contains [\"moderator\", \"vip\"]",
    "message.content contains \"ferventio\"",
    "message.content matches /hello|привет/i",
)
