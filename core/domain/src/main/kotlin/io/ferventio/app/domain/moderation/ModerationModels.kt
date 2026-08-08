package io.ferventio.app.domain

import java.time.Instant

enum class AutoModMessageStatus {
    HELD,
    APPROVED,
    DENIED,
}

data class AutoModBoundary(
    val start: Int,
    val endInclusive: Int,
)

data class AutoModHeldMessage(
    val channelId: String,
    val channelLogin: String,
    val channelName: String,
    val userId: String,
    val userLogin: String,
    val userName: String,
    val messageId: String,
    val text: String,
    val fragments: List<ChatFragment> = emptyList(),
    val reason: String? = null,
    val category: String? = null,
    val level: Int? = null,
    val boundaries: List<AutoModBoundary> = emptyList(),
    val heldAt: String = Instant.now().toString(),
    val status: AutoModMessageStatus = AutoModMessageStatus.HELD,
    val decidedByUserId: String? = null,
    val decidedByUserLogin: String? = null,
    val decidedByUserName: String? = null,
) {
    val heldAtMillis: Long
        get() = runCatching { Instant.parse(heldAt).toEpochMilli() }.getOrDefault(0L)
}

data class ModerationChatSettings(
    val channelId: String,
    val slowMode: Boolean = false,
    val slowModeWaitSeconds: Int = 30,
    val followerMode: Boolean = false,
    val followerModeDurationMinutes: Int = 0,
    val subscriberMode: Boolean = false,
    val emoteMode: Boolean = false,
    val uniqueChatMode: Boolean = false,
)

enum class ModerationUserGroup {
    BROADCASTER,
    STAFF,
    VIP,
    MODERATOR,
    CHATBOT,
    VIEWER,
    UNKNOWN,
}

data class ModerationUser(
    val id: String,
    val login: String,
    val displayName: String,
    val group: ModerationUserGroup = ModerationUserGroup.UNKNOWN,
)

data class BannedChatUser(
    val id: String,
    val login: String,
    val displayName: String,
    val expiresAt: String? = null,
    val createdAt: String? = null,
    val reason: String? = null,
    val moderatorId: String? = null,
    val moderatorLogin: String? = null,
    val moderatorName: String? = null,
) {
    val isPermanent: Boolean get() = expiresAt.isNullOrBlank()
}

data class RemoteModerationAction(
    val id: String,
    val channelId: String,
    val channelLogin: String,
    val channelName: String,
    val moderatorId: String,
    val moderatorLogin: String,
    val moderatorName: String,
    val action: String,
    val targetUserId: String? = null,
    val targetUserLogin: String? = null,
    val targetUserName: String? = null,
    val messageId: String? = null,
    val reason: String? = null,
    val durationSeconds: Int? = null,
    val createdAt: String = Instant.now().toString(),
) {
    val createdAtMillis: Long
        get() = runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrDefault(0L)
}

enum class ModerationPeopleTab {
    CHATTERS,
    MODERATORS,
    VIPS,
    BANNED,
}

data class ModerationUiState(
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshingPeople: Boolean = false,
    val chatSettings: ModerationChatSettings? = null,
    val autoModQueue: List<AutoModHeldMessage> = emptyList(),
    val chatters: List<ModerationUser> = emptyList(),
    val chattersAreComplete: Boolean = false,
    val peopleNotice: String? = null,
    val moderators: List<ModerationUser> = emptyList(),
    val vips: List<ModerationUser> = emptyList(),
    val bannedUsers: List<BannedChatUser> = emptyList(),
    val localHistory: List<LocalModerationAction> = emptyList(),
    val remoteHistory: List<RemoteModerationAction> = emptyList(),
    val peopleTab: ModerationPeopleTab = ModerationPeopleTab.CHATTERS,
    val autoModNotificationsEnabled: Boolean = true,
    val errorMessage: String? = null,
)
