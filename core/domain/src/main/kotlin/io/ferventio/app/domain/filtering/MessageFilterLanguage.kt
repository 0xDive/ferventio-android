package io.ferventio.app.domain

import androidx.compose.runtime.Immutable
import kotlin.uuid.Uuid

const val MAX_FILTER_EXPRESSION_LENGTH = 2_000
const val MAX_SAVED_FILTERS = 100
const val SAVED_FILTER_REFERENCE_PREFIX = "@filter:"

fun savedFilterReference(filterId: String): String =
    SAVED_FILTER_REFERENCE_PREFIX + filterId.trim().take(160)

fun savedFilterIdFromReference(value: String): String? = value.trim()
    .takeIf { it.startsWith(SAVED_FILTER_REFERENCE_PREFIX) }
    ?.removePrefix(SAVED_FILTER_REFERENCE_PREFIX)
    ?.takeIf(String::isNotBlank)

fun resolveSplitFilterExpression(
    value: String,
    savedFilters: List<SavedMessageFilter>,
): String {
    val filterId = savedFilterIdFromReference(value) ?: return value
    return savedFilters.firstOrNull { it.id == filterId }?.expression.orEmpty()
}

@Immutable
data class SavedMessageFilter(
    val id: String = Uuid.random().toString(),
    val name: String,
    val expression: String,
)

@Immutable
data class FilterSpan(
    val start: Int,
    val endExclusive: Int,
) {
    init {
        require(start >= 0)
        require(endExclusive >= start)
    }
}

@Immutable
enum class FilterDiagnosticSeverity {
    ERROR,
    WARNING,
}

@Immutable
data class FilterDiagnostic(
    val severity: FilterDiagnosticSeverity,
    val message: String,
    val span: FilterSpan,
)

@Immutable
enum class FilterTokenKind {
    IDENTIFIER,
    STRING,
    NUMBER,
    BOOLEAN,
    REGEX,
    OPERATOR,
    KEYWORD_OPERATOR,
    LEFT_PAREN,
    RIGHT_PAREN,
    LEFT_BRACKET,
    RIGHT_BRACKET,
    COMMA,
    EOF,
    INVALID,
}

@Immutable
data class FilterToken(
    val kind: FilterTokenKind,
    val lexeme: String,
    val span: FilterSpan,
    val value: Any? = null,
)

@Immutable
data class FilterTokenization(
    val tokens: List<FilterToken>,
    val diagnostics: List<FilterDiagnostic>,
)

@Immutable
enum class FilterValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    STRING_LIST,
    REGEX,
    NULL,
    UNKNOWN,
}

sealed interface FilterExpression {
    val span: FilterSpan

    @Immutable
    data class Field(
        val path: String,
        override val span: FilterSpan,
    ) : FilterExpression

    @Immutable
    data class Literal(
        val value: FilterLiteralValue,
        override val span: FilterSpan,
    ) : FilterExpression

    @Immutable
    data class ListLiteral(
        val items: List<FilterExpression>,
        override val span: FilterSpan,
    ) : FilterExpression

    @Immutable
    data class Unary(
        val operator: FilterUnaryOperator,
        val operand: FilterExpression,
        override val span: FilterSpan,
    ) : FilterExpression

    @Immutable
    data class Binary(
        val left: FilterExpression,
        val operator: FilterBinaryOperator,
        val right: FilterExpression,
        override val span: FilterSpan,
    ) : FilterExpression
}

sealed interface FilterLiteralValue {
    @Immutable
    data class StringValue(val value: String) : FilterLiteralValue

    @Immutable
    data class NumberValue(val value: Double) : FilterLiteralValue

    @Immutable
    data class BooleanValue(val value: Boolean) : FilterLiteralValue

    data class RegexValue(
        val pattern: String,
        val ignoreCase: Boolean,
        val compiled: Regex,
    ) : FilterLiteralValue
}

@Immutable
enum class FilterUnaryOperator {
    NOT,
}

@Immutable
enum class FilterBinaryOperator {
    EQUALS,
    NOT_EQUALS,
    LESS,
    GREATER,
    LESS_OR_EQUAL,
    GREATER_OR_EQUAL,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    MATCHES,
    AND,
    OR,
}

@Immutable
data class FilterParseResult(
    val expression: FilterExpression?,
    val tokens: List<FilterToken>,
    val diagnostics: List<FilterDiagnostic>,
)

