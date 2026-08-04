@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.ferventio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CompiledMessageFilter
import io.ferventio.app.domain.FilterDiagnostic
import io.ferventio.app.domain.FilterDiagnosticSeverity
import io.ferventio.app.domain.FilterTokenKind
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.MAX_FILTER_EXPRESSION_LENGTH
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.savedFilterIdFromReference
import io.ferventio.app.domain.savedFilterReference

@Composable
internal fun MessageFilterSettings(
    state: FerventioUiState,
    controller: FerventioController,
) {
    @Suppress("DEPRECATION") // LocalClipboard migration requires suspend clipboard writes.
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf<SavedMessageFilter?>(null) }
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val previewMessages = remember(state.messagesByChannel) {
        state.messagesByChannel.values
            .asSequence()
            .flatten()
            .sortedByDescending(ChatMessage::timestampMillis)
            .take(1_500)
            .toList()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Фильтры компилируются один раз и затем применяются к сообщениям. " +
                "Строковые сравнения без regex не учитывают регистр.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.savedMessageFilters.isEmpty()) {
            Text("Сохранённых фильтров пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.savedMessageFilters.forEachIndexed { index, filter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = filter }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(filter.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            filter.expression,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { controller.addSavedFilterSplit(filter.id) }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = "Добавить в split")
                    }
                    IconButton(onClick = { editing = filter }) {
                        Icon(Icons.Default.Edit, contentDescription = "Изменить")
                    }
                    IconButton(onClick = { controller.deleteSavedMessageFilter(filter.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
                if (index != state.savedMessageFilters.lastIndex) HorizontalDivider()
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Создать фильтр")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(controller.exportSavedMessageFilters()))
                    status = "JSON фильтров скопирован"
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Экспорт")
            }
            OutlinedButton(
                onClick = { importing = true },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Импорт")
            }
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        Text("Примеры", fontWeight = FontWeight.SemiBold)
        MessageFilterExamples(onSelect = { example ->
            editing = SavedMessageFilter(name = "Новый фильтр", expression = example)
        })
        Text(
            "Поля reward.title и reward.cost доступны только если Twitch передал metadata награды; " +
                "иначе сравнение с ними не совпадает.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                if (controller.upsertSavedMessageFilter(filter)) {
                    creating = false
                    editing = null
                }
            },
        )
    }

    if (importing) {
        ImportFiltersDialog(
            initial = clipboard.getText()?.text.orEmpty(),
            onDismiss = { importing = false },
            onImport = { raw ->
                if (controller.importSavedMessageFilters(raw)) {
                    importing = false
                    status = "Фильтры импортированы"
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedFilterEditorDialog(
    initial: SavedMessageFilter?,
    previewMessages: List<ChatMessage>,
    onDismiss: () -> Unit,
    onSave: (SavedMessageFilter) -> Unit,
) {
    val original = remember(initial) { initial ?: SavedMessageFilter(name = "", expression = "") }
    var name by rememberSaveable(original.id) { mutableStateOf(original.name) }
    var expression by rememberSaveable(original.id) { mutableStateOf(original.expression) }
    val compiled = remember(expression) { MessageFilterLanguage.compile(expression) }
    val preview = remember(compiled, previewMessages) { preview(compiled, previewMessages) }
    val valid = name.isNotBlank() && compiled.isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Новый фильтр" else "Редактирование фильтра") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Название") },
                        singleLine = true,
                    )
                }
                item {
                    FilterExpressionField(
                        value = expression,
                        onValueChange = { expression = it.take(MAX_FILTER_EXPRESSION_LENGTH) },
                        label = "Выражение",
                    )
                }
                item { FilterDiagnostics(compiled.diagnostics) }
                item {
                    Text(
                        "Предпросмотр: ${preview.totalMatches} из ${preview.totalChecked}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(preview.messages.size, key = { preview.messages[it].id }) { index ->
                    PreviewMessageRow(preview.messages[index])
                }
                item {
                    Text("Примеры", fontWeight = FontWeight.SemiBold)
                    MessageFilterExamples(onSelect = { expression = it })
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
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SplitFilterEditorDialog(
    initialExpression: String,
    savedFilters: List<SavedMessageFilter>,
    messages: List<ChatMessage>,
    isHighlightsSplit: (ChatMessage) -> Boolean,
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
    var savedMenu by remember { mutableStateOf(false) }
    val isHighlights = expression.trim() == HIGHLIGHTS_FILTER_QUERY
    val compiled = remember(expression) {
        if (expression.isBlank() || expression.trim() == HIGHLIGHTS_FILTER_QUERY) null
        else MessageFilterLanguage.compileForSplit(expression)
    }
    val valid = expression.isBlank() || isHighlights || compiled?.isValid == true
    val preview = remember(compiled, messages, isHighlights) {
        when {
            expression.isBlank() -> FilterPreview(messages.size, messages.size, messages.take(8))
            isHighlights -> {
                val matched = messages.filter(isHighlightsSplit)
                FilterPreview(messages.size, matched.size, matched.take(8))
            }
            compiled != null -> preview(compiled, messages)
            else -> FilterPreview(messages.size, 0, emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Фильтр split") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                if (savedFilters.isNotEmpty()) {
                    item {
                        Box {
                            OutlinedButton(
                                onClick = { savedMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Выбрать сохранённый фильтр") }
                            DropdownMenu(expanded = savedMenu, onDismissRequest = { savedMenu = false }) {
                                savedFilters.forEach { filter ->
                                    DropdownMenuItem(
                                        text = { Text(filter.name) },
                                        onClick = {
                                            linkedFilterId = filter.id
                                            expression = filter.expression
                                            savedMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                linkedFilterId?.let { filterId ->
                    val linkedName = savedFilters.firstOrNull { it.id == filterId }?.name
                    if (linkedName != null) {
                        item {
                            Text(
                                "Связан с сохранённым фильтром: $linkedName",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                item {
                    FilterExpressionField(
                        value = expression,
                        onValueChange = { value ->
                            linkedFilterId = null
                            expression = value.take(MAX_FILTER_EXPRESSION_LENGTH)
                        },
                        label = "Выражение; пусто — без фильтра",
                    )
                }
                if (isHighlights) {
                    item {
                        Text(
                            "Специальный split Highlights",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (compiled != null) {
                    item { FilterDiagnostics(compiled.diagnostics) }
                }
                item {
                    Text(
                        "Предпросмотр: ${preview.totalMatches} из ${preview.totalChecked}",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(preview.messages.size, key = { preview.messages[it].id }) { index ->
                    PreviewMessageRow(preview.messages[index])
                }
                item {
                    Text("Примеры", fontWeight = FontWeight.SemiBold)
                    MessageFilterExamples(onSelect = { value ->
                        linkedFilterId = null
                        expression = value
                    })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val value = linkedFilterId?.let(::savedFilterReference) ?: expression.trim()
                    onSave(value)
                },
            ) { Text("Применить") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    linkedFilterId = null
                    expression = ""
                }) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun FilterExpressionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    val transformation = remember(colors) {
        MessageFilterSyntaxTransformation(
            fieldColor = colors.primary,
            stringColor = colors.tertiary,
            numberColor = colors.secondary,
            regexColor = colors.error,
            operatorColor = colors.onSurface,
            booleanColor = colors.secondary,
            invalidColor = colors.error,
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        label = { Text(label) },
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        visualTransformation = transformation,
        minLines = 4,
        maxLines = 9,
    )
}

@Composable
private fun FilterDiagnostics(diagnostics: List<FilterDiagnostic>) {
    if (diagnostics.isEmpty()) {
        Text("Выражение корректно", color = MaterialTheme.colorScheme.primary)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        diagnostics.take(6).forEach { diagnostic ->
            val color = if (diagnostic.severity == FilterDiagnosticSeverity.ERROR) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.tertiary
            }
            Text(
                "${diagnostic.span.start + 1}: ${diagnostic.message}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun MessageFilterExamples(onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MessageFilterLanguage.examples().forEach { (title, expression) ->
            FilterChip(
                selected = false,
                onClick = { onSelect(expression) },
                label = { Text(title) },
            )
        }
    }
}

@Composable
private fun PreviewMessageRow(message: ChatMessage) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            "#${message.channelLogin} · ${message.userDisplayName}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(message.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportFiltersDialog(
    initial: String,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var raw by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт фильтров") },
        text = {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it.take(200_000) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
                label = { Text("JSON") },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                minLines = 10,
            )
        },
        confirmButton = {
            TextButton(enabled = raw.isNotBlank(), onClick = { onImport(raw) }) { Text("Импортировать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
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
    val sample = ArrayList<ChatMessage>(8)
    messages.forEach { message ->
        if (compiled.matches(message)) {
            count++
            if (sample.size < 8) sample += message
        }
    }
    return FilterPreview(messages.size, count, sample)
}

private class MessageFilterSyntaxTransformation(
    private val fieldColor: Color,
    private val stringColor: Color,
    private val numberColor: Color,
    private val regexColor: Color,
    private val operatorColor: Color,
    private val booleanColor: Color,
    private val invalidColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val tokenization = MessageFilterLanguage.tokenize(text.text)
        val annotated = buildAnnotatedString {
            append(text.text)
            tokenization.tokens.forEach { token ->
                if (token.span.start >= token.span.endExclusive || token.span.endExclusive > text.length) {
                    return@forEach
                }
                val color = when (token.kind) {
                    FilterTokenKind.IDENTIFIER -> fieldColor
                    FilterTokenKind.STRING -> stringColor
                    FilterTokenKind.NUMBER -> numberColor
                    FilterTokenKind.BOOLEAN -> booleanColor
                    FilterTokenKind.REGEX -> regexColor
                    FilterTokenKind.OPERATOR,
                    FilterTokenKind.KEYWORD_OPERATOR,
                    FilterTokenKind.LEFT_PAREN,
                    FilterTokenKind.RIGHT_PAREN,
                    FilterTokenKind.LEFT_BRACKET,
                    FilterTokenKind.RIGHT_BRACKET,
                    FilterTokenKind.COMMA,
                    -> operatorColor
                    FilterTokenKind.INVALID -> invalidColor
                    FilterTokenKind.EOF -> return@forEach
                }
                addStyle(SpanStyle(color = color), token.span.start, token.span.endExclusive)
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}
