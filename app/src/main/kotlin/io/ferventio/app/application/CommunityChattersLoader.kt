package io.ferventio.app.application

import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import io.ferventio.app.twitch.TwitchUnofficialChatterGroup
import io.ferventio.app.twitch.TwitchUnofficialChattersClient

/**
 * Loads Twitch CommunityTab chatter roles independently from the official Helix
 * chatter endpoint. Helix is still preferred for canonical IDs/display names; this
 * loader supplies the role buckets that Helix does not expose in its chatter list.
 */
suspend fun FerventioController.loadCategorizedCommunityChatters(
    channelId: String,
): List<ModerationUser> {
    val channel = state.value.channels.firstOrNull { it.id == channelId } ?: return emptyList()
    return TwitchUnofficialChattersClient().use { client ->
        client.getChatters(channel.login).map { chatter ->
            ModerationUser(
                id = chatter.id.ifBlank { "gql:${chatter.login.lowercase()}" },
                login = chatter.login,
                displayName = chatter.login,
                group = chatter.group.toModerationUserGroup(),
            )
        }
    }
}

internal fun mergeCategorizedChatters(
    canonical: List<ModerationUser>,
    categorized: List<ModerationUser>,
): List<ModerationUser> {
    if (categorized.isEmpty()) return canonical
    val canonicalByLogin = canonical.associateBy { it.login.lowercase() }
    val categorizedLogins = categorized.mapTo(hashSetOf()) { it.login.lowercase() }
    val merged = categorized.map { categorizedUser ->
        val canonicalUser = canonicalByLogin[categorizedUser.login.lowercase()]
        if (canonicalUser == null) {
            categorizedUser
        } else {
            canonicalUser.copy(group = categorizedUser.group)
        }
    }
    return merged + canonical.filterNot { it.login.lowercase() in categorizedLogins }
}

private fun TwitchUnofficialChatterGroup.toModerationUserGroup(): ModerationUserGroup = when (this) {
    TwitchUnofficialChatterGroup.BROADCASTER -> ModerationUserGroup.BROADCASTER
    TwitchUnofficialChatterGroup.STAFF -> ModerationUserGroup.STAFF
    TwitchUnofficialChatterGroup.VIP -> ModerationUserGroup.VIP
    TwitchUnofficialChatterGroup.MODERATOR -> ModerationUserGroup.MODERATOR
    TwitchUnofficialChatterGroup.CHATBOT -> ModerationUserGroup.CHATBOT
    TwitchUnofficialChatterGroup.VIEWER -> ModerationUserGroup.VIEWER
}
