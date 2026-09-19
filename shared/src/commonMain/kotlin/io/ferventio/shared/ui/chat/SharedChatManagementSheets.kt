package io.ferventio.shared.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ModerationChatSettings
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.shared.chat.TwitchChatManagementClient
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_modes_apply
import io.ferventio.shared.generated.resources.chat_modes_clear
import io.ferventio.shared.generated.resources.chat_modes_clear_confirm
import io.ferventio.shared.generated.resources.chat_modes_clear_summary
import io.ferventio.shared.generated.resources.chat_modes_clear_title
import io.ferventio.shared.generated.resources.chat_modes_emote_only
import io.ferventio.shared.generated.resources.chat_modes_follower_minutes
import io.ferventio.shared.generated.resources.chat_modes_followers
import io.ferventio.shared.generated.resources.chat_modes_loading
import io.ferventio.shared.generated.resources.chat_modes_slow
import io.ferventio.shared.generated.resources.chat_modes_slow_seconds
import io.ferventio.shared.generated.resources.chat_modes_subscribers
import io.ferventio.shared.generated.resources.chat_modes_title
import io.ferventio.shared.generated.resources.chat_modes_unique
import io.ferventio.shared.generated.resources.chat_reply_cancel
import io.ferventio.shared.generated.resources.chat_users_empty
import io.ferventio.shared.generated.resources.chat_users_local_fallback
import io.ferventio.shared.generated.resources.chat_users_local_only
import io.ferventio.shared.generated.resources.chat_users_search
import io.ferventio.shared.generated.resources.chat_users_title
import io.ferventio.shared.generated.resources.chat_users_twitch_total
import io.ferventio.shared.generated.resources.quick_moderation_error_title
import io.ferventio.shared.generated.resources.quick_moderation_ok
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.user.SharedUserCardSheet
import io.ferventio.shared.ui.user.projectModerationUserCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedChatUsersSheet(
    channel: ChatChannel,
    canQueryHelix: Boolean,
    onDismiss: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val client = remember { TwitchChatManagementClient() }
    val messages = runtime.chat.messages(channel.id)
    val localUsers = remember(messages) { localChatUsers(messages) }
    var remoteUsers by remember(channel.id) { mutableStateOf(emptyList<ModerationUser>()) }
    var twitchTotal by remember(channel.id) { mutableStateOf<Int?>(null) }
    var loading by remember(channel.id) { mutableStateOf(false) }
    var loadFailed by remember(channel.id) { mutableStateOf(false) }
    var query by remember(channel.id) { mutableStateOf("") }
    var userCardData by remember(channel.id) {
        mutableStateOf<io.ferventio.app.domain.UserCardData?>(null)
    }

    DisposableEffect(client) {
        onDispose { client.close() }
    }
    LaunchedEffect(channel.id, authentication, canQueryHelix) {
        remoteUsers = emptyList()
        twitchTotal = null
        loadFailed = false
        loading = false
        if (!canQueryHelix || authentication == null) return@LaunchedEffect
        loading = true
        try {
            val snapshot = client.getChatters(
                authentication = authentication,
                broadcasterId = channel.id,
            )
            remoteUsers = snapshot.users
            twitchTotal = snapshot.total
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val users = remember(remoteUsers, localUsers) {
        mergeChatUsers(remoteUsers, localUsers)
    }
    val visibleUsers = remember(users, query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) users else users.filter { user ->
            user.login.contains(normalized, ignoreCase = true) ||
                user.displayName.contains(normalized, ignoreCase = true)
        }
    }

    if (userCardData == null) {
        ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                text = stringResource(Res.string.chat_users_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.chat_users_search)) },
            )
            val notice = when {
                twitchTotal != null -> stringResource(
                    Res.string.chat_users_twitch_total,
                    twitchTotal ?: 0,
                )
                loadFailed -> stringResource(Res.string.chat_users_local_fallback)
                else -> stringResource(Res.string.chat_users_local_only)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (visibleUsers.isEmpty() && !loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.chat_users_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = visibleUsers,
                        key = { user -> user.id + ":" + user.login },
                    ) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    userCardData = projectModerationUserCard(
                                        channel = channel,
                                        user = user,
                                        channelMessages = messages,
                                        canModerate = canQueryHelix,
                                    )
                                }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = user.displayName.ifBlank { user.login },
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "@" + user.login,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    userCardData?.let { data ->
        SharedUserCardSheet(
            data = data,
            onDismiss = { userCardData = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedChatModesSheet(
    channel: ChatChannel,
    onDismiss: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication
    val client = remember { TwitchChatManagementClient() }
    val scope = rememberCoroutineScope()
    var settings by remember(channel.id) { mutableStateOf<ModerationChatSettings?>(null) }
    var loading by remember(channel.id) { mutableStateOf(true) }
    var saving by remember(channel.id) { mutableStateOf(false) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var slowSeconds by remember(channel.id) { mutableStateOf("30") }
    var followerMinutes by remember(channel.id) { mutableStateOf("0") }
    var confirmClear by remember(channel.id) { mutableStateOf(false) }

    DisposableEffect(client) {
        onDispose { client.close() }
    }
    LaunchedEffect(channel.id, authentication) {
        loading = true
        errorMessage = null
        if (authentication == null) {
            loading = false
            errorMessage = "Authentication is unavailable"
            return@LaunchedEffect
        }
        try {
            val loaded = client.getChatSettings(authentication, channel.id)
            settings = loaded
            slowSeconds = loaded.slowModeWaitSeconds.toString()
            followerMinutes = loaded.followerModeDurationMinutes.toString()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            errorMessage = error.message
        } finally {
            loading = false
        }
    }

    fun update(
        slowMode: Boolean? = null,
        slowModeWaitSeconds: Int? = null,
        followerMode: Boolean? = null,
        followerModeDurationMinutes: Int? = null,
        subscriberMode: Boolean? = null,
        emoteMode: Boolean? = null,
        uniqueChatMode: Boolean? = null,
    ) {
        val auth = authentication ?: return
        scope.launch {
            saving = true
            errorMessage = null
            try {
                val updated = client.updateChatSettings(
                    authentication = auth,
                    broadcasterId = channel.id,
                    slowMode = slowMode,
                    slowModeWaitSeconds = slowModeWaitSeconds,
                    followerMode = followerMode,
                    followerModeDurationMinutes = followerModeDurationMinutes,
                    subscriberMode = subscriberMode,
                    emoteMode = emoteMode,
                    uniqueChatMode = uniqueChatMode,
                )
                settings = updated
                slowSeconds = updated.slowModeWaitSeconds.toString()
                followerMinutes = updated.followerModeDurationMinutes.toString()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errorMessage = error.message
            } finally {
                saving = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.chat_modes_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (loading && settings == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(Res.string.chat_modes_loading),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            } else {
                val actual = settings ?: ModerationChatSettings(channel.id)
                ChatModeSwitchRow(
                    label = stringResource(Res.string.chat_modes_slow),
                    checked = actual.slowMode,
                    enabled = !saving,
                    onCheckedChange = { enabled ->
                        update(
                            slowMode = enabled,
                            slowModeWaitSeconds = slowSeconds.toIntOrNull()
                                ?.coerceIn(3, 120)
                                ?: 30,
                        )
                    },
                )
                if (actual.slowMode) {
                    ChatModeNumberRow(
                        label = stringResource(Res.string.chat_modes_slow_seconds),
                        value = slowSeconds,
                        enabled = !saving,
                        onValueChange = { slowSeconds = it.filter(Char::isDigit).take(3) },
                        onApply = {
                            slowSeconds.toIntOrNull()?.takeIf { it in 3..120 }?.let { seconds ->
                                update(slowMode = true, slowModeWaitSeconds = seconds)
                            }
                        },
                    )
                }
                ChatModeSwitchRow(
                    label = stringResource(Res.string.chat_modes_subscribers),
                    checked = actual.subscriberMode,
                    enabled = !saving,
                    onCheckedChange = { update(subscriberMode = it) },
                )
                ChatModeSwitchRow(
                    label = stringResource(Res.string.chat_modes_followers),
                    checked = actual.followerMode,
                    enabled = !saving,
                    onCheckedChange = { enabled ->
                        update(
                            followerMode = enabled,
                            followerModeDurationMinutes = followerMinutes.toIntOrNull()
                                ?.coerceIn(0, 129_600)
                                ?: 0,
                        )
                    },
                )
                if (actual.followerMode) {
                    ChatModeNumberRow(
                        label = stringResource(Res.string.chat_modes_follower_minutes),
                        value = followerMinutes,
                        enabled = !saving,
                        onValueChange = { followerMinutes = it.filter(Char::isDigit).take(6) },
                        onApply = {
                            followerMinutes.toIntOrNull()
                                ?.takeIf { it in 0..129_600 }
                                ?.let { minutes ->
                                    update(
                                        followerMode = true,
                                        followerModeDurationMinutes = minutes,
                                    )
                                }
                        },
                    )
                }
                ChatModeSwitchRow(
                    label = stringResource(Res.string.chat_modes_emote_only),
                    checked = actual.emoteMode,
                    enabled = !saving,
                    onCheckedChange = { update(emoteMode = it) },
                )
                ChatModeSwitchRow(
                    label = stringResource(Res.string.chat_modes_unique),
                    checked = actual.uniqueChatMode,
                    enabled = !saving,
                    onCheckedChange = { update(uniqueChatMode = it) },
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = !saving && authentication != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.chat_modes_clear),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(Res.string.chat_modes_clear_title)) },
            text = { Text(stringResource(Res.string.chat_modes_clear_summary)) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        val auth = authentication ?: return@Button
                        scope.launch {
                            saving = true
                            errorMessage = null
                            try {
                                runtime.moderation.clearChatMessages(
                                    authentication = auth,
                                    broadcasterId = channel.id,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                errorMessage = error.message
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) {
                    Text(stringResource(Res.string.chat_modes_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(Res.string.chat_reply_cancel))
                }
            },
        )
    }
}

@Composable
private fun ChatModeSwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun ChatModeNumberRow(
    label: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = onApply,
                enabled = enabled,
            ) {
                Text(stringResource(Res.string.chat_modes_apply))
            }
        }
    }
}

private fun localChatUsers(messages: List<ChatMessage>): List<ModerationUser> =
    messages.asReversed().asSequence()
        .filterNot(ChatMessage::isSystem)
        .filter { message -> message.userId.isNotBlank() || message.userLogin.isNotBlank() }
        .distinctBy { message ->
            message.userId.takeIf(String::isNotBlank)
                ?: message.userLogin.lowercase()
        }
        .map { message ->
            ModerationUser(
                id = message.userId.ifBlank { "local:" + message.userLogin.lowercase() },
                login = message.userLogin,
                displayName = message.userDisplayName.ifBlank { message.userLogin },
                group = ModerationUserGroup.VIEWER,
            )
        }
        .toList()

private fun mergeChatUsers(
    remote: List<ModerationUser>,
    local: List<ModerationUser>,
): List<ModerationUser> {
    val remoteIds = remote.mapTo(hashSetOf()) { user -> user.id }
    val remoteLogins = remote.mapTo(hashSetOf()) { user -> user.login.lowercase() }
    return remote + local.filterNot { user ->
        user.id in remoteIds || user.login.lowercase() in remoteLogins
    }
}
