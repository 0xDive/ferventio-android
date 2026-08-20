package io.ferventio.shared.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule

class SharedMessageRulesStateHolder(
    initial: SharedMessageRulesSnapshot = SharedMessageRulesSnapshot(),
) {
    var highlightRules by mutableStateOf(initial.highlightRules)
        private set

    var ignoreRules by mutableStateOf(initial.ignoreRules)
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
        highlightRules = highlightRules.upsertById(rule.id, rule)
        saveErrorMessage = null
    }

    fun deleteHighlight(ruleId: String) {
        val id = requireRuleId(ruleId)
        highlightRules = highlightRules.filterNot { it.id == id }
        saveErrorMessage = null
    }

    fun upsertIgnore(rule: IgnoreRule) {
        ignoreRules = ignoreRules.upsertById(rule.id, rule)
        saveErrorMessage = null
    }

    fun deleteIgnore(ruleId: String) {
        val id = requireRuleId(ruleId)
        ignoreRules = ignoreRules.filterNot { it.id == id }
        saveErrorMessage = null
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
    }

    private fun requireRuleId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Message rule id must not be blank")

    private fun <T> List<T>.upsertById(
        id: String,
        value: T,
    ): List<T> where T : Any {
        val normalizedId = requireRuleId(id)
        val existingIndex = indexOfFirst { item ->
            when (item) {
                is HighlightRule -> item.id == normalizedId
                is IgnoreRule -> item.id == normalizedId
                else -> false
            }
        }
        if (existingIndex < 0) return this + value
        return toMutableList().apply { this[existingIndex] = value }
    }
}