class CompiledMessageFilter internal constructor(
    val source: String,
    val expression: FilterExpression?,
    val tokens: List<FilterToken>,
    val diagnostics: List<FilterDiagnostic>,
    val resultType: FilterValueType,
    val isLegacyTextFilter: Boolean = false,
    private val legacyQuery: String? = null,
) {
    val isValid: Boolean
        get() = diagnostics.none { it.severity == FilterDiagnosticSeverity.ERROR } &&
            (expression != null || isLegacyTextFilter)

    fun matches(message: ChatMessage): Boolean {
        if (!isValid) return false
        legacyQuery?.let { query ->
            return message.text.contains(query, ignoreCase = true) ||
                message.userLogin.contains(query, ignoreCase = true) ||
                message.userDisplayName.contains(query, ignoreCase = true) ||
                message.type.name.contains(query, ignoreCase = true)
        }
        val root = expression ?: return false
        return evaluate(root, message).asBoolean() == true
    }

    private fun evaluate(expression: FilterExpression, message: ChatMessage): RuntimeFilterValue = when (expression) {
        is FilterExpression.Field -> fieldValue(expression.path, message)
        is FilterExpression.Literal -> when (val literal = expression.value) {
            is FilterLiteralValue.StringValue -> RuntimeFilterValue.StringValue(literal.value)
            is FilterLiteralValue.NumberValue -> RuntimeFilterValue.NumberValue(literal.value)
            is FilterLiteralValue.BooleanValue -> RuntimeFilterValue.BooleanValue(literal.value)
            is FilterLiteralValue.RegexValue -> RuntimeFilterValue.RegexValue(literal.compiled)
        }
        is FilterExpression.ListLiteral -> RuntimeFilterValue.ListValue(
            expression.items.map { evaluate(it, message) },
        )
        is FilterExpression.Unary -> when (expression.operator) {
            FilterUnaryOperator.NOT -> RuntimeFilterValue.BooleanValue(
                evaluate(expression.operand, message).asBoolean() != true,
            )
        }
        is FilterExpression.Binary -> when (expression.operator) {
            FilterBinaryOperator.AND -> {
                val left = evaluate(expression.left, message).asBoolean() == true
                if (!left) RuntimeFilterValue.BooleanValue(false)
                else RuntimeFilterValue.BooleanValue(evaluate(expression.right, message).asBoolean() == true)
            }
            FilterBinaryOperator.OR -> {
                val left = evaluate(expression.left, message).asBoolean() == true
                if (left) RuntimeFilterValue.BooleanValue(true)
                else RuntimeFilterValue.BooleanValue(evaluate(expression.right, message).asBoolean() == true)
            }
            else -> RuntimeFilterValue.BooleanValue(
                compare(
                    left = evaluate(expression.left, message),
                    operator = expression.operator,
                    right = evaluate(expression.right, message),
                ),
            )
        }
    }

    private fun compare(
        left: RuntimeFilterValue,
        operator: FilterBinaryOperator,
        right: RuntimeFilterValue,
    ): Boolean {
        return when (operator) {
            FilterBinaryOperator.EQUALS -> valuesEqual(left, right)
            FilterBinaryOperator.NOT_EQUALS -> !valuesEqual(left, right)
            FilterBinaryOperator.LESS -> orderedCompare(left, right)?.let { it < 0 } == true
            FilterBinaryOperator.GREATER -> orderedCompare(left, right)?.let { it > 0 } == true
            FilterBinaryOperator.LESS_OR_EQUAL -> orderedCompare(left, right)?.let { it <= 0 } == true
            FilterBinaryOperator.GREATER_OR_EQUAL -> orderedCompare(left, right)?.let { it >= 0 } == true
            FilterBinaryOperator.CONTAINS -> contains(left, right)
            FilterBinaryOperator.STARTS_WITH -> {
                val lhs = left.asString() ?: return false
                val rhs = right.asString() ?: return false
                lhs.startsWith(rhs, ignoreCase = true)
            }
            FilterBinaryOperator.ENDS_WITH -> {
                val lhs = left.asString() ?: return false
                val rhs = right.asString() ?: return false
                lhs.endsWith(rhs, ignoreCase = true)
            }
            FilterBinaryOperator.MATCHES -> {
                val lhs = left.asString() ?: return false
                val regex = when (right) {
                    is RuntimeFilterValue.RegexValue -> right.value
                    is RuntimeFilterValue.StringValue -> runCatching { Regex(right.value) }.getOrNull()
                    else -> null
                } ?: return false
                regex.containsMatchIn(lhs)
            }
            FilterBinaryOperator.AND,
            FilterBinaryOperator.OR,
            -> false
        }
    }

    private fun valuesEqual(left: RuntimeFilterValue, right: RuntimeFilterValue): Boolean {
        if (left is RuntimeFilterValue.ListValue && right !is RuntimeFilterValue.ListValue) {
            return left.items.any { valuesEqual(it, right) }
        }
        if (right is RuntimeFilterValue.ListValue && left !is RuntimeFilterValue.ListValue) {
            return right.items.any { valuesEqual(left, it) }
        }
        return when {
            left is RuntimeFilterValue.NullValue && right is RuntimeFilterValue.NullValue -> true
            left is RuntimeFilterValue.StringValue && right is RuntimeFilterValue.StringValue ->
                left.value.equals(right.value, ignoreCase = true)
            left is RuntimeFilterValue.NumberValue && right is RuntimeFilterValue.NumberValue ->
                left.value == right.value
            left is RuntimeFilterValue.BooleanValue && right is RuntimeFilterValue.BooleanValue ->
                left.value == right.value
            left is RuntimeFilterValue.ListValue && right is RuntimeFilterValue.ListValue ->
                left.items.size == right.items.size && left.items.zip(right.items).all { (a, b) -> valuesEqual(a, b) }
            else -> false
        }
    }

    private fun contains(left: RuntimeFilterValue, right: RuntimeFilterValue): Boolean {
        return when (left) {
            is RuntimeFilterValue.StringValue -> {
                val needle = right.asString() ?: return false
                left.value.contains(needle, ignoreCase = true)
            }
            is RuntimeFilterValue.ListValue -> when (right) {
                is RuntimeFilterValue.ListValue -> right.items.any { candidate ->
                    left.items.any { item -> valuesEqual(item, candidate) }
                }
                else -> left.items.any { item -> valuesEqual(item, right) }
            }
            else -> false
        }
    }

    private fun orderedCompare(left: RuntimeFilterValue, right: RuntimeFilterValue): Int? = when {
        left is RuntimeFilterValue.NumberValue && right is RuntimeFilterValue.NumberValue ->
            left.value.compareTo(right.value)
        left is RuntimeFilterValue.StringValue && right is RuntimeFilterValue.StringValue ->
            left.value.lowercase().compareTo(right.value.lowercase())
        else -> null
    }
}

