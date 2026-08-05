package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.ChatReward
import io.ferventio.app.domain.MessageFlags
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.ReplyContext
import java.time.Instant

sealed interface TwitchIrcEvent {
    data class RoomResolved(
        val channelLogin: String,
        val roomId: String,
    ) : TwitchIrcEvent

    data class Chat(
        val event: ChatEvent,
    ) : TwitchIrcEvent

    data class Notice(
        val channelLogin: String?,
        val message: String,
    ) : TwitchIrcEvent
}

/**
 * Parser for Twitch's IRC-over-WebSocket payloads used by the read-only anonymous transport.
 * It deliberately stays independent of Android and networking so malformed IRC lines cannot
 * terminate the socket loop and the important tag/fragment rules are unit-testable.
 */
object TwitchIrcParser {
    fun parse(
        rawLine: String,
        channelIdForLogin: (String) -> String?,
    ): List<TwitchIrcEvent> {
        if (rawLine.length > MAX_IRC_LINE_CHARS) return emptyList()
        val line = parseLine(rawLine) ?: return emptyList()
        val channelLogin = line.params.firstOrNull()
            ?.removePrefix("#")
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
        val roomId = line.tags["room-id"]?.takeIf(String::isNotBlank)
        val events = mutableListOf<TwitchIrcEvent>()

        if (channelLogin != null && roomId != null) {
            events += TwitchIrcEvent.RoomResolved(channelLogin, roomId)
        }

        when (line.command) {
            "PRIVMSG" -> {
                val login = channelLogin ?: return events
                val channelId = roomId ?: channelIdForLogin(login) ?: return events
                val rawText = line.trailing.orEmpty()
                val actionPrefix = "\u0001ACTION "
                val isAction = rawText.startsWith(actionPrefix) && rawText.endsWith("\u0001")
                val text = if (isAction) {
                    rawText.removePrefix(actionPrefix).removeSuffix("\u0001")
                } else {
                    rawText
                }
                val actionCodePointOffset = if (isAction) actionPrefix.codePointCount(0, actionPrefix.length) else 0
                val userLogin = line.tags["login"]
                    ?.takeIf(String::isNotBlank)
                    ?: line.prefix?.substringBefore('!').orEmpty().ifBlank { "unknown" }
                val displayName = line.tags["display-name"]
                    ?.takeIf(String::isNotBlank)
                    ?: userLogin
                val sentAtMillis = line.receivedAtMillis() ?: System.currentTimeMillis()
                val wasDeletedBeforeSnapshot = line.tags["rm-deleted"] == "1"
                val messageId = line.tags["id"]
                    ?.takeIf(String::isNotBlank)
                    ?: "irc:$channelId:$sentAtMillis:${rawText.hashCode()}"
                val reply = line.tags["reply-parent-msg-id"]?.takeIf(String::isNotBlank)?.let { parentId ->
                    ReplyContext(
                        parentMessageId = parentId,
                        parentMessageBody = line.tags["reply-parent-msg-body"],
                        parentUserId = line.tags["reply-parent-user-id"],
                        parentUserLogin = line.tags["reply-parent-user-login"],
                        parentUserName = line.tags["reply-parent-display-name"],
                        threadMessageId = line.tags["reply-thread-parent-msg-id"],
                        threadUserId = line.tags["reply-thread-parent-user-id"],
                        threadUserLogin = line.tags["reply-thread-parent-user-login"],
                        threadUserName = line.tags["reply-thread-parent-display-name"],
                    )
                }
                val rewardId = line.tags["custom-reward-id"]?.takeIf(String::isNotBlank)
                val message = ChatMessage(
                    id = messageId,
                    channelId = channelId,
                    channelLogin = login,
                    author = ChatAuthor(
                        id = line.tags["user-id"]?.takeIf(String::isNotBlank) ?: "anonymous:$userLogin",
                        login = userLogin,
                        displayName = displayName,
                        color = line.tags["color"]?.takeIf(String::isNotBlank),
                        badges = parseBadges(line.tags["badges"]),
                    ),
                    text = text,
                    fragments = parseFragments(
                        text = text,
                        emoteTag = line.tags["emotes"],
                        codePointOffset = actionCodePointOffset,
                    ),
                    timestamp = Instant.ofEpochMilli(sentAtMillis).toString(),
                    timestampMillis = sentAtMillis,
                    reply = reply,
                    reward = rewardId?.let { ChatReward(id = it) },
                    type = when {
                        isAction -> ChatMessageType.ACTION
                        rewardId != null -> ChatMessageType.REWARD
                        else -> ChatMessageType.CHAT
                    },
                    flags = MessageFlags(
                        isDeleted = wasDeletedBeforeSnapshot,
                        isAction = isAction,
                        isFirstMessage = line.tags["first-msg"] == "1",
                        isReturningChatter = line.tags["returning-chatter"] == "1",
                    ),
                    moderation = if (wasDeletedBeforeSnapshot) {
                        ModerationState(
                            action = ModerationAction.DELETE,
                            atMillis = sentAtMillis,
                        )
                    } else {
                        ModerationState()
                    },
                    serverMessageId = messageId,
                )
                events += TwitchIrcEvent.Chat(ChatEvent.Message(message))
            }

            "CLEARMSG" -> {
                val login = channelLogin ?: return events
                val channelId = roomId ?: channelIdForLogin(login) ?: return events
                val targetMessageId = line.tags["target-msg-id"]?.takeIf(String::isNotBlank)
                    ?: return events
                events += TwitchIrcEvent.Chat(
                    ChatEvent.MessageDeleted(
                        channelId = channelId,
                        messageId = targetMessageId,
                        eventId = "irc:clearmsg:$targetMessageId",
                        createdAt = line.receivedAtMillis()
                            ?.let { Instant.ofEpochMilli(it).toString() },
                    ),
                )
            }

            "CLEARCHAT" -> {
                val login = channelLogin ?: return events
                val channelId = roomId ?: channelIdForLogin(login) ?: return events
                val targetLogin = line.trailing?.takeIf(String::isNotBlank)
                val targetUserId = line.tags["target-user-id"]?.takeIf(String::isNotBlank)
                if (targetLogin == null && targetUserId == null) {
                    events += TwitchIrcEvent.Chat(
                        ChatEvent.ChatCleared(
                            channelId = channelId,
                            eventId = line.receivedAtMillis()
                                ?.let { "irc:clearchat:$channelId:$it" },
                            createdAt = line.receivedAtMillis()
                                ?.let { Instant.ofEpochMilli(it).toString() },
                        ),
                    )
                } else {
                    events += TwitchIrcEvent.Chat(
                        ChatEvent.UserMessagesCleared(
                            channelId = channelId,
                            userId = targetUserId ?: "anonymous:${targetLogin.orEmpty().lowercase()}",
                            userLogin = targetLogin,
                            durationSeconds = line.tags["ban-duration"]?.toIntOrNull(),
                            isPermanent = line.tags["ban-duration"].isNullOrBlank(),
                            eventId = line.receivedAtMillis()
                                ?.let { "irc:clearchat:$channelId:${targetUserId.orEmpty()}:$it" },
                            createdAt = line.receivedAtMillis()
                                ?.let { Instant.ofEpochMilli(it).toString() },
                        ),
                    )
                }
            }

            "NOTICE" -> line.trailing?.takeIf(String::isNotBlank)?.let { message ->
                events += TwitchIrcEvent.Notice(channelLogin, message)
            }
        }

        return events
    }

