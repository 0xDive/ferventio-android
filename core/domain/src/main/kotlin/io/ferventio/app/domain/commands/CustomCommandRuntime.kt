package io.ferventio.app.domain

/**
 * Runtime-only context for custom commands. Persisted command definitions stay
 * intentionally small; volatile chat/user/clipboard data is supplied only when
 * a command is expanded.
 */
data class CustomCommandRuntimeContext(
    val base: CustomCommandContext,
    val selectedUser: CustomCommandUser? = null,
    val reply: CustomCommandReply? = null,
    val clipboardText: String? = null,
)

data class CustomCommandUser(
    val id: String,
    val login: String,
    val displayName: String = login,
)

data class CustomCommandReply(
    val messageId: String,
    val user: CustomCommandUser,
    val text: String,
)

enum class CustomCommandRisk {
    SAFE_TEXT,
    CHAT_COMMAND,
    MODERATION,
    MASS_MODERATION,
}

data class CustomCommandExecutionPlan(
    val expandedText: String,
    val risk: CustomCommandRisk,
    val requiresPreview: Boolean,
    val requiresConfirmation: Boolean,
)

sealed interface CustomCommandPlanResult {
    data class Success(val plan: CustomCommandExecutionPlan) : CustomCommandPlanResult
    data class Error(val message: String) : CustomCommandPlanResult
}

/**
 * Expands volatile variables after the existing positional/channel/stream
 * expansion. This keeps the v1 command codec backwards-compatible.
 */
object CustomCommandRuntimeExpander {
    fun expand(
        command: CustomCommand,
        arguments: List<String>,
        context: CustomCommandRuntimeContext,
    ): CustomCommandExpansionResult {
        val baseExpanded = when (
            val result = CustomCommandExpander.expand(command, arguments, context.base)
        ) {
            is CustomCommandExpansionResult.Success -> result.value
            is CustomCommandExpansionResult.Error -> return result
        }

        var value = baseExpanded
        val selectedUser = context.selectedUser
        val reply = context.reply

        value = replaceRequired(value, "{user}", selectedUser?.login)
            ?: return missingVariable(command, "{user}")
        value = replaceRequired(value, "{user.id}", selectedUser?.id)
            ?: return missingVariable(command, "{user.id}")
        value = replaceRequired(value, "{user.name}", selectedUser?.displayName)
            ?: return missingVariable(command, "{user.name}")
        value = replaceRequired(value, "{reply.id}", reply?.messageId)
            ?: return missingVariable(command, "{reply.id}")
        value = replaceRequired(value, "{reply.user}", reply?.user?.login)
            ?: return missingVariable(command, "{reply.user}")
        value = replaceRequired(value, "{reply.user.id}", reply?.user?.id)
            ?: return missingVariable(command, "{reply.user.id}")
        value = replaceRequired(value, "{reply.user.name}", reply?.user?.displayName)
            ?: return missingVariable(command, "{reply.user.name}")
        value = replaceRequired(value, "{reply.text}", reply?.text)
            ?: return missingVariable(command, "{reply.text}")
        value = replaceRequired(value, "{clipboard}", context.clipboardText)
            ?: return missingVariable(command, "{clipboard}")

        return if (value.isBlank()) {
            CustomCommandExpansionResult.Error(
                "Custom command /${command.normalizedName} expanded to an empty value",
            )
        } else {
            CustomCommandExpansionResult.Success(value)
        }
    }

    private fun replaceRequired(value: String, token: String, replacement: String?): String? {
        if (token !in value) return value
        return replacement?.let { value.replace(token, it) }
    }

    private fun missingVariable(command: CustomCommand, variable: String): CustomCommandExpansionResult.Error =
        CustomCommandExpansionResult.Error(
            "Custom command /${command.normalizedName} requires $variable context",
        )
}

/**
 * Conservative command-risk classifier. Anything that can alter another user,
 * a room, or many messages requires an explicit confirmation surface. Mass
 * moderation additionally requires a preview before execution.
 */
object CustomCommandSafety {
    fun classify(expandedText: String): CustomCommandRisk {
        val trimmed = expandedText.trimStart()
        if (!trimmed.startsWith('/')) return CustomCommandRisk.SAFE_TEXT

        val command = trimmed
            .substringAfter('/')
            .substringBefore(' ')
            .lowercase()

        return when (command) {
            "nuke", "purge", "massban", "masstimeout" -> CustomCommandRisk.MASS_MODERATION
            "ban",
            "unban",
            "timeout",
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
            "blockterm",
            "unblockterm",
            "pin",
            "unpin",
            "endpoll",
            "cancelpoll",
            "lockprediction",
            "cancelprediction",
            "completeprediction" -> CustomCommandRisk.MODERATION
            else -> CustomCommandRisk.CHAT_COMMAND
        }
    }

    fun executionPlan(expandedText: String): CustomCommandExecutionPlan {
        val risk = classify(expandedText)
        return CustomCommandExecutionPlan(
            expandedText = expandedText,
            risk = risk,
            requiresPreview = risk == CustomCommandRisk.MASS_MODERATION,
            requiresConfirmation = risk == CustomCommandRisk.MODERATION ||
                risk == CustomCommandRisk.MASS_MODERATION,
        )
    }
}

object CustomCommandPlanner {
    fun plan(
        command: CustomCommand,
        arguments: List<String>,
        context: CustomCommandRuntimeContext,
    ): CustomCommandPlanResult = when (
        val expansion = CustomCommandRuntimeExpander.expand(command, arguments, context)
    ) {
        is CustomCommandExpansionResult.Success -> CustomCommandPlanResult.Success(
            CustomCommandSafety.executionPlan(expansion.value),
        )

        is CustomCommandExpansionResult.Error -> CustomCommandPlanResult.Error(expansion.message)
    }
}
