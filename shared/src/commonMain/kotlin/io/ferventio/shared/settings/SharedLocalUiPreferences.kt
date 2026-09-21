package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

const val QUICK_BAN_BUTTON_KEY = "quick_ban_button_enabled"
const val QUICK_DELETE_BUTTON_KEY = "quick_delete_button_enabled"
const val CONFIRM_MODERATION_ACTIONS_KEY = "confirm_moderation_actions_enabled"
const val COMPOSER_DRAFTS_BY_CHANNEL_KEY = "drafts_by_channel"
const val SENT_MESSAGE_HISTORY_BY_CHANNEL_KEY = "sent_message_history_by_channel"
const val MAX_COMPOSER_DRAFT_LENGTH = 500
const val MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL = 50
private const val MAX_LOCAL_COMPOSER_CHANNELS = 100

data class SharedLocalUiPreferences(
    val showQuickBan: Boolean = false,
    val showQuickDelete: Boolean = false,
    val confirmModerationActions: Boolean = true,
    val draftsByChannel: Map<String, String> = emptyMap(),
    val sentMessageHistoryByChannel: Map<String, List<String>> = emptyMap(),
) {
    fun normalized(): SharedLocalUiPreferences = copy(
        draftsByChannel = normalizeDrafts(draftsByChannel),
        sentMessageHistoryByChannel = normalizeSentHistory(sentMessageHistoryByChannel),
    )

    private fun normalizeDrafts(values: Map<String, String>): Map<String, String> = buildMap {
        values.entries.take(MAX_LOCAL_COMPOSER_CHANNELS).forEach { (rawChannelId, rawDraft) ->
            val channelId = rawChannelId.trim()
            val draft = rawDraft.take(MAX_COMPOSER_DRAFT_LENGTH)
            if (channelId.isNotEmpty() && draft.isNotEmpty()) put(channelId, draft)
        }
    }

    private fun normalizeSentHistory(
        values: Map<String, List<String>>,
    ): Map<String, List<String>> = buildMap {
        values.entries.take(MAX_LOCAL_COMPOSER_CHANNELS).forEach { (rawChannelId, rawMessages) ->
            val channelId = rawChannelId.trim()
            if (channelId.isEmpty()) return@forEach
            val messages = rawMessages.asSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map { it.take(MAX_COMPOSER_DRAFT_LENGTH) }
                .distinct()
                .take(MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL)
                .toList()
            if (messages.isNotEmpty()) put(channelId, messages)
        }
    }

}

/** Device-local UI preferences that intentionally do not participate in backend settings sync. */
interface SharedLocalUiPreferencesStore {
    fun load(): SharedLocalUiPreferences

    fun save(preferences: SharedLocalUiPreferences)
}

class SharedLocalUiPreferencesStateHolder(
    private val store: SharedLocalUiPreferencesStore = InMemorySharedLocalUiPreferencesStore(),
) {
    var preferences by mutableStateOf(store.load().normalized())
        private set

    fun update(transform: (SharedLocalUiPreferences) -> SharedLocalUiPreferences): SharedLocalUiPreferences =
        commit(transform(preferences).normalized())

    fun setShowQuickBan(value: Boolean) {
        commit(preferences.copy(showQuickBan = value))
    }

    fun setShowQuickDelete(value: Boolean) {
        commit(preferences.copy(showQuickDelete = value))
    }

    fun setConfirmModerationActions(value: Boolean) {
        commit(preferences.copy(confirmModerationActions = value))
    }

    fun draft(channelId: String): String =
        preferences.draftsByChannel[channelId.trim()].orEmpty()

    fun setDraft(channelId: String, value: String) {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty()) return
        val normalizedDraft = value.take(MAX_COMPOSER_DRAFT_LENGTH)
        val current = preferences
        val existing = current.draftsByChannel[normalizedChannelId].orEmpty()
        if (existing == normalizedDraft) return
        if (
            normalizedDraft.isNotEmpty() &&
            normalizedChannelId !in current.draftsByChannel &&
            current.draftsByChannel.size >= MAX_LOCAL_COMPOSER_CHANNELS
        ) {
            return
        }
        val drafts = if (normalizedDraft.isEmpty()) {
            current.draftsByChannel - normalizedChannelId
        } else {
            current.draftsByChannel + (normalizedChannelId to normalizedDraft)
        }
        commit(current.copy(draftsByChannel = drafts))
    }

    fun sentMessageHistory(channelId: String): List<String> =
        preferences.sentMessageHistoryByChannel[channelId.trim()].orEmpty()

    fun recordSentMessage(channelId: String, text: String) {
        val normalizedChannelId = channelId.trim()
        val normalizedText = text.trim().take(MAX_COMPOSER_DRAFT_LENGTH)
        if (normalizedChannelId.isEmpty() || normalizedText.isEmpty()) return
        val current = preferences
        if (
            normalizedChannelId !in current.sentMessageHistoryByChannel &&
            current.sentMessageHistoryByChannel.size >= MAX_LOCAL_COMPOSER_CHANNELS
        ) {
            return
        }
        val existing = current.sentMessageHistoryByChannel[normalizedChannelId].orEmpty()
        if (existing.firstOrNull() == normalizedText) return
        val history = buildList(minOf(MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL, existing.size + 1)) {
            add(normalizedText)
            existing.forEach { message ->
                if (message != normalizedText && size < MAX_SENT_MESSAGE_HISTORY_PER_CHANNEL) {
                    add(message)
                }
            }
        }
        commit(
            current.copy(
                sentMessageHistoryByChannel =
                    current.sentMessageHistoryByChannel + (normalizedChannelId to history),
            ),
        )
    }

    private fun commit(updated: SharedLocalUiPreferences): SharedLocalUiPreferences {
        val current = preferences
        if (updated == current) return current
        store.save(updated)
        preferences = updated
        return updated
    }
}

private class InMemorySharedLocalUiPreferencesStore : SharedLocalUiPreferencesStore {
    private var value = SharedLocalUiPreferences()

    override fun load(): SharedLocalUiPreferences = value

    override fun save(preferences: SharedLocalUiPreferences) {
        value = preferences
    }
}
