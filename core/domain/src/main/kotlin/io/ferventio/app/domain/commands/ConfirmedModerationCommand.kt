package io.ferventio.app.domain

sealed interface ConfirmedModerationCommand {
    data class Ban(val userLogin: String, val reason: String?) : ConfirmedModerationCommand
    data class Timeout(
        val userLogin: String,
        val durationSeconds: Int,
        val reason: String?,
    ) : ConfirmedModerationCommand
    data class Unban(val userLogin: String) : ConfirmedModerationCommand
    data class Delete(val messageId: String) : ConfirmedModerationCommand
    data object Clear : ConfirmedModerationCommand
    data class Slow(val seconds: Int) : ConfirmedModerationCommand
    data object SlowOff : ConfirmedModerationCommand
    data class Followers(val minutes: Int) : ConfirmedModerationCommand
    data object FollowersOff : ConfirmedModerationCommand
    data object Subscribers : ConfirmedModerationCommand
    data object SubscribersOff : ConfirmedModerationCommand
    data object EmoteOnly : ConfirmedModerationCommand
    data object EmoteOnlyOff : ConfirmedModerationCommand
}

sealed interface ConfirmedModerationCommandParseResult {
    data class Success(val command: ConfirmedModerationCommand) : ConfirmedModerationCommandParseResult
    data class Error(val message: String) : ConfirmedModerationCommandParseResult
    data object Unsupported : ConfirmedModerationCommandParseResult
}

object ConfirmedModerationCommandParser {
    fun parse(expandedText: String): ConfirmedModerationCommandParseResult {
        return when (val parsed = ChatCommandParser.parse(expandedText)) {
            is ChatInputParseResult.Error -> ConfirmedModerationCommandParseResult.Error(parsed.message)
            is ChatInputParseResult.Success -> when (val input = parsed.input) {
                is ParsedChatInput.Ban -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Ban(input.userLogin, input.reason),
                )
                is ParsedChatInput.Timeout -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Timeout(
                        userLogin = input.userLogin,
                        durationSeconds = input.durationSeconds,
                        reason = input.reason,
                    ),
                )
                is ParsedChatInput.Unban -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Unban(input.userLogin),
                )
                is ParsedChatInput.Delete -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Delete(input.messageId),
                )
                ParsedChatInput.Clear -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.Clear)
                is ParsedChatInput.Slow -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Slow(input.seconds),
                )
                ParsedChatInput.SlowOff -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.SlowOff)
                is ParsedChatInput.Followers -> ConfirmedModerationCommandParseResult.Success(
                    ConfirmedModerationCommand.Followers(input.minutes),
                )
                ParsedChatInput.FollowersOff -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.FollowersOff)
                ParsedChatInput.Subscribers -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.Subscribers)
                ParsedChatInput.SubscribersOff -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.SubscribersOff)
                ParsedChatInput.EmoteOnly -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.EmoteOnly)
                ParsedChatInput.EmoteOnlyOff -> ConfirmedModerationCommandParseResult.Success(ConfirmedModerationCommand.EmoteOnlyOff)
                else -> ConfirmedModerationCommandParseResult.Unsupported
            }
        }
    }
}
