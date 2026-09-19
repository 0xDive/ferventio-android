package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatCommandParser
import io.ferventio.app.domain.ChatInputParseResult
import io.ferventio.app.domain.ConfirmedModerationCommand
import io.ferventio.app.domain.ConfirmedModerationCommandParseResult
import io.ferventio.app.domain.ConfirmedModerationCommandParser
import io.ferventio.app.domain.NukeComposerCommandParseResult
import io.ferventio.app.domain.NukeComposerCommandParser
import io.ferventio.app.domain.NukePreviewConfig
import io.ferventio.app.domain.ParsedChatInput

internal sealed interface SharedComposerSubmission {
    data class Send(val text: String) : SharedComposerSubmission
    data class UserCard(val login: String) : SharedComposerSubmission
    data class Moderation(val command: ConfirmedModerationCommand) : SharedComposerSubmission
    data class Nuke(val config: NukePreviewConfig) : SharedComposerSubmission
    data class Error(val message: String) : SharedComposerSubmission
}

internal fun routeSharedComposerSubmission(rawInput: String): SharedComposerSubmission {
    val input = rawInput.trim()
    if (input.isEmpty()) return SharedComposerSubmission.Error("Сообщение пустое")
    if (!input.startsWith('/')) return SharedComposerSubmission.Send(input)

    val commandName = input
        .removePrefix("/")
        .substringBefore(' ')
        .trim()
        .lowercase()
    if (commandName !in SHARED_INTERCEPTED_COMMANDS) {
        // Preserve arbitrary bot commands and server-side command syntaxes exactly like Android.
        return SharedComposerSubmission.Send(input)
    }

    if (commandName in USER_CARD_COMMANDS) {
        return when (val parsed = ChatCommandParser.parse(input)) {
            is ChatInputParseResult.Error -> SharedComposerSubmission.Error(parsed.message)
            is ChatInputParseResult.Success -> when (val value = parsed.input) {
                is ParsedChatInput.UserCard -> SharedComposerSubmission.UserCard(value.login)
                else -> SharedComposerSubmission.Error("Unsupported user-card command")
            }
        }
    }

    if (commandName == "nuke") {
        return when (val parsed = NukeComposerCommandParser.parse(input)) {
            NukeComposerCommandParseResult.NotNuke ->
                SharedComposerSubmission.Send(input)
            is NukeComposerCommandParseResult.Error ->
                SharedComposerSubmission.Error(parsed.message)
            is NukeComposerCommandParseResult.Success ->
                SharedComposerSubmission.Nuke(parsed.config)
        }
    }

    if (commandName == "me") {
        return when (val parsed = ChatCommandParser.parse(input)) {
            is ChatInputParseResult.Error -> SharedComposerSubmission.Error(parsed.message)
            is ChatInputParseResult.Success -> when (val value = parsed.input) {
                is ParsedChatInput.Action -> SharedComposerSubmission.Send("/me " + value.text)
                else -> SharedComposerSubmission.Error("Unsupported action command")
            }
        }
    }

    return when (val parsed = ConfirmedModerationCommandParser.parse(input)) {
        is ConfirmedModerationCommandParseResult.Success ->
            SharedComposerSubmission.Moderation(parsed.command)
        is ConfirmedModerationCommandParseResult.Error ->
            SharedComposerSubmission.Error(parsed.message)
        ConfirmedModerationCommandParseResult.Unsupported ->
            SharedComposerSubmission.Send(input)
    }
}

private val USER_CARD_COMMANDS = setOf("user", "usercard")
private val SHARED_INTERCEPTED_COMMANDS = USER_CARD_COMMANDS + setOf(
    "me",
    "nuke",
    "ban",
    "timeout",
    "unban",
    "untimeout",
    "delete",
    "clear",
    "slow",
    "slowoff",
    "followers",
    "followersoff",
    "subscribers",
    "subscribersoff",
    "emoteonly",
    "emoteonlyoff",
)
