package io.ferventio.app.domain

import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.UUID

@Immutable
enum class HighlightRuleType {
    USERNAME,
    WORD,
    REGEX,
    USER,
    MODERATOR,
    VIP,
    SUBSCRIBER,
    REPLY,
    REWARD,
    BITS,
}

@Immutable
data class HighlightRule(
    val id: String = UUID.randomUUID().toString(),
    val type: HighlightRuleType,
    val pattern: String = "",
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val colorArgb: Long = DEFAULT_HIGHLIGHT_COLOR_ARGB,
    val playSound: Boolean = false,
    val push: Boolean = false,
    val addToMentions: Boolean = true,
    val filteredSplit: Boolean = false,
)

@Immutable
enum class IgnoreRuleType {
    USER,
    WORD,
    REGEX,
    BOT_COMMAND,
    MESSAGE_TYPE,
}

@Immutable
enum class IgnoreDisplayMode {
    HIDE,
    COLLAPSE,
    TAP_TO_REVEAL,
}

@Immutable
data class IgnoreRule(
    val id: String = UUID.randomUUID().toString(),
    val type: IgnoreRuleType,
    val pattern: String = "",
    val enabled: Boolean = true,
    val caseSensitive: Boolean = false,
    val displayMode: IgnoreDisplayMode = IgnoreDisplayMode.HIDE,
)

@Immutable
data class MessageDecoration(
    val highlightColorArgb: Long? = null,
    val highlightReasons: List<String> = emptyList(),
    val playSound: Boolean = false,
    val push: Boolean = false,
    val addToMentions: Boolean = false,
    val filteredSplit: Boolean = false,
    val ignoreDisplayMode: IgnoreDisplayMode? = null,
    val ignoreReason: String? = null,
) {
    val isHighlighted: Boolean get() = highlightColorArgb != null
    val isIgnored: Boolean get() = ignoreDisplayMode != null
}

@Immutable
data class AttentionEntry(
    val messageId: String,
    val channelId: String,
    val channelLogin: String,
    val authorId: String,
    val authorLogin: String,
    val authorDisplayName: String,
    val text: String,
    val timestamp: String,
    val timestampMillis: Long,
    val isRead: Boolean,
    val isDirectMention: Boolean,
    val isHighlight: Boolean,
    val highlightReasons: List<String> = emptyList(),
    val highlightColorArgb: Long? = null,
) {
    fun asMessage(): ChatMessage = ChatMessage(
        id = messageId,
        channelId = channelId,
        channelLogin = channelLogin,
        author = ChatAuthor(
            id = authorId,
            login = authorLogin,
            displayName = authorDisplayName,
        ),
        text = text,
        fragments = listOf(ChatFragment.Text(text)),
        timestamp = timestamp,
        timestampMillis = timestampMillis,
    )
}

@Immutable
data class HighlightAlert(
    val message: ChatMessage,
    val reasons: List<String>,
    val playSound: Boolean,
    val push: Boolean,
)

class MessageRuleEvaluator private constructor(
    private val highlightRules: List<CompiledHighlightRule>,
    private val ignoreRules: List<CompiledIgnoreRule>,
    private val session: TwitchSession?,
    private val directMentionRegex: Regex?,
) {
    fun evaluate(message: ChatMessage): MessageDecoration {
        val ignored = ignoreRules.firstOrNull { it.matches(message) }
        if (ignored != null) {
            return MessageDecoration(
                ignoreDisplayMode = ignored.rule.displayMode,
                ignoreReason = ignored.description,
            )
        }

        if (highlightRules.isEmpty()) return MessageDecoration()
        var firstColor: Long? = null
        var playSound = false
        var push = false
        var addToMentions = false
        var filteredSplit = false
        val reasons = LinkedHashSet<String>()
        highlightRules.forEach { compiled ->
            if (!compiled.matches(message)) return@forEach
            if (firstColor == null) firstColor = compiled.rule.colorArgb
            reasons += compiled.description
            playSound = playSound || compiled.rule.playSound
            push = push || compiled.rule.push
            addToMentions = addToMentions || compiled.rule.addToMentions
            filteredSplit = filteredSplit || compiled.rule.filteredSplit
        }
        val color = firstColor ?: return MessageDecoration()
        return MessageDecoration(
            highlightColorArgb = color,
            highlightReasons = reasons.toList(),
            playSound = playSound,
            push = push,
            addToMentions = addToMentions,
            filteredSplit = filteredSplit,
        )
    }

    fun isDirectMention(message: ChatMessage): Boolean {
        val current = session ?: return false
        if (message.userId == current.userId) return false
        if (message.reply?.parentUserId == current.userId ||
            message.reply?.parentUserLogin.equals(current.login, ignoreCase = true)
        ) {
            return true
        }
        if (message.fragments.any { fragment ->
                fragment is ChatFragment.Mention && (
                    fragment.userId == current.userId ||
                        fragment.userLogin.equals(current.login, ignoreCase = true)
                    )
            }
        ) {
            return true
        }
        return directMentionRegex?.containsMatchIn(message.text) == true
    }

    companion object {
        fun compile(
            highlights: List<HighlightRule>,
            ignores: List<IgnoreRule>,
            session: TwitchSession?,
        ): MessageRuleEvaluator = MessageRuleEvaluator(
            highlightRules = highlights.asSequence()
                .filter(HighlightRule::enabled)
                .mapNotNull { CompiledHighlightRule.create(it, session) }
                .toList(),
            ignoreRules = ignores.asSequence()
                .filter(IgnoreRule::enabled)
                .mapNotNull(CompiledIgnoreRule::create)
                .toList(),
            session = session,
            directMentionRegex = session?.login
                ?.takeIf(String::isNotBlank)
                ?.let { usernameRegex(it, caseSensitive = false) },
        )
    }
}

