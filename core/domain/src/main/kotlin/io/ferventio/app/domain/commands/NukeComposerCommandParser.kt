package io.ferventio.app.domain

sealed interface NukeComposerCommandParseResult {
    data object NotNuke : NukeComposerCommandParseResult
    data class Success(val config: NukePreviewConfig) : NukeComposerCommandParseResult
    data class Error(val message: String) : NukeComposerCommandParseResult
}

/**
 * Recognizes only Ferventio's explicit /nuke command. Every other slash command
 * remains untouched so Twitch and bot commands keep their existing pass-through
 * behavior in the composer.
 */
object NukeComposerCommandParser {
    fun parse(input: String): NukeComposerCommandParseResult {
        val trimmed = input.trim()
        if (!trimmed.startsWith('/') || trimmed.length == 1) {
            return NukeComposerCommandParseResult.NotNuke
        }

        val rawName = trimmed
            .substring(1)
            .takeWhile { !it.isWhitespace() }
        if (!rawName.equals("nuke", ignoreCase = true)) {
            return NukeComposerCommandParseResult.NotNuke
        }

        val query = trimmed
            .drop(1 + rawName.length)
            .trim()
        if (query.isEmpty()) {
            return NukeComposerCommandParseResult.Error("Nuke query must not be empty")
        }

        return NukeComposerCommandParseResult.Success(
            NukePreviewConfig(query = query),
        )
    }
}