object MessageFilterLanguage {
    private val knownFields = mapOf(
        "message.content" to FilterValueType.STRING,
        "message.length" to FilterValueType.NUMBER,
        "author.name" to FilterValueType.STRING,
        "author.id" to FilterValueType.STRING,
        "author.badges" to FilterValueType.STRING_LIST,
        "author.subbed" to FilterValueType.BOOLEAN,
        "channel.name" to FilterValueType.STRING,
        "channel.id" to FilterValueType.STRING,
        "flags.deleted" to FilterValueType.BOOLEAN,
        "flags.reward_message" to FilterValueType.BOOLEAN,
        "flags.subscription" to FilterValueType.BOOLEAN,
        "flags.moderation" to FilterValueType.BOOLEAN,
        "reply.parent_author" to FilterValueType.STRING,
        "reward.title" to FilterValueType.STRING,
        "reward.cost" to FilterValueType.NUMBER,
    )

    val fields: Map<String, FilterValueType>
        get() = knownFields

    fun tokenize(source: String): FilterTokenization = FilterTokenizer(source).tokenize()

    fun parse(source: String): FilterParseResult {
        val normalized = source.take(MAX_FILTER_EXPRESSION_LENGTH)
        val tokenization = tokenize(normalized)
        val parser = FilterParser(tokenization.tokens)
        val expression = parser.parse()
        return FilterParseResult(
            expression = expression,
            tokens = tokenization.tokens,
            diagnostics = tokenization.diagnostics + parser.diagnostics,
        )
    }

    fun compile(source: String): CompiledMessageFilter {
        val normalized = source.trim().take(MAX_FILTER_EXPRESSION_LENGTH)
        if (normalized.isBlank()) {
            return CompiledMessageFilter(
                source = normalized,
                expression = null,
                tokens = emptyList(),
                diagnostics = listOf(
                    FilterDiagnostic(
                        FilterDiagnosticSeverity.ERROR,
                        "Выражение фильтра пустое",
                        FilterSpan(0, 0),
                    ),
                ),
                resultType = FilterValueType.UNKNOWN,
            )
        }
        val parsed = parse(normalized)
        val typeChecker = FilterTypeChecker(knownFields)
        val resultType = parsed.expression?.let(typeChecker::check) ?: FilterValueType.UNKNOWN
        val diagnostics = parsed.diagnostics + typeChecker.diagnostics + buildList {
            if (parsed.expression != null && resultType != FilterValueType.BOOLEAN &&
                typeChecker.diagnostics.none { it.severity == FilterDiagnosticSeverity.ERROR }
            ) {
                add(
                    FilterDiagnostic(
                        FilterDiagnosticSeverity.ERROR,
                        "Фильтр должен возвращать Boolean, получено ${resultType.displayName()}",
                        parsed.expression.span,
                    ),
                )
            }
        }
        return CompiledMessageFilter(
            source = normalized,
            expression = parsed.expression,
            tokens = parsed.tokens,
            diagnostics = diagnostics.sortedWith(compareBy({ it.span.start }, { it.severity.ordinal })),
            resultType = resultType,
        )
    }

