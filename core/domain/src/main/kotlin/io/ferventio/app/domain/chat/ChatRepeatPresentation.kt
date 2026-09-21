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

        val visibleMessages = projectVisibleMessages(
            canonicalMessages = canonicalMessages,
            visibleMessageIds = plan.visibleMessageIds,
        )

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
        if (!config.enabled) {
            return ChatRepeatPresentation(
                messages = canonicalMessages,
                anchorByMessageId = emptyMap(),
                summariesByAnchorId = emptyMap(),
            )
        }
        val plan = ChatRepeatCollapser.build(
            messages = canonicalMessages,
            config = config,
        )
        return project(canonicalMessages, plan)
    }

    private fun projectVisibleMessages(
        canonicalMessages: List<ChatMessage>,
        visibleMessageIds: Set<String>,
    ): List<ChatMessage> {
        if (visibleMessageIds.isEmpty()) return canonicalMessages

        var filtered: MutableList<ChatMessage>? = null
        for (index in canonicalMessages.indices) {
            val message = canonicalMessages[index]
            if (message.id in visibleMessageIds) {
                filtered?.add(message)
            } else if (filtered == null) {
                filtered = ArrayList<ChatMessage>(canonicalMessages.size - 1).apply {
                    for (prefixIndex in 0 until index) {
                        add(canonicalMessages[prefixIndex])
                    }
                }
            }
        }
        return filtered ?: canonicalMessages
    }
}
