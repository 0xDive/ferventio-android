@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.DEFAULT_HIGHLIGHT_COLOR_ARGB
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.IgnoreRuleType
import io.ferventio.shared.generated.resources.*
import io.ferventio.shared.settings.SharedMessageRulesStateHolder
import io.ferventio.shared.settings.SharedSettingsSaveStatus
import io.ferventio.shared.ui.color.colorFromArgb
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioMessageRulesSheet(
    state: SharedMessageRulesStateHolder,
    onUpsertHighlightRule: (HighlightRule) -> Unit,
    onDeleteHighlightRule: (String) -> Unit,
    onUpsertIgnoreRule: (IgnoreRule) -> Unit,
    onDeleteIgnoreRule: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingHighlight by remember { mutableStateOf<HighlightRule?>(null) }
    var creatingHighlight by remember { mutableStateOf(false) }
    var editingIgnore by remember { mutableStateOf<IgnoreRule?>(null) }
    var creatingIgnore by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = stringResource(Res.string.message_rules_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(Res.string.message_rules_highlights),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.message_rules_highlights_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (state.highlightRules.isEmpty()) {
                Text(
                    text = stringResource(Res.string.message_rules_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                state.highlightRules.forEachIndexed { index, rule ->
                    HighlightRuleCard(
                        rule = rule,
                        onEnabledChange = { enabled ->
                            onUpsertHighlightRule(rule.copy(enabled = enabled))
                        },
                        onEdit = { editingHighlight = rule },
                        onDelete = { onDeleteHighlightRule(rule.id) },
                    )
                    if (index != state.highlightRules.lastIndex) HorizontalDivider()
                }
            }
            Button(
                onClick = { creatingHighlight = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(Res.string.message_rules_add_highlight))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 18.dp))
            Text(
                text = stringResource(Res.string.message_rules_ignore),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(Res.string.message_rules_ignore_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (state.ignoreRules.isEmpty()) {
                Text(
                    text = stringResource(Res.string.message_rules_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                state.ignoreRules.forEachIndexed { index, rule ->
                    IgnoreRuleCard(
                        rule = rule,
                        onEnabledChange = { enabled ->
                            onUpsertIgnoreRule(rule.copy(enabled = enabled))
                        },
                        onEdit = { editingIgnore = rule },
                        onDelete = { onDeleteIgnoreRule(rule.id) },
                    )
                    if (index != state.ignoreRules.lastIndex) HorizontalDivider()
                }
            }
            Button(
                onClick = { creatingIgnore = true },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(Res.string.message_rules_add_ignore))
            }

            when (state.saveStatus) {
                SharedSettingsSaveStatus.SAVING -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(stringResource(Res.string.settings_saving))
                }
                SharedSettingsSaveStatus.FAILED -> Text(
                    text = stringResource(
                        Res.string.settings_save_failed,
                        state.saveErrorMessage.orEmpty(),
                    ),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 18.dp),
                )
                SharedSettingsSaveStatus.IDLE -> Unit
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            ) {
                Text(stringResource(Res.string.message_rules_close))
            }
        }
    }

    if (creatingHighlight || editingHighlight != null) {
        HighlightRuleDialog(
            initial = editingHighlight,
            onDismiss = {
                creatingHighlight = false
                editingHighlight = null
            },
            onSave = { rule ->
                onUpsertHighlightRule(rule)
                creatingHighlight = false
                editingHighlight = null
            },
        )
    }

    if (creatingIgnore || editingIgnore != null) {
        IgnoreRuleDialog(
            initial = editingIgnore,
            onDismiss = {
                creatingIgnore = false
                editingIgnore = null
            },
            onSave = { rule ->
                onUpsertIgnoreRule(rule)
                creatingIgnore = false
                editingIgnore = null
            },
        )
    }
}

@Composable
private fun HighlightRuleCard(
    rule: HighlightRule,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val actionParts = listOfNotNull(
        stringResource(Res.string.message_rules_sound).takeIf { rule.playSound },
        stringResource(Res.string.message_rules_notification).takeIf { rule.push },
        "Mentions".takeIf { rule.addToMentions },
        "Highlights split".takeIf { rule.filteredSplit },
    )
    val summary = listOfNotNull(
        rule.pattern.takeIf(String::isNotBlank),
        actionParts.joinToString(" · ").takeIf(String::isNotBlank),
    ).ifEmpty { listOf("—") }.joinToString(" · ")
    MessageRuleCard(
        title = highlightTypeLabel(rule.type),
        summary = summary,
        enabled = rule.enabled,
        accent = colorFromArgb(rule.colorArgb),
        onEnabledChange = onEnabledChange,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun IgnoreRuleCard(
    rule: IgnoreRule,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    MessageRuleCard(
        title = ignoreTypeLabel(rule.type),
        summary = "${rule.pattern} · ${ignoreModeLabel(rule.displayMode)}",
        enabled = rule.enabled,
        accent = MaterialTheme.colorScheme.outline,
        onEnabledChange = onEnabledChange,
        onEdit = onEdit,
        onDelete = onDelete,
    )
}

@Composable
private fun MessageRuleCard(
    title: String,
    summary: String,
    enabled: Boolean,
    accent: Color,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(10.dp),
                color = accent,
                shape = MaterialTheme.shapes.extraLarge,
            ) {}
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onEdit) {
                Text(stringResource(Res.string.message_rules_edit))
            }
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(Res.string.message_rules_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun HighlightRuleDialog(
    initial: HighlightRule?,
    onDismiss: () -> Unit,
    onSave: (HighlightRule) -> Unit,
) {
    val original = initial ?: remember { HighlightRule(type = HighlightRuleType.USERNAME) }
    var type by remember(original.id) { mutableStateOf(original.type) }
    var pattern by remember(original.id) { mutableStateOf(original.pattern) }
    var enabled by remember(original.id) { mutableStateOf(original.enabled) }
    var caseSensitive by remember(original.id) { mutableStateOf(original.caseSensitive) }
    var colorArgb by remember(original.id) { mutableStateOf(original.colorArgb) }
    var colorText by remember(original.id) { mutableStateOf(argbToHex(original.colorArgb)) }
    var playSound by remember(original.id) { mutableStateOf(original.playSound) }
    var push by remember(original.id) { mutableStateOf(original.push) }
    var addToMentions by remember(original.id) { mutableStateOf(original.addToMentions) }
    var filteredSplit by remember(original.id) { mutableStateOf(original.filteredSplit) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    val requiresPattern = type in PATTERN_HIGHLIGHT_TYPES
    val regexValid = type != HighlightRuleType.REGEX ||
        pattern.isBlank() || runCatching { Regex(pattern) }.isSuccess
    val valid = (!requiresPattern || pattern.isNotBlank() || type == HighlightRuleType.USERNAME) &&
        regexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) Res.string.message_rules_new_highlight
                    else Res.string.message_rules_edit_highlight,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(
                        onClick = { typeMenuVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(highlightTypeLabel(type))
                    }
                    DropdownMenu(
                        expanded = typeMenuVisible,
                        onDismissRequest = { typeMenuVisible = false },
                    ) {
                        HighlightRuleType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(highlightTypeLabel(candidate)) },
                                onClick = {
                                    type = candidate
                                    typeMenuVisible = false
                                },
                            )
                        }
                    }
                }
                if (requiresPattern) {
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(highlightPatternLabel(type)) },
                        supportingText = if (!regexValid) {
                            { Text(stringResource(Res.string.message_rules_invalid_regex)) }
                        } else null,
                        isError = !regexValid,
                        singleLine = type != HighlightRuleType.REGEX,
                    )
                }
                Text(
                    text = stringResource(Res.string.message_rules_color),
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    HIGHLIGHT_COLORS.forEach { candidate ->
                        Surface(
                            modifier = Modifier.size(34.dp).clickable {
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
                    label = { Text(stringResource(Res.string.message_rules_color_argb)) },
                    singleLine = true,
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_enabled),
                    enabled,
                    { enabled = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_case_sensitive),
                    caseSensitive,
                    { caseSensitive = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_sound),
                    playSound,
                    { playSound = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_notification),
                    push,
                    { push = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_mentions),
                    addToMentions,
                    { addToMentions = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_filtered_split),
                    filteredSplit,
                    { filteredSplit = it },
                )
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
            ) {
                Text(stringResource(Res.string.message_rules_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.message_rules_cancel))
            }
        },
    )
}