    private fun ParsedIrcLine.receivedAtMillis(): Long? =
        tags["tmi-sent-ts"]?.toLongOrNull()
            ?: tags["rm-received-ts"]?.toLongOrNull()

    private fun parseBadges(raw: String?): List<ChatBadge> = raw
        ?.split(',')
        ?.mapNotNull { entry ->
            val separator = entry.indexOf('/')
            if (separator <= 0 || separator == entry.lastIndex) return@mapNotNull null
            ChatBadge(
                setId = entry.substring(0, separator),
                id = entry.substring(separator + 1),
            )
        }
        .orEmpty()

    private fun parseFragments(
        text: String,
        emoteTag: String?,
        codePointOffset: Int,
    ): List<ChatFragment> {
        if (text.isEmpty()) return listOf(ChatFragment.Text(""))
        val ranges = emoteTag
            ?.split('/')
            ?.flatMap { group ->
                val separator = group.indexOf(':')
                if (separator <= 0 || separator == group.lastIndex) return@flatMap emptyList()
                val emoteId = group.substring(0, separator)
                group.substring(separator + 1).split(',').mapNotNull { range ->
                    val dash = range.indexOf('-')
                    if (dash <= 0 || dash == range.lastIndex) return@mapNotNull null
                    val rawStart = range.substring(0, dash).toIntOrNull() ?: return@mapNotNull null
                    val rawEnd = range.substring(dash + 1).toIntOrNull() ?: return@mapNotNull null
                    val start = rawStart - codePointOffset
                    val endInclusive = rawEnd - codePointOffset
                    if (start < 0 || endInclusive < start) return@mapNotNull null
                    EmoteRange(emoteId, start, endInclusive)
                }
            }
            ?.sortedWith(compareBy<EmoteRange> { it.startCodePoint }.thenBy { it.endCodePointInclusive })
            .orEmpty()

        if (ranges.isEmpty()) return listOf(ChatFragment.Text(text))

        val codePointCount = text.codePointCount(0, text.length)
        val fragments = mutableListOf<ChatFragment>()
        var cursorCodePoint = 0
        ranges.forEach { range ->
            if (range.startCodePoint < cursorCodePoint || range.endCodePointInclusive >= codePointCount) {
                return@forEach
            }
            if (range.startCodePoint > cursorCodePoint) {
                fragments += ChatFragment.Text(text.substringByCodePoints(cursorCodePoint, range.startCodePoint))
            }
            val endExclusive = range.endCodePointInclusive + 1
            fragments += ChatFragment.TwitchEmote(
                text = text.substringByCodePoints(range.startCodePoint, endExclusive),
                emoteId = range.emoteId,
            )
            cursorCodePoint = endExclusive
        }
        if (cursorCodePoint < codePointCount) {
            fragments += ChatFragment.Text(text.substringByCodePoints(cursorCodePoint, codePointCount))
        }
        return fragments.ifEmpty { listOf(ChatFragment.Text(text)) }
    }

