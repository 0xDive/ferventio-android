package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageDecoration

class SharedMessageRulesStateHolder(
    initial: SharedMessageRulesSnapshot = SharedMessageRulesSnapshot(),
) {
    var highlightRules by mutableStateOf(initial.highlightRules)
        private set

    var ignoreRules by mutableStateOf(initial.ignoreRules)
        private set

    /**
     * One-time live decorations keyed by Twitch message id.
     *
     * Rules are evaluated when an EventSub message is accepted, not while Compose renders it. This
     * keeps already-received messages stable when the user edits rules and matches Android 0.0.5.
     */
    var decorationsByMessageId by mutableStateOf(emptyMap<String, MessageDecoration>())
        private set

    var saveStatus by mutableStateOf(SharedSettingsSaveStatus.IDLE)
        private set

    var saveErrorMessage by mutableStateOf<String?>(null)
        private set

    val snapshot: SharedMessageRulesSnapshot
        get() = SharedMessageRulesSnapshot(
            highlightRules = highlightRules,
            ignoreRules = ignoreRules,
        )

    fun restore(snapshot: SharedMessageRulesSnapshot) {
        highlightRules = snapshot.highlightRules
        ignoreRules = snapshot.ignoreRules
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }

    fun upsertHighlight(rule: HighlightRule) {
        val id = requireRuleId(rule.id)
        val existingIndex = highlightRules.indexOfFirst { it.id == id }
        highlightRules = if (existingIndex < 0) {
            highlightRules + rule
        } else {
            highlightRules.toMutableList().apply { this[existingIndex] = rule }
        }
        saveErrorMessage = null
    }

    fun deleteHighlight(ruleId: String) {
        val id = requireRuleId(ruleId)
        highlightRules = highlightRules.filterNot { it.id == id }
        saveErrorMessage = null
    }

    fun upsertIgnore(rule: IgnoreRule) {
        val id = requireRuleId(rule.id)
        val existingIndex = ignoreRules.indexOfFirst { it.id == id }
        ignoreRules = if (existingIndex < 0) {
            ignoreRules + rule
        } else {
            ignoreRules.toMutableList().apply { this[existingIndex] = rule }
        }
        saveErrorMessage = null
    }

    fun deleteIgnore(ruleId: String) {
        val id = requireRuleId(ruleId)
        ignoreRules = ignoreRules.filterNot { it.id == id }
        saveErrorMessage = null
    }

    fun recordDecoration(messageId: String, decoration: MessageDecoration) {
        val id = requireMessageId(messageId)
        val updated = LinkedHashMap(decorationsByMessageId)
        updated.remove(id)
        updated[id] = decoration
        while (updated.size > MAX_LIVE_DECORATIONS) {
            val oldest = updated.keys.firstOrNull() ?: break
            updated.remove(oldest)
        }
        decorationsByMessageId = updated
    }

    fun decoration(messageId: String): MessageDecoration =
        decorationsByMessageId[messageId.trim()] ?: MessageDecoration()

    fun clearDecorations() {
        decorationsByMessageId = emptyMap()
    }

    fun markSaveStarted() {
        saveStatus = SharedSettingsSaveStatus.SAVING
        saveErrorMessage = null
    }

    fun markSaveSucceeded(snapshot: SharedMessageRulesSnapshot) {
        restore(snapshot)
    }

    fun markSaveFailed(message: String?) {
        saveStatus = SharedSettingsSaveStatus.FAILED
        saveErrorMessage = message?.trim()?.takeIf(String::isNotEmpty)
            ?: "Failed to save message rules"
    }

    fun clear() {
        restore(SharedMessageRulesSnapshot())
        clearDecorations()
    }

    private fun requireRuleId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Message rule id must not be blank")

    private fun requireMessageId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Message id must not be blank")

    private companion object {
        const val MAX_LIVE_DECORATIONS = 100_000
    }
}
