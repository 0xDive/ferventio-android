package io.ferventio.app.domain

import java.time.LocalDate
import java.time.ZoneId

/** Scope selected in the search screen. */
enum class ChatSearchScope {
    CURRENT_CHANNEL,
    ALL_CHANNELS,
}

enum class ChatSearchDateRange(val days: Int?) {
    ALL(null),
    LAST_24_HOURS(1),
    LAST_7_DAYS(7),
    LAST_30_DAYS(30),
}

data class ChatSearchRequest(
    val rawQuery: String,
    val scope: ChatSearchScope = ChatSearchScope.ALL_CHANNELS,
    val currentChannelId: String? = null,
    val dateRange: ChatSearchDateRange = ChatSearchDateRange.ALL,
    val messageTypes: Set<ChatMessageType> = emptySet(),
    val limit: Int = 300,
    val nowMillis: Long = System.currentTimeMillis(),
)

data class ParsedChatSearchQuery(
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

data class ChatSearchSqlPlan(
    val sql: String,
    val args: List<Any>,
    val candidateLimit: Int,
)

object ChatSearchParser {
    private const val MAX_QUERY_LENGTH = 500
    private val SEARCH_OPERATORS = setOf("from", "in", "has", "is", "regex", "type", "after", "before")

    fun parse(raw: String, zoneId: ZoneId = ZoneId.systemDefault()): Result<ParsedChatSearchQuery> = runCatching {
        require(raw.length <= MAX_QUERY_LENGTH) { "Запрос слишком длинный: максимум $MAX_QUERY_LENGTH символов" }
        val tokens = tokenize(raw)
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

        tokens.forEach { token ->
            val separator = token.indexOf(':')
            if (separator <= 0) {
                terms += token
                return@forEach
            }
            val operator = token.substring(0, separator).lowercase()
            val value = token.substring(separator + 1).trim()
            if (operator !in SEARCH_OPERATORS) {
                if ("://" in token) {
                    terms += token
                    return@forEach
                }
                throw IllegalArgumentException("Неизвестный оператор: $operator:")
            }
            when (operator) {
                "from" -> {
                    require(value.isNotBlank()) { "После from: укажи имя пользователя" }
                    author = value.removePrefix("@").lowercase()
                }
                "in" -> {
                    require(value.isNotBlank()) { "После in: укажи канал" }
                    channel = value.removePrefix("#").lowercase()
                }
                "has" -> when (value.lowercase()) {
                    "link" -> hasLink = true
                    else -> throw IllegalArgumentException("Неизвестный has:$value. Доступно: has:link")
                }
                "is" -> when (value.lowercase()) {
                    "deleted" -> deleted = true
                    "sub", "subscription" -> subscription = true
                    "timeout" -> timeout = true
                    else -> throw IllegalArgumentException("Неизвестный is:$value. Доступно: deleted, sub, timeout")
                }
                "regex" -> {
                    require(value.isNotBlank()) { "После regex: укажи выражение" }
                    runCatching { Regex(value, RegexOption.IGNORE_CASE) }
                        .getOrElse { error -> throw IllegalArgumentException("Ошибка regex: ${error.message}") }
                    regex = value
                }
                "type" -> {
                    require(value.isNotBlank()) { "После type: укажи тип сообщения" }
                    value.split(',').map(String::trim).filter(String::isNotBlank).forEach { rawType ->
                        types += parseType(rawType)
                    }
                }
                "after" -> {
                    val date = parseDate(value, "after")
                    afterMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
                "before" -> {
                    val date = parseDate(value, "before")
                    beforeMillis = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
                }
                else -> throw IllegalArgumentException("Неизвестный оператор: $operator:")
            }
        }

        if (afterMillis != null && beforeMillis != null) {
            require(afterMillis < beforeMillis) { "Дата after: должна быть раньше before:" }
        }

        ParsedChatSearchQuery(
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

    private fun parseDate(value: String, operator: String): LocalDate =
        runCatching { LocalDate.parse(value) }
            .getOrElse { throw IllegalArgumentException("$operator: использует дату YYYY-MM-DD") }

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
        else -> throw IllegalArgumentException("Неизвестный type:$value")
    }

    /** Splits by spaces while preserving quoted operator values such as regex:"hello world". */
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
        require(!quoted) { "Не закрыта кавычка в поисковом запросе" }
        if (escaped) current.append('\\')
        flush()
        return tokens
    }
}

object ChatSearchSqlBuilder {
    private const val MAX_RESULTS = 500
    private const val MAX_REGEX_CANDIDATES = 5_000

    fun build(request: ChatSearchRequest, parsed: ParsedChatSearchQuery): ChatSearchSqlPlan {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (request.scope == ChatSearchScope.CURRENT_CHANNEL) {
            val channelId = request.currentChannelId?.takeIf(String::isNotBlank)
                ?: throw IllegalArgumentException("Текущий канал не выбран")
            clauses += "channel_id = ?"
            args += channelId
        }
        parsed.channelLogin?.let {
            clauses += "LOWER(channel_login) = ?"
            args += it.lowercase()
        }
        parsed.authorLogin?.let {
            clauses += "(LOWER(author_login) = ? OR LOWER(author_display_name) = ?)"
            args += it.lowercase()
            args += it.lowercase()
        }
        parsed.terms.forEach { term ->
            val pattern = "%${escapeLike(term.lowercase())}%"
            clauses += "(LOWER(text) LIKE ? ESCAPE '\\' OR LOWER(author_login) LIKE ? ESCAPE '\\' OR LOWER(author_display_name) LIKE ? ESCAPE '\\')"
            repeat(3) { args += pattern }
        }
        if (parsed.hasLink) {
            clauses += "(LOWER(text) LIKE '%http://%' OR LOWER(text) LIKE '%https://%' OR LOWER(text) LIKE '%www.%')"
        }
        if (parsed.isDeleted) clauses += "is_deleted = 1"
        if (parsed.isTimeout) clauses += "moderation_action = 'TIMEOUT'"
        if (parsed.isSubscription) {
            clauses += "message_type IN ('SUBSCRIPTION', 'RESUBSCRIPTION', 'GIFT_SUBSCRIPTION')"
        }

        val requestedTypes = when {
            request.messageTypes.isNotEmpty() && parsed.types.isNotEmpty() ->
                request.messageTypes.intersect(parsed.types)
            request.messageTypes.isNotEmpty() -> request.messageTypes
            else -> parsed.types
        }
        if (request.messageTypes.isNotEmpty() && parsed.types.isNotEmpty() && requestedTypes.isEmpty()) {
            clauses += "0 = 1"
        } else if (requestedTypes.isNotEmpty()) {
            clauses += "message_type IN (${requestedTypes.joinToString { "?" }})"
            args.addAll(requestedTypes.map(ChatMessageType::name))
        }

        val rangeCutoff = request.dateRange.days?.let { days ->
            request.nowMillis - days * 86_400_000L
        }
        listOfNotNull(rangeCutoff, parsed.afterMillis).maxOrNull()?.let {
            clauses += "timestamp_millis >= ?"
            args += it
        }
        parsed.beforeMillis?.let {
            clauses += "timestamp_millis < ?"
            args += it
        }

        val safeLimit = request.limit.coerceIn(1, MAX_RESULTS)
        val candidateLimit = if (parsed.regexPattern == null) safeLimit else MAX_REGEX_CANDIDATES
        val where = clauses.takeIf(List<String>::isNotEmpty)?.joinToString(" AND ") ?: "1 = 1"
        val sql = "SELECT id FROM chat_messages WHERE $where ORDER BY timestamp_millis DESC, id DESC LIMIT ?"
        args += candidateLimit
        return ChatSearchSqlPlan(sql, args, candidateLimit)
    }

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