private data class CompiledHighlightRule(
    val rule: HighlightRule,
    val description: String,
    val matcher: (ChatMessage) -> Boolean,
) {
    fun matches(message: ChatMessage): Boolean = matcher(message)

    companion object {
        fun create(rule: HighlightRule, session: TwitchSession?): CompiledHighlightRule? {
            val pattern = rule.pattern.trim()
            val matcher: (ChatMessage) -> Boolean = when (rule.type) {
                HighlightRuleType.USERNAME -> {
                    val name = pattern.ifBlank { session?.login.orEmpty() }
                    if (name.isBlank()) return null
                    val regex = usernameRegex(name, rule.caseSensitive)
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        message.text.isNotBlank() && regex.containsMatchIn(message.text)
                    }
                    matcherFn
                }

                HighlightRuleType.WORD -> {
                    if (pattern.isBlank()) return null
                    val regex = wordRegex(pattern, rule.caseSensitive)
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        regex.containsMatchIn(message.text)
                    }
                    matcherFn
                }

                HighlightRuleType.REGEX -> {
                    if (pattern.isBlank()) return null
                    val regex = compileRegex(pattern, rule.caseSensitive) ?: return null
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        regex.containsMatchIn(message.text)
                    }
                    matcherFn
                }

                HighlightRuleType.USER -> {
                    if (pattern.isBlank()) return null
                    { message ->
                        equalValue(message.userId, pattern, rule.caseSensitive) ||
                            equalValue(message.userLogin, pattern.removePrefix("@"), rule.caseSensitive) ||
                            equalValue(message.userDisplayName, pattern.removePrefix("@"), rule.caseSensitive)
                    }
                }

                HighlightRuleType.MODERATOR -> { message ->
                    message.badges.any { badge ->
                        badge.setId.lowercase(Locale.ROOT) in MODERATOR_BADGES
                    }
                }

                HighlightRuleType.VIP -> { message ->
                    message.badges.any { it.setId.equals("vip", ignoreCase = true) }
                }

                HighlightRuleType.SUBSCRIBER -> { message ->
                    message.badges.any {
                        it.setId.equals("subscriber", ignoreCase = true) ||
                            it.setId.equals("founder", ignoreCase = true)
                    }
                }

                HighlightRuleType.REPLY -> { message ->
                    val current = session
                    current != null && message.userId != current.userId && (
                        message.reply?.parentUserId == current.userId ||
                            message.reply?.parentUserLogin.equals(current.login, ignoreCase = true)
                        )
                }

                HighlightRuleType.REWARD -> { message -> message.type == ChatMessageType.REWARD }
                HighlightRuleType.BITS -> { message ->
                    message.type == ChatMessageType.CHEER ||
                        message.fragments.any { it is ChatFragment.Cheermote }
                }
            }
            return CompiledHighlightRule(
                rule = rule,
                description = ruleDescription(rule, pattern),
                matcher = matcher,
            )
        }
    }
}

