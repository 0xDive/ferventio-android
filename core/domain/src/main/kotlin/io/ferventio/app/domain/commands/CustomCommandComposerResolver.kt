package io.ferventio.app.domain

sealed interface CustomCommandComposerResolution {
    data class PassThrough(val text: String) : CustomCommandComposerResolution
    data class Planned(val plan: CustomCommandExecutionPlan) : CustomCommandComposerResolution
    data class Error(val message: String) : CustomCommandComposerResolution
}

/**
 * Resolves only enabled user-defined commands from composer input.
 *
 * Unknown slash commands deliberately pass through byte-for-byte so Twitch and
 * bot commands keep their existing behavior. Once an enabled custom command is
 * matched, expansion is recursive and the final execution plan retains the
 * conservative safety classification from [CustomCommandPlanner].
 */
object CustomCommandComposerResolver {
    private const val MAX_DEPTH = 8

    fun resolve(
        input: String,
        commands: List<CustomCommand>,
        context: CustomCommandRuntimeContext,
    ): CustomCommandComposerResolution {
        val enabledCommands = commands
            .asSequence()
            .filter(CustomCommand::enabled)
            .associateBy(CustomCommand::normalizedName)

        val firstName = invocationName(input)
            ?: return CustomCommandComposerResolution.PassThrough(input)
        if (firstName !in enabledCommands) {
            return CustomCommandComposerResolution.PassThrough(input)
        }

        return resolveCustom(
            input = input,
            enabledCommands = enabledCommands,
            context = context,
            stack = emptySet(),
            depth = 0,
        )
    }

    private fun resolveCustom(
        input: String,
        enabledCommands: Map<String, CustomCommand>,
        context: CustomCommandRuntimeContext,
        stack: Set<String>,
        depth: Int,
    ): CustomCommandComposerResolution {
        val name = invocationName(input)
            ?: return CustomCommandComposerResolution.Planned(
                CustomCommandSafety.executionPlan(input),
            )
        val command = enabledCommands[name]
            ?: return CustomCommandComposerResolution.Planned(
                CustomCommandSafety.executionPlan(input),
            )

        if (name in stack) {
            val cycle = (stack + name).joinToString(" -> ") { "/$it" }
            return CustomCommandComposerResolution.Error(
                "Custom command cycle detected: $cycle",
            )
        }
        if (depth >= MAX_DEPTH) {
            return CustomCommandComposerResolution.Error(
                "Custom command expansion exceeded the maximum depth of $MAX_DEPTH",
            )
        }

        val tokens = when (val tokenized = CommandTokenizer.tokenize(input.trim())) {
            is CommandTokenizationResult.Success -> tokenized.tokens
            is CommandTokenizationResult.Error -> {
                return CustomCommandComposerResolution.Error(tokenized.message)
            }
        }
        val arguments = tokens.drop(1)
        val plan = when (val planned = CustomCommandPlanner.plan(command, arguments, context)) {
            is CustomCommandPlanResult.Success -> planned.plan
            is CustomCommandPlanResult.Error -> {
                return CustomCommandComposerResolution.Error(planned.message)
            }
        }

        val nestedName = invocationName(plan.expandedText)
        return if (nestedName != null && nestedName in enabledCommands) {
            resolveCustom(
                input = plan.expandedText,
                enabledCommands = enabledCommands,
                context = context,
                stack = stack + name,
                depth = depth + 1,
            )
        } else {
            CustomCommandComposerResolution.Planned(plan)
        }
    }

    private fun invocationName(input: String): String? {
        val trimmed = input.trimStart()
        if (!trimmed.startsWith('/') || trimmed.length == 1) return null
        val rawName = trimmed
            .substring(1)
            .takeWhile { !it.isWhitespace() }
        if (rawName.isBlank()) return null
        return CommandRegistry.normalizeName(rawName)
    }
}