    fun compileForSplit(source: String): CompiledMessageFilter {
        val normalized = source.trim().take(MAX_FILTER_EXPRESSION_LENGTH)
        if (normalized.isBlank()) {
            return CompiledMessageFilter(
                source = normalized,
                expression = FilterExpression.Literal(
                    FilterLiteralValue.BooleanValue(true),
                    FilterSpan(0, 0),
                ),
                tokens = emptyList(),
                diagnostics = emptyList(),
                resultType = FilterValueType.BOOLEAN,
            )
        }
        val compiled = compile(normalized)
        if (compiled.isValid || !looksLikeLegacyText(normalized)) return compiled
        return CompiledMessageFilter(
            source = normalized,
            expression = null,
            tokens = compiled.tokens,
            diagnostics = listOf(
                FilterDiagnostic(
                    FilterDiagnosticSeverity.WARNING,
                    "Используется совместимый простой поиск. Открой редактор и замени его выражением языка фильтров.",
                    FilterSpan(0, normalized.length),
                ),
            ),
            resultType = FilterValueType.BOOLEAN,
            isLegacyTextFilter = true,
            legacyQuery = normalized,
        )
    }

    fun examples(): List<Pair<String, String>> = listOf(
        "Сообщения длиннее 80 символов" to "message.length > 80",
        "Moderator или VIP" to "author.badges contains [\"moderator\", \"vip\"]",
        "Упоминание слова" to "message.content contains \"ferventio\"",
        "Regex без регистра" to "message.content matches /hello|привет/i",
        "Не удалённые ответы" to "!flags.deleted && reply.parent_author != \"\"",
        "Награды дороже 1000" to "flags.reward_message && reward.cost >= 1000",
    )

    private fun looksLikeLegacyText(source: String): Boolean {
        if (source.startsWith("@")) return false
        if (source.any { it in "()[]<>=!&|/\"'" }) return false
        if (source.lowercase().let { value ->
                knownFields.keys.any(value::contains) ||
                    listOf(" contains ", " startswith ", " endswith ", " matches ").any(value::contains)
            }
        ) {
            return false
        }
        return true
    }
}

private sealed interface RuntimeFilterValue {
    data class StringValue(val value: String) : RuntimeFilterValue
    data class NumberValue(val value: Double) : RuntimeFilterValue
    data class BooleanValue(val value: Boolean) : RuntimeFilterValue
    data class ListValue(val items: List<RuntimeFilterValue>) : RuntimeFilterValue
    data class RegexValue(val value: Regex) : RuntimeFilterValue
    data object NullValue : RuntimeFilterValue

    fun asBoolean(): Boolean? = (this as? BooleanValue)?.value
    fun asString(): String? = (this as? StringValue)?.value
}

private fun fieldValue(path: String, message: ChatMessage): RuntimeFilterValue = when (path) {
    "message.content" -> RuntimeFilterValue.StringValue(message.text)
    "message.length" -> RuntimeFilterValue.NumberValue(message.text.length.toDouble())
    "author.name" -> RuntimeFilterValue.StringValue(message.userDisplayName)
    "author.id" -> RuntimeFilterValue.StringValue(message.userId)
    "author.badges" -> RuntimeFilterValue.ListValue(
        message.badges.map { RuntimeFilterValue.StringValue(it.setId.lowercase()) },
    )
    "author.subbed" -> RuntimeFilterValue.BooleanValue(
        message.badges.any { badge ->
            badge.setId.equals("subscriber", ignoreCase = true) ||
                badge.setId.equals("founder", ignoreCase = true)
        },
    )
    "channel.name" -> RuntimeFilterValue.StringValue(message.channelLogin)
    "channel.id" -> RuntimeFilterValue.StringValue(message.channelId)
    "flags.deleted" -> RuntimeFilterValue.BooleanValue(message.isDeleted)
    "flags.reward_message" -> RuntimeFilterValue.BooleanValue(message.type == ChatMessageType.REWARD)
    "flags.subscription" -> RuntimeFilterValue.BooleanValue(
        message.type in setOf(
            ChatMessageType.SUBSCRIPTION,
            ChatMessageType.RESUBSCRIPTION,
            ChatMessageType.GIFT_SUBSCRIPTION,
        ),
    )
    "flags.moderation" -> RuntimeFilterValue.BooleanValue(
        message.type == ChatMessageType.MODERATION || message.moderation.action != null,
    )
    "reply.parent_author" -> RuntimeFilterValue.StringValue(
        message.reply?.let { reply ->
            reply.parentUserLogin?.takeIf(String::isNotBlank)
                ?: reply.parentUserName.orEmpty()
        }.orEmpty(),
    )
    "reward.title" -> RuntimeFilterValue.StringValue(message.reward?.title.orEmpty())
    "reward.cost" -> message.reward?.cost?.let { RuntimeFilterValue.NumberValue(it.toDouble()) }
        ?: RuntimeFilterValue.NullValue
    else -> RuntimeFilterValue.NullValue
}

