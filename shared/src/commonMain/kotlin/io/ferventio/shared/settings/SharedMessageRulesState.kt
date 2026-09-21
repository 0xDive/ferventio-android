package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
    private val mutableDecorationsByMessageId = mutableStateMapOf<String, MessageDecoration>()
    private val decorationOrder = ArrayDeque<String>()

    val decorationsByMessageId: Map<String, MessageDecoration>
        get() = mutableDecorationsByMessageId

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
        if (highlightRules != snapshot.highlightRules) {
            highlightRules = snapshot.highlightRules
        }
        if (ignoreRules != snapshot.ignoreRules) {
            ignoreRules = snapshot.ignoreRules
        }
        saveStatus = SharedSettingsSaveStatus.IDLE
        saveErrorMessage = null
    }

    fun upsertHighlight(rule: HighlightRule) {
        val id = requireRuleId(rule.id)
        val existingIndex = highlightRules.indexOfFirst { it.id == id }
        highlightRules = when {
            existingIndex < 0 -> highlightRules + rule
            highlightRules[existingIndex] == rule -> highlightRules
            else -> highlightRules.toMutableList().apply { this[existingIndex] = rule }
        }
        saveErrorMessage = null
    }

    fun deleteHighlight(ruleId: String) {
        val id = requireRuleId(ruleId)
        val index = highlightRules.indexOfFirst { it.id == id }
        if (index >= 0) {
            highlightRules = highlightRules.toMutableList().apply { removeAt(index) }
        }
        saveErrorMessage = null
    }

    fun upsertIgnore(rule: IgnoreRule) {
        val id = requireRuleId(rule.id)
        val existingIndex = ignoreRules.indexOfFirst { it.id == id }
        ignoreRules = when {
            existingIndex < 0 -> ignoreRules + rule
            ignoreRules[existingIndex] == rule -> ignoreRules
            else -> ignoreRules.toMutableList().apply { this[existingIndex] = rule }
        }
        saveErrorMessage = null
    }

    fun deleteIgnore(ruleId: String) {
        val id = requireRuleId(ruleId)
        val index = ignoreRules.indexOfFirst { it.id == id }
        if (index >= 0) {
            ignoreRules = ignoreRules.toMutableList().apply { removeAt(index) }
        }
        saveErrorMessage = null
    }

    fun recordDecoration(messageId: String, decoration: MessageDecoration) {
        val id = requireMessageId(messageId)
        val existing = mutableDecorationsByMessageId[id]
        if (existing == decoration) return

        // Default decoration is represented by absence. Live EventSub delivery is de-duplicated
        // before this state holder, so ordinary messages do not need one map entry each.
        if (decoration == MessageDecoration()) {
            if (existing != null) {
                mutableDecorationsByMessageId.remove(id)
                decorationOrder.remove(id)
            }
            return
        }

        if (existing != null) {
            decorationOrder.remove(id)
        }
        mutableDecorationsByMessageId[id] = decoration
        decorationOrder.addLast(id)
        while (mutableDecorationsByMessageId.size > MAX_LIVE_DECORATIONS) {
            if (decorationOrder.isEmpty()) break
            mutableDecorationsByMessageId.remove(decorationOrder.removeFirst())
        }
    }

    fun decoration(messageId: String): MessageDecoration =
        mutableDecorationsByMessageId[messageId.trim()] ?: MessageDecoration()

    fun clearDecorations() {
        if (mutableDecorationsByMessageId.isNotEmpty()) {
            mutableDecorationsByMessageId.clear()
        }
        decorationOrder.clear()
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
