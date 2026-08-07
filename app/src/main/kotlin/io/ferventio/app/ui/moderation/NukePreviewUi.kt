package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
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
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.NukeExecutionPlan
import io.ferventio.app.domain.NukeExecutionPlanResult
import io.ferventio.app.domain.NukeExecutionPlanner
import io.ferventio.app.domain.NukeMatchMode
import io.ferventio.app.domain.NukePreviewConfig
import io.ferventio.app.domain.NukePreviewPlanner
import io.ferventio.app.domain.NukePreviewResult
import io.ferventio.app.domain.NukePreviewSample
import io.ferventio.app.domain.NukeTargetUser

internal data class NukePreviewUiStrings(
    val title: String,
    val queryLabel: String,
    val plainText: String,
    val regex: String,
    val caseSensitive: String,
    val timeWindow: String,
    val excludeBroadcaster: String,
    val excludeModerators: String,
    val excludeVips: String,
    val matchedMessages: String,
    val matchedUsers: String,
    val excludedMatches: String,
    val samples: String,
    val showAllMatches: String,
    val showExamples: String,
    val allMatchedUsers: String,
    val noMatches: String,
    val confirm: String,
    val cancel: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NukePreviewSheet(
    messages: List<ChatMessage>,
    initialConfig: NukePreviewConfig,
    strings: NukePreviewUiStrings,
    onDismiss: () -> Unit,
    onConfirm: (NukeExecutionPlan) -> Unit,
) {
    var config by remember(initialConfig) { mutableStateOf(initialConfig) }
    var showAllMatches by remember(config) { mutableStateOf(false) }
    val previewedAtMillis = remember(messages, config) { System.currentTimeMillis() }
    val previewResult = remember(messages, config, previewedAtMillis) {
        NukePreviewPlanner.build(
            messages = messages,
            config = config,
            nowMillis = previewedAtMillis,
        )
    }
    val preview = (previewResult as? NukePreviewResult.Success)?.preview
    val frozenPlan = remember(config, preview, previewedAtMillis) {
        preview?.let {
            NukeExecutionPlanner.freeze(
                config = config,
                preview = it,
                previewedAtMillis = previewedAtMillis,
            )
        }
    }
    val allMatchedMessages = remember(messages, preview?.matchedMessageIds) {
        val ids = preview?.matchedMessageIds?.toHashSet().orEmpty()
        if (ids.isEmpty()) emptyList() else messages.filter { it.id in ids }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                LocalizedText(
                    strings.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            item(key = "query") {
                OutlinedTextField(
                    value = config.query,
                    onValueChange = { value -> config = config.copy(query = value.take(256)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { LocalizedText(strings.queryLabel) },
                    minLines = 1,
                    maxLines = 3,
                )
            }

            item(key = "match-mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.matchMode == NukeMatchMode.PLAIN_TEXT,
                        onClick = { config = config.copy(matchMode = NukeMatchMode.PLAIN_TEXT) },
                        label = { LocalizedText(strings.plainText) },
                    )
                    FilterChip(
                        selected = config.matchMode == NukeMatchMode.REGEX,
                        onClick = { config = config.copy(matchMode = NukeMatchMode.REGEX) },
                        label = { LocalizedText(strings.regex) },
                    )
                }
            }

            item(key = "case-sensitive") {
                NukeToggleRow(
                    title = strings.caseSensitive,
                    checked = config.caseSensitive,
                    onCheckedChange = { config = config.copy(caseSensitive = it) },
                )
            }

            item(key = "window") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LocalizedText(
                        strings.timeWindow,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NUKE_WINDOW_PRESETS_MILLIS.forEach { windowMillis ->
                            FilterChip(
                                selected = config.windowMillis == windowMillis,
                                onClick = { config = config.copy(windowMillis = windowMillis) },
                                label = { VerbatimText("${windowMillis / 1_000}s") },
                            )
                        }
                    }
                }
            }

            item(key = "exclusions") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        NukeToggleRow(
                            title = strings.excludeBroadcaster,
                            checked = config.excludeBroadcaster,
                            onCheckedChange = { config = config.copy(excludeBroadcaster = it) },
                        )
                        NukeToggleRow(
                            title = strings.excludeModerators,
                            checked = config.excludeModerators,
                            onCheckedChange = { config = config.copy(excludeModerators = it) },
                        )
                        NukeToggleRow(
                            title = strings.excludeVips,
                            checked = config.excludeVips,
                            onCheckedChange = { config = config.copy(excludeVips = it) },
                        )
                    }
                }
            }

            item(key = "summary") {
                when (previewResult) {
                    is NukePreviewResult.Error -> LocalizedText(
                        previewResult.message,
                        color = MaterialTheme.colorScheme.error,
                    )

                    is NukePreviewResult.Success -> {
                        val current = previewResult.preview
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                NukeFact(strings.matchedMessages, current.matchedMessageCount)
                                NukeFact(strings.matchedUsers, current.matchedUserCount)
                                if (current.excludedMatchCount > 0) {
                                    NukeFact(strings.excludedMatches, current.excludedMatchCount)
                                }
                                if (current.matchedMessageCount == 0) {
                                    Spacer(Modifier.height(2.dp))
                                    LocalizedText(
                                        strings.noMatches,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!preview?.samples.isNullOrEmpty()) {
                item(key = "preview-mode") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LocalizedText(
                            if (showAllMatches) strings.matchedMessages else strings.samples,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (preview.matchedMessageCount > preview.samples.size) {
                            TextButton(onClick = { showAllMatches = !showAllMatches }) {
                                LocalizedText(
                                    if (showAllMatches) strings.showExamples
                                    else "${strings.showAllMatches} (${preview.matchedMessageCount})",
                                )
                            }
                        }
                    }
                }

                if (showAllMatches) {
                    items(
                        items = allMatchedMessages,
                        key = { message -> "nuke-match:${message.id}" },
                    ) { message ->
                        NukeMatchedMessageRow(message)
                    }
                    item(key = "all-user-title") {
                        LocalizedText(
                            strings.allMatchedUsers,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(
                        items = preview.matchedUsers,
                        key = { user -> "nuke-user:${user.userId.ifBlank { user.userLogin }}" },
                    ) { user ->
                        NukeTargetUserRow(user)
                    }
                } else {
                    items(
                        items = preview.samples,
                        key = NukePreviewSample::messageId,
                    ) { sample ->
                        NukeSampleRow(sample)
                    }
                }
            }

            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        LocalizedText(strings.cancel)
                    }
                    OutlinedButton(
                        onClick = {
                            val plan = (frozenPlan as? NukeExecutionPlanResult.Success)?.plan
                                ?: return@OutlinedButton
                            onConfirm(plan)
                        },
                        enabled = frozenPlan is NukeExecutionPlanResult.Success,
                    ) {
                        LocalizedText(strings.confirm)
                    }
                }
            }
        }
    }
}

@Composable
private fun NukeToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalizedText(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NukeFact(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LocalizedText(label, style = MaterialTheme.typography.bodyMedium)
        VerbatimText(value.toString(), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NukeSampleRow(sample: NukePreviewSample) {
    NukeMessageSurface(
        user = sample.userDisplayName.ifBlank { sample.userLogin },
        text = sample.text,
    )
}

@Composable
private fun NukeMatchedMessageRow(message: ChatMessage) {
    NukeMessageSurface(
        user = message.userDisplayName.ifBlank { message.userLogin },
        text = message.text,
    )
}

@Composable
private fun NukeMessageSurface(user: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            VerbatimText(
                user,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            VerbatimText(
                text,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun NukeTargetUserRow(user: NukeTargetUser) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            VerbatimText(
                user.userDisplayName.ifBlank { user.userLogin },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (user.userLogin.isNotBlank()) {
                VerbatimText(
                    "@${user.userLogin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val NUKE_WINDOW_PRESETS_MILLIS = listOf(10_000L, 30_000L, 60_000L)
