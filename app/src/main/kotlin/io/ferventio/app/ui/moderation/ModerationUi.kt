package io.ferventio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.ferventio.app.application.FerventioController
import io.ferventio.app.application.loadCategorizedCommunityChatters
import io.ferventio.app.application.mergeCategorizedChatters
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.BannedChatUser
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.ModerationChatSettings
import io.ferventio.app.domain.ModerationPeopleTab
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.app.domain.ModerationUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ModerationPanel(val title: String) {
    AUTOMOD("AutoMod"),
    MODES("Режимы"),
    PEOPLE("Пользователи"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModerationScreen(
    state: FerventioUiState,
    controller: FerventioController,
    snackbarHostState: SnackbarHostState,
) {
    val moderatedChannels = state.channels.filter { it.id in state.moderatedChannelIds }
    val selectedChannelId = state.moderation.selectedChannelId
        ?.takeIf { id -> moderatedChannels.any { it.id == id } }
        ?: state.selectedChannelId?.takeIf { it in state.moderatedChannelIds }
        ?: moderatedChannels.firstOrNull()?.id
    var panel by rememberSaveable { mutableStateOf(ModerationPanel.AUTOMOD) }

    LaunchedEffect(selectedChannelId) {
        if (selectedChannelId != null && state.moderation.selectedChannelId != selectedChannelId) {
            controller.selectModerationChannel(selectedChannelId)
        } else if (selectedChannelId != null && state.moderation.chatSettings?.channelId != selectedChannelId) {
            controller.refreshModerationDashboard(selectedChannelId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LocalizedText("Модерация", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { controller.refreshModerationDashboard(selectedChannelId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = localizedString("Обновить"))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            if (moderatedChannels.isEmpty()) {
                EmptyModerationState()
                return@Column
            }

            ModerationChannelPicker(
                channels = moderatedChannels,
                selectedChannelId = selectedChannelId,
                onSelected = controller::selectModerationChannel,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ModerationPanel.entries.forEach { item ->
                    FilterChip(
                        selected = item == panel,
                        onClick = { panel = item },
                        label = { LocalizedText(item.title) },
                    )
                }
            }

            when (panel) {
                ModerationPanel.AUTOMOD -> AutoModPanel(
                    queue = state.moderation.autoModQueue.filter { it.channelId == selectedChannelId },
                    notificationsEnabled = state.moderation.autoModNotificationsEnabled,
                    onNotificationsChanged = controller::setAutoModNotificationsEnabled,
                    onDecision = controller::decideAutoModMessage,
                    modifier = Modifier.weight(1f),
                )

                ModerationPanel.MODES -> ChatModesPanel(
                    channelId = selectedChannelId,
                    settings = state.moderation.chatSettings,
                    loading = state.moderation.isLoading,
                    onUpdate = controller::updateModerationChatSettings,
                    onClear = controller::clearModeratedChat,
                    modifier = Modifier.weight(1f),
                )

                ModerationPanel.PEOPLE -> PeoplePanel(
                    channelId = selectedChannelId,
                    selectedTab = state.moderation.peopleTab,
                    availableTabs = ModerationPeopleTab.entries,
                    chatters = state.moderation.chatters,
                    moderators = state.moderation.moderators,
                    vips = state.moderation.vips,
                    banned = state.moderation.bannedUsers,
                    loading = state.moderation.isRefreshingPeople,
                    notice = state.moderation.peopleNotice,
                    controller = controller,
                    onTabSelected = controller::refreshModerationPeople,
                    onOpenUser = { login -> controller.openUserCardByLogin(selectedChannelId, login) },
                    onUnban = { user ->
                        selectedChannelId?.let { controller.unbanFromModeration(it, user) }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            state.moderation.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                LocalizedText(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun AutoModInlineReviewCard(
    message: AutoModHeldMessage,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val allowColor = if (isSystemInDarkTheme()) Color(0xFF39F23F) else Color(0xFF167D20)
    val localizedReason = localizedString(autoModInlineReason(message))
    val publishExplanation = localizedString("Разрешение опубликует сообщение в чате.")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = allowColor,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                VerbatimText(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) { append("AutoMod: ") }
                        append(localizedReason)
                        append(" ")
                        append(publishExplanation)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                LocalizedText(
                    "Разрешить",
                    color = allowColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable(onClick = onApprove)
                        .padding(PaddingValues(horizontal = 5.dp, vertical = 4.dp)),
                )
                LocalizedText(
                    "Отклонить",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable(onClick = onDeny)
                        .padding(PaddingValues(horizontal = 5.dp, vertical = 4.dp)),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                VerbatimText(
                    formatAutoModTime(message.heldAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.width(5.dp))
                VerbatimText(
                    "${message.userName.ifBlank { message.userLogin }}: ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                VerbatimText(
                    text = autoModAnnotatedText(message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatModesBottomSheet(
    channelId: String,
    state: ModerationUiState,
    controller: FerventioController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(channelId) {
        if (state.selectedChannelId != channelId) {
            controller.selectModerationChannel(channelId)
        } else {
            controller.refreshModerationDashboard(channelId)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 14.dp),
        ) {
            LocalizedText(
                "Режимы чата",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            ChatModesPanel(
                channelId = channelId,
                settings = state.chatSettings?.takeIf { it.channelId == channelId },
                loading = state.isLoading,
                onUpdate = controller::updateModerationChatSettings,
                onClear = controller::clearModeratedChat,
                modifier = Modifier.weight(1f),
            )
            state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                LocalizedText(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatUsersBottomSheet(
    channelId: String,
    state: ModerationUiState,
    canModerate: Boolean,
    isOwner: Boolean,
    controller: FerventioController,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(channelId) {
        controller.selectChatUsersChannel(channelId)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 14.dp),
        ) {
            LocalizedText(
                "Пользователи",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            PeoplePanel(
                channelId = channelId,
                selectedTab = state.peopleTab,
                availableTabs = if (isOwner) ModerationPeopleTab.entries else listOf(ModerationPeopleTab.CHATTERS),
                chatters = state.chatters,
                moderators = state.moderators,
                vips = state.vips,
                banned = state.bannedUsers,
                loading = state.isRefreshingPeople,
                notice = state.peopleNotice,
                controller = controller,
                onTabSelected = controller::refreshModerationPeople,
                onOpenUser = { login -> controller.openUserCardByLogin(channelId, login) },
                onUnban = { user -> controller.unbanFromModeration(channelId, user) },
                modifier = Modifier.weight(1f),
            )
            state.errorMessage?.takeIf(String::isNotBlank)?.let { error ->
                LocalizedText(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (canModerate && !state.chattersAreComplete && state.isRefreshingPeople) {
                LocalizedText(
                    "Получаем полный список Twitch…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyModerationState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(10.dp))
            LocalizedText("Нет добавленных каналов, где доступна модерация")
            LocalizedText(
                "Права обновляются после повторного входа и подключения EventSub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModerationChannelPicker(
    channels: List<ChatChannel>,
    selectedChannelId: String?,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = channels.firstOrNull { it.id == selectedChannelId } ?: channels.first()
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            VerbatimText("#${selected.displayName}", modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            channels.forEach { channel ->
                DropdownMenuItem(
                    text = { VerbatimText("#${channel.displayName}") },
                    onClick = {
                        expanded = false
                        onSelected(channel.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AutoModPanel(
    queue: List<AutoModHeldMessage>,
    notificationsEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    onDecision: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDeny by remember { mutableStateOf<AutoModHeldMessage?>(null) }
    Column(modifier) {
        SettingSwitchRow(
            title = "Уведомлять о новых сообщениях AutoMod",
            checked = notificationsEnabled,
            onCheckedChange = onNotificationsChanged,
        )
        HorizontalDivider()
        val held = queue.filter { it.status == AutoModMessageStatus.HELD }
        val recent = queue.filter { it.status != AutoModMessageStatus.HELD }
        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (held.isEmpty()) {
                item {
                    LocalizedText(
                        "Очередь AutoMod пуста",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                item { SectionTitle("Ожидают решения · ${held.size}") }
                items(held, key = { "held:${it.messageId}" }) { message ->
                    AutoModMessageCard(
                        message = message,
                        onApprove = { onDecision(message.messageId, true) },
                        onDeny = { pendingDeny = message },
                    )
                }
            }
            if (recent.isNotEmpty()) {
                item { SectionTitle("Недавние решения") }
                items(recent.take(30), key = { "recent:${it.messageId}:${it.status}" }) { message ->
                    AutoModMessageCard(message = message, onApprove = null, onDeny = null)
                }
            }
        }
    }

    pendingDeny?.let { message ->
        AlertDialog(
            onDismissRequest = { pendingDeny = null },
            title = { LocalizedText("Отклонить сообщение?") },
            text = { LocalizedText("Сообщение @${verbatimArgument(message.userLogin)} будет удалено из очереди AutoMod.") },
            confirmButton = {
                Button(onClick = {
                    pendingDeny = null
                    onDecision(message.messageId, false)
                }) { LocalizedText("Отклонить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeny = null }) { LocalizedText("Отмена") }
            },
        )
    }
}

@Composable
private fun AutoModMessageCard(
    message: AutoModHeldMessage,
    onApprove: (() -> Unit)?,
    onDeny: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (message.status) {
                AutoModMessageStatus.HELD -> MaterialTheme.colorScheme.surfaceContainer
                AutoModMessageStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                AutoModMessageStatus.DENIED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VerbatimText(
                    "@${message.userLogin}",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                LocalizedText(
                    autoModStatusText(message.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = when (message.status) {
                        AutoModMessageStatus.HELD -> MaterialTheme.colorScheme.tertiary
                        AutoModMessageStatus.APPROVED -> MaterialTheme.colorScheme.primary
                        AutoModMessageStatus.DENIED -> MaterialTheme.colorScheme.error
                    },
                )
            }
            VerbatimText(
                text = autoModAnnotatedText(message),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            val categoryLabel = localizedString("категория")
            val levelLabel = localizedString("уровень")
            val details = listOfNotNull(
                message.reason?.takeIf(String::isNotBlank),
                message.category?.takeIf(String::isNotBlank)?.let { "$categoryLabel: $it" },
                message.level?.let { "$levelLabel: $it" },
            ).joinToString(" · ")
            if (details.isNotBlank()) {
                VerbatimText(
                    details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onApprove != null && onDeny != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDeny) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        LocalizedText("Отклонить")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onApprove) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        LocalizedText("Разрешить")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatModesPanel(
    channelId: String?,
    settings: ModerationChatSettings?,
    loading: Boolean,
    onUpdate: (
        channelId: String,
        slowMode: Boolean?,
        slowSeconds: Int?,
        followerMode: Boolean?,
        followerMinutes: Int?,
        subscriberMode: Boolean?,
        emoteMode: Boolean?,
        uniqueChatMode: Boolean?,
    ) -> Unit,
    onClear: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channelId == null) return
    var slowSeconds by rememberSaveable(settings?.slowModeWaitSeconds) {
        mutableIntStateOf(settings?.slowModeWaitSeconds ?: 30)
    }
    var followerMinutes by rememberSaveable(settings?.followerModeDurationMinutes) {
        mutableIntStateOf(settings?.followerModeDurationMinutes ?: 0)
    }
    var confirmClear by remember { mutableStateOf(false) }

    if (loading && settings == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val actual = settings ?: ModerationChatSettings(channelId)
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            SectionTitle("Режимы чата")
            SettingSwitchRow("Slow mode", actual.slowMode) { enabled ->
                onUpdate(channelId, enabled, slowSeconds, null, null, null, null, null)
            }
            if (actual.slowMode) {
                NumberSetting(
                    label = "Задержка между сообщениями, секунд",
                    value = slowSeconds,
                    range = 3..120,
                    onValueChanged = { slowSeconds = it },
                    onApply = { onUpdate(channelId, true, slowSeconds, null, null, null, null, null) },
                )
            }
            SettingSwitchRow("Только подписчики", actual.subscriberMode) { enabled ->
                onUpdate(channelId, null, null, null, null, enabled, null, null)
            }
            SettingSwitchRow("Только фолловеры", actual.followerMode) { enabled ->
                onUpdate(channelId, null, null, enabled, followerMinutes, null, null, null)
            }
            if (actual.followerMode) {
                NumberSetting(
                    label = "Минимальный срок подписки, минут",
                    value = followerMinutes,
                    range = 0..129_600,
                    onValueChanged = { followerMinutes = it },
                    onApply = { onUpdate(channelId, null, null, true, followerMinutes, null, null, null) },
                )
            }
            SettingSwitchRow("Только emotes", actual.emoteMode) { enabled ->
                onUpdate(channelId, null, null, null, null, null, enabled, null)
            }
            SettingSwitchRow("Уникальный чат", actual.uniqueChatMode) { enabled ->
                onUpdate(channelId, null, null, null, null, null, null, enabled)
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            LocalizedText("Опасные действия", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { confirmClear = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                LocalizedText("Очистить весь чат", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { LocalizedText("Очистить чат?") },
            text = { LocalizedText("Все сообщения в текущем чате будут удалены. Это действие нельзя отменить.") },
            confirmButton = {
                Button(onClick = {
                    confirmClear = false
                    onClear(channelId)
                }) { LocalizedText("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { LocalizedText("Отмена") } },
        )
    }
}

@Composable
private fun PeoplePanel(
    channelId: String?,
    selectedTab: ModerationPeopleTab,
    availableTabs: List<ModerationPeopleTab>,
    chatters: List<ModerationUser>,
    moderators: List<ModerationUser>,
    vips: List<ModerationUser>,
    banned: List<BannedChatUser>,
    loading: Boolean,
    notice: String?,
    controller: FerventioController,
    onTabSelected: (ModerationPeopleTab) -> Unit,
    onOpenUser: (String) -> Unit,
    onUnban: (BannedChatUser) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingUnban by remember { mutableStateOf<BannedChatUser?>(null) }
    var categorizedChatters by remember(channelId) { mutableStateOf<List<ModerationUser>>(emptyList()) }
    val chattersListState = rememberLazyListState()
    val moderationPreferences = rememberQuickModerationPreferenceState()
    LaunchedEffect(channelId, selectedTab) {
        if (channelId != null && selectedTab == ModerationPeopleTab.CHATTERS) {
            categorizedChatters = runCatching {
                controller.loadCategorizedCommunityChatters(channelId)
            }.getOrDefault(emptyList())
        }
    }
    val effectiveChatters = remember(chatters, categorizedChatters) {
        mergeCategorizedChatters(chatters, categorizedChatters)
    }
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            availableTabs.forEach { tab ->
                FilterChip(
                    selected = selectedTab == tab,
                    onClick = { onTabSelected(tab) },
                    label = { LocalizedText(peopleTabTitle(tab)) },
                )
            }
        }
        notice?.takeIf(String::isNotBlank)?.let { message ->
            LocalizedText(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        if (loading && selectedTab != ModerationPeopleTab.CHATTERS) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        when (selectedTab) {
            ModerationPeopleTab.BANNED -> LazyColumn(Modifier.fillMaxSize()) {
                if (banned.isEmpty()) item { EmptyListText("Нет заблокированных пользователей") }
                items(banned, key = BannedChatUser::id) { user ->
                    BannedUserRow(
                        user = user,
                        onOpen = { onOpenUser(user.login) },
                        onUnban = {
                            if (moderationPreferences.confirmActions) pendingUnban = user else onUnban(user)
                        },
                    )
                }
            }
            ModerationPeopleTab.CHATTERS -> {
                val grouped = remember(effectiveChatters) {
                    CHATTER_GROUP_ORDER.mapNotNull { group ->
                        effectiveChatters
                            .filter { user -> user.group == group }
                            .takeIf(List<ModerationUser>::isNotEmpty)
                            ?.let { users -> group to users }
                    }
                }
                val groupTopology = remember(grouped) { grouped.map { (group, _) -> group } }
                LaunchedEffect(channelId, selectedTab, groupTopology) {
                    chattersListState.scrollToItem(0)
                }
                LazyColumn(
                    state = chattersListState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (loading) {
                        item(key = "chatters-loading") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                LocalizedText(
                                    "Обновляем категории Twitch…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (effectiveChatters.isEmpty()) {
                        item { EmptyListText("Список пуст или недоступен для этого канала") }
                    }
                    grouped.forEach { (group, users) ->
                        item(key = "chatter-group:$group") {
                            LocalizedText(
                                "${chatterGroupTitle(group)} · ${users.size}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                        items(
                            items = users,
                            key = { user -> "chatter:$group:${user.id}" },
                        ) { user ->
                            ModerationUserRow(user = user, onOpenUser = onOpenUser)
                        }
                    }
                }
            }
            else -> {
                val users = when (selectedTab) {
                    ModerationPeopleTab.MODERATORS -> moderators
                    ModerationPeopleTab.VIPS -> vips
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    if (users.isEmpty()) item { EmptyListText("Список пуст или недоступен для этого канала") }
                    items(users, key = ModerationUser::id) { user ->
                        ModerationUserRow(user = user, onOpenUser = onOpenUser)
                    }
                }
            }
        }
    }

    pendingUnban?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingUnban = null },
            title = { LocalizedText("Разблокировать @${verbatimArgument(user.login)}?") },
            text = { LocalizedText("Ban или активный timeout будет снят немедленно.") },
            confirmButton = {
                Button(onClick = {
                    pendingUnban = null
                    onUnban(user)
                }) { LocalizedText("Разблокировать") }
            },
            dismissButton = { TextButton(onClick = { pendingUnban = null }) { LocalizedText("Отмена") } },
        )
    }
}

@Composable
private fun ModerationUserRow(
    user: ModerationUser,
    onOpenUser: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUser(user.login) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            VerbatimText(user.displayName, fontWeight = FontWeight.SemiBold)
            VerbatimText("@${user.login}", style = MaterialTheme.typography.bodySmall)
        }
    }
    HorizontalDivider()
}

@Composable
private fun BannedUserRow(
    user: BannedChatUser,
    onOpen: () -> Unit,
    onUnban: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            VerbatimText(user.displayName, fontWeight = FontWeight.SemiBold)
            val restriction = if (user.isPermanent) {
                "ban"
            } else {
                localizedString("timeout до ${formatInstant(user.expiresAt)}")
            }
            VerbatimText(
                listOfNotNull(
                    "@${user.login}",
                    restriction,
                    user.reason?.takeIf(String::isNotBlank),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onUnban) { LocalizedText("Unban") }
    }
    HorizontalDivider()
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LocalizedText(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NumberSetting(
    label: String,
    value: Int,
    range: IntRange,
    onValueChanged: (Int) -> Unit,
    onApply: () -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter(Char::isDigit).take(6)
                text.toIntOrNull()?.coerceIn(range)?.let(onValueChanged)
            },
            label = { LocalizedText(label) },
            modifier = Modifier.weight(1f),
            singleLine = true,
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = onApply) { LocalizedText("Применить") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    LocalizedText(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@Composable
private fun EmptyListText(text: String) {
    LocalizedText(
        text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

@Composable
private fun autoModAnnotatedText(message: AutoModHeldMessage): AnnotatedString {
    val blockedBackground = MaterialTheme.colorScheme.errorContainer
    val blockedForeground = MaterialTheme.colorScheme.onErrorContainer
    return buildAnnotatedString {
        append(message.text)
        message.boundaries.forEach { boundary ->
            val start = boundary.start.coerceIn(0, message.text.length)
            val endExclusive = (boundary.endInclusive + 1).coerceIn(start, message.text.length)
            if (endExclusive > start) {
                addStyle(
                    SpanStyle(background = blockedBackground, color = blockedForeground, fontWeight = FontWeight.Bold),
                    start = start,
                    end = endExclusive,
                )
            }
        }
    }
}

private fun autoModInlineReason(message: AutoModHeldMessage): String {
    val reason = message.reason?.trim().orEmpty()
    return when (reason.lowercase()) {
        "blocked_term" -> {
            val terms = blockedAutoModTerms(message)
            when (terms.size) {
                0 -> "удержал сообщение: совпадение с запрещённым термином."
                1 -> "удержал сообщение: совпадение с запрещённым термином \"${terms.single()}\"."
                else -> "удержал сообщение: совпадение с ${terms.size} запрещёнными терминами."
            }
        }
        "automod" -> {
            val details = listOfNotNull(
                message.category?.takeIf(String::isNotBlank)?.let { "категория $it" },
                message.level?.let { "уровень $it" },
            ).joinToString(", ")
            if (details.isBlank()) "удержал сообщение AutoMod." else "удержал сообщение AutoMod ($details)."
        }
        "" -> "удержал сообщение."
        else -> "удержал сообщение: $reason."
    }
}

private fun blockedAutoModTerms(message: AutoModHeldMessage): List<String> = message.boundaries
    .mapNotNull { boundary ->
        val start = boundary.start.coerceIn(0, message.text.length)
        val endExclusive = (boundary.endInclusive + 1).coerceIn(start, message.text.length)
        message.text.substring(start, endExclusive)
            .trim()
            .takeIf(String::isNotBlank)
    }
    .distinct()

private val autoModTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatAutoModTime(value: String): String = runCatching {
    autoModTimeFormatter.format(Instant.parse(value))
}.getOrDefault("--:--")

private fun autoModStatusText(status: AutoModMessageStatus): String = when (status) {
    AutoModMessageStatus.HELD -> "Ожидает"
    AutoModMessageStatus.APPROVED -> "Разрешено"
    AutoModMessageStatus.DENIED -> "Отклонено"
}

private fun peopleTabTitle(tab: ModerationPeopleTab): String = when (tab) {
    ModerationPeopleTab.CHATTERS -> "Чат"
    ModerationPeopleTab.MODERATORS -> "Моды"
    ModerationPeopleTab.VIPS -> "VIP"
    ModerationPeopleTab.BANNED -> "Баны"
}

private val CHATTER_GROUP_ORDER = listOf(
    ModerationUserGroup.BROADCASTER,
    ModerationUserGroup.MODERATOR,
    ModerationUserGroup.VIP,
    ModerationUserGroup.STAFF,
    ModerationUserGroup.CHATBOT,
    ModerationUserGroup.VIEWER,
    ModerationUserGroup.UNKNOWN,
)

private fun chatterGroupTitle(group: ModerationUserGroup): String = when (group) {
    ModerationUserGroup.BROADCASTER -> "Владелец канала"
    ModerationUserGroup.STAFF -> "Staff Twitch"
    ModerationUserGroup.VIP -> "VIP"
    ModerationUserGroup.MODERATOR -> "Модераторы"
    ModerationUserGroup.CHATBOT -> "Боты"
    ModerationUserGroup.VIEWER -> "Зрители"
    ModerationUserGroup.UNKNOWN -> "Остальные"
}

private val moderationTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    .withZone(ZoneId.systemDefault())

private fun formatInstant(value: String?): String = value
    ?.takeIf(String::isNotBlank)
    ?.let { runCatching { moderationTimeFormatter.format(Instant.parse(it)) }.getOrNull() }
    ?: "—"