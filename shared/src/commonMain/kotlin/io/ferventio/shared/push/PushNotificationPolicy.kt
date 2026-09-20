package io.ferventio.shared.push

import io.ferventio.app.domain.NotificationEventType
import io.ferventio.shared.settings.SharedAppPreferences

/**
 * Shared notification policy used by native push adapters before presentation and registration.
 * Sparse channel overrides inherit global settings, matching the settings UI.
 */
class PushNotificationPolicy {
    fun isEnabled(
        preferences: SharedAppPreferences,
        ruleId: String,
        channelId: String?,
    ): Boolean = preferences.notificationPreferences.isEnabled(
        ruleId = ruleId,
        channelId = channelId,
        legacyDefault = { rule -> legacyDefault(preferences, rule) },
    )

    fun enabledRules(
        preferences: SharedAppPreferences,
        channelIds: List<String>,
    ): List<String> = preferences.notificationPreferences.enabledRuleIds(
        channelIds = channelIds,
        legacyDefault = { rule -> legacyDefault(preferences, rule) },
    ).ifEmpty {
        listOf(BACKEND_DISABLED_RULE)
    }

    fun channelRuleOverrides(
        preferences: SharedAppPreferences,
        channelIds: List<String>,
    ): Map<String, List<String>> {
        val knownChannelIds = channelIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        if (knownChannelIds.isEmpty()) return emptyMap()
        val normalized = preferences.notificationPreferences.normalized()
        return buildMap {
            normalized.channelOverrides.forEach { (channelId, _) ->
                if (channelId !in knownChannelIds) return@forEach
                val rules = NotificationEventType.entries
                    .filter { event ->
                        normalized.isEnabled(
                            ruleId = event.ruleId,
                            channelId = channelId,
                            legacyDefault = { rule -> legacyDefault(preferences, rule) },
                        )
                    }
                    .map(NotificationEventType::ruleId)
                    .ifEmpty { listOf(BACKEND_DISABLED_RULE) }
                put(channelId, rules)
            }
        }
    }

    companion object {
        const val BACKEND_DISABLED_RULE = "__disabled__"
    }

    private fun legacyDefault(
        preferences: SharedAppPreferences,
        ruleId: String,
    ): Boolean = when (ruleId) {
        NotificationEventType.REPLY.ruleId -> preferences.replyNotificationsEnabled
        NotificationEventType.AUTOMOD_HOLD.ruleId -> preferences.autoModNotificationsEnabled
        else -> true
    }
}
