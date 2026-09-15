package io.ferventio.shared.history

import io.ferventio.app.domain.ChatHistoryConfig
import io.ferventio.app.domain.ChatHistoryDateRange
import io.ferventio.app.domain.ChatHistorySearchRequest
import io.ferventio.app.domain.ChatHistorySearchScope
import io.ferventio.app.domain.ChatHistoryStore
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import kotlin.time.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface ChatHistorySnapshotStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

internal fun interface ChatHistoryLocalDateResolver {
    /** Returns [startOfDayMillis, startOfNextDayMillis) for an ISO YYYY-MM-DD local date. */
    fun resolve(value: String): Pair<Long, Long>?
}

/**
 * Durable history implementation shared by native platforms that persist one bounded snapshot.
 * Android intentionally keeps using Room through RoomChatHistoryStore.
 */
internal class SnapshotChatHistoryStore(
    private val storage: ChatHistorySnapshotStorage,
    private val localDateResolver: ChatHistoryLocalDateResolver,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ChatHistoryStore {
    private val mutex = Mutex()

    override suspend fun saveMessage(message: ChatMessage, config: ChatHistoryConfig) =
        saveMessages(listOf(message), config)

    override suspend fun saveMessages(messages: List<ChatMessage>, config: ChatHistoryConfig) {
        if (!config.enabled || messages.isEmpty()) return
        val valid = messages.filter { it.id.isNotBlank() && it.channelId.isNotBlank() }
        if (valid.isEmpty()) return
        mutate { current ->
            val byId = current.associateByTo(linkedMapOf(), ChatMessage::id)
            valid.forEach { byId[it.id] = it }
            prune(byId.values.toList(), config, nowMillis())
        }
    }

    override suspend fun loadRecentMessages(
        channelIds: List<String>,
        config: ChatHistoryConfig,
    ): Map<String, List<ChatMessage>> {
        if (!config.enabled || channelIds.isEmpty()) return emptyMap()
        val validChannelIds = channelIds.filter(String::isNotBlank).distinct()
        if (validChannelIds.isEmpty()) return emptyMap()
        return withSnapshot(writeBack = true) { current ->
            val pruned = prune(current, config, nowMillis())
            val pageSize = minOf(
                config.limitPerChannel.coerceIn(MIN_HISTORY_LIMIT, MAX_HISTORY_LIMIT),
                INITIAL_HISTORY_PAGE_SIZE,
            )
            val result = validChannelIds.associateWith { channelId ->
                pruned.asSequence()
                    .filter { it.channelId == channelId }
                    .sortedWith(messageAscending)
                    .toList()
                    .takeLast(pageSize)
            }
            SnapshotResult(pruned, result)
        }
    }

    override suspend fun loadOlderMessages(
        channelId: String,
        beforeTimestampMillis: Long,
        beforeMessageId: String,
        limit: Int,
    ): List<ChatMessage> {
        if (channelId.isBlank() || beforeMessageId.isBlank()) return emptyList()
        return readSnapshot().asSequence()
            .filter { message ->
                message.channelId == channelId && (
                    message.timestampMillis < beforeTimestampMillis ||
                        (message.timestampMillis == beforeTimestampMillis && message.id < beforeMessageId)
                    )
            }
            .sortedWith(messageDescending)
            .take(limit.coerceIn(1, HISTORY_PAGE_SIZE))
            .toList()
            .asReversed()
    }

    override suspend fun searchMessages(request: ChatHistorySearchRequest): Result<List<ChatMessage>> = runCatching {
        val parsed = NativeChatSearchParser.parse(request.rawQuery, localDateResolver).getOrThrow()
        if (parsed.isEmpty && request.dateRange.days == null && request.messageTypes.isEmpty()) {
            return@runCatching emptyList()
        }
        val scopedChannelId = if (request.scope == ChatHistorySearchScope.CURRENT_CHANNEL) {
            request.currentChannelId?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Current channel is not selected")
        } else {
            null
        }
        val rangeCutoff = request.dateRange.days?.let { days ->
            request.nowMillis - days * MILLIS_PER_DAY
        }
        val effectiveAfter = listOfNotNull(rangeCutoff, parsed.afterMillis).maxOrNull()
        val requestedTypes = when {
            request.messageTypes.isNotEmpty() && parsed.types.isNotEmpty() ->
                request.messageTypes.intersect(parsed.types)
            request.messageTypes.isNotEmpty() -> request.messageTypes
            else -> parsed.types
        }
        if (request.messageTypes.isNotEmpty() && parsed.types.isNotEmpty() && requestedTypes.isEmpty()) {
            return@runCatching emptyList()
        }
        val regex = parsed.regexPattern?.let { Regex(it, RegexOption.IGNORE_CASE) }
        readSnapshot().asSequence()
            .filter { scopedChannelId == null || it.channelId == scopedChannelId }
            .filter { parsed.channelLogin == null || it.channelLogin.equals(parsed.channelLogin, ignoreCase = true) }
            .filter { message ->
                parsed.authorLogin == null ||
                    message.author.login.equals(parsed.authorLogin, ignoreCase = true) ||
                    message.author.displayName.equals(parsed.authorLogin, ignoreCase = true)
            }
            .filter { message ->
                parsed.terms.all { term ->
                    message.text.contains(term, ignoreCase = true) ||
                        message.author.login.contains(term, ignoreCase = true) ||
                        message.author.displayName.contains(term, ignoreCase = true)
                }
            }
            .filter { !parsed.hasLink || it.hasLink() }
            .filter { !parsed.isDeleted || it.flags.isDeleted }
            .filter { !parsed.isTimeout || it.moderation.action == ModerationAction.TIMEOUT }
            .filter { !parsed.isSubscription || it.type in subscriptionTypes }
            .filter { requestedTypes.isEmpty() || it.type in requestedTypes }
            .filter { effectiveAfter == null || it.timestampMillis >= effectiveAfter }
            .filter { parsed.beforeMillis == null || it.timestampMillis < parsed.beforeMillis }
            .filter { regex == null || regex.containsMatchIn(it.text) }
            .sortedWith(messageDescending)
            .take(request.limit.coerceIn(1, MAX_SEARCH_RESULTS))
            .toList()
    }

    override suspend fun loadMessageContext(messageId: String, radius: Int): List<ChatMessage> {
        if (messageId.isBlank()) return emptyList()
        val current = readSnapshot()
        val target = current.firstOrNull { it.id == messageId } ?: return emptyList()
        val channel = current.filter { it.channelId == target.channelId }.sortedWith(messageAscending)
        val index = channel.indexOfFirst { it.id == target.id }
        if (index < 0) return emptyList()
        val safeRadius = radius.coerceIn(1, 25)
        return channel.subList(
            fromIndex = (index - safeRadius).coerceAtLeast(0),
            toIndex = (index + safeRadius + 1).coerceAtMost(channel.size),
        )
    }

    override suspend fun markMessageDeleted(channelId: String, messageId: String) {
        if (channelId.isBlank() || messageId.isBlank()) return
        val atMillis = nowMillis()
        mutate { current ->
            current.map { message ->
                if (message.channelId == channelId && message.id == messageId) {
                    message.copy(
                        flags = message.flags.copy(isDeleted = true),
                        moderation = message.moderation.withAction(ModerationAction.DELETE, atMillis),
                    )
                } else {
                    message
                }
            }
        }
    }

    override suspend fun markUserMessagesDeleted(channelId: String, userId: String) {
        if (channelId.isBlank() || userId.isBlank()) return
        val atMillis = nowMillis()
        mutate { current ->
            current.map { message ->
                if (message.channelId == channelId && message.author.id == userId) {
                    message.copy(
                        flags = message.flags.copy(isDeleted = true),
                        moderation = message.moderation.withAction(ModerationAction.TIMEOUT, atMillis),
                    )
                } else {
                    message
                }
            }
        }
    }

    override suspend fun clearChannel(channelId: String) {
        if (channelId.isBlank()) return
        mutate { current -> current.filterNot { it.channelId == channelId } }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.Default) {
            mutex.withLock { storage.clear() }
        }
    }

    private suspend fun readSnapshot(): List<ChatMessage> = withContext(Dispatchers.Default) {
        mutex.withLock { ChatHistorySnapshotCodec.decodeOrEmpty(storage.read()) }
    }

    private suspend fun mutate(transform: (List<ChatMessage>) -> List<ChatMessage>) {
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val current = ChatHistorySnapshotCodec.decodeOrEmpty(storage.read())
                storage.write(
                    ChatHistorySnapshotCodec.encode(
                        transform(current).sortedWith(messageAscending),
                    ),
                )
            }
        }
    }

    private suspend fun <T> withSnapshot(
        writeBack: Boolean,
        transform: (List<ChatMessage>) -> SnapshotResult<T>,
    ): T = withContext(Dispatchers.Default) {
        mutex.withLock {
            val current = ChatHistorySnapshotCodec.decodeOrEmpty(storage.read())
            val result = transform(current)
            if (writeBack && result.messages != current) {
                storage.write(ChatHistorySnapshotCodec.encode(result.messages.sortedWith(messageAscending)))
            }
            result.value
        }
    }

    private fun prune(messages: List<ChatMessage>, config: ChatHistoryConfig, now: Long): List<ChatMessage> {
        val safeLimit = config.limitPerChannel.coerceIn(MIN_HISTORY_LIMIT, MAX_HISTORY_LIMIT)
        val safeDays = config.retentionDays.coerceIn(MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        val cutoff = if (safeDays > 0) now - safeDays * MILLIS_PER_DAY else null
        var retained = messages.asSequence()
            .filter { cutoff == null || it.timestampMillis >= cutoff }
            .groupBy(ChatMessage::channelId)
            .values
            .flatMap { channelMessages ->
                channelMessages.sortedWith(messageDescending).take(safeLimit)
            }
            .sortedWith(messageAscending)

        val safeSizeMb = config.maxDatabaseSizeMb.coerceIn(0, MAX_DATABASE_SIZE_MB)
        if (safeSizeMb > 0 && retained.isNotEmpty()) {
            val maxBytes = safeSizeMb.toLong() * BYTES_PER_MEBIBYTE
            var encoded = ChatHistorySnapshotCodec.encode(retained)
            while (encoded.encodeToByteArray().size > maxBytes && retained.isNotEmpty()) {
                retained = retained.drop(minOf(SIZE_TRIM_BATCH, retained.size))
                encoded = ChatHistorySnapshotCodec.encode(retained)
            }
        }
        return retained
    }

    private data class SnapshotResult<T>(val messages: List<ChatMessage>, val value: T)

    private companion object {
        const val MIN_HISTORY_LIMIT = 100
        const val MAX_HISTORY_LIMIT = 5_000
        const val MIN_RETENTION_DAYS = 0
        const val MAX_RETENTION_DAYS = 365
        const val MAX_DATABASE_SIZE_MB = 1_024
        const val INITIAL_HISTORY_PAGE_SIZE = 200
        const val HISTORY_PAGE_SIZE = 200
        const val MAX_SEARCH_RESULTS = 500
        const val SIZE_TRIM_BATCH = 250
        const val MILLIS_PER_DAY = 86_400_000L
        const val BYTES_PER_MEBIBYTE = 1_048_576L

        val messageAscending = compareBy<ChatMessage>({ it.timestampMillis }, { it.id })
        val messageDescending = compareByDescending<ChatMessage> { it.timestampMillis }
            .thenByDescending { it.id }
        val subscriptionTypes = setOf(
            ChatMessageType.SUBSCRIPTION,
            ChatMessageType.RESUBSCRIPTION,
            ChatMessageType.GIFT_SUBSCRIPTION,
        )
    }
}

