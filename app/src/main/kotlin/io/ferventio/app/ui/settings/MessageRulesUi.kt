@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.ferventio.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterAlt
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.DEFAULT_HIGHLIGHT_COLOR_ARGB
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.IgnoreRuleType

@Composable
internal fun HighlightRulesSettings(
    state: FerventioUiState,
    controller: FerventioController,
) {
    var editing by remember { mutableStateOf<HighlightRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LocalizedText(
            "Highlights проверяются один раз при получении сообщения. Правила могут выделять строку, проигрывать звук, показывать Android-уведомление и добавлять запись в общий список упоминаний.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.highlightRules.isEmpty()) {
            LocalizedText("Правил пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.highlightRules.forEachIndexed { index, rule ->
                RuleCard(
                    title = highlightRuleTitle(rule),
                    summary = highlightRuleSummary(rule),
                    enabled = rule.enabled,
                    accent = colorFromArgb(rule.colorArgb),
                    onEnabledChange = { controller.upsertHighlightRule(rule.copy(enabled = it)) },
                    onEdit = { editing = rule },
                    onDelete = { controller.deleteHighlightRule(rule.id) },
                )
                if (index != state.highlightRules.lastIndex) HorizontalDivider()
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            LocalizedText("Добавить highlight")
        }
        OutlinedButton(
            onClick = controller::addHighlightsFilteredSplit,
            enabled = state.channels.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FilterAlt, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            LocalizedText("Добавить filtered split Highlights")
        }
        LocalizedText(
            "Filtered split показывает правила, у которых включён соответствующий переключатель. На телефоне он появится в текущей вкладке workspace, если свободен split.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (creating || editing != null) {
        HighlightRuleDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { rule ->
                controller.upsertHighlightRule(rule)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
internal fun IgnoreRulesSettings(
    state: FerventioUiState,
    controller: FerventioController,
) {
    var editing by remember { mutableStateOf<IgnoreRule?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LocalizedText(
            "Ignore применяется раньше highlights: полностью скрытое или свернутое сообщение не создаёт звук, уведомление и новую запись Mentions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.ignoreRules.isEmpty()) {
            LocalizedText("Правил пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.ignoreRules.forEachIndexed { index, rule ->
                RuleCard(
                    title = ignoreRuleTitle(rule),
                    summary = ignoreRuleSummary(rule),
                    enabled = rule.enabled,
                    accent = MaterialTheme.colorScheme.outline,
                    onEnabledChange = { controller.upsertIgnoreRule(rule.copy(enabled = it)) },
                    onEdit = { editing = rule },
                    onDelete = { controller.deleteIgnoreRule(rule.id) },
                )
                if (index != state.ignoreRules.lastIndex) HorizontalDivider()
            }
        }
        Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            LocalizedText("Добавить ignore")
        }
    }

    if (creating || editing != null) {
        IgnoreRuleDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { rule ->
                controller.upsertIgnoreRule(rule)
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun RuleCard(
    title: String,
    summary: String,
    enabled: Boolean,
    accent: Color,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(10.dp),
            color = accent,
            shape = MaterialTheme.shapes.extraLarge,
        ) {}
        Spacer(Modifier.width(9.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onEdit),
        ) {
            LocalizedText(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LocalizedText(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = localizedString("Изменить")) }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = localizedString("Удалить")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HighlightRuleDialog(
    initial: HighlightRule?,
    onDismiss: () -> Unit,
    onSave: (HighlightRule) -> Unit,
) {
    val original = initial ?: HighlightRule(type = HighlightRuleType.USERNAME)
    var type by rememberSaveable(original.id) { mutableStateOf(original.type) }
    var pattern by rememberSaveable(original.id) { mutableStateOf(original.pattern) }
    var enabled by rememberSaveable(original.id) { mutableStateOf(original.enabled) }
    var caseSensitive by rememberSaveable(original.id) { mutableStateOf(original.caseSensitive) }
    var colorArgb by rememberSaveable(original.id) { mutableStateOf(original.colorArgb) }
    var colorText by rememberSaveable(original.id) { mutableStateOf(argbToHex(original.colorArgb)) }
    var playSound by rememberSaveable(original.id) { mutableStateOf(original.playSound) }
    var push by rememberSaveable(original.id) { mutableStateOf(original.push) }
    var addToMentions by rememberSaveable(original.id) { mutableStateOf(original.addToMentions) }
    var filteredSplit by rememberSaveable(original.id) { mutableStateOf(original.filteredSplit) }
    var typeMenu by remember { mutableStateOf(false) }
    val requiresPattern = type in PATTERN_HIGHLIGHT_TYPES
    val regexValid = type != HighlightRuleType.REGEX || pattern.isBlank() || runCatching { Regex(pattern) }.isSuccess
    val valid = (!requiresPattern || pattern.isNotBlank() || type == HighlightRuleType.USERNAME) && regexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText(if (initial == null) "Новый highlight" else "Изменить highlight") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            LocalizedText(highlightTypeLabel(type))
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            HighlightRuleType.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { LocalizedText(highlightTypeLabel(candidate)) },
                                    onClick = {
                                        type = candidate
                                        typeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (requiresPattern) {
                    item {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it.take(240) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { LocalizedText(highlightPatternLabel(type)) },
                            supportingText = if (!regexValid) ({ LocalizedText("Некорректное регулярное выражение") }) else null,
                            isError = !regexValid,
                            singleLine = type != HighlightRuleType.REGEX,
                        )
                    }
                }
                item {
                    LocalizedText("Цвет строки", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        HIGHLIGHT_COLORS.forEach { candidate ->
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        colorArgb = candidate
                                        colorText = argbToHex(candidate)
                                    },
                                shape = MaterialTheme.shapes.extraLarge,
                                color = colorFromArgb(candidate),
                                border = if (candidate == colorArgb) {
                                    BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface)
                                } else null,
                            ) {}
                        }
                    }
                    OutlinedTextField(
                        value = colorText,
                        onValueChange = { value ->
                            colorText = value.take(9)
                            parseArgb(value)?.let { colorArgb = it }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { LocalizedText("ARGB, например #FFFFC857") },
                        singleLine = true,
                    )
                }
                item { RuleSwitch("Правило включено", enabled, { enabled = it }) }
                item { RuleSwitch("Учитывать регистр", caseSensitive, { caseSensitive = it }) }
                item { RuleSwitch("Звук", playSound, { playSound = it }) }
                item { RuleSwitch("Android-уведомление", push, { push = it }) }
                item { RuleSwitch("Добавлять в Mentions", addToMentions, { addToMentions = it }) }
                item { RuleSwitch("Показывать в Highlights split", filteredSplit, { filteredSplit = it }) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        original.copy(
                            type = type,
                            pattern = pattern.trim(),
                            enabled = enabled,
                            caseSensitive = caseSensitive,
                            colorArgb = colorArgb,
                            playSound = playSound,
                            push = push,
                            addToMentions = addToMentions,
                            filteredSplit = filteredSplit,
                        ),
                    )
                },
            ) { LocalizedText("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { LocalizedText("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IgnoreRuleDialog(
    initial: IgnoreRule?,
    onDismiss: () -> Unit,
    onSave: (IgnoreRule) -> Unit,
) {
    val original = initial ?: IgnoreRule(type = IgnoreRuleType.USER)
    var type by rememberSaveable(original.id) { mutableStateOf(original.type) }
    var pattern by rememberSaveable(original.id) { mutableStateOf(original.pattern) }
    var enabled by rememberSaveable(original.id) { mutableStateOf(original.enabled) }
    var caseSensitive by rememberSaveable(original.id) { mutableStateOf(original.caseSensitive) }
    var displayMode by rememberSaveable(original.id) { mutableStateOf(original.displayMode) }
    var typeMenu by remember { mutableStateOf(false) }
    val regexValid = type != IgnoreRuleType.REGEX || pattern.isBlank() || runCatching { Regex(pattern) }.isSuccess
    val valid = pattern.isNotBlank() && regexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LocalizedText(if (initial == null) "Новое ignore-правило" else "Изменить ignore") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Box {
                        OutlinedButton(onClick = { typeMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            LocalizedText(ignoreTypeLabel(type))
                        }
                        DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                            IgnoreRuleType.entries.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { LocalizedText(ignoreTypeLabel(candidate)) },
                                    onClick = {
                                        type = candidate
                                        typeMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { LocalizedText(ignorePatternLabel(type)) },
                        supportingText = if (!regexValid) ({ LocalizedText("Некорректное регулярное выражение") }) else null,
                        isError = !regexValid,
                        singleLine = type != IgnoreRuleType.REGEX,
                    )
                }
                item {
                    LocalizedText("Отображение", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        IgnoreDisplayMode.entries.forEach { mode ->
                            FilterChip(
                                selected = displayMode == mode,
                                onClick = { displayMode = mode },
                                label = { LocalizedText(ignoreModeLabel(mode)) },
                            )
                        }
                    }
                }
                item { RuleSwitch("Правило включено", enabled, { enabled = it }) }
                item { RuleSwitch("Учитывать регистр", caseSensitive, { caseSensitive = it }) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        original.copy(
                            type = type,
                            pattern = pattern.trim(),
                            enabled = enabled,
                            caseSensitive = caseSensitive,
                            displayMode = displayMode,
                        ),
                    )
                },
            ) { LocalizedText("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { LocalizedText("Отмена") } },
    )
}

@Composable
private fun RuleSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalizedText(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun highlightRuleTitle(rule: HighlightRule): String = highlightTypeLabel(rule.type)

private fun highlightRuleSummary(rule: HighlightRule): String = buildList {
    if (rule.pattern.isNotBlank()) add(rule.pattern)
    if (rule.playSound) add("звук")
    if (rule.push) add("уведомление")
    if (rule.addToMentions) add("Mentions")
    if (rule.filteredSplit) add("filtered split")
}.ifEmpty { listOf("Триггер без параметра") }.joinToString(" · ")

private fun ignoreRuleTitle(rule: IgnoreRule): String = ignoreTypeLabel(rule.type)

private fun ignoreRuleSummary(rule: IgnoreRule): String =
    "${rule.pattern} · ${ignoreModeLabel(rule.displayMode)}"

private fun highlightTypeLabel(type: HighlightRuleType): String = when (type) {
    HighlightRuleType.USERNAME -> "Имя пользователя"
    HighlightRuleType.WORD -> "Слово"
    HighlightRuleType.REGEX -> "Regex"
    HighlightRuleType.USER -> "Пользователь"
    HighlightRuleType.MODERATOR -> "Moderator"
    HighlightRuleType.VIP -> "VIP"
    HighlightRuleType.SUBSCRIBER -> "Subscriber"
    HighlightRuleType.REPLY -> "Reply"
    HighlightRuleType.REWARD -> "Reward"
    HighlightRuleType.BITS -> "Bits"
}

private fun ignoreTypeLabel(type: IgnoreRuleType): String = when (type) {
    IgnoreRuleType.USER -> "Пользователь"
    IgnoreRuleType.WORD -> "Слово"
    IgnoreRuleType.REGEX -> "Regex"
    IgnoreRuleType.BOT_COMMAND -> "Команда бота"
    IgnoreRuleType.MESSAGE_TYPE -> "Тип сообщения"
}

private fun highlightPatternLabel(type: HighlightRuleType): String = when (type) {
    HighlightRuleType.USERNAME -> "Имя; пусто — текущий Twitch login"
    HighlightRuleType.WORD -> "Слово"
    HighlightRuleType.REGEX -> "Регулярное выражение"
    HighlightRuleType.USER -> "Login, display name или Twitch ID"
    else -> "Параметр"
}

private fun ignorePatternLabel(type: IgnoreRuleType): String = when (type) {
    IgnoreRuleType.USER -> "Login, display name или Twitch ID"
    IgnoreRuleType.WORD -> "Слово"
    IgnoreRuleType.REGEX -> "Регулярное выражение"
    IgnoreRuleType.BOT_COMMAND -> "Например !song; ! — любая команда"
    IgnoreRuleType.MESSAGE_TYPE -> "CHAT, ACTION, SYSTEM, REWARD, CHEER…"
}

private fun ignoreModeLabel(mode: IgnoreDisplayMode): String = when (mode) {
    IgnoreDisplayMode.HIDE -> "Скрыть полностью"
    IgnoreDisplayMode.COLLAPSE -> "Свернуть"
    IgnoreDisplayMode.TAP_TO_REVEAL -> "Показать по нажатию"
}

private fun argbToHex(value: Long): String = "#%08X".format(value and 0xFFFFFFFFL)

private fun parseArgb(value: String): Long? {
    val normalized = value.trim().removePrefix("#")
    if (normalized.length !in setOf(6, 8)) return null
    val raw = normalized.toLongOrNull(16) ?: return null
    return if (normalized.length == 6) 0xFF000000L or raw else raw
}

private val PATTERN_HIGHLIGHT_TYPES = setOf(
    HighlightRuleType.USERNAME,
    HighlightRuleType.WORD,
    HighlightRuleType.REGEX,
    HighlightRuleType.USER,
)

private val HIGHLIGHT_COLORS = listOf(
    DEFAULT_HIGHLIGHT_COLOR_ARGB,
    0xFFFF7A90L,
    0xFF72D6FFL,
    0xFF9CE38AL,
    0xFFC9A7FFL,
    0xFFFFA45BL,
)