private class FilterTokenizer(private val source: String) {
    private val tokens = mutableListOf<FilterToken>()
    private val diagnostics = mutableListOf<FilterDiagnostic>()
    private var index = 0

    fun tokenize(): FilterTokenization {
        while (index < source.length) {
            val start = index
            when (val char = source[index]) {
                ' ', '\t', '\r', '\n' -> index++
                '(' -> single(FilterTokenKind.LEFT_PAREN)
                ')' -> single(FilterTokenKind.RIGHT_PAREN)
                '[' -> single(FilterTokenKind.LEFT_BRACKET)
                ']' -> single(FilterTokenKind.RIGHT_BRACKET)
                ',' -> single(FilterTokenKind.COMMA)
                '!' -> {
                    index++
                    if (match('=')) add(FilterTokenKind.OPERATOR, start, index)
                    else add(FilterTokenKind.OPERATOR, start, index)
                }
                '=' -> {
                    index++
                    if (match('=')) add(FilterTokenKind.OPERATOR, start, index)
                    else invalid(start, index, "Ожидался оператор ==")
                }
                '<', '>' -> {
                    index++
                    match('=')
                    add(FilterTokenKind.OPERATOR, start, index)
                }
                '&' -> {
                    index++
                    if (match('&')) add(FilterTokenKind.OPERATOR, start, index)
                    else invalid(start, index, "Ожидался оператор &&")
                }
                '|' -> {
                    index++
                    if (match('|')) add(FilterTokenKind.OPERATOR, start, index)
                    else invalid(start, index, "Ожидался оператор ||")
                }
                '"', '\'' -> readString(char)
                '/' -> readRegex()
                else -> when {
                    char.isDigit() -> readNumber()
                    char.isLetter() || char == '_' -> readIdentifier()
                    else -> {
                        index++
                        invalid(start, index, "Недопустимый символ '$char'")
                    }
                }
            }
        }
        tokens += FilterToken(FilterTokenKind.EOF, "", FilterSpan(source.length, source.length))
        return FilterTokenization(tokens.toList(), diagnostics.toList())
    }

    private fun single(kind: FilterTokenKind) {
        val start = index
        index++
        add(kind, start, index)
    }

    private fun readString(quote: Char) {
        val start = index
        index++
        val value = StringBuilder()
        var terminated = false
        while (index < source.length) {
            val char = source[index++]
            if (char == quote) {
                terminated = true
                break
            }
            if (char == '\\' && index < source.length) {
                val escaped = source[index++]
                value.append(
                    when (escaped) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        '\\' -> '\\'
                        '"' -> '"'
                        '\'' -> '\''
                        else -> escaped
                    },
                )
            } else {
                value.append(char)
            }
        }
        add(FilterTokenKind.STRING, start, index, value.toString())
        if (!terminated) {
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Строка не закрыта",
                FilterSpan(start, index),
            )
        }
    }

    private fun readRegex() {
        val start = index
        index++
        val pattern = StringBuilder()
        var escaped = false
        var terminated = false
        while (index < source.length) {
            val char = source[index++]
            if (!escaped && char == '/') {
                terminated = true
                break
            }
            if (!escaped && char == '\\') {
                escaped = true
                pattern.append(char)
            } else {
                escaped = false
                pattern.append(char)
            }
        }
        var ignoreCase = false
        if (terminated && index < source.length && source[index].lowercaseChar() == 'i') {
            ignoreCase = true
            index++
        }
        val options = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        val compiled = runCatching { Regex(pattern.toString(), options) }.getOrElse { error ->
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Некорректный regex: ${error.message.orEmpty()}",
                FilterSpan(start, index),
            )
            Regex("(?!)")
        }
        add(
            FilterTokenKind.REGEX,
            start,
            index,
            FilterLiteralValue.RegexValue(pattern.toString(), ignoreCase, compiled),
        )
        if (!terminated) {
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Regex не закрыт символом /",
                FilterSpan(start, index),
            )
        }
    }

    private fun readNumber() {
        val start = index
        while (index < source.length && source[index].isDigit()) index++
        if (index < source.length && source[index] == '.' &&
            index + 1 < source.length && source[index + 1].isDigit()
        ) {
            index++
            while (index < source.length && source[index].isDigit()) index++
        }
        val raw = source.substring(start, index)
        add(FilterTokenKind.NUMBER, start, index, raw.toDoubleOrNull())
    }

    private fun readIdentifier() {
        val start = index
        while (index < source.length) {
            val char = source[index]
            if (char.isLetterOrDigit() || char == '_' || char == '.') index++ else break
        }
        val raw = source.substring(start, index)
        when (raw.lowercase()) {
            "true" -> add(FilterTokenKind.BOOLEAN, start, index, true)
            "false" -> add(FilterTokenKind.BOOLEAN, start, index, false)
            "contains", "startswith", "endswith", "matches" ->
                add(FilterTokenKind.KEYWORD_OPERATOR, start, index)
            else -> add(FilterTokenKind.IDENTIFIER, start, index, raw)
        }
    }

    private fun match(expected: Char): Boolean {
        if (index >= source.length || source[index] != expected) return false
        index++
        return true
    }

    private fun add(kind: FilterTokenKind, start: Int, end: Int, value: Any? = null) {
        tokens += FilterToken(kind, source.substring(start, end), FilterSpan(start, end), value)
    }

    private fun invalid(start: Int, end: Int, message: String) {
        add(FilterTokenKind.INVALID, start, end)
        diagnostics += FilterDiagnostic(FilterDiagnosticSeverity.ERROR, message, FilterSpan(start, end))
    }
}

