package io.ferventio.app.domain

sealed interface ComposerSuggestion {
    val key: String
    val replacement: String

    data class Emote(
        val asset: ThirdPartyEmoteAsset,
    ) : ComposerSuggestion {
        override val key: String = "emote:${asset.usageKey}"
        override val replacement: String = asset.code
    }

    data class User(
        val userId: String,
        val login: String,
        val displayName: String,
        val avatarUrl: String?,
        val useCount: Int,
    ) : ComposerSuggestion {
        override val key: String = "user:$userId:$login"
        override val replacement: String = "@$login"
    }
}

object ComposerAutocomplete {
    fun suggestions(
        input: String,
        messages: List<ChatMessage>,
        profilesById: Map<String, TwitchUser>,
        catalog: List<ThirdPartyEmoteAsset>,
        recentEmoteKeys: List<String>,
        favoriteEmoteKeys: Set<String>,
        currentUserId: String?,
        userIndex: List<ComposerSuggestion.User>? = null,
        limit: Int = 8,
    ): List<ComposerSuggestion> {
        val token = currentToken(input)
        if (token.isBlank()) return emptyList()
        return when {
            token.startsWith("@") -> userSuggestions(
                query = token.removePrefix("@"),
                candidates = userIndex ?: buildUserIndex(
                    messages = messages,
                    profilesById = profilesById,
                    currentUserId = currentUserId,
                ),
                limit = limit,
            )

            token.startsWith("/") -> emptyList()

            token.length >= 2 -> EmoteCatalogRanking.suggestions(
                input = input,
                catalog = catalog,
                recentEmoteKeys = recentEmoteKeys,
                favoriteEmoteKeys = favoriteEmoteKeys,
                limit = limit,
            ).map(ComposerSuggestion::Emote)

            else -> emptyList()
        }
    }

    fun applySuggestion(input: String, suggestion: ComposerSuggestion): String {
        val start = input.indexOfCurrentToken()
        val prefix = input.substring(0, start)
        return prefix + suggestion.replacement + " "
    }

    fun currentToken(input: String): String = input.substring(input.indexOfCurrentToken())

    private fun String.indexOfCurrentToken(): Int {
        var index = length
        while (index > 0 && !this[index - 1].isWhitespace()) index--
        return index
    }

    fun buildUserIndex(
        messages: List<ChatMessage>,
        profilesById: Map<String, TwitchUser>,
        currentUserId: String?,
        maxMessages: Int = 400,
    ): List<ComposerSuggestion.User> {
        if (messages.isEmpty()) return emptyList()
        val recentMessages = if (messages.size > maxMessages) messages.takeLast(maxMessages) else messages
        val counts = recentMessages.groupingBy { it.userId to it.userLogin.lowercase() }.eachCount()
        return recentMessages.asReversed().asSequence()
            .filter { it.userId != currentUserId }
            .distinctBy { it.userId.ifBlank { it.userLogin.lowercase() } }
            .map { message ->
                val profile = profilesById[message.userId]
                ComposerSuggestion.User(
                    userId = message.userId,
                    login = profile?.login?.takeIf(String::isNotBlank) ?: message.userLogin,
                    displayName = profile?.displayName?.takeIf(String::isNotBlank) ?: message.userDisplayName,
                    avatarUrl = profile?.profileImageUrl ?: message.author.profileImageUrl,
                    useCount = counts[message.userId to message.userLogin.lowercase()] ?: 0,
                )
            }
            .toList()
    }

    private fun userSuggestions(
        query: String,
        candidates: List<ComposerSuggestion.User>,
        limit: Int,
    ): List<ComposerSuggestion.User> {
        val normalized = query.trim().lowercase()
        return candidates.asSequence()
            .filter { suggestion ->
                normalized.isEmpty() ||
                    suggestion.login.contains(normalized, ignoreCase = true) ||
                    suggestion.displayName.contains(normalized, ignoreCase = true)
            }
            .sortedWith(
                compareBy<ComposerSuggestion.User> {
                    when {
                        normalized.isEmpty() -> 1
                        it.login.equals(normalized, ignoreCase = true) -> 0
                        it.login.startsWith(normalized, ignoreCase = true) -> 1
                        it.displayName.startsWith(normalized, ignoreCase = true) -> 2
                        else -> 3
                    }
                }.thenByDescending { it.useCount }
                    .thenBy { it.login.lowercase() },
            )
            .take(limit)
            .toList()
    }
}
