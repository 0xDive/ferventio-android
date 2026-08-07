package io.ferventio.app.domain

enum class NukeMatchMode {
    PLAIN_TEXT,
    REGEX,
}

data class NukePreviewConfig(
    val query: String,
    val matchMode: NukeMatchMode = NukeMatchMode.PLAIN_TEXT,
    val caseSensitive: Boolean = false,
    val windowMillis: Long = 30_000L,
    val excludeBroadcaster: Boolean = true,
    val excludeModerators: Boolean = true,
    val excludeVips: Boolean = true,
    val excludedUserIds: Set<String> = emptySet(),
    val maxSamples: Int = 25,
)

data class NukePreviewSample(
    val messageId: String,
    val userId: String,
    val userLogin: String,
    val userDisplayName: String,
    val text: String,
    val timestampMillis: Long,
)

data class NukeTargetUser(
    val userId: String,
    val userLogin: String,
    val userDisplayName: String,
)

data class NukePreview(
    val matchedMessageCount: Int,
    val matchedUserCount: Int,
    val excludedMatchCount: Int,
    val scannedMessageCount: Int,
    val matchedUserIds: Set<String>,
    val matchedUsers: List<NukeTargetUser>,
    val matchedMessageIds: List<String>,
    val samples: List<NukePreviewSample>,
)

data class NukeExecutionPlan(
    val query: String,
    val matchMode: NukeMatchMode,
    val caseSensitive: Boolean,
    val previewedAtMillis: Long,
    val targetUsers: List<NukeTargetUser>,
    val targetMessageIds: List<String>,
) {
    val targetUserCount: Int get() = targetUsers.size
    val targetMessageCount: Int get() = targetMessageIds.size
}

sealed interface NukePreviewResult {
    data class Success(val preview: NukePreview) : NukePreviewResult
    data class Error(val message: String) : NukePreviewResult
}

sealed interface NukeExecutionPlanResult {
    data class Success(val plan: NukeExecutionPlan) : NukeExecutionPlanResult
    data class Error(val message: String) : NukeExecutionPlanResult
}

/**
 * Read-only planner for dangerous mass moderation. It never executes moderation;
 * it only answers exactly what a future nuke action would target.
 */
object NukePreviewPlanner {
    private const val MAX_QUERY_LENGTH = 256

    fun build(
        messages: List<ChatMessage>,
        config: NukePreviewConfig,
        nowMillis: Long,
    ): NukePreviewResult {
        val query = config.query.trim()
        if (query.isEmpty()) return NukePreviewResult.Error("Nuke query must not be empty")
        if (query.length > MAX_QUERY_LENGTH) {
            return NukePreviewResult.Error("Nuke query is longer than $MAX_QUERY_LENGTH characters")
        }

        val matcher = when (config.matchMode) {
            NukeMatchMode.PLAIN_TEXT -> plainTextMatcher(query, config.caseSensitive)
            NukeMatchMode.REGEX -> regexMatcher(query, config.caseSensitive)
                ?: return NukePreviewResult.Error("Invalid regular expression")
        }

        val cutoff = nowMillis - config.windowMillis.coerceAtLeast(0L)
        val maxSamples = config.maxSamples.coerceAtLeast(0)
        val matchedUsersByKey = LinkedHashMap<String, NukeTargetUser>()
        val matchedMessageIds = ArrayList<String>()
        val samples = ArrayList<NukePreviewSample>(maxSamples.coerceAtMost(25))
        var matchedMessages = 0
        var excludedMatches = 0
        var scannedMessages = 0

        messages.forEach { message ->
            if (!isScannable(message, cutoff, nowMillis)) return@forEach
            scannedMessages += 1
            if (!matcher(message.text)) return@forEach

            if (isExcluded(message, config)) {
                excludedMatches += 1
                return@forEach
            }

            matchedMessages += 1
            val userKey = message.userId.ifBlank { "login:${message.userLogin.lowercase()}" }
            matchedUsersByKey.putIfAbsent(
                userKey,
                NukeTargetUser(
                    userId = message.userId,
                    userLogin = message.userLogin,
                    userDisplayName = message.userDisplayName,
                ),
            )
            matchedMessageIds += message.id
            if (samples.size < maxSamples) {
                samples += NukePreviewSample(
                    messageId = message.id,
                    userId = message.userId,
                    userLogin = message.userLogin,
                    userDisplayName = message.userDisplayName,
                    text = message.text,
                    timestampMillis = message.timestampMillis,
                )
            }
        }

        return NukePreviewResult.Success(
            NukePreview(
                matchedMessageCount = matchedMessages,
                matchedUserCount = matchedUsersByKey.size,
                excludedMatchCount = excludedMatches,
                scannedMessageCount = scannedMessages,
                matchedUserIds = matchedUsersByKey.keys,
                matchedUsers = matchedUsersByKey.values.toList(),
                matchedMessageIds = matchedMessageIds,
                samples = samples,
            ),
        )
    }

    private fun isScannable(message: ChatMessage, cutoff: Long, nowMillis: Long): Boolean =
        message.id.isNotBlank() &&
            message.type in SCANNABLE_TYPES &&
            !message.isDeleted &&
            !message.isSystem &&
            message.timestampMillis in cutoff..nowMillis

    private fun isExcluded(message: ChatMessage, config: NukePreviewConfig): Boolean {
        if (message.userId in config.excludedUserIds) return true
        val badgeSets = message.badges.asSequence().map(ChatBadge::setId).toSet()
        return (config.excludeBroadcaster && "broadcaster" in badgeSets) ||
            (config.excludeModerators && "moderator" in badgeSets) ||
            (config.excludeVips && "vip" in badgeSets)
    }

    private fun plainTextMatcher(query: String, caseSensitive: Boolean): (String) -> Boolean =
        if (caseSensitive) {
            { text -> query in text }
        } else {
            { text -> text.contains(query, ignoreCase = true) }
        }

    private fun regexMatcher(query: String, caseSensitive: Boolean): ((String) -> Boolean)? = runCatching {
        val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        Regex(query, options).let { regex -> { text: String -> regex.containsMatchIn(text) } }
    }.getOrNull()

    private val SCANNABLE_TYPES = setOf(ChatMessageType.CHAT, ChatMessageType.ACTION)
}

/**
 * Freezes the exact users and message ids shown by a preview. Executors must use
 * this immutable plan instead of re-running the query against a newer live feed.
 */
object NukeExecutionPlanner {
    fun freeze(
        config: NukePreviewConfig,
        preview: NukePreview,
        previewedAtMillis: Long,
    ): NukeExecutionPlanResult {
        if (preview.matchedUserCount <= 0 || preview.matchedUsers.isEmpty()) {
            return NukeExecutionPlanResult.Error("Nuke preview has no target users")
        }
        if (preview.matchedMessageCount != preview.matchedMessageIds.size) {
            return NukeExecutionPlanResult.Error("Nuke preview target set is inconsistent")
        }
        if (preview.matchedUserCount != preview.matchedUsers.size) {
            return NukeExecutionPlanResult.Error("Nuke preview user set is inconsistent")
        }
        return NukeExecutionPlanResult.Success(
            NukeExecutionPlan(
                query = config.query.trim(),
                matchMode = config.matchMode,
                caseSensitive = config.caseSensitive,
                previewedAtMillis = previewedAtMillis,
                targetUsers = preview.matchedUsers.toList(),
                targetMessageIds = preview.matchedMessageIds.toList(),
            ),
        )
    }
}
