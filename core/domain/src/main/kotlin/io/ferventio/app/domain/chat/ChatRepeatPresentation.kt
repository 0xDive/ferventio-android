package io.ferventio.app.domain

/**
 * Immutable projection of the canonical message list into the repeat-collapsed
 * presentation list. Canonical messages remain untouched and continue to be the
 * source of truth for search, moderation, persistence and reply resolution.
 */
data class ChatRepeatPresentation(
    val messages: List<ChatMessage>,
    val anchorByMessageId: Map<String, String>,
    val summariesByAnchorId: Map<String, ChatRepeatSummary>,
) {
    private val visibleIndexByMessageId: Map<String, Int> by lazy(LazyThreadSafetyMode.NONE) {
        messages.mapIndexed { index, message -> message.id to index }.toMap()
    }

    fun anchorFor(messageId: String): String = anchorByMessageId[messageId] ?: messageId

    fun visibleIndexFor(messageId: String): Int? = visibleIndexByMessageId[anchorFor(messageId)]

    fun visibleMessageFor(messageId: String): ChatMessage? =
        visibleIndexFor(messageId)?.let(messages::get)

    fun summaryFor(messageId: String): ChatRepeatSummary? =
        summariesByAnchorId[anchorFor(messageId)]

    companion object {
        val Empty = ChatRepeatPresentation(
            messages = emptyList(),
            anchorByMessageId = emptyMap(),
            summariesByAnchorId = emptyMap(),
        )
    }
}

object ChatRepeatPresentationProjector {
    fun project(
        canonicalMessages: List<ChatMessage>,
        plan: ChatRepeatCollapsePlan,
    ): ChatRepeatPresentation {
        if (canonicalMessages.isEmpty()) return ChatRepeatPresentation.Empty

        val visibleMessages = if (plan.visibleMessageIds.isEmpty()) {
            canonicalMessages
        } else {
            canonicalMessages.filter { message -> message.id in plan.visibleMessageIds }
        }

        return ChatRepeatPresentation(
            messages = visibleMessages,
            anchorByMessageId = plan.anchorByMessageId,
            summariesByAnchorId = plan.summariesByAnchorId,
        )
    }

    fun build(
        canonicalMessages: List<ChatMessage>,
        config: ChatRepeatCollapseConfig = ChatRepeatCollapseConfig(),
    ): ChatRepeatPresentation {
        if (canonicalMessages.isEmpty()) return ChatRepeatPresentation.Empty
        val plan = ChatRepeatCollapser.build(
            messages = canonicalMessages,
            config = config,
        )
        return project(canonicalMessages, plan)
    }
}
