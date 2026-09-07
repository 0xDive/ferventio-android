package io.ferventio.app.domain

import java.util.Locale

object UserCardModerationText {
    fun actionLabel(actionId: String, language: AppLanguage, locale: Locale = Locale.getDefault()): String {
        val resolved = language.resolve(locale)
        return when {
            actionId.startsWith("timeout:") -> actionId.substringAfter(':').toIntOrNull()
                ?.let { seconds -> "${timeoutLabel(resolved)} ${formatCompactDuration(seconds, resolved)}" }
                ?: timeoutLabel(resolved)
            actionId == "warn" -> warnLabel(resolved)
            actionId == "ban" -> banLabel(resolved)
            actionId == "unban" -> unbanLabel(resolved)
            else -> actionId
        }
    }

    fun formatCompactDuration(
        seconds: Int,
        language: AppLanguage,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (seconds <= 0) return "—"
        return when (language.resolve(locale)) {
            AppLanguage.ENGLISH -> when {
                seconds % 86_400 == 0 -> "${seconds / 86_400}d"
                seconds % 3_600 == 0 -> "${seconds / 3_600}h"
                seconds % 60 == 0 -> "${seconds / 60}m"
                else -> "${seconds}s"
            }
            AppLanguage.RUSSIAN, AppLanguage.SYSTEM -> when {
                seconds % 86_400 == 0 -> "${seconds / 86_400} д"
                seconds % 3_600 == 0 -> "${seconds / 3_600} ч"
                seconds % 60 == 0 -> "${seconds / 60} мин"
                else -> "${seconds} с"
            }
        }
    }

    fun moderationTitle(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Moderation" else "Модерация"

    fun confirmLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Confirm" else "Подтвердить"

    fun cancelLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Cancel" else "Отмена"

    fun reasonLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Reason" else "Причина"

    fun warnTitle(login: String, language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Warn @$login" else "Предупредить @$login"

    fun warnSupportingText(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) {
            "The user cannot chat until they acknowledge the warning"
        } else {
            "Пользователь не сможет писать, пока не подтвердит предупреждение"
        }

    fun dangerTitle(action: String, login: String, language: AppLanguage, locale: Locale = Locale.getDefault()): String {
        val english = language.resolve(locale) == AppLanguage.ENGLISH
        return when (action) {
            "ban" -> if (english) "Ban @$login?" else "Забанить @$login?"
            "unban" -> if (english) "Remove ban/timeout from @$login?" else "Снять бан/таймаут с @$login?"
            else -> if (english) "Block @$login for your account?" else "Заблокировать @$login для аккаунта?"
        }
    }

    fun dangerBody(action: String, language: AppLanguage, locale: Locale = Locale.getDefault()): String {
        val english = language.resolve(locale) == AppLanguage.ENGLISH
        return when (action) {
            "ban" -> if (english) {
                "The user cannot chat until a moderator unbans them."
            } else {
                "Пользователь не сможет писать в чат до ручного разбана."
            }
            "unban" -> if (english) {
                "The active ban or timeout will be removed immediately."
            } else {
                "Активный бан или таймаут будет снят немедленно."
            }
            else -> if (english) {
                "The user will be added to your personal Twitch block list."
            } else {
                "Пользователь будет добавлен в личный список блокировок Twitch."
            }
        }
    }

    fun timeoutLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Timeout" else "Таймаут"

    fun warnLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Warn" else "Предупредить"

    fun banLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Ban" else "Забанить"

    fun unbanLabel(language: AppLanguage, locale: Locale = Locale.getDefault()): String =
        if (language.resolve(locale) == AppLanguage.ENGLISH) "Unban" else "Разбанить"
}