private data class CompiledIgnoreRule(
    val rule: IgnoreRule,
    val description: String,
    val matcher: (ChatMessage) -> Boolean,
) {
    fun matches(message: ChatMessage): Boolean = matcher(message)

    companion object {
        fun create(rule: IgnoreRule): CompiledIgnoreRule? {
            val pattern = rule.pattern.trim()
            val matcher: (ChatMessage) -> Boolean = when (rule.type) {
                IgnoreRuleType.USER -> {
                    if (pattern.isBlank()) return null
                    { message ->
                        equalValue(message.userId, pattern, rule.caseSensitive) ||
                            equalValue(message.userLogin, pattern.removePrefix("@"), rule.caseSensitive) ||
                            equalValue(message.userDisplayName, pattern.removePrefix("@"), rule.caseSensitive)
                    }
                }

                IgnoreRuleType.WORD -> {
                    if (pattern.isBlank()) return null
                    val regex = wordRegex(pattern, rule.caseSensitive)
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        regex.containsMatchIn(message.text)
                    }
                    matcherFn
                }

                IgnoreRuleType.REGEX -> {
                    if (pattern.isBlank()) return null
                    val regex = compileRegex(pattern, rule.caseSensitive) ?: return null
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        regex.containsMatchIn(message.text)
                    }
                    matcherFn
                }

                IgnoreRuleType.BOT_COMMAND -> {
                    val command = pattern.ifBlank { "!" }
                    val matcherFn: (ChatMessage) -> Boolean = { message ->
                        val text = message.text.trimStart()
                        if (command == "!") {
                            text.startsWith("!") && text.length > 1 && !text[1].isWhitespace()
                        } else {
                            startsWithValue(text, command, rule.caseSensitive) &&
                                (text.length == command.length || text.getOrNull(command.length)?.isWhitespace() == true)
                        }
                    }
                    matcherFn
                }

                IgnoreRuleType.MESSAGE_TYPE -> {
                    val type = runCatching { ChatMessageType.valueOf(pattern.uppercase(Locale.ROOT)) }.getOrNull()
                        ?: return null
                    val matcherFn: (ChatMessage) -> Boolean = { message -> message.type == type }
                    matcherFn
                }
            }
            return CompiledIgnoreRule(
                rule = rule,
                description = ignoreDescription(rule, pattern),
                matcher = matcher,
            )
        }
    }
}

private fun ruleDescription(rule: HighlightRule, pattern: String): String = when (rule.type) {
    HighlightRuleType.USERNAME -> if (pattern.isBlank()) "Имя аккаунта" else "Имя: $pattern"
    HighlightRuleType.WORD -> "Слово: $pattern"
    HighlightRuleType.REGEX -> "Regex: $pattern"
    HighlightRuleType.USER -> "Пользователь: ${pattern.removePrefix("@")}" 
    HighlightRuleType.MODERATOR -> "Moderator"
    HighlightRuleType.VIP -> "VIP"
    HighlightRuleType.SUBSCRIBER -> "Subscriber"
    HighlightRuleType.REPLY -> "Reply"
    HighlightRuleType.REWARD -> "Reward"
    HighlightRuleType.BITS -> "Bits"
}

private fun ignoreDescription(rule: IgnoreRule, pattern: String): String = when (rule.type) {
    IgnoreRuleType.USER -> "Пользователь: ${pattern.removePrefix("@")}" 
    IgnoreRuleType.WORD -> "Слово: $pattern"
    IgnoreRuleType.REGEX -> "Regex: $pattern"
    IgnoreRuleType.BOT_COMMAND -> "Команда бота: ${pattern.ifBlank { "любая !команда" }}"
    IgnoreRuleType.MESSAGE_TYPE -> "Тип: ${pattern.uppercase(Locale.ROOT)}"
}

private fun usernameRegex(value: String, caseSensitive: Boolean): Regex {
    val escaped = Regex.escape(value.removePrefix("@"))
    val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return Regex("(?<![\\p{L}\\p{N}_])@?$escaped(?![\\p{L}\\p{N}_])", options)
}

private fun wordRegex(value: String, caseSensitive: Boolean): Regex {
    val escaped = Regex.escape(value)
    val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return Regex("(?<![\\p{L}\\p{N}_])$escaped(?![\\p{L}\\p{N}_])", options)
}

private fun compileRegex(value: String, caseSensitive: Boolean): Regex? = runCatching {
    Regex(
        value,
        if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE),
    )
}.getOrNull()

private fun equalValue(left: String, right: String, caseSensitive: Boolean): Boolean =
    if (caseSensitive) left == right else left.equals(right, ignoreCase = true)

private fun startsWithValue(left: String, right: String, caseSensitive: Boolean): Boolean =
    left.startsWith(right, ignoreCase = !caseSensitive)

const val HIGHLIGHTS_FILTER_QUERY = "@highlights"
const val DEFAULT_HIGHLIGHT_COLOR_ARGB: Long = 0xFFFFC857L

private val MODERATOR_BADGES = setOf("moderator", "staff", "admin", "global_mod", "broadcaster")