private class FilterParser(private val tokens: List<FilterToken>) {
    val diagnostics = mutableListOf<FilterDiagnostic>()
    private var index = 0

    fun parse(): FilterExpression? {
        if (peek().kind == FilterTokenKind.EOF) return null
        val expression = parseOr()
        if (!isAtEnd()) {
            val token = peek()
            diagnostics += error(token, "Лишний токен '${token.lexeme}'")
        }
        return expression
    }

    private fun parseOr(): FilterExpression? {
        var expression = parseAnd() ?: return null
        while (matchOperator("||")) {
            val operator = previous()
            val right = parseAnd() ?: missingOperand(operator)
            expression = FilterExpression.Binary(
                expression,
                FilterBinaryOperator.OR,
                right,
                expression.span.merge(right.span),
            )
        }
        return expression
    }

    private fun parseAnd(): FilterExpression? {
        var expression = parseComparison() ?: return null
        while (matchOperator("&&")) {
            val operator = previous()
            val right = parseComparison() ?: missingOperand(operator)
            expression = FilterExpression.Binary(
                expression,
                FilterBinaryOperator.AND,
                right,
                expression.span.merge(right.span),
            )
        }
        return expression
    }

    private fun parseComparison(): FilterExpression? {
        var expression = parseUnary() ?: return null
        val operator = parseComparisonOperator() ?: return expression
        val operatorToken = previous()
        val right = parseUnary() ?: missingOperand(operatorToken)
        expression = FilterExpression.Binary(
            expression,
            operator,
            right,
            expression.span.merge(right.span),
        )
        if (isComparisonToken(peek())) {
            diagnostics += error(peek(), "Цепочки сравнений не поддерживаются; раздели их через &&")
        }
        return expression
    }

    private fun parseUnary(): FilterExpression? {
        if (matchOperator("!")) {
            val operator = previous()
            val operand = parseUnary() ?: missingOperand(operator)
            return FilterExpression.Unary(
                FilterUnaryOperator.NOT,
                operand,
                operator.span.merge(operand.span),
            )
        }
        return parsePrimary()
    }

    private fun parsePrimary(): FilterExpression? {
        val token = advance()
        return when (token.kind) {
            FilterTokenKind.IDENTIFIER -> FilterExpression.Field(token.lexeme, token.span)
            FilterTokenKind.STRING -> FilterExpression.Literal(
                FilterLiteralValue.StringValue(token.value as? String ?: ""),
                token.span,
            )
            FilterTokenKind.NUMBER -> FilterExpression.Literal(
                FilterLiteralValue.NumberValue(token.value as? Double ?: 0.0),
                token.span,
            )
            FilterTokenKind.BOOLEAN -> FilterExpression.Literal(
                FilterLiteralValue.BooleanValue(token.value as? Boolean ?: false),
                token.span,
            )
            FilterTokenKind.REGEX -> FilterExpression.Literal(
                token.value as? FilterLiteralValue.RegexValue
                    ?: FilterLiteralValue.RegexValue("(?!)", false, Regex("(?!)")),
                token.span,
            )
            FilterTokenKind.LEFT_PAREN -> {
                val expression = parseOr()
                if (!match(FilterTokenKind.RIGHT_PAREN)) {
                    diagnostics += error(peek(), "Ожидалась закрывающая скобка )")
                }
                expression
            }
            FilterTokenKind.LEFT_BRACKET -> parseList(token)
            FilterTokenKind.EOF -> {
                diagnostics += error(token, "Ожидалось выражение")
                null
            }
            else -> {
                diagnostics += error(token, "Ожидалось поле, литерал, список или скобка")
                null
            }
        }
    }

