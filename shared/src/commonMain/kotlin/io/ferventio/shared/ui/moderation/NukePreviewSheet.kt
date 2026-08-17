package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.NukeMatchMode
import io.ferventio.app.domain.NukePreviewConfig
import io.ferventio.app.domain.NukePreviewPlanner
import io.ferventio.app.domain.NukePreviewResult
import io.ferventio.app.domain.NukePreviewSample
import io.ferventio.app.domain.NukeTargetUser
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.nuke_all_target_users
import io.ferventio.shared.generated.resources.nuke_case_sensitive
import io.ferventio.shared.generated.resources.nuke_close
import io.ferventio.shared.generated.resources.nuke_error_empty_query
import io.ferventio.shared.generated.resources.nuke_error_invalid_query
import io.ferventio.shared.generated.resources.nuke_exclude_broadcaster
import io.ferventio.shared.generated.resources.nuke_exclude_moderators
import io.ferventio.shared.generated.resources.nuke_exclude_vips
import io.ferventio.shared.generated.resources.nuke_excluded_matches
import io.ferventio.shared.generated.resources.nuke_matched_messages
import io.ferventio.shared.generated.resources.nuke_matched_users
import io.ferventio.shared.generated.resources.nuke_no_matches
import io.ferventio.shared.generated.resources.nuke_plain_text
import io.ferventio.shared.generated.resources.nuke_preview_read_only
import io.ferventio.shared.generated.resources.nuke_preview_title
import io.ferventio.shared.generated.resources.nuke_query_label
import io.ferventio.shared.generated.resources.nuke_regex
import io.ferventio.shared.generated.resources.nuke_samples
import io.ferventio.shared.generated.resources.nuke_scanned_messages
import io.ferventio.shared.generated.resources.nuke_seconds
import io.ferventio.shared.generated.resources.nuke_time_window
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NukePreviewSheet(
    channelId: String,
    messages: List<ChatMessage>,
    onDismiss: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val moderatorUserId = runtime.authentication.state.authentication
        ?.accessLease
        ?.session
        ?.userId
        ?.trim()
        .orEmpty()
    val hardExcludedUserIds = remember(channelId, moderatorUserId) {
        setOf(channelId.trim(), moderatorUserId)
            .filter(String::isNotEmpty)
            .toSet()
    }
    var config by remember(channelId, hardExcludedUserIds) {
        mutableStateOf(
            NukePreviewConfig(
                query = "",
                excludedUserIds = hardExcludedUserIds,
                maxSamples = 20,
            ),
        )
    }
    var executionInFlight by remember(channelId) { mutableStateOf(false) }
    val previewedAtMillis = remember(messages, config) { currentEpochMillis() }
    val previewResult = remember(messages, config, previewedAtMillis) {
        NukePreviewPlanner.build(
            messages = messages,
            config = config,
            nowMillis = previewedAtMillis,
        )
    }
    val preview = (previewResult as? NukePreviewResult.Success)?.preview

    ModalBottomSheet(
        onDismissRequest = {
            if (!executionInFlight) onDismiss()
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(Res.string.nuke_preview_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.nuke_preview_read_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "query") {
                OutlinedTextField(
                    value = config.query,
                    onValueChange = { value ->
                        config = config.copy(query = value.take(MAX_QUERY_LENGTH))
                    },
                    enabled = !executionInFlight,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.nuke_query_label)) },
                    minLines = 1,
                    maxLines = 3,
                )
            }

            item(key = "match-mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.matchMode == NukeMatchMode.PLAIN_TEXT,
                        onClick = { config = config.copy(matchMode = NukeMatchMode.PLAIN_TEXT) },
                        enabled = !executionInFlight,
                        label = { Text(stringResource(Res.string.nuke_plain_text)) },
                    )
                    FilterChip(
                        selected = config.matchMode == NukeMatchMode.REGEX,
                        onClick = { config = config.copy(matchMode = NukeMatchMode.REGEX) },
                        enabled = !executionInFlight,
                        label = { Text(stringResource(Res.string.nuke_regex)) },
                    )
                }
            }

            item(key = "case-sensitive") {
                NukeToggleRow(
                    title = stringResource(Res.string.nuke_case_sensitive),
                    checked = config.caseSensitive,
                    enabled = !executionInFlight,
                    onCheckedChange = { config = config.copy(caseSensitive = it) },
                )
            }

            item(key = "window") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(Res.string.nuke_time_window),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NUKE_WINDOW_PRESETS_MILLIS.forEach { windowMillis ->
                            FilterChip(
                                selected = config.windowMillis == windowMillis,
                                onClick = { config = config.copy(windowMillis = windowMillis) },
                                enabled = !executionInFlight,
                                label = {
                                    Text(
                                        stringResource(
                                            Res.string.nuke_seconds,
                                            windowMillis / 1_000L,
                                        ),
                                    )
                                },
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
                            title = stringResource(Res.string.nuke_exclude_broadcaster),
                            checked = config.excludeBroadcaster,
                            enabled = !executionInFlight,
                            onCheckedChange = { config = config.copy(excludeBroadcaster = it) },
                        )
                        NukeToggleRow(
                            title = stringResource(Res.string.nuke_exclude_moderators),
                            checked = config.excludeModerators,
                            enabled = !executionInFlight,
                            onCheckedChange = { config = config.copy(excludeModerators = it) },
                        )
                        NukeToggleRow(
                            title = stringResource(Res.string.nuke_exclude_vips),
                            checked = config.excludeVips,
                            enabled = !executionInFlight,
                            onCheckedChange = { config = config.copy(excludeVips = it) },
                        )
                    }
                }
            }

            item(key = "summary") {
                when (previewResult) {
                    is NukePreviewResult.Error -> Text(
                        text = if (config.query.trim().isEmpty()) {
                            stringResource(Res.string.nuke_error_empty_query)
                        } else {
                            stringResource(Res.string.nuke_error_invalid_query)
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
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
                                NukeFact(
                                    stringResource(Res.string.nuke_matched_messages),
                                    current.matchedMessageCount,
                                )
                                NukeFact(
                                    stringResource(Res.string.nuke_matched_users),
                                    current.matchedUserCount,
                                )
                                NukeFact(
                                    stringResource(Res.string.nuke_scanned_messages),
                                    current.scannedMessageCount,
                                )
                                if (current.excludedMatchCount > 0) {
                                    NukeFact(
                                        stringResource(Res.string.nuke_excluded_matches),
                                        current.excludedMatchCount,
                                    )
                                }
                                if (current.matchedMessageCount == 0) {
                                    Text(
                                        text = stringResource(Res.string.nuke_no_matches),
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
                item(key = "samples-title") {
                    Text(
                        text = stringResource(Res.string.nuke_samples),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = preview.samples,
                    key = NukePreviewSample::messageId,
                ) { sample ->
                    NukeMessageSurface(
                        user = sample.userDisplayName.ifBlank { sample.userLogin },
                        text = sample.text,
                    )
                }
            }

            if (!preview?.matchedUsers.isNullOrEmpty()) {
                item(key = "users-title") {
                    Text(
                        text = stringResource(Res.string.nuke_all_target_users),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = preview.matchedUsers.take(MAX_VISIBLE_TARGET_USERS),
                    key = { user -> "${user.userId}:${user.userLogin}" },
                ) { user ->
                    NukeTargetUserRow(user)
                }
            }

            if (preview != null && preview.matchedUserCount > 0) {
                item(key = "execute") {
                    NukeExecutionControls(
                        channelId = channelId,
                        config = config,
                        preview = preview,
                        previewedAtMillis = previewedAtMillis,
                        onExecutionInFlightChanged = { executionInFlight = it },
                    )
                }
            }

            item(key = "close") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !executionInFlight,
                    ) {
                        Text(stringResource(Res.string.nuke_close))
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
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun NukeFact(label: String, value: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
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
            Text(
                text = user,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text,
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
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = user.userDisplayName.ifBlank { user.userLogin },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (user.userLogin.isNotBlank()) {
                Text(
                    text = "@${user.userLogin}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_QUERY_LENGTH = 256
private const val MAX_VISIBLE_TARGET_USERS = 25
private val NUKE_WINDOW_PRESETS_MILLIS = listOf(10_000L, 30_000L, 60_000L)
