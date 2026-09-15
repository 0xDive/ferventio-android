package io.ferventio.shared.ui.user

import io.ferventio.app.domain.ChannelUserRole
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.UserCardData

internal const val USER_CARD_RECENT_MESSAGE_LIMIT = 20

internal data class UserCardModerationAvailability(
    val canModerateUser: Boolean,
    val canDeleteSourceMessage: Boolean,
)

internal fun userCardModerationAvailability(
    data: UserCardData,
    authenticatedUserId: String?,
): UserCardModerationAvailability {
    val moderatorId = authenticatedUserId?.trim().orEmpty()
    val targetId = data.user.id.trim()
    val canUseModeration = data.canModerate && moderatorId.isNotEmpty()
    val canModerateUser = canUseModeration &&
        targetId.isNotEmpty() &&
        targetId != moderatorId &&
        data.role != ChannelUserRole.BROADCASTER
    val sourceMessage = data.sourceMessageId?.let { messageId ->
        data.recentMessages.firstOrNull { message -> message.id == messageId }
    }
    return UserCardModerationAvailability(
        canModerateUser = canModerateUser,
        canDeleteSourceMessage = canUseModeration && sourceMessage?.isDeleted == false,
    )
}

internal fun projectLocalUserCard(
    sourceMessage: ChatMessage,
    channelMessages: List<ChatMessage>,
    canModerate: Boolean,
): UserCardData {
    val matchingMessages = channelMessages.filter { message ->
        sameUser(sourceMessage, message)
    }
    val withSource = if (matchingMessages.any { it.id == sourceMessage.id }) {
        matchingMessages
    } else {
        matchingMessages + sourceMessage
    }
    val recentMessages = withSource.takeLast(USER_CARD_RECENT_MESSAGE_LIMIT)
    val latestProfileAuthor = recentMessages
        .asReversed()
        .firstOrNull { !it.author.profileImageUrl.isNullOrBlank() }
        ?.author
        ?: sourceMessage.author
    val badges = buildList {
        addAll(sourceMessage.badges)
        recentMessages.forEach { addAll(it.badges) }
    }

    return UserCardData(
        channelId = sourceMessage.channelId,
        user = TwitchUser(
            id = sourceMessage.userId,
            login = sourceMessage.userLogin,
            displayName = sourceMessage.userDisplayName,
            profileImageUrl = latestProfileAuthor.profileImageUrl,
        ),
        role = resolveLocalUserRole(badges),
        canModerate = canModerate,
        sourceMessageId = sourceMessage.id,
        recentMessages = recentMessages,
    )
}

internal fun resolveLocalUserRole(badges: Iterable<ChatBadge>): ChannelUserRole {
    val sets = badges.mapTo(hashSetOf()) { it.setId.lowercase() }
    return when {
        "broadcaster" in sets -> ChannelUserRole.BROADCASTER
        "moderator" in sets || "global_mod" in sets -> ChannelUserRole.MODERATOR
        "vip" in sets -> ChannelUserRole.VIP
        "subscriber" in sets || "founder" in sets -> ChannelUserRole.SUBSCRIBER
        else -> ChannelUserRole.VIEWER
    }
}

private fun sameUser(source: ChatMessage, candidate: ChatMessage): Boolean {
    val sourceId = source.userId.trim()
    val candidateId = candidate.userId.trim()
    if (sourceId.isNotEmpty() && candidateId.isNotEmpty()) {
        return sourceId == candidateId
    }
    return source.userLogin.equals(candidate.userLogin, ignoreCase = true)
}
