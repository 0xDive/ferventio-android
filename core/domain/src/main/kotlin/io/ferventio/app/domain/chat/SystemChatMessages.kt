package io.ferventio.app.domain

import kotlin.time.Clock
import kotlin.uuid.Uuid

object SystemChatMessages {
    fun moderation(action: RemoteModerationAction): ChatMessage = create(
        id = "system:moderation:${action.id}",
        channelId = action.channelId,
        channelLogin = action.channelLogin,
        text = moderationText(action),
        timestamp = action.createdAt,
    )

    fun messageDeleted(
        channelId: String,
        channelLogin: String,
        messageId: String,
        eventId: String?,
        createdAt: String?,
    ): ChatMessage = create(
        id = "system:delete:${eventId ?: messageId}",
        channelId = channelId,
        channelLogin = channelLogin,
        text = "Сообщение удалено модератором.",
        timestamp = createdAt,
    )

    fun userMessagesCleared(
        channelId: String,
        channelLogin: String,
        userId: String,
        userLogin: String?,
        durationSeconds: Int?,
        isPermanent: Boolean?,
        eventId: String?,
        createdAt: String?,
    ): ChatMessage {
        val target = userLogin?.takeIf(String::isNotBlank)?.let { "@$it" }
            ?: userId.takeIf(String::isNotBlank)?.let { "пользователь $it" }
            ?: "пользователь"
        val text = when {
            durationSeconds != null && durationSeconds > 0 -> {
                "$target получил таймаут на ${formatDuration(durationSeconds)}."
            }
            isPermanent == true -> "$target заблокирован."
            else -> "Сообщения $target удалены модератором."
        }
        return create(
            id = "system:clear-user:${stableEventKey(eventId, createdAt, channelId, userId)}",
            channelId = channelId,
            channelLogin = channelLogin,
            text = text,
            timestamp = createdAt,
        )
    }

    fun chatCleared(
        channelId: String,
        channelLogin: String,
        eventId: String?,
        createdAt: String?,
    ): ChatMessage = create(
        id = "system:clear:${stableEventKey(eventId, createdAt, channelId)}",
        channelId = channelId,
        channelLogin = channelLogin,
        text = "Чат очищен модератором.",
        timestamp = createdAt,
    )

    private fun stableEventKey(
        eventId: String?,
        createdAt: String?,
        vararg identityParts: String,
    ): String = eventId?.takeIf(String::isNotBlank)
        ?: createdAt?.takeIf(String::isNotBlank)?.let { timestamp ->
            (identityParts.asList() + timestamp).joinToString(":")
        }
        ?: Uuid.random().toString()

    private fun create(
        id: String,
        channelId: String,
        channelLogin: String,
        text: String,
        timestamp: String?,
    ): ChatMessage {
        val safeTimestamp = timestamp?.takeIf(String::isNotBlank) ?: Clock.System.now().toString()
        return ChatMessage(
            id = id.ifBlank { "system:${Uuid.random()}" },
            channelId = channelId,
            channelLogin = channelLogin,
            author = ChatAuthor(id = "system", login = "system", displayName = "Twitch"),
            text = text,
            fragments = listOf(ChatFragment.Text(text)),
            timestamp = safeTimestamp,
            timestampMillis = safeTimestamp.toEpochMillisOrNow(),
            type = ChatMessageType.MODERATION,
            flags = MessageFlags(isSystem = true),
        )
    }

    internal fun moderationText(action: RemoteModerationAction): String {
        val moderator = action.moderatorName.ifBlank { action.moderatorLogin }.ifBlank { "Модератор" }
        val target = action.targetUserName?.takeIf(String::isNotBlank)
            ?: action.targetUserLogin?.takeIf(String::isNotBlank)
            ?: "пользователь"
        val reason = action.reason?.takeIf(String::isNotBlank)?.let { " Причина: $it" }.orEmpty()
        return when (action.action.lowercase()) {
            "ban", "shared_chat_ban" -> "$moderator заблокировал @$target.$reason"
            "timeout", "shared_chat_timeout" -> {
                val duration = action.durationSeconds?.takeIf { it > 0 }?.let(::formatDuration) ?: "неизвестное время"
                "$moderator выдал @$target таймаут на $duration.$reason"
            }
            "unban", "shared_chat_unban" -> "$moderator снял блокировку с @$target."
            "untimeout", "shared_chat_untimeout" -> "$moderator снял таймаут с @$target."
            "delete" -> "$moderator удалил сообщение @$target.$reason"
            "clear" -> "$moderator очистил чат."
            "warn" -> "$moderator предупредил @$target.$reason"
            "mod" -> "$moderator назначил @$target модератором."
            "unmod" -> "$moderator снял роль модератора с @$target."
            "vip" -> "$moderator назначил @$target VIP."
            "unvip" -> "$moderator снял VIP с @$target."
            "slow" -> "$moderator включил медленный режим."
            "slowoff" -> "$moderator выключил медленный режим."
            "followers" -> "$moderator включил режим только для фолловеров."
            "followersoff" -> "$moderator выключил режим только для фолловеров."
            "subscribers" -> "$moderator включил режим только для подписчиков."
            "subscribersoff" -> "$moderator выключил режим только для подписчиков."
            "emoteonly" -> "$moderator включил режим только emotes."
            "emoteonlyoff" -> "$moderator выключил режим только emotes."
            "uniquechat" -> "$moderator включил уникальный чат."
            "uniquechatoff" -> "$moderator выключил уникальный чат."
            else -> "$moderator выполнил действие «${action.action}»${action.targetUserLogin?.let { " для @$it" }.orEmpty()}.$reason"
        }.replace("..", ".")
    }

    internal fun formatDuration(seconds: Int): String = when {
        seconds % 86_400 == 0 -> "${seconds / 86_400} дн"
        seconds % 3_600 == 0 -> "${seconds / 3_600} ч"
        seconds % 60 == 0 -> "${seconds / 60} мин"
        else -> "$seconds сек"
    }
}
