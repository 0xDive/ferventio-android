@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.ferventio.shared.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CompiledMessageFilter
import io.ferventio.app.domain.FilterDiagnostic
import io.ferventio.app.domain.FilterDiagnosticSeverity
import io.ferventio.app.domain.MAX_FILTER_EXPRESSION_LENGTH
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.saved_filters_add
import io.ferventio.shared.generated.resources.saved_filters_cancel
import io.ferventio.shared.generated.resources.saved_filters_close
import io.ferventio.shared.generated.resources.saved_filters_delete
import io.ferventio.shared.generated.resources.saved_filters_diagnostic_error
import io.ferventio.shared.generated.resources.saved_filters_diagnostic_warning
import io.ferventio.shared.generated.resources.saved_filters_edit
import io.ferventio.shared.generated.resources.saved_filters_edit_title
import io.ferventio.shared.generated.resources.saved_filters_empty
import io.ferventio.shared.generated.resources.saved_filters_expression
import io.ferventio.shared.generated.resources.saved_filters_expression_invalid
import io.ferventio.shared.generated.resources.saved_filters_expression_valid
import io.ferventio.shared.generated.resources.saved_filters_intro
import io.ferventio.shared.generated.resources.saved_filters_name
import io.ferventio.shared.generated.resources.saved_filters_new_title
import io.ferventio.shared.generated.resources.saved_filters_preview
import io.ferventio.shared.generated.resources.saved_filters_preview_empty
import io.ferventio.shared.generated.resources.saved_filters_save
import io.ferventio.shared.generated.resources.saved_filters_title
import io.ferventio.shared.generated.resources.settings_save_failed
import io.ferventio.shared.generated.resources.settings_saving
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.settings.SharedSavedFiltersStateHolder
import io.ferventio.shared.settings.SharedSettingsSaveStatus
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioSavedFiltersSheet(
    state: SharedSavedFiltersStateHolder,
    onUpsert: (SavedMessageFilter) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    var editing by remember { mutableStateOf<SavedMessageFilter?>(null) }
    var creating by remember { mutableStateOf(false) }
    val previewMessages = remember(runtime.chat.messagesByChannel) {
        runtime.chat.messagesByChannel.values
            .asSequence()
            .flatten()
            .sortedByDescending(ChatMessage::timestampMillis)
            .take(MAX_PREVIEW_MESSAGES)
            .toList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.saved_filters_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(Res.string.saved_filters_intro),
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.filters.isEmpty()) {
                Text(
                    text = stringResource(Res.string.saved_filters_empty),
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.filters.forEachIndexed { index, filter ->
                    SavedFilterRow(
                        filter = filter,
                        onEdit = { editing = filter },
                        onDelete = { onDelete(filter.id) },
                    )
                    if (index != state.filters.lastIndex) HorizontalDivider()
                }
            }

            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(stringResource(Res.string.saved_filters_add))
            }

            when (state.saveStatus) {
                SharedSettingsSaveStatus.SAVING -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(Res.string.settings_saving))
                }
                SharedSettingsSaveStatus.FAILED -> Text(
                    text = stringResource(
                        Res.string.settings_save_failed,
                        state.saveErrorMessage.orEmpty(),
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                SharedSettingsSaveStatus.IDLE -> Unit
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            ) {
                Text(stringResource(Res.string.saved_filters_close))
            }
        }
    }

    if (creating || editing != null) {
        SavedFilterEditorDialog(
            initial = editing,
            previewMessages = previewMessages,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { filter ->
                onUpsert(filter)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun SavedFilterRow(
    filter: SavedMessageFilter,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
    ) {
        Text(
            text = filter.name,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = filter.expression,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) {
                Text(stringResource(Res.string.saved_filters_edit))
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(Res.string.saved_filters_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SavedFilterEditorDialog(
    initial: SavedMessageFilter?,
    previewMessages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onSave: (SavedMessageFilter) -> Unit,
) {
    val original = remember(initial?.id) {
        initial ?: SavedMessageFilter(name = "", expression = "")
    }
    var name by remember(original.id) { mutableStateOf(original.name) }
    var expression by remember(original.id) { mutableStateOf(original.expression) }
    val compiled = remember(expression) { MessageFilterLanguage.compile(expression) }
    val preview = remember(compiled, previewMessages) { preview(compiled, previewMessages) }
    val valid = name.isNotBlank() && expression.isNotBlank() && compiled.isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) Res.string.saved_filters_new_title
                    else Res.string.saved_filters_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.saved_filters_name)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = expression,
                    onValueChange = { expression = it.take(MAX_FILTER_EXPRESSION_LENGTH) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    label = { Text(stringResource(Res.string.saved_filters_expression)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    minLines = 4,
                    maxLines = 9,
                    isError = expression.isNotBlank() && !compiled.isValid,
                )
                if (expression.isNotBlank()) {
                    if (compiled.diagnostics.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.saved_filters_expression_valid),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Text(
                            text = stringResource(Res.string.saved_filters_expression_invalid),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (compiled.isValid) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                        FilterDiagnostics(compiled.diagnostics)
                    }
                }

                Text(
                    text = stringResource(
                        Res.string.saved_filters_preview,
                        preview.totalMatches,
                        preview.totalChecked,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (previewMessages.isEmpty()) {
                    Text(
                        text = stringResource(Res.string.saved_filters_preview_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    preview.messages.forEach { message ->
                        PreviewMessageRow(message)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        original.copy(
                            name = name.trim(),
                            expression = expression.trim(),
                        ),
                    )
                },
            ) {
                Text(stringResource(Res.string.saved_filters_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.saved_filters_cancel))
            }
        },
    )
}

@Composable
private fun FilterDiagnostics(diagnostics: List<FilterDiagnostic>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        diagnostics.take(MAX_VISIBLE_DIAGNOSTICS).forEach { diagnostic ->
            val isError = diagnostic.severity == FilterDiagnosticSeverity.ERROR
            val severity = stringResource(
                if (isError) Res.string.saved_filters_diagnostic_error
                else Res.string.saved_filters_diagnostic_warning,
            )
            Text(
                text = "$severity · ${diagnostic.span.start + 1}: ${diagnostic.message}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun PreviewMessageRow(message: ChatMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Text(
            text = "#${message.channelLogin} · ${message.userDisplayName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class FilterPreview(
    val totalChecked: Int,
    val totalMatches: Int,
    val messages: List<ChatMessage>,
)

private fun preview(
    compiled: CompiledMessageFilter,
    messages: List<ChatMessage>,
): FilterPreview {
    if (!compiled.isValid) return FilterPreview(messages.size, 0, emptyList())
    var count = 0
    val sample = ArrayList<ChatMessage>(MAX_PREVIEW_SAMPLES)
    messages.forEach { message ->
        if (compiled.matches(message)) {
            count += 1
            if (sample.size < MAX_PREVIEW_SAMPLES) sample += message
        }
    }
    return FilterPreview(
        totalChecked = messages.size,
        totalMatches = count,
        messages = sample,
    )
}

private const val MAX_PREVIEW_MESSAGES = 1_500
private const val MAX_PREVIEW_SAMPLES = 8
private const val MAX_VISIBLE_DIAGNOSTICS = 6
