package io.ferventio.shared.settings

import io.ferventio.app.domain.ChatCommandParser
import io.ferventio.app.domain.UserCardModerationLayout

internal object UserCardSettingsEditor {
    const val MAX_TIMEOUT_PRESET_INPUT_LENGTH = 12

    fun parseTimeoutPreset(raw: String): Int? =
        ChatCommandParser.parseDurationSeconds(raw.trim())

    fun canAddTimeoutPreset(
        preferences: SharedAppPreferences,
        raw: String,
    ): Boolean {
        val normalized = preferences.normalized()
        val seconds = parseTimeoutPreset(raw) ?: return false
        return seconds !in normalized.userCardTimeoutPresetsSeconds &&
            normalized.userCardTimeoutPresetsSeconds.size < SharedAppPreferences.MAX_TIMEOUT_PRESETS
    }

    fun addTimeoutPreset(
        preferences: SharedAppPreferences,
        raw: String,
    ): SharedAppPreferences {
        val normalized = preferences.normalized()
        val seconds = parseTimeoutPreset(raw) ?: return normalized
        val current = normalized.userCardTimeoutPresetsSeconds
        if (seconds in current || current.size >= SharedAppPreferences.MAX_TIMEOUT_PRESETS) {
            return normalized
        }
        return normalized.withTimeoutPresets(current + seconds)
    }

    fun removeTimeoutPreset(
        preferences: SharedAppPreferences,
        seconds: Int,
    ): SharedAppPreferences {
        val normalized = preferences.normalized()
        val current = normalized.userCardTimeoutPresetsSeconds
        if (seconds !in current || current.size <= 1) return normalized
        return normalized.withTimeoutPresets(current - seconds)
    }

    fun resetTimeoutPresets(preferences: SharedAppPreferences): SharedAppPreferences =
        preferences.normalized().withTimeoutPresets(SharedAppPreferences.DEFAULT_TIMEOUT_PRESETS)

    fun moveModerationAction(
        preferences: SharedAppPreferences,
        actionId: String,
        direction: Int,
    ): SharedAppPreferences {
        val normalized = preferences.normalized()
        if (direction == 0) return normalized
        val updatedOrder = UserCardModerationLayout.move(
            storedOrder = normalized.userCardModerationActionOrder,
            timeoutPresetsSeconds = normalized.userCardTimeoutPresetsSeconds,
            actionId = actionId,
            direction = direction,
            hiddenActionIds = if (normalized.userCardShowBanAction) {
                emptySet()
            } else {
                setOf(UserCardModerationLayout.BAN)
            },
        )
        return normalized.copy(userCardModerationActionOrder = updatedOrder).normalized()
    }

    fun visibleModerationActionIds(preferences: SharedAppPreferences): List<String> {
        val normalized = preferences.normalized()
        return normalized.userCardModerationActionOrder.filterNot { actionId ->
            actionId == UserCardModerationLayout.BAN && !normalized.userCardShowBanAction
        }
    }

    fun timeoutSeconds(actionId: String): Int? {
        if (!actionId.startsWith(TIMEOUT_PREFIX)) return null
        return actionId.removePrefix(TIMEOUT_PREFIX)
            .toIntOrNull()
            ?.takeIf { it in SharedAppPreferences.MIN_TIMEOUT_SECONDS..SharedAppPreferences.MAX_TIMEOUT_SECONDS }
    }

    fun formatTimeoutPreset(seconds: Int): String = when {
        seconds % SECONDS_PER_DAY == 0 -> "${seconds / SECONDS_PER_DAY}d"
        seconds % SECONDS_PER_HOUR == 0 -> "${seconds / SECONDS_PER_HOUR}h"
        seconds % SECONDS_PER_MINUTE == 0 -> "${seconds / SECONDS_PER_MINUTE}m"
        else -> "${seconds}s"
    }

    private fun SharedAppPreferences.withTimeoutPresets(values: List<Int>): SharedAppPreferences {
        val normalizedValues = values
            .filter {
                it in SharedAppPreferences.MIN_TIMEOUT_SECONDS..SharedAppPreferences.MAX_TIMEOUT_SECONDS
            }
            .distinct()
            .take(SharedAppPreferences.MAX_TIMEOUT_PRESETS)
            .ifEmpty { SharedAppPreferences.DEFAULT_TIMEOUT_PRESETS }
        val updatedOrder = UserCardModerationLayout.normalize(
            storedOrder = userCardModerationActionOrder,
            timeoutPresetsSeconds = normalizedValues,
        )
        return copy(
            userCardTimeoutPresetsSeconds = normalizedValues,
            userCardModerationActionOrder = updatedOrder,
        ).normalized()
    }

    private const val TIMEOUT_PREFIX = "timeout:"
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
    private const val SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
}
