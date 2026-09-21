package io.ferventio.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.NotificationEventType
import io.ferventio.app.push.PushUiState

@Composable
internal fun NotificationSettingsContent(
    state: FerventioUiState,
    controller: FerventioController,
    pushState: PushUiState,
    onTestPush: () -> Unit,
    onReconnectPush: () -> Unit,
) {
    val context = LocalContext.current
    var expandedChannelId by rememberSaveable { mutableStateOf<String?>(null) }
    val legacyDefault: (String) -> Boolean = { ruleId ->
        when (ruleId) {
            NotificationEventType.REPLY.ruleId -> state.replyNotificationsEnabled
            NotificationEventType.AUTOMOD_HOLD.ruleId ->
                state.moderation.autoModNotificationsEnabled
            else -> true
        }
    }

    SettingsSection("Push-уведомления") {
        SettingsSwitchRow(
            title = "Все уведомления",
            description = "Мастер-переключатель. Индивидуальные правила событий и каналов сохраняются и применятся снова после включения.",
            checked = state.notificationPreferences.enabled,
            onCheckedChange = { enabled ->
                controller.setNotificationPreferences(
                    state.notificationPreferences.withEnabled(enabled),
                )
            },
        )

        LocalizedText("События", fontWeight = FontWeight.SemiBold)
        NotificationEventType.entries.forEach { event ->
            SettingsSwitchRow(
                title = notificationEventTitle(event),
                description = "Глобальное правило; канал может переопределить его ниже.",
                checked = state.notificationPreferences.isEnabled(
                    ruleId = event.ruleId,
                    legacyDefault = legacyDefault,
                ),
                onCheckedChange = { enabled ->
                    when (event) {
                        NotificationEventType.REPLY ->
                            controller.setReplyNotificationsEnabled(enabled)
                        NotificationEventType.AUTOMOD_HOLD ->
                            controller.setAutoModNotificationsEnabled(enabled)
                        else -> controller.setNotificationPreferences(
                            state.notificationPreferences.withGlobalEvent(
                                event.ruleId,
                                enabled,
                            ),
                        )
                    }
                },
            )
        }

        if (state.channels.isNotEmpty()) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LocalizedText("Настройки по каналам", fontWeight = FontWeight.SemiBold)
            state.channels.forEach { channel ->
                val custom = channel.id in state.notificationPreferences.channelOverrides
                SettingsSwitchRow(
                    title = "#${channel.displayName}: индивидуальные настройки",
                    description = if (custom) {
                        "События этого канала можно настраивать независимо."
                    } else {
                        "Используются глобальные настройки уведомлений."
                    },
                    checked = custom,
                    onCheckedChange = { enabled ->
                        controller.setNotificationPreferences(
                            if (enabled) {
                                state.notificationPreferences.enableChannelOverrides(channel.id)
                            } else {
                                state.notificationPreferences.clearChannelOverride(channel.id)
                            },
                        )
                        expandedChannelId = if (enabled) {
                            channel.id
                        } else {
                            expandedChannelId.takeUnless { it == channel.id }
                        }
                    },
                )
                if (custom) {
                    val expanded = expandedChannelId == channel.id
                    TextButton(
                        onClick = {
                            expandedChannelId = if (expanded) null else channel.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        LocalizedText(
                            if (expanded) "Скрыть настройки" else "Настроить события",
                        )
                    }
                }
                if (custom && expandedChannelId == channel.id) {
                    SettingsSwitchRow(
                        title = "Уведомления #${channel.displayName}",
                        description = "Полностью выключить уведомления только для этого канала.",
                        checked = state.notificationPreferences.channelOverrides
                            .getValue(channel.id)
                            .enabled,
                        onCheckedChange = { enabled ->
                            controller.setNotificationPreferences(
                                state.notificationPreferences.withChannelEnabled(
                                    channel.id,
                                    enabled,
                                ),
                            )
                        },
                    )
                    val channelPreferences = state.notificationPreferences
                        .channelOverrides
                        .getValue(channel.id)
                    if (channelPreferences.eventOverrides.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                controller.setNotificationPreferences(
                                    state.notificationPreferences
                                        .clearChannelEventOverrides(channel.id),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LocalizedText("Все события как глобально")
                        }
                    }
                    NotificationEventType.entries.forEach { event ->
                        val overridden = event.ruleId in state.notificationPreferences
                            .channelOverrides
                            .getValue(channel.id)
                            .eventOverrides
                        SettingsSwitchRow(
                            title = notificationEventTitle(event),
                            description = if (overridden) {
                                "Индивидуальное правило для #${channel.displayName}."
                            } else {
                                "Наследуется глобальная настройка."
                            },
                            checked = state.notificationPreferences.isEnabled(
                                ruleId = event.ruleId,
                                channelId = channel.id,
                                legacyDefault = legacyDefault,
                            ),
                            onCheckedChange = { enabled ->
                                controller.setNotificationPreferences(
                                    state.notificationPreferences.withChannelEvent(
                                        channelId = channel.id,
                                        ruleId = event.ruleId,
                                        value = enabled,
                                    ),
                                )
                            },
                        )
                        if (overridden) {
                            TextButton(
                                onClick = {
                                    controller.setNotificationPreferences(
                                        state.notificationPreferences
                                            .clearChannelEventOverride(
                                                channelId = channel.id,
                                                ruleId = event.ruleId,
                                            ),
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                LocalizedText("Как глобально")
                            }
                        }
                    }
                }
            }
        }

        LocalizedText(
            pushStatusLabel(pushState),
            color = pushStatusColor(pushState.status),
        )
        LocalizedText(
            "Уведомления подключаются автоматически после входа в Twitch. Вводить адрес сервера или отдельно включать push не нужно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    },
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            LocalizedText("Настройки уведомлений Android")
        }

        if (pushState.foregroundServiceRequired) {
            LocalizedText(
                "FOSS-сборка получает уведомления самостоятельно через постоянное защищённое соединение. Android будет показывать служебное уведомление, пока автономный push включён.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pushState.lastConnectedAtMillis?.let { timestamp ->
                LocalizedText("Последнее подключение: ${formatPushTime(timestamp)}")
            }
            pushState.lastHeartbeatAtMillis?.let { timestamp ->
                LocalizedText("Последний heartbeat: ${formatPushTime(timestamp)}")
            }
            if (pushState.reconnectAttempt > 0) {
                LocalizedText("Попытка переподключения: ${pushState.reconnectAttempt}")
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                LocalizedText("Настройки батареи")
            }
        }

        if (pushState.enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onReconnectPush,
                    modifier = Modifier.weight(1f),
                ) {
                    LocalizedText("Переподключить")
                }
                OutlinedButton(
                    onClick = onTestPush,
                    modifier = Modifier.weight(1f),
                ) {
                    LocalizedText("Отправить тест")
                }
            }
        }
    }
}

private fun notificationEventTitle(event: NotificationEventType): String = when (event) {
    NotificationEventType.MENTION -> "Упоминания"
    NotificationEventType.REPLY -> "Ответы"
    NotificationEventType.AUTOMOD_HOLD -> "AutoMod: сообщение на проверке"
    NotificationEventType.BAN -> "Баны"
    NotificationEventType.TIMEOUT -> "Таймауты"
    NotificationEventType.HIGHLIGHT -> "Highlights"
    NotificationEventType.SELECTED_USER -> "Выбранные пользователи"
    NotificationEventType.STREAM_ONLINE -> "Начало стрима"
    NotificationEventType.TITLE_CHANGE -> "Изменение названия"
    NotificationEventType.GAME_CHANGE -> "Изменение категории"
    NotificationEventType.RAID -> "Рейды"
    NotificationEventType.REWARD -> "Награды за баллы канала"
    NotificationEventType.SUBSCRIPTION -> "Подписки"
    NotificationEventType.MODERATION_ACTION -> "Действия модерации"
}
