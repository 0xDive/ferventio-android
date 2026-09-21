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
): List<ChatMessage> = filterWorkspaceSplitMessages(
    messages = messages,
    filter = compileWorkspaceSplitMessageFilter(filterQuery, savedFilters),
    decorations = decorations,
    showSystemMessages = showSystemMessages,
)

internal fun filterWorkspaceSplitMessages(
    messages: List<ChatMessage>,
    filter: WorkspaceSplitMessageFilter,
    decorations: Map<String, MessageDecoration>,
    showSystemMessages: Boolean,
): List<ChatMessage> {
    var filtered: MutableList<ChatMessage>? = null
    for (index in messages.indices) {
        val message = messages[index]
        val decoration = decorations[message.id]
        val keep = (showSystemMessages || !message.isSystem) &&
            filter.matches(message, decoration) &&
            decoration?.ignoreDisplayMode != IgnoreDisplayMode.HIDE
        if (keep) {
            filtered?.add(message)
        } else if (filtered == null) {
            filtered = ArrayList<ChatMessage>(messages.size - 1).apply {
                for (prefixIndex in 0 until index) {
                    add(messages[prefixIndex])
                }
            }
        }
    }
    return filtered ?: messages
}