    private fun parseList(opening: FilterToken): FilterExpression.ListLiteral {
        val items = mutableListOf<FilterExpression>()
        if (!check(FilterTokenKind.RIGHT_BRACKET)) {
            do {
                parseOr()?.let(items::add)
            } while (match(FilterTokenKind.COMMA))
        }
        val closing = if (match(FilterTokenKind.RIGHT_BRACKET)) previous() else {
            diagnostics += error(peek(), "Ожидалась закрывающая скобка ]")
            opening
        }
        return FilterExpression.ListLiteral(items, opening.span.merge(closing.span))
    }

    private fun parseComparisonOperator(): FilterBinaryOperator? {
        val token = peek()
        val operator = when (token.lexeme.lowercase()) {
            "==" -> FilterBinaryOperator.EQUALS
            "!=" -> FilterBinaryOperator.NOT_EQUALS
            "<" -> FilterBinaryOperator.LESS
            ">" -> FilterBinaryOperator.GREATER
            "<=" -> FilterBinaryOperator.LESS_OR_EQUAL
            ">=" -> FilterBinaryOperator.GREATER_OR_EQUAL
            "contains" -> FilterBinaryOperator.CONTAINS
            "startswith" -> FilterBinaryOperator.STARTS_WITH
            "endswith" -> FilterBinaryOperator.ENDS_WITH
            "matches" -> FilterBinaryOperator.MATCHES
            else -> null
        } ?: return null
        index++
        return operator
    }

    private fun missingOperand(operator: FilterToken): FilterExpression {
        diagnostics += error(operator, "После оператора '${operator.lexeme}' отсутствует выражение")
        return FilterExpression.Literal(FilterLiteralValue.BooleanValue(false), operator.span)
    }

    private fun matchOperator(value: String): Boolean {
        if (peek().lexeme != value) return false
        index++
        return true
    }

    private fun isComparisonToken(token: FilterToken): Boolean = token.kind in setOf(
        FilterTokenKind.OPERATOR,
        FilterTokenKind.KEYWORD_OPERATOR,
    ) && token.lexeme !in setOf("!", "&&", "||")

    private fun match(kind: FilterTokenKind): Boolean {
        if (!check(kind)) return false
        index++
        return true
    }

    private fun check(kind: FilterTokenKind): Boolean = peek().kind == kind
    private fun isAtEnd(): Boolean = peek().kind == FilterTokenKind.EOF
    private fun peek(): FilterToken = tokens[index.coerceIn(0, tokens.lastIndex)]
    private fun previous(): FilterToken = tokens[(index - 1).coerceAtLeast(0)]
    private fun advance(): FilterToken = peek().also { if (!isAtEnd()) index++ }
    private fun error(token: FilterToken, message: String) = FilterDiagnostic(
        FilterDiagnosticSeverity.ERROR,
        message,
        token.span,
    )
}