@Composable
private fun IgnoreRuleDialog(
    initial: IgnoreRule?,
    onDismiss: () -> Unit,
    onSave: (IgnoreRule) -> Unit,
) {
    val original = initial ?: remember { IgnoreRule(type = IgnoreRuleType.USER) }
    var type by remember(original.id) { mutableStateOf(original.type) }
    var pattern by remember(original.id) { mutableStateOf(original.pattern) }
    var enabled by remember(original.id) { mutableStateOf(original.enabled) }
    var caseSensitive by remember(original.id) { mutableStateOf(original.caseSensitive) }
    var displayMode by remember(original.id) { mutableStateOf(original.displayMode) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    val regexValid = type != IgnoreRuleType.REGEX ||
        pattern.isBlank() || runCatching { Regex(pattern) }.isSuccess
    val valid = pattern.isNotBlank() && regexValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) Res.string.message_rules_new_ignore
                    else Res.string.message_rules_edit_ignore,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    OutlinedButton(
                        onClick = { typeMenuVisible = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(ignoreTypeLabel(type))
                    }
                    DropdownMenu(
                        expanded = typeMenuVisible,
                        onDismissRequest = { typeMenuVisible = false },
                    ) {
                        IgnoreRuleType.entries.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(ignoreTypeLabel(candidate)) },
                                onClick = {
                                    type = candidate
                                    typeMenuVisible = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it.take(240) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(ignorePatternLabel(type)) },
                    supportingText = if (!regexValid) {
                        { Text(stringResource(Res.string.message_rules_invalid_regex)) }
                    } else null,
                    isError = !regexValid,
                    singleLine = type != IgnoreRuleType.REGEX,
                )
                Text(
                    text = stringResource(Res.string.message_rules_display),
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    IgnoreDisplayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = displayMode == mode,
                            onClick = { displayMode = mode },
                            label = { Text(ignoreModeLabel(mode)) },
                        )
                    }
                }
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_enabled),
                    enabled,
                    { enabled = it },
                )
                MessageRuleSwitch(
                    stringResource(Res.string.message_rules_case_sensitive),
                    caseSensitive,
                    { caseSensitive = it },
                )
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
            ) {
                Text(stringResource(Res.string.message_rules_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.message_rules_cancel))
            }
        },
    )
}

