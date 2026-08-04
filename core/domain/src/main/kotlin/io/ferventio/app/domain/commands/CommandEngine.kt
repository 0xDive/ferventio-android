package io.ferventio.app.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface CommandTokenizationResult {
    data class Success(val tokens: List<String>) : CommandTokenizationResult
    data class Error(val message: String) : CommandTokenizationResult
}

object CommandTokenizer {
    fun tokenize(input: String): CommandTokenizationResult {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        input.forEachIndexed { index, char ->
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                    tokenStarted = true
                }

                char == '\\' -> {
                    escaping = true
                    tokenStarted = true
                }

                quote != null && char == quote -> {
                    quote = null
                    tokenStarted = true
                }

                quote == null && (char == '"' || char == '\'') -> {
                    quote = char
                    tokenStarted = true
                }

                quote == null && char.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += current.toString()
                        current.clear()
                        tokenStarted = false
                    }
                }

                else -> {
                    current.append(char)
                    tokenStarted = true
                }
            }

            if (index == input.lastIndex) {
                if (escaping) return CommandTokenizationResult.Error("После \\ ожидается символ")
                if (quote != null) return CommandTokenizationResult.Error("Не закрыта кавычка $quote")
            }
        }

        if (escaping) return CommandTokenizationResult.Error("После \\ ожидается символ")
        if (quote != null) return CommandTokenizationResult.Error("Не закрыта кавычка $quote")
        if (tokenStarted) tokens += current.toString()
        return CommandTokenizationResult.Success(tokens)
    }
}

data class CommandDefinition(
    val name: String,
    val usage: String,
    val description: String,
    val aliases: Set<String> = emptySet(),
)

object CommandRegistry {
    val builtIns: List<CommandDefinition> = listOf(
        CommandDefinition("me", "/me текст", "Отправить action-сообщение"),
        CommandDefinition("help", "/help", "Показать список команд", setOf("commands")),
        CommandDefinition("ban", "/ban user [причина]", "Заблокировать пользователя"),
        CommandDefinition("unban", "/unban user", "Снять ban или timeout", setOf("untimeout")),
        CommandDefinition("timeout", "/timeout user [10s|5m|2h|1d] [причина]", "Выдать timeout"),
        CommandDefinition("delete", "/delete message-id", "Удалить сообщение Twitch"),
        CommandDefinition("clear", "/clear", "Очистить чат Twitch для всех"),
        CommandDefinition("slow", "/slow [3-120]", "Включить slow mode"),
        CommandDefinition("slowoff", "/slowoff", "Отключить slow mode"),
        CommandDefinition("followers", "/followers [0|10m|1h|1d|1w|1mo]", "Включить followers-only"),
        CommandDefinition("followersoff", "/followersoff", "Отключить followers-only"),
        CommandDefinition("subscribers", "/subscribers", "Включить subscribers-only"),
        CommandDefinition("subscribersoff", "/subscribersoff", "Отключить subscribers-only"),
        CommandDefinition("emoteonly", "/emoteonly", "Включить emote-only"),
        CommandDefinition("emoteonlyoff", "/emoteonlyoff", "Отключить emote-only"),
        CommandDefinition("user", "/user login", "Открыть карточку пользователя", setOf("usercard")),
        CommandDefinition("clip", "/clip [название]", "Создать Twitch Clip"),
        CommandDefinition("marker", "/marker [описание]", "Создать маркер трансляции"),
        CommandDefinition("settitle", "/settitle название", "Изменить название трансляции"),
        CommandDefinition("setgame", "/setgame категория", "Изменить категорию трансляции"),
        CommandDefinition("uptime", "/uptime", "Показать длительность трансляции"),
        CommandDefinition("chatters", "/chatters", "Показать пользователей в чате"),
        CommandDefinition("clearmessages", "/clearmessages", "Очистить локальную ленту текущего канала"),
        CommandDefinition("reconnect", "/reconnect", "Переподключить EventSub"),
    )

    val reservedNames: Set<String> = builtIns
        .flatMap { definition -> listOf(definition.name) + definition.aliases }
        .toSet()

    fun definition(name: String): CommandDefinition? {
        val normalized = normalizeName(name)
        return builtIns.firstOrNull { it.name == normalized || normalized in it.aliases }
    }

