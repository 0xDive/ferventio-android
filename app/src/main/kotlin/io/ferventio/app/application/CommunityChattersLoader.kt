package io.ferventio.app.application

import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.app.twitch.TwitchUnofficialChatterGroup
import io.ferventio.app.twitch.TwitchUnofficialChattersClient
import kotlinx.coroutines.CancellationException

/**
 * Loads Twitch CommunityTab chatter roles independently from the official Helix
 * chatter endpoint. Helix is still preferred for canonical IDs/display names; this
 * loader supplies the role buckets that Helix does not expose in its chatter list.
 *
 * CommunityTab is private Twitch surface area and may fail independently from chat.
 * Keep role presentation useful in that case by combining the broadcaster identity,
 * the authenticated moderator identity, and role badges already observed in chat.
 */
suspend fun FerventioController.loadCategorizedCommunityChatters(
    channelId: String,
): List<ModerationUser> {
    val snapshot = state.value
    val channel = snapshot.channels.firstOrNull { it.id == channelId } ?: return emptyList()
    val inferred = inferCategorizedChatters(snapshot, channelId)
    val community = try {
        TwitchUnofficialChattersClient().use { client ->
            client.getChatters(channel.login).map { chatter ->
                ModerationUser(
                    id = chatter.id.ifBlank { "gql:${chatter.login.lowercase()}" },
                    login = chatter.login,
                    displayName = chatter.login,
                    group = chatter.group.toModerationUserGroup(),
                )
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        emptyList()
    }
    return mergeRoleHints(inferred, community)
}

internal fun mergeCategorizedChatters(
    canonical: List<ModerationUser>,
    categorized: List<ModerationUser>,
): List<ModerationUser> {
    val canonicalByLogin = canonical.associateBy { it.login.lowercase() }
    val categorizedLogins = categorized.mapTo(hashSetOf()) { it.login.lowercase() }
    val merged = categorized.map { categorizedUser ->
        val canonicalUser = canonicalByLogin[categorizedUser.login.lowercase()]
        if (canonicalUser == null) {
            categorizedUser.withViewerFallback()
        } else {
            canonicalUser.copy(
                group = categorizedUser.group.takeUnless { it == ModerationUserGroup.UNKNOWN }
                    ?: canonicalUser.group.takeUnless { it == ModerationUserGroup.UNKNOWN }
                    ?: ModerationUserGroup.VIEWER,
            )
        }
    }
    return merged + canonical
        .filterNot { it.login.lowercase() in categorizedLogins }
        .map(ModerationUser::withViewerFallback)
}

internal fun inferCategorizedChatters(
    state: FerventioUiState,
    channelId: String,
): List<ModerationUser> {
    val channel = state.channels.firstOrNull { it.id == channelId } ?: return emptyList()
    val hints = linkedMapOf<String, ModerationUser>()

    fun add(user: ModerationUser) {
        val login = user.login.trim().lowercase()
        if (login.isBlank()) return
        val previous = hints[login]
        if (previous == null || rolePriority(user.group) > rolePriority(previous.group)) {
            hints[login] = user
        } else if (previous.id.startsWith("observed:") && !user.id.startsWith("observed:")) {
            hints[login] = previous.copy(id = user.id, displayName = user.displayName.ifBlank { previous.displayName })
        }
    }

    add(
        ModerationUser(
            id = channel.id,
            login = channel.login,
            displayName = channel.displayName.ifBlank { channel.login },
            group = ModerationUserGroup.BROADCASTER,
        ),
    )

    state.messagesByChannel[channelId].orEmpty().asReversed().forEach { message ->
        if (message.isSystem || message.userLogin.isBlank()) return@forEach
        val group = when {
            message.userId == channel.id || message.badges.any { it.setId == "broadcaster" } ->
                ModerationUserGroup.BROADCASTER
            message.badges.any { it.setId == "staff" } -> ModerationUserGroup.STAFF
            message.badges.any { it.setId == "vip" } -> ModerationUserGroup.VIP
            message.badges.any { it.setId == "moderator" } -> ModerationUserGroup.MODERATOR
            else -> return@forEach
        }
        add(
            ModerationUser(
                id = message.userId.ifBlank { "observed:${message.userLogin.lowercase()}" },
                login = message.userLogin,
                displayName = message.userDisplayName.ifBlank { message.userLogin },
                group = group,
            ),
        )
    }

    val session = state.session
    if (session != null && channelId in state.moderatedChannelIds) {
        add(
            ModerationUser(
                id = session.userId,
                login = session.login,
                displayName = state.userProfilesById[session.userId]?.displayName?.ifBlank { session.login }
                    ?: session.login,
                group = if (session.userId == channel.id) {
                    ModerationUserGroup.BROADCASTER
                } else {
                    ModerationUserGroup.MODERATOR
                },
            ),
        )
    }

    return hints.values.toList()
}

private fun mergeRoleHints(
    first: List<ModerationUser>,
    second: List<ModerationUser>,
): List<ModerationUser> {
    val result = linkedMapOf<String, ModerationUser>()
    (first + second).forEach { incoming ->
        val login = incoming.login.trim().lowercase()
        if (login.isBlank()) return@forEach
        val existing = result[login]
        if (existing == null) {
            result[login] = incoming.withViewerFallback()
            return@forEach
        }
        val preferred = if (rolePriority(incoming.group) > rolePriority(existing.group)) incoming else existing
        val other = if (preferred === incoming) existing else incoming
        result[login] = preferred.copy(
            id = preferred.id.takeIf { it.isNotBlank() && !it.startsWith("gql:") }
                ?: other.id.takeIf(String::isNotBlank)
                ?: preferred.id,
            displayName = preferred.displayName.takeIf { it.isNotBlank() && !it.equals(preferred.login, true) }
                ?: other.displayName.takeIf(String::isNotBlank)
                ?: preferred.login,
        ).withViewerFallback()
    }
    return result.values.toList()
}

private fun ModerationUser.withViewerFallback(): ModerationUser =
    if (group == ModerationUserGroup.UNKNOWN) copy(group = ModerationUserGroup.VIEWER) else this

private fun rolePriority(group: ModerationUserGroup): Int = when (group) {
    ModerationUserGroup.BROADCASTER -> 6
    ModerationUserGroup.STAFF -> 5
    ModerationUserGroup.VIP -> 4
    ModerationUserGroup.MODERATOR -> 3
    ModerationUserGroup.CHATBOT -> 2
    ModerationUserGroup.VIEWER -> 1
    ModerationUserGroup.UNKNOWN -> 0
}

private fun TwitchUnofficialChatterGroup.toModerationUserGroup(): ModerationUserGroup = when (this) {
    TwitchUnofficialChatterGroup.BROADCASTER -> ModerationUserGroup.BROADCASTER
    TwitchUnofficialChatterGroup.STAFF -> ModerationUserGroup.STAFF
    TwitchUnofficialChatterGroup.VIP -> ModerationUserGroup.VIP
    TwitchUnofficialChatterGroup.MODERATOR -> ModerationUserGroup.MODERATOR
    TwitchUnofficialChatterGroup.CHATBOT -> ModerationUserGroup.CHATBOT
    TwitchUnofficialChatterGroup.VIEWER -> ModerationUserGroup.VIEWER
}
