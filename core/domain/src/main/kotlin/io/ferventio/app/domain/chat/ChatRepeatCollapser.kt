package io.ferventio.app.domain

/**
 * Presentation-only plan for folding short copypasta waves without mutating the
 * canonical chat history. Search, replies, moderation, persistence and message
 * navigation can continue to operate on the complete message list.
 */
data class ChatRepeatCollapsePlan(
    val visibleMessageIds: Set<String>,
    val anchorByMessageId: Map<String, String>,
    val summariesByAnchorId: Map<String, ChatRepeatSummary>,
) {
    fun anchorFor(messageId: String): String = anchorByMessageId[messageId] ?: messageId

    fun summaryFor(messageId: String): ChatRepeatSummary? =
        summariesByAnchorId[anchorFor(messageId)]

    companion object {
        val Empty = ChatRepeatCollapsePlan(
            visibleMessageIds = emptySet(),
            anchorByMessageId = emptyMap(),
            summariesByAnchorId = emptyMap(),
        )
    }
}

data class ChatRepeatCollapseConfig(
    val enabled: Boolean = true,
    val minRepeatCount: Int = ChatRepeatCollapser.DEFAULT_MIN_REPEAT_COUNT,
    val windowMillis: Long = ChatRepeatCollapser.DEFAULT_WINDOW_MILLIS,
    val maxParticipants: Int = ChatRepeatCollapser.DEFAULT_MAX_PARTICIPANTS,
)

data class ChatRepeatSummary(
    val anchorMessageId: String,
    val count: Int,
    val participants: List<ChatRepeatParticipant>,
    val totalParticipantCount: Int,
) {
    val collapsedMessageCount: Int get() = (count - 1).coerceAtLeast(0)
    val omittedParticipantCount: Int get() = (totalParticipantCount - participants.size).coerceAtLeast(0)
}

data class ChatRepeatParticipant(
    val userId: String,
    val displayName: String,
)

/**
 * Builds a conservative repeat-collapse plan for one already-filtered chat feed.
 *
 * The first version deliberately collapses only consecutive, normalized-exact
 * chat/action messages. It does not use fuzzy matching yet: moderation and reply
 * context must never disappear because two unrelated messages merely look similar.
 */
object ChatRepeatCollapser {
    const val DEFAULT_MIN_REPEAT_COUNT = 3
    const val DEFAULT_WINDOW_MILLIS = 10_000L
    const val DEFAULT_MAX_PARTICIPANTS = 20

    fun build(
        messages: List<ChatMessage>,
        config: ChatRepeatCollapseConfig,
    ): ChatRepeatCollapsePlan {
        if (messages.isEmpty()) return ChatRepeatCollapsePlan.Empty
        if (!config.enabled) return uncollapsedPlan(messages)

        return buildEnabled(
            messages = messages,
            minRepeatCount = config.minRepeatCount,
            windowMillis = config.windowMillis,
            maxParticipants = config.maxParticipants,
        )
    }

    fun build(
        messages: List<ChatMessage>,
        minRepeatCount: Int = DEFAULT_MIN_REPEAT_COUNT,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS,
        maxParticipants: Int = DEFAULT_MAX_PARTICIPANTS,
    ): ChatRepeatCollapsePlan = buildEnabled(
        messages = messages,
        minRepeatCount = minRepeatCount,
        windowMillis = windowMillis,
        maxParticipants = maxParticipants,
    )

    private fun buildEnabled(
        messages: List<ChatMessage>,
        minRepeatCount: Int,
        windowMillis: Long,
        maxParticipants: Int,
    ): ChatRepeatCollapsePlan {
        if (messages.isEmpty()) return ChatRepeatCollapsePlan.Empty

        val threshold = minRepeatCount.coerceAtLeast(2)
        val boundedWindowMillis = windowMillis.coerceAtLeast(0L)
        val participantLimit = maxParticipants.coerceAtLeast(0)
        val visibleIds = LinkedHashSet<String>(messages.size)
        val anchorByMessageId = LinkedHashMap<String, String>()
        val summaries = LinkedHashMap<String, ChatRepeatSummary>()

        var run = mutableListOf<ChatMessage>()
        var runKey: String? = null

        fun flushRun() {
            if (run.isEmpty()) return

            if (run.size >= threshold) {
                val anchor = run.first()
                visibleIds += anchor.id
                val distinctParticipants = run
                    .asSequence()
                    .map { ChatRepeatParticipant(it.userId, it.userDisplayName) }
                    .distinctBy { it.userId.ifBlank { it.displayName.lowercase() } }
                    .toList()
                summaries[anchor.id] = ChatRepeatSummary(
                    anchorMessageId = anchor.id,
                    count = run.size,
                    participants = distinctParticipants.take(participantLimit),
                    totalParticipantCount = distinctParticipants.size,
                )
                run.forEach { message -> anchorByMessageId[message.id] = anchor.id }
            } else {
                run.forEach { message -> visibleIds += message.id }
            }

            run = mutableListOf()
            runKey = null
        }

        messages.forEach { message ->
            val key = collapseKey(message)
            if (key == null) {
                flushRun()
                visibleIds += message.id
                return@forEach
            }

            val previous = run.lastOrNull()
            val continuesRun = previous != null &&
                runKey == key &&
                message.timestampMillis >= previous.timestampMillis &&
                message.timestampMillis - run.first().timestampMillis <= boundedWindowMillis

            if (!continuesRun) flushRun()
            if (run.isEmpty()) runKey = key
            run += message
        }
        flushRun()

        return ChatRepeatCollapsePlan(
            visibleMessageIds = visibleIds,
            anchorByMessageId = anchorByMessageId,
            summariesByAnchorId = summaries,
        )
    }

    private fun uncollapsedPlan(messages: List<ChatMessage>): ChatRepeatCollapsePlan =
        ChatRepeatCollapsePlan(
            visibleMessageIds = messages.mapTo(LinkedHashSet(messages.size), ChatMessage::id),
            anchorByMessageId = emptyMap(),
            summariesByAnchorId = emptyMap(),
        )

    private fun collapseKey(message: ChatMessage): String? {
        if (message.id.isBlank()) return null
        if (message.type != ChatMessageType.CHAT && message.type != ChatMessageType.ACTION) return null
        if (message.isDeleted || message.isSystem || message.reply != null || message.notice != null) return null
        if (message.reward != null) return null
        if (message.author.badges.any { it.setId in PROTECTED_BADGE_SET_IDS }) return null

        val normalized = normalize(message.text)
        return normalized.takeIf(String::isNotBlank)
    }

    private fun normalize(text: String): String = text
        .trim()
        .lowercase()
        .replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
    private val PROTECTED_BADGE_SET_IDS = setOf("broadcaster", "moderator", "vip")
}
