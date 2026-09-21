package io.ferventio.app.domain

enum class NotificationEventType(val ruleId: String) {
    MENTION("mention"),
    REPLY("reply"),
    AUTOMOD_HOLD("automod_hold"),
    BAN("ban"),
    TIMEOUT("timeout"),
    HIGHLIGHT("highlight"),
    SELECTED_USER("selected_user"),
    STREAM_ONLINE("stream_online"),
    TITLE_CHANGE("title_change"),
    GAME_CHANGE("game_change"),
    RAID("raid"),
    REWARD("reward"),
    SUBSCRIPTION("subscription"),
    MODERATION_ACTION("moderation_action");

    companion object {
        val allRuleIds: List<String> = entries.map(NotificationEventType::ruleId)

        fun fromRuleId(value: String): NotificationEventType? {
            val normalized = value.trim().lowercase()
            return entries.firstOrNull { it.ruleId == normalized }
        }
    }
}

data class ChannelNotificationPreferences(
    val enabled: Boolean = true,
    val eventOverrides: Map<String, Boolean> = emptyMap(),
) {
    fun normalized(): ChannelNotificationPreferences = copy(
        eventOverrides = normalizeNotificationEventOverrides(eventOverrides),
    )
}

data class NotificationPreferences(
    val enabled: Boolean = true,
    val eventOverrides: Map<String, Boolean> = emptyMap(),
    val channelOverrides: Map<String, ChannelNotificationPreferences> = emptyMap(),
) {
    fun normalized(): NotificationPreferences {
        val normalizedChannels = linkedMapOf<String, ChannelNotificationPreferences>()
        channelOverrides.entries
            .asSequence()
            .mapNotNull { (rawChannelId, preferences) ->
                rawChannelId.trim().takeIf(String::isNotEmpty)?.let { it to preferences.normalized() }
            }
            .distinctBy(Pair<String, ChannelNotificationPreferences>::first)
            .take(MAX_CHANNEL_OVERRIDES)
            .forEach { (channelId, preferences) ->
                normalizedChannels[channelId] = preferences
            }
        return copy(
            eventOverrides = normalizeNotificationEventOverrides(eventOverrides),
            channelOverrides = normalizedChannels,
        )
    }

    fun isEnabled(
        ruleId: String,
        channelId: String? = null,
        legacyDefault: (String) -> Boolean = { true },
    ): Boolean {
        if (!enabled) return false
        val normalizedRuleId = ruleId.trim().lowercase()
        if (normalizedRuleId.isEmpty()) return false

        val globalEnabled = eventOverrides[normalizedRuleId] ?: legacyDefault(normalizedRuleId)
        val normalizedChannelId = channelId?.trim()?.takeIf(String::isNotEmpty)
            ?: return globalEnabled
        val channel = channelOverrides[normalizedChannelId] ?: return globalEnabled
        if (!channel.enabled) return false
        return channel.eventOverrides[normalizedRuleId] ?: globalEnabled
    }

    fun enabledRuleIds(
        channelIds: Iterable<String>,
        legacyDefault: (String) -> Boolean = { true },
    ): List<String> {
        if (!enabled) return emptyList()
        val channels = channelIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .toList()
        return NotificationEventType.entries
            .filter { event ->
                if (channels.isEmpty()) {
                    isEnabled(event.ruleId, legacyDefault = legacyDefault)
                } else {
                    channels.any { channelId ->
                        isEnabled(event.ruleId, channelId, legacyDefault)
                    }
                }
            }
            .map(NotificationEventType::ruleId)
    }

    fun withEnabled(value: Boolean): NotificationPreferences =
        copy(enabled = value).normalized()

    fun withGlobalEvent(ruleId: String, value: Boolean): NotificationPreferences {
        val event = requireNotNull(NotificationEventType.fromRuleId(ruleId)) {
            "Unknown notification event: $ruleId"
        }
        return copy(
            eventOverrides = eventOverrides + (event.ruleId to value),
        ).normalized()
    }

    fun clearGlobalEventOverride(ruleId: String): NotificationPreferences {
        val event = NotificationEventType.fromRuleId(ruleId) ?: return this
        if (event.ruleId !in eventOverrides) return this
        return copy(eventOverrides = eventOverrides - event.ruleId).normalized()
    }

    fun enableChannelOverrides(channelId: String): NotificationPreferences {
        val normalizedChannelId = requireChannelId(channelId)
        if (normalizedChannelId in channelOverrides) return this
        return copy(
            channelOverrides = channelOverrides + (
                normalizedChannelId to ChannelNotificationPreferences()
            ),
        ).normalized()
    }

    fun clearChannelOverride(channelId: String): NotificationPreferences {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty() || normalizedChannelId !in channelOverrides) return this
        return copy(channelOverrides = channelOverrides - normalizedChannelId).normalized()
    }

    fun withChannelEnabled(channelId: String, value: Boolean): NotificationPreferences {
        val normalizedChannelId = requireChannelId(channelId)
        val current = channelOverrides[normalizedChannelId] ?: ChannelNotificationPreferences()
        return copy(
            channelOverrides = channelOverrides + (
                normalizedChannelId to current.copy(enabled = value)
            ),
        ).normalized()
    }

    fun withChannelEvent(
        channelId: String,
        ruleId: String,
        value: Boolean,
    ): NotificationPreferences {
        val normalizedChannelId = requireChannelId(channelId)
        val event = requireNotNull(NotificationEventType.fromRuleId(ruleId)) {
            "Unknown notification event: $ruleId"
        }
        val current = channelOverrides[normalizedChannelId] ?: ChannelNotificationPreferences()
        return copy(
            channelOverrides = channelOverrides + (
                normalizedChannelId to current.copy(
                    eventOverrides = current.eventOverrides + (event.ruleId to value),
                )
            ),
        ).normalized()
    }

    fun clearChannelEventOverride(
        channelId: String,
        ruleId: String,
    ): NotificationPreferences {
        val normalizedChannelId = channelId.trim()
        val event = NotificationEventType.fromRuleId(ruleId) ?: return this
        val current = channelOverrides[normalizedChannelId] ?: return this
        if (event.ruleId !in current.eventOverrides) return this
        return copy(
            channelOverrides = channelOverrides + (
                normalizedChannelId to current.copy(
                    eventOverrides = current.eventOverrides - event.ruleId,
                )
            ),
        ).normalized()
    }
    fun clearChannelEventOverrides(channelId: String): NotificationPreferences {
        val normalizedChannelId = channelId.trim()
        val current = channelOverrides[normalizedChannelId] ?: return this
        if (current.eventOverrides.isEmpty()) return this
        return copy(
            channelOverrides = channelOverrides + (
                normalizedChannelId to current.copy(eventOverrides = emptyMap())
            ),
        ).normalized()
    }


    private fun requireChannelId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Notification channel id must not be blank")

    private companion object {
        const val MAX_CHANNEL_OVERRIDES = 100
    }
}

private fun normalizeNotificationEventOverrides(
    value: Map<String, Boolean>,
): Map<String, Boolean> {
    if (value.isEmpty()) return emptyMap()
    val result = linkedMapOf<String, Boolean>()
    NotificationEventType.entries.forEach { event ->
        value[event.ruleId]?.let { enabled -> result[event.ruleId] = enabled }
    }
    return result
}