private fun ModerationState.withAction(action: ModerationAction, atMillis: Long): ModerationState = copy(
    action = action,
    atMillis = atMillis,
)

private fun ChatMessage.hasLink(): Boolean =
    text.contains("http://", ignoreCase = true) ||
        text.contains("https://", ignoreCase = true) ||
        text.contains("www.", ignoreCase = true)

private data class ParsedNativeChatSearchQuery(
    val terms: List<String> = emptyList(),
    val authorLogin: String? = null,
    val channelLogin: String? = null,
    val hasLink: Boolean = false,
    val isDeleted: Boolean = false,
    val isSubscription: Boolean = false,
    val isTimeout: Boolean = false,
    val regexPattern: String? = null,
    val types: Set<ChatMessageType> = emptySet(),
    val afterMillis: Long? = null,
    val beforeMillis: Long? = null,
) {
    val isEmpty: Boolean
        get() = terms.isEmpty() && authorLogin == null && channelLogin == null && !hasLink &&
            !isDeleted && !isSubscription && !isTimeout && regexPattern == null &&
            types.isEmpty() && afterMillis == null && beforeMillis == null
}

private object NativeChatSearchParser {
    private const val MAX_QUERY_LENGTH = 500
    private val operators = setOf("from", "in", "has", "is", "regex", "type", "after", "before")

