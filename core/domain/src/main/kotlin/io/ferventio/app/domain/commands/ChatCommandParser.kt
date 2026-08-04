package io.ferventio.app.domain

sealed interface ParsedChatInput {
    data class Message(val text: String) : ParsedChatInput
    data class Action(val text: String) : ParsedChatInput
    data class Ban(val userLogin: String, val reason: String?) : ParsedChatInput
    data class Timeout(
        val userLogin: String,
        val durationSeconds: Int,
        val reason: String?,
    ) : ParsedChatInput
    data class Unban(val userLogin: String) : ParsedChatInput
    data class Delete(val messageId: String) : ParsedChatInput
    data object Clear : ParsedChatInput
    data class Slow(val seconds: Int) : ParsedChatInput
    data object SlowOff : ParsedChatInput
    data class Followers(val minutes: Int) : ParsedChatInput
    data object FollowersOff : ParsedChatInput
    data object Subscribers : ParsedChatInput
    data object SubscribersOff : ParsedChatInput
    data object EmoteOnly : ParsedChatInput
    data object EmoteOnlyOff : ParsedChatInput
    data class UserCard(val userLogin: String) : ParsedChatInput
    data class Clip(val title: String?) : ParsedChatInput
    data class Marker(val description: String?) : ParsedChatInput
    data class SetTitle(val title: String) : ParsedChatInput
    data class SetGame(val gameName: String) : ParsedChatInput
    data object Uptime : ParsedChatInput
    data object Chatters : ParsedChatInput
    data object ClearMessages : ParsedChatInput
    data object Reconnect : ParsedChatInput
    data object Help : ParsedChatInput
    data class Custom(val name: String, val arguments: List<String>) : ParsedChatInput
}

sealed interface ChatInputParseResult {
    data class Success(val input: ParsedChatInput) : ChatInputParseResult
    data class Error(val message: String) : ChatInputParseResult
}

object ChatCommandParser {
    fun parse(rawInput: String, customCommandNames: Set<String> = emptySet()): ChatInputParseResult {
        val input = rawInput.trim()
        if (input.isEmpty()) return ChatInputParseResult.Error("Сообщение пустое")
        if (!input.startsWith('/')) return ChatInputParseResult.Success(ParsedChatInput.Message(input))

        val tokens = when (val result = CommandTokenizer.tokenize(input)) {
            is CommandTokenizationResult.Success -> result.tokens
            is CommandTokenizationResult.Error -> return ChatInputParseResult.Error(result.message)
        }
        if (tokens.isEmpty()) return ChatInputParseResult.Error("Команда пустая")

        val command = CommandRegistry.normalizeName(tokens.first())
        val arguments = tokens.drop(1)
        fun noArguments(value: ParsedChatInput): ChatInputParseResult = if (arguments.isEmpty()) {
            ChatInputParseResult.Success(value)
        } else {
            ChatInputParseResult.Error("Использование: ${CommandRegistry.definition(command)?.usage ?: "/$command"}")
        }
        fun joined(): String = arguments.joinToString(" ")

        return when (command) {
            "me" -> requiredText(arguments, "После /me укажи текст") { ParsedChatInput.Action(it) }
            "ban" -> parseBan(arguments)
            "timeout" -> parseTimeout(arguments)
            "unban", "untimeout" -> singleLogin(arguments, "/unban username", ParsedChatInput::Unban)
            "delete" -> if (arguments.size == 1) {
                ChatInputParseResult.Success(ParsedChatInput.Delete(arguments.first()))
            } else ChatInputParseResult.Error("Использование: /delete message-id")
            "clear" -> noArguments(ParsedChatInput.Clear)
            "slow" -> parseSlow(arguments)
            "slowoff" -> noArguments(ParsedChatInput.SlowOff)
            "followers" -> parseFollowers(arguments)
            "followersoff" -> noArguments(ParsedChatInput.FollowersOff)
            "subscribers" -> noArguments(ParsedChatInput.Subscribers)
            "subscribersoff" -> noArguments(ParsedChatInput.SubscribersOff)
            "emoteonly" -> noArguments(ParsedChatInput.EmoteOnly)
            "emoteonlyoff" -> noArguments(ParsedChatInput.EmoteOnlyOff)
            "user", "usercard" -> singleLogin(arguments, "/$command username", ParsedChatInput::UserCard)
            "clip" -> ChatInputParseResult.Success(ParsedChatInput.Clip(joined().ifBlank { null }))
            "marker" -> ChatInputParseResult.Success(ParsedChatInput.Marker(joined().ifBlank { null }))
            "settitle" -> requiredText(arguments, "Использование: /settitle название") { ParsedChatInput.SetTitle(it) }
            "setgame" -> requiredText(arguments, "Использование: /setgame категория") { ParsedChatInput.SetGame(it) }
            "uptime" -> noArguments(ParsedChatInput.Uptime)
            "chatters" -> noArguments(ParsedChatInput.Chatters)
            "clearmessages" -> noArguments(ParsedChatInput.ClearMessages)
            "reconnect" -> noArguments(ParsedChatInput.Reconnect)
            "help", "commands" -> noArguments(ParsedChatInput.Help)
            in customCommandNames.map(CommandRegistry::normalizeName).toSet() ->
                ChatInputParseResult.Success(ParsedChatInput.Custom(command, arguments))
            else -> ChatInputParseResult.Error(
                "Неизвестная команда /$command. Введи /help, чтобы увидеть доступные команды.",
            )
        }
    }