private class FilterTypeChecker(
    private val fields: Map<String, FilterValueType>,
) {
    val diagnostics = mutableListOf<FilterDiagnostic>()

    fun check(expression: FilterExpression): FilterValueType = when (expression) {
        is FilterExpression.Field -> fields[expression.path.lowercase()] ?: run {
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Неизвестное поле '${expression.path}'",
                expression.span,
            )
            FilterValueType.UNKNOWN
        }
        is FilterExpression.Literal -> when (expression.value) {
            is FilterLiteralValue.StringValue -> FilterValueType.STRING
            is FilterLiteralValue.NumberValue -> FilterValueType.NUMBER
            is FilterLiteralValue.BooleanValue -> FilterValueType.BOOLEAN
            is FilterLiteralValue.RegexValue -> FilterValueType.REGEX
        }
        is FilterExpression.ListLiteral -> checkList(expression)
        is FilterExpression.Unary -> {
            val operandType = check(expression.operand)
            requireType(operandType, setOf(FilterValueType.BOOLEAN), expression.operand.span, "Оператор !")
            FilterValueType.BOOLEAN
        }
        is FilterExpression.Binary -> checkBinary(expression)
    }

    private fun checkList(expression: FilterExpression.ListLiteral): FilterValueType {
        if (expression.items.isEmpty()) {
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Список не может быть пустым",
                expression.span,
            )
            return FilterValueType.UNKNOWN
        }
        val itemTypes = expression.items.map(::check).filter { it != FilterValueType.UNKNOWN }.toSet()
        return if (itemTypes.all { it == FilterValueType.STRING }) {
            FilterValueType.STRING_LIST
        } else {
            diagnostics += FilterDiagnostic(
                FilterDiagnosticSeverity.ERROR,
                "Списки поддерживают только строки",
                expression.span,
            )
            FilterValueType.UNKNOWN
        }
    }

    private fun checkBinary(expression: FilterExpression.Binary): FilterValueType {
        val left = check(expression.left)
        val right = check(expression.right)
        when (expression.operator) {
            FilterBinaryOperator.AND,
            FilterBinaryOperator.OR,
            -> {
                requireType(left, setOf(FilterValueType.BOOLEAN), expression.left.span, "Логический оператор")
                requireType(right, setOf(FilterValueType.BOOLEAN), expression.right.span, "Логический оператор")
            }
            FilterBinaryOperator.EQUALS,
            FilterBinaryOperator.NOT_EQUALS,
            -> if (!compatibleEquality(left, right)) {
                diagnostics += mismatch(expression, left, right)
            }
            FilterBinaryOperator.LESS,
            FilterBinaryOperator.GREATER,
            FilterBinaryOperator.LESS_OR_EQUAL,
            FilterBinaryOperator.GREATER_OR_EQUAL,
            -> {
                val valid = (left == FilterValueType.NUMBER && right == FilterValueType.NUMBER) ||
                    (left == FilterValueType.STRING && right == FilterValueType.STRING)
                if (!valid && left != FilterValueType.UNKNOWN && right != FilterValueType.UNKNOWN) {
                    diagnostics += FilterDiagnostic(
                        FilterDiagnosticSeverity.ERROR,
                        "Сравнение порядка возможно только между двумя Number или двумя String",
                        expression.span,
                    )
                }
            }
            FilterBinaryOperator.CONTAINS -> {
                val valid = (left == FilterValueType.STRING && right == FilterValueType.STRING) ||
                    (left == FilterValueType.STRING_LIST && right in setOf(FilterValueType.STRING, FilterValueType.STRING_LIST))
                if (!valid && left != FilterValueType.UNKNOWN && right != FilterValueType.UNKNOWN) {
                    diagnostics += FilterDiagnostic(
                        FilterDiagnosticSeverity.ERROR,
                        "contains ожидает String contains String или List<String> contains String/List<String>",
                        expression.span,
                    )
                }
            }
            FilterBinaryOperator.STARTS_WITH,
            FilterBinaryOperator.ENDS_WITH,
            -> {
                requireType(left, setOf(FilterValueType.STRING), expression.left.span, "Строковый оператор")
                requireType(right, setOf(FilterValueType.STRING), expression.right.span, "Строковый оператор")
            }
            FilterBinaryOperator.MATCHES -> {
                requireType(left, setOf(FilterValueType.STRING), expression.left.span, "matches")
                requireType(
                    right,
                    setOf(FilterValueType.STRING, FilterValueType.REGEX),
                    expression.right.span,
                    "matches",
                )
                val stringPattern = (expression.right as? FilterExpression.Literal)?.value
                    as? FilterLiteralValue.StringValue
                if (stringPattern != null && runCatching { Regex(stringPattern.value) }.isFailure) {
                    diagnostics += FilterDiagnostic(
                        FilterDiagnosticSeverity.ERROR,
                        "Некорректный regex в строке",
                        expression.right.span,
                    )
                }
            }
        }
        return FilterValueType.BOOLEAN
    }

    private fun compatibleEquality(left: FilterValueType, right: FilterValueType): Boolean {
        if (left == FilterValueType.UNKNOWN || right == FilterValueType.UNKNOWN) return true
        if (left == right) return true
        if (left == FilterValueType.STRING_LIST && right == FilterValueType.STRING) return true
        if (left == FilterValueType.STRING && right == FilterValueType.STRING_LIST) return true
        return false
    }

    private fun requireType(
        actual: FilterValueType,
        allowed: Set<FilterValueType>,
        span: FilterSpan,
        context: String,
    ) {
        if (actual == FilterValueType.UNKNOWN || actual in allowed) return
        diagnostics += FilterDiagnostic(
            FilterDiagnosticSeverity.ERROR,
            "$context ожидает ${allowed.joinToString(" или ") { it.displayName() }}, получено ${actual.displayName()}",
            span,
        )
    }

    private fun mismatch(
        expression: FilterExpression.Binary,
        left: FilterValueType,
        right: FilterValueType,
    ) = FilterDiagnostic(
        FilterDiagnosticSeverity.ERROR,
        "Несовместимые типы: ${left.displayName()} и ${right.displayName()}",
        expression.span,
    )
}

private fun FilterValueType.displayName(): String = when (this) {
    FilterValueType.STRING -> "String"
    FilterValueType.NUMBER -> "Number"
    FilterValueType.BOOLEAN -> "Boolean"
    FilterValueType.STRING_LIST -> "List<String>"
    FilterValueType.REGEX -> "Regex"
    FilterValueType.NULL -> "Null"
    FilterValueType.UNKNOWN -> "Unknown"
}

private fun FilterSpan.merge(other: FilterSpan): FilterSpan = FilterSpan(
    minOf(start, other.start),
    maxOf(endExclusive, other.endExclusive),
)
