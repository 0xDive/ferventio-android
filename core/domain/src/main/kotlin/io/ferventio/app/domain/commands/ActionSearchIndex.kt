package io.ferventio.app.domain

enum class SearchableActionKind {
    NAVIGATION,
    SETTING,
    COMMAND,
    MODERATION,
    CHANNEL,
    USER,
}

data class SearchableAction(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val keywords: Set<String> = emptySet(),
    val kind: SearchableActionKind,
    val requiresConfirmation: Boolean = false,
    val requiresPreview: Boolean = false,
)

data class SearchableActionMatch(
    val action: SearchableAction,
    val score: Int,
)

/**
 * Small deterministic search index shared by the future Android action sheet.
 * The UI supplies actions from settings, navigation, commands and moderation;
 * this object only ranks them and stays independent from Compose.
 */
object ActionSearchIndex {
    fun search(
        query: String,
        actions: List<SearchableAction>,
        limit: Int = 20,
    ): List<SearchableActionMatch> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) {
            return actions
                .take(limit.coerceAtLeast(0))
                .map { SearchableActionMatch(it, score = 0) }
        }

        val queryTokens = normalizedQuery.split(' ').filter(String::isNotBlank)
        return actions
            .asSequence()
            .mapNotNull { action -> score(action, normalizedQuery, queryTokens) }
            .sortedWith(
                compareByDescending<SearchableActionMatch>(SearchableActionMatch::score)
                    .thenBy { match -> match.action.title.lowercase() },
            )
            .take(limit.coerceAtLeast(0))
            .toList()
    }

    private fun score(
        action: SearchableAction,
        query: String,
        queryTokens: List<String>,
    ): SearchableActionMatch? {
        val title = normalize(action.title)
        val subtitle = normalize(action.subtitle)
        val id = normalize(action.id)
        val keywords = action.keywords.map(::normalize)
        val searchable = buildList {
            add(title)
            add(subtitle)
            add(id)
            addAll(keywords)
        }

        if (queryTokens.any { token -> searchable.none { candidate -> token in candidate } }) return null

        var score = 0
        when {
            title == query -> score += 1_000
            title.startsWith(query) -> score += 700
            query in title -> score += 500
        }
        when {
            id == query -> score += 500
            id.startsWith(query) -> score += 300
        }
        if (subtitle.startsWith(query)) score += 160
        if (keywords.any { it == query }) score += 220
        if (keywords.any { it.startsWith(query) }) score += 120

        queryTokens.forEach { token ->
            if (title.split(' ').any { it.startsWith(token) }) score += 80
            if (keywords.any { keyword -> keyword.split(' ').any { it.startsWith(token) } }) score += 40
            if (subtitle.contains(token)) score += 20
        }

        return SearchableActionMatch(action, score)
    }

    private fun normalize(value: String): String = value
        .trim()
        .lowercase()
        .replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}

object SearchableActionFactory {
    fun fromCustomCommand(command: CustomCommand): SearchableAction {
        val risk = CustomCommandSafety.classify(command.template)
        return SearchableAction(
            id = "command:${command.normalizedName}",
            title = "/${command.normalizedName}",
            subtitle = command.description,
            keywords = setOf(command.normalizedName, command.template),
            kind = when (risk) {
                CustomCommandRisk.MODERATION,
                CustomCommandRisk.MASS_MODERATION -> SearchableActionKind.MODERATION
                CustomCommandRisk.SAFE_TEXT,
                CustomCommandRisk.CHAT_COMMAND -> SearchableActionKind.COMMAND
            },
            requiresConfirmation = risk == CustomCommandRisk.MODERATION ||
                risk == CustomCommandRisk.MASS_MODERATION,
            requiresPreview = risk == CustomCommandRisk.MASS_MODERATION,
        )
    }

    fun fromCommandDefinition(definition: CommandDefinition): SearchableAction = SearchableAction(
        id = "command:${definition.name}",
        title = "/${definition.name}",
        subtitle = definition.description,
        keywords = definition.aliases + definition.usage,
        kind = when (CustomCommandSafety.classify("/${definition.name}")) {
            CustomCommandRisk.MODERATION,
            CustomCommandRisk.MASS_MODERATION -> SearchableActionKind.MODERATION
            else -> SearchableActionKind.COMMAND
        },
    )
}
