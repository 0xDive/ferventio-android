package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.CompiledMessageFilter
import io.ferventio.app.domain.HIGHLIGHTS_FILTER_QUERY
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.MessageDecoration
import io.ferventio.app.domain.MessageFilterLanguage
import io.ferventio.app.domain.SavedMessageFilter
import io.ferventio.app.domain.resolveSplitFilterExpression

internal data class WorkspaceSplitMessageFilter(
    val expression: String,
    val compiled: CompiledMessageFilter?,
) {
    val highlightsOnly: Boolean
        get() = expression == HIGHLIGHTS_FILTER_QUERY

    fun matches(message: ChatMessage, decoration: MessageDecoration?): Boolean = when {
        expression.isEmpty() -> true
        highlightsOnly -> decoration?.filteredSplit == true
        else -> compiled?.matches(message) == true
    }
}

internal fun compileWorkspaceSplitMessageFilter(
    filterQuery: String,
    savedFilters: List<SavedMessageFilter>,
): WorkspaceSplitMessageFilter {
    val expression = resolveSplitFilterExpression(filterQuery, savedFilters).trim()
    return WorkspaceSplitMessageFilter(
        expression = expression,
        compiled = if (expression.isEmpty() || expression == HIGHLIGHTS_FILTER_QUERY) {
            null
        } else {
            MessageFilterLanguage.compileForSplit(expression)
        },
    )
}

internal fun filterWorkspaceSplitMessages(
    messages: List<ChatMessage>,
    filterQuery: String,
    savedFilters: List<SavedMessageFilter>,
    decorations: Map<String, MessageDecoration>,
    showSystemMessages: Boolean,
): List<ChatMessage> {
    val filter = compileWorkspaceSplitMessageFilter(filterQuery, savedFilters)
    return messages.filter { message ->
        if (!showSystemMessages && message.isSystem) return@filter false
        val decoration = decorations[message.id]
        filter.matches(message, decoration) && decoration?.ignoreDisplayMode != IgnoreDisplayMode.HIDE
    }
}
