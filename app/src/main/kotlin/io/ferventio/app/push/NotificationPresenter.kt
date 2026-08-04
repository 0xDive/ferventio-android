package io.ferventio.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.ferventio.app.MainActivity
import io.ferventio.app.R

class NotificationPresenter(private val context: Context) {
    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channels = listOf(
            NotificationChannel(
                CHANNEL_BACKGROUND_CONNECTION,
                "Фоновое соединение",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Постоянное соединение автономной FOSS-сборки с сервером Ferventio"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
            NotificationChannel(
                CHANNEL_MENTIONS,
                "Упоминания и ответы",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Mention, reply и выбранные пользователем highlights" },
            NotificationChannel(
                CHANNEL_MODERATION,
                "Модерация и AutoMod",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "AutoMod, ban, timeout и другие действия модерации" },
            NotificationChannel(
                CHANNEL_STREAM_STATUS,
                "Состояние трансляций",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Stream online, изменение title и game" },
            NotificationChannel(
                CHANNEL_REWARDS,
                "Награды и подписки",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Rewards, subscriptions и raids" },
            NotificationChannel(
                CHANNEL_ALERTS,
                "Другие уведомления чата",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Прочие важные события Twitch-чата" },
            NotificationChannel(
                CHANNEL_SILENT_ALERTS,
                "Тихие уведомления чата",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Highlights без звука"
                setSound(null, null)
                enableVibration(false)
            },
        )
        channels.forEach(manager::createNotificationChannel)
    }

    fun backgroundServiceNotification(status: String): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DESTINATION, "push_settings")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            BACKGROUND_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_BACKGROUND_CONNECTION)
            .setSmallIcon(R.drawable.ic_stat_ferventio)
            .setContentTitle("Ferventio работает в фоне")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText(status))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun show(payload: PushNotificationPayload) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CHANNEL_ID, payload.channelId)
            putExtra(EXTRA_CHANNEL_LOGIN, payload.channelLogin)
            putExtra(EXTRA_MESSAGE_ID, payload.messageId)
            putExtra(EXTRA_DESTINATION, payload.destination)
        }
        val requestCode = (payload.eventId ?: payload.messageId ?: payload.body).hashCode() and Int.MAX_VALUE
        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationChannel = if (payload.silent) {
            CHANNEL_SILENT_ALERTS
        } else {
            channelForType(payload.type)
        }
        val notification = NotificationCompat.Builder(context, notificationChannel)
            .setSmallIcon(R.drawable.ic_stat_ferventio)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    private fun channelForType(type: String): String = when (type) {
        "mention", "reply", "highlight", "selected_user" -> CHANNEL_MENTIONS
        "automod_hold", "ban", "timeout", "moderation_action" -> CHANNEL_MODERATION
        "stream_online", "title_change", "game_change" -> CHANNEL_STREAM_STATUS
        "raid", "reward", "subscription" -> CHANNEL_REWARDS
        else -> CHANNEL_ALERTS
    }

    companion object {
        const val CHANNEL_BACKGROUND_CONNECTION = "ferventio_background_connection"
        const val CHANNEL_MENTIONS = "ferventio_mentions"
        const val CHANNEL_MODERATION = "ferventio_moderation"
        const val CHANNEL_STREAM_STATUS = "ferventio_stream_status"
        const val CHANNEL_REWARDS = "ferventio_rewards"
        const val CHANNEL_ALERTS = "chat_alerts"
        const val CHANNEL_SILENT_ALERTS = "chat_alerts_silent"
        const val BACKGROUND_NOTIFICATION_ID = 0x465256
        const val EXTRA_CHANNEL_ID = "push_channel_id"
        const val EXTRA_CHANNEL_LOGIN = "push_channel_login"
        const val EXTRA_MESSAGE_ID = "push_message_id"
        const val EXTRA_DESTINATION = "push_destination"
    }
}