    private fun parseBan(arguments: List<String>): ChatInputParseResult {
        if (arguments.isEmpty()) return ChatInputParseResult.Error("Использование: /ban username [причина]")
        val login = normalizeLogin(arguments.first())
        if (login.isBlank()) return ChatInputParseResult.Error("Не указан пользователь для ban")
        return ChatInputParseResult.Success(
            ParsedChatInput.Ban(login, arguments.drop(1).joinToString(" ").ifBlank { null }),
        )
    }

    private fun parseTimeout(arguments: List<String>): ChatInputParseResult {
        if (arguments.isEmpty()) {
            return ChatInputParseResult.Error("Использование: /timeout username [10s|5m|2h|1d] [причина]")
        }
        val login = normalizeLogin(arguments.first())
        if (login.isBlank()) return ChatInputParseResult.Error("Не указан пользователь для timeout")
        val possibleDuration = arguments.getOrNull(1)?.let(::parseDurationSeconds)
        val duration = possibleDuration ?: DEFAULT_TIMEOUT_SECONDS
        val reasonStart = if (possibleDuration != null) 2 else 1
        return ChatInputParseResult.Success(
            ParsedChatInput.Timeout(
                userLogin = login,
                durationSeconds = duration,
                reason = arguments.drop(reasonStart).joinToString(" ").ifBlank { null },
            ),
        )
    }

    private fun parseSlow(arguments: List<String>): ChatInputParseResult {
        if (arguments.size > 1) return ChatInputParseResult.Error("Использование: /slow [3-120]")
        val seconds = if (arguments.isEmpty()) {
            DEFAULT_SLOW_SECONDS
        } else {
            arguments.first().toIntOrNull()
                ?: return ChatInputParseResult.Error("Slow mode: укажи целое число от 3 до 120 секунд")
        }
        if (seconds !in 3..120) return ChatInputParseResult.Error("Slow mode: укажи от 3 до 120 секунд")
        return ChatInputParseResult.Success(ParsedChatInput.Slow(seconds))
    }

    private fun parseFollowers(arguments: List<String>): ChatInputParseResult {
        if (arguments.size > 1) {
            return ChatInputParseResult.Error("Использование: /followers [0|10m|1h|1d|1w|1mo]")
        }
        val minutes = if (arguments.isEmpty()) {
            0
        } else {
            parseFollowerMinutes(arguments.first()) ?: return ChatInputParseResult.Error(
                "Followers-only: длительность от 0 до 3 месяцев, например 10m, 1h, 1d, 1w",
            )
        }
        return ChatInputParseResult.Success(ParsedChatInput.Followers(minutes))
    }

    fun parseDurationSeconds(value: String): Int? {
        val match = DURATION_PATTERN.matchEntire(value.lowercase()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "", "s" -> 1L
            "m" -> 60L
            "h" -> 3_600L
            "d" -> 86_400L
            else -> return null
        }
        if (amount > MAX_TIMEOUT_SECONDS.toLong() / multiplier) return null
        val seconds = amount * multiplier
        return seconds.takeIf { it >= MIN_TIMEOUT_SECONDS }?.toInt()
    }

    internal fun parseFollowerMinutes(value: String): Int? {
        val match = FOLLOWER_PATTERN.matchEntire(value.lowercase()) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "", "m" -> 1L
            "h" -> 60L
            "d" -> 1_440L
            "w" -> 10_080L
            "mo" -> 43_200L
            else -> return null
        }
        if (amount > MAX_FOLLOWER_MINUTES / multiplier) return null
        return (amount * multiplier).toInt()
    }

    private fun requiredText(
        arguments: List<String>,
        error: String,
        transform: (String) -> ParsedChatInput,
    ): ChatInputParseResult {
        val value = arguments.joinToString(" ").trim()
        return if (value.isEmpty()) ChatInputParseResult.Error(error)
        else ChatInputParseResult.Success(transform(value))
    }

    private fun singleLogin(
        arguments: List<String>,
        usage: String,
        transform: (String) -> ParsedChatInput,
    ): ChatInputParseResult = if (arguments.size == 1 && normalizeLogin(arguments.first()).isNotBlank()) {
        ChatInputParseResult.Success(transform(normalizeLogin(arguments.first())))
    } else ChatInputParseResult.Error("Использование: $usage")

    private fun normalizeLogin(value: String): String = value.trim().removePrefix("@").lowercase()

    private const val DEFAULT_TIMEOUT_SECONDS = 10 * 60
    private const val DEFAULT_SLOW_SECONDS = 30
    private const val MIN_TIMEOUT_SECONDS = 1
    private const val MAX_TIMEOUT_SECONDS = 14 * 24 * 60 * 60
    private const val MAX_FOLLOWER_MINUTES = 129_600L
    private val DURATION_PATTERN = Regex("^(\\d+)([smhd]?)$")
    private val FOLLOWER_PATTERN = Regex("^(\\d+)(mo|[mhdw]?)$")
}