    private fun String.substringByCodePoints(start: Int, endExclusive: Int): String {
        val startIndex = offsetByCodePoints(0, start)
        val endIndex = offsetByCodePoints(0, endExclusive)
        return substring(startIndex, endIndex)
    }

    private fun parseLine(rawLine: String): ParsedIrcLine? {
        var rest = rawLine.trimEnd('\r', '\n')
        if (rest.isBlank()) return null

        val tags = if (rest.startsWith('@')) {
            val end = rest.indexOf(' ')
            if (end <= 1) return null
            val parsed = rest.substring(1, end)
                .split(';')
                .associate { entry ->
                    val separator = entry.indexOf('=')
                    if (separator < 0) entry to "" else entry.substring(0, separator) to unescapeTag(entry.substring(separator + 1))
                }
            rest = rest.substring(end + 1)
            parsed
        } else {
            emptyMap()
        }

        val prefix = if (rest.startsWith(':')) {
            val end = rest.indexOf(' ')
            if (end <= 1) return null
            val value = rest.substring(1, end)
            rest = rest.substring(end + 1)
            value
        } else {
            null
        }

        val commandEnd = rest.indexOf(' ')
        val command = if (commandEnd < 0) rest else rest.substring(0, commandEnd)
        rest = if (commandEnd < 0) "" else rest.substring(commandEnd + 1)
        if (command.isBlank()) return null

        val params = mutableListOf<String>()
        var trailing: String? = null
        while (rest.isNotEmpty()) {
            if (rest.startsWith(':')) {
                trailing = rest.substring(1)
                break
            }
            val separator = rest.indexOf(' ')
            if (separator < 0) {
                params += rest
                break
            }
            params += rest.substring(0, separator)
            rest = rest.substring(separator + 1).trimStart()
        }

        return ParsedIrcLine(
            tags = tags,
            prefix = prefix,
            command = command.uppercase(),
            params = params,
            trailing = trailing,
        )
    }

    private fun unescapeTag(value: String): String = buildString(value.length) {
        var index = 0
        while (index < value.length) {
            if (value[index] != '\\' || index == value.lastIndex) {
                append(value[index])
                index += 1
                continue
            }
            when (value[index + 1]) {
                's' -> append(' ')
                ':' -> append(';')
                '\\' -> append('\\')
                'r' -> append('\r')
                'n' -> append('\n')
                else -> append(value[index + 1])
            }
            index += 2
        }
    }

    private data class ParsedIrcLine(
        val tags: Map<String, String>,
        val prefix: String?,
        val command: String,
        val params: List<String>,
        val trailing: String?,
    )

    private data class EmoteRange(
        val emoteId: String,
        val startCodePoint: Int,
        val endCodePointInclusive: Int,
    )

    internal const val MAX_IRC_LINE_CHARS = 16 * 1024
}