    fun parse(
        raw: String,
        localDateResolver: ChatHistoryLocalDateResolver,
    ): Result<ParsedNativeChatSearchQuery> = runCatching {
        require(raw.length <= MAX_QUERY_LENGTH) {
            "Search query is too long: maximum is $MAX_QUERY_LENGTH characters"
        }
        var author: String? = null
        var channel: String? = null
        var hasLink = false
        var deleted = false
        var subscription = false
        var timeout = false
        var regex: String? = null
        var afterMillis: Long? = null
        var beforeMillis: Long? = null
        val types = linkedSetOf<ChatMessageType>()
        val terms = mutableListOf<String>()

        tokenize(raw).forEach { token ->
            val separator = token.indexOf(':')
            if (separator <= 0) {
                terms += token
                return@forEach
            }
            val operator = token.substring(0, separator).lowercase()
            val value = token.substring(separator + 1).trim()
            if (operator !in operators) {
                if ("://" in token) {
                    terms += token
                    return@forEach
                }
                throw IllegalArgumentException("Unknown search operator: $operator:")
            }
            when (operator) {
                "from" -> {
                    require(value.isNotBlank()) { "from: requires a user name" }
                    author = value.removePrefix("@").lowercase()
                }
                "in" -> {
                    require(value.isNotBlank()) { "in: requires a channel" }
                    channel = value.removePrefix("#").lowercase()
                }
                "has" -> when (value.lowercase()) {
                    "link" -> hasLink = true
                    else -> throw IllegalArgumentException("Unknown has:$value. Available: has:link")
                }
                "is" -> when (value.lowercase()) {
                    "deleted" -> deleted = true
                    "sub", "subscription" -> subscription = true
                    "timeout" -> timeout = true
                    else -> throw IllegalArgumentException(
                        "Unknown is:$value. Available: deleted, sub, timeout",
                    )
                }
                "regex" -> {
                    require(value.isNotBlank()) { "regex: requires an expression" }
                    runCatching { Regex(value, RegexOption.IGNORE_CASE) }
                        .getOrElse { error ->
                            throw IllegalArgumentException("Invalid regex: ${error.message}")
                        }
                    regex = value
                }
                "type" -> {
                    require(value.isNotBlank()) { "type: requires a message type" }
                    value.split(',')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .forEach { types += parseType(it) }
                }
                "after" -> {
                    val range = resolveDate(value, "after", localDateResolver)
                    afterMillis = range.first
                }
                "before" -> {
                    val range = resolveDate(value, "before", localDateResolver)
                    beforeMillis = range.second
                }
            }
        }
        if (afterMillis != null && beforeMillis != null) {
            require(afterMillis < beforeMillis) { "after: date must be earlier than before:" }
        }
        ParsedNativeChatSearchQuery(
            terms = terms.filter(String::isNotBlank),
            authorLogin = author,
            channelLogin = channel,
            hasLink = hasLink,
            isDeleted = deleted,
            isSubscription = subscription,
            isTimeout = timeout,
            regexPattern = regex,
            types = types,
            afterMillis = afterMillis,
            beforeMillis = beforeMillis,
        )
    }