@Composable
private fun MessageRuleSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun highlightTypeLabel(type: HighlightRuleType): String = when (type) {
    HighlightRuleType.USERNAME -> stringResource(Res.string.message_rules_type_username)
    HighlightRuleType.WORD -> stringResource(Res.string.message_rules_type_word)
    HighlightRuleType.REGEX -> stringResource(Res.string.message_rules_type_regex)
    HighlightRuleType.USER -> stringResource(Res.string.message_rules_type_user)
    HighlightRuleType.MODERATOR -> stringResource(Res.string.message_rules_type_moderator)
    HighlightRuleType.VIP -> stringResource(Res.string.message_rules_type_vip)
    HighlightRuleType.SUBSCRIBER -> stringResource(Res.string.message_rules_type_subscriber)
    HighlightRuleType.REPLY -> stringResource(Res.string.message_rules_type_reply)
    HighlightRuleType.REWARD -> stringResource(Res.string.message_rules_type_reward)
    HighlightRuleType.BITS -> stringResource(Res.string.message_rules_type_bits)
}

@Composable
private fun ignoreTypeLabel(type: IgnoreRuleType): String = when (type) {
    IgnoreRuleType.USER -> stringResource(Res.string.message_rules_type_user)
    IgnoreRuleType.WORD -> stringResource(Res.string.message_rules_type_word)
    IgnoreRuleType.REGEX -> stringResource(Res.string.message_rules_type_regex)
    IgnoreRuleType.BOT_COMMAND -> stringResource(Res.string.message_rules_type_bot_command)
    IgnoreRuleType.MESSAGE_TYPE -> stringResource(Res.string.message_rules_type_message_type)
}

@Composable
private fun highlightPatternLabel(type: HighlightRuleType): String = when (type) {
    HighlightRuleType.USERNAME -> stringResource(Res.string.message_rules_pattern_username)
    HighlightRuleType.WORD -> stringResource(Res.string.message_rules_pattern_word)
    HighlightRuleType.REGEX -> stringResource(Res.string.message_rules_pattern_regex)
    HighlightRuleType.USER -> stringResource(Res.string.message_rules_pattern_user)
    else -> stringResource(Res.string.message_rules_pattern_parameter)
}

@Composable
private fun ignorePatternLabel(type: IgnoreRuleType): String = when (type) {
    IgnoreRuleType.USER -> stringResource(Res.string.message_rules_pattern_user)
    IgnoreRuleType.WORD -> stringResource(Res.string.message_rules_pattern_word)
    IgnoreRuleType.REGEX -> stringResource(Res.string.message_rules_pattern_regex)
    IgnoreRuleType.BOT_COMMAND -> stringResource(Res.string.message_rules_pattern_bot_command)
    IgnoreRuleType.MESSAGE_TYPE -> stringResource(Res.string.message_rules_pattern_message_type)
}

@Composable
private fun ignoreModeLabel(mode: IgnoreDisplayMode): String = when (mode) {
    IgnoreDisplayMode.HIDE -> stringResource(Res.string.message_rules_mode_hide)
    IgnoreDisplayMode.COLLAPSE -> stringResource(Res.string.message_rules_mode_collapse)
    IgnoreDisplayMode.TAP_TO_REVEAL -> stringResource(Res.string.message_rules_mode_tap)
}

private fun argbToHex(value: Long): String = buildString(9) {
    append('#')
    for (shift in 28 downTo 0 step 4) {
        append(HEX_DIGITS[((value ushr shift) and 0xFL).toInt()])
    }
}

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

private const val HEX_DIGITS = "0123456789ABCDEF"