    fun normalizeName(name: String): String = name.trim().removePrefix("/").lowercase()
}

data class CustomCommand(
    val name: String,
    val template: String,
    val description: String = "",
    val enabled: Boolean = true,
) {
    val normalizedName: String get() = CommandRegistry.normalizeName(name)
}

data class CustomCommandContext(
    val channelName: String,
    val channelId: String,
    val myName: String,
    val myId: String,
    val streamTitle: String = "",
    val streamGame: String = "",
)

sealed interface CustomCommandExpansionResult {
    data class Success(val value: String) : CustomCommandExpansionResult
    data class Error(val message: String) : CustomCommandExpansionResult
}

object CustomCommandExpander {
    private val positional = Regex("\\{(\\d+)(\\+)?}")

    fun expand(
        command: CustomCommand,
        arguments: List<String>,
        context: CustomCommandContext,
    ): CustomCommandExpansionResult {
        var missingIndex: Int? = null
        var value = positional.replace(command.template) { match ->
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@replace match.value
            val remainder = match.groupValues[2] == "+"
            if (index !in arguments.indices) {
                missingIndex = index + 1
                ""
            } else if (remainder) {
                arguments.drop(index).joinToString(" ")
            } else {
                arguments[index]
            }
        }
        missingIndex?.let { return CustomCommandExpansionResult.Error("Для /${command.normalizedName} не указан аргумент {$it}") }

        value = value
            .replace("{channel.name}", context.channelName)
            .replace("{channel.id}", context.channelId)
            .replace("{my.name}", context.myName)
            .replace("{my.id}", context.myId)
            .replace("{stream.title}", context.streamTitle)
            .replace("{stream.game}", context.streamGame)
            .trim()

        return if (value.isBlank()) {
            CustomCommandExpansionResult.Error("Пользовательская команда /${command.normalizedName} раскрылась в пустую строку")
        } else {
            CustomCommandExpansionResult.Success(value)
        }
    }
}

object CustomCommandCodec {
    private const val SCHEMA_VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(commands: List<CustomCommand>): String = JsonObject(
        mapOf(
            "schemaVersion" to JsonPrimitive(SCHEMA_VERSION),
            "commands" to JsonArray(
                commands
                    .sortedBy(CustomCommand::normalizedName)
                    .map { command ->
                        JsonObject(
                            mapOf(
                                "name" to JsonPrimitive(command.normalizedName),
                                "template" to JsonPrimitive(command.template),
                                "description" to JsonPrimitive(command.description),
                                "enabled" to JsonPrimitive(command.enabled),
                            ),
                        )
                    },
            ),
        ),
    ).toString()

    fun decode(raw: String?): Result<List<CustomCommand>> = runCatching {
        if (raw.isNullOrBlank()) return@runCatching emptyList()
        val root = json.parseToJsonElement(raw).jsonObject
        val version = root["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        require(version in 1..SCHEMA_VERSION) { "Неподдерживаемая версия файла команд: $version" }
        root["commands"]?.jsonArray.orEmpty().map { element ->
            val item = element.jsonObject
            val command = CustomCommand(
                name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                template = item["template"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                description = item["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                enabled = item["enabled"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true,
            )
            validate(command).getOrThrow()
        }.distinctBy(CustomCommand::normalizedName)
    }

    fun validate(command: CustomCommand, oldName: String? = null): Result<CustomCommand> = runCatching {
        val name = command.normalizedName
        require(NAME_PATTERN.matches(name)) {
            "Имя команды: 1–32 символа, латиница, цифры, _ или -"
        }
        require(name !in CommandRegistry.reservedNames || name == CommandRegistry.normalizeName(oldName.orEmpty())) {
            "/$name — встроенная команда"
        }
        val template = command.template.trim()
        require(template.isNotEmpty()) { "Шаблон команды пустой" }
        require(template.length <= 500) { "Шаблон команды длиннее 500 символов" }
        require(command.description.length <= 160) { "Описание длиннее 160 символов" }
        command.copy(name = name, template = template, description = command.description.trim())
    }

    private val NAME_PATTERN = Regex("^[a-z0-9_][a-z0-9_-]{0,31}$")
}