    private fun resolveDate(
        value: String,
        operator: String,
        resolver: ChatHistoryLocalDateResolver,
    ): Pair<Long, Long> = resolver.resolve(value)
        ?: throw IllegalArgumentException("$operator: uses date YYYY-MM-DD")

    private fun parseType(value: String): ChatMessageType = when (value.lowercase()) {
        "chat", "message" -> ChatMessageType.CHAT
        "action", "me" -> ChatMessageType.ACTION
        "system" -> ChatMessageType.SYSTEM
        "announcement" -> ChatMessageType.ANNOUNCEMENT
        "sub", "subscription" -> ChatMessageType.SUBSCRIPTION
        "resub", "resubscription" -> ChatMessageType.RESUBSCRIPTION
        "gift", "giftsub", "gift_subscription" -> ChatMessageType.GIFT_SUBSCRIPTION
        "raid" -> ChatMessageType.RAID
        "cheer", "bits" -> ChatMessageType.CHEER
        "reward" -> ChatMessageType.REWARD
        "moderation", "mod" -> ChatMessageType.MODERATION
        "unknown" -> ChatMessageType.UNKNOWN
        else -> throw IllegalArgumentException("Unknown type:$value")
    }

    private fun tokenize(raw: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        var escaped = false
        fun flush() {
            current.toString().trim().takeIf(String::isNotEmpty)?.let(tokens::add)
            current.clear()
        }
        raw.trim().forEach { char ->
            when {
                escaped -> {
                    if (char == '"' || char == '\\') {
                        current.append(char)
                    } else {
                        current.append('\\').append(char)
                    }
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == '"' -> quoted = !quoted
                char.isWhitespace() && !quoted -> flush()
                else -> current.append(char)
            }
        }
        require(!quoted) { "Search query contains an unclosed quote" }
        if (escaped) current.append('\\')
        flush()
        return tokens
    }
}
