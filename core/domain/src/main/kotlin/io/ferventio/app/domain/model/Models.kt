package io.ferventio.app.domain

import androidx.compose.runtime.Immutable
import java.time.Instant


data class BackendSessionCredential(
    val serverUrl: String,
    val token: String,
    val expiresAtEpochMillis: Long,
)

data class BackendAuthorizationStart(
    val authorizationUrl: String,
    val state: String,
    val expiresAtEpochMillis: Long,
)

data class TwitchAccessLease(
    val accessToken: String,
    val leaseExpiresAtEpochMillis: Long,
    val twitchExpiresAtEpochMillis: Long,
    val twitchValidatedAtEpochMillis: Long,
    val backendSessionExpiresAtEpochMillis: Long,
    val session: TwitchSession,
)

data class TwitchSession(
    val clientId: String,
    val userId: String,
    val login: String,
    val scopes: Set<String>,
    val expiresInSeconds: Long,
)

@Immutable
data class TwitchUser(
    val id: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String? = null,
    val createdAt: String? = null,
    val broadcasterType: String? = null,
    val description: String? = null,
)


data class TwitchChannelInfo(
    val broadcasterId: String,
    val title: String,
    val gameId: String,
    val gameName: String,
)

data class TwitchStreamInfo(
    val id: String,
    val startedAt: String,
    val viewerCount: Int,
    val title: String,
    val gameName: String,
)

data class TwitchCategory(
    val id: String,
    val name: String,
)

data class TwitchClipResult(
    val id: String,
    val editUrl: String,
)

data class TwitchMarkerResult(
    val id: String,
    val positionSeconds: Int,
    val description: String,
)

data class TwitchChatter(
    val id: String,
    val login: String,
    val displayName: String,
)

data class TwitchChattersResult(
    val total: Int,
    val users: List<TwitchChatter>,
)

data class ChannelFollowerInfo(
    val followedAt: String? = null,
)

data class PublicChannelRelationship(
    val followedAt: String? = null,
    val subscriptionStatusHidden: Boolean = false,
    val isCurrentlySubscribed: Boolean? = null,
    val subscriberMonths: Int? = null,
    val subscriberTier: String? = null,
)

@Immutable
data class ChatChannel(
    val id: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String? = null,
)

@Immutable
data class ChatBadge(
    val setId: String,
    val id: String,
    val info: String? = null,
)

@Immutable
data class ChatBadgeAsset(
    val setId: String,
    val id: String,
    val imageUrl1x: String,
    val imageUrl2x: String,
    val imageUrl4x: String,
    val title: String,
    val description: String,
) {
    val key: String get() = chatBadgeAssetKey(setId, id)
}

fun chatBadgeAssetKey(setId: String, id: String): String = "$setId/$id"


enum class EmoteScope {
    GLOBAL,
    CHANNEL,
}

@Immutable
data class ThirdPartyEmoteAsset(
    val id: String,
    val code: String,
    val provider: String,
    val imageType: String,
    val animated: Boolean,
    val imageUrl1x: String,
    val imageUrl2x: String,
    val imageUrl3x: String,
    val scope: EmoteScope = EmoteScope.GLOBAL,
    val channelId: String? = null,
    val ownerId: String? = null,
    val ownerName: String? = null,
    val emoteType: String? = null,
    val emoteSetId: String? = null,
    val sourceUrl: String? = null,
    val zeroWidth: Boolean = false,
    val textResolvable: Boolean = true,
)

val ThirdPartyEmoteAsset.usageKey: String
    get() = "$provider:$id"

@Immutable
data class EmoteProviderCatalog(
    val emotes: Map<String, ThirdPartyEmoteAsset> = emptyMap(),
    val subscriptionIds: Set<String> = emptySet(),
)

@Immutable
data class CheermoteAsset(
    val prefix: String,
    val minBits: Int,
    val tier: Int,
    val color: String,
    val animatedImageUrl: String?,
    val staticImageUrl: String?,
) {
    fun imageUrl(animate: Boolean): String? = when {
        animate && !animatedImageUrl.isNullOrBlank() -> animatedImageUrl
        !staticImageUrl.isNullOrBlank() -> staticImageUrl
        else -> animatedImageUrl
    }
}

@Immutable
data class ChatAuthor(
    val id: String,
    val login: String,
    val displayName: String,
    val color: String? = null,
    val badges: List<ChatBadge> = emptyList(),
    val profileImageUrl: String? = null,
)

@Immutable
sealed interface ChatFragment {
    val text: String

    data class Text(
        override val text: String,
    ) : ChatFragment

    data class TwitchEmote(
        override val text: String,
        val emoteId: String,
        val emoteSetId: String? = null,
        val ownerId: String? = null,
        val formats: Set<String> = emptySet(),
    ) : ChatFragment

    data class ThirdPartyEmote(
        override val text: String,
        val emoteId: String,
        val provider: String,
        val animated: Boolean = false,
        val imageUrl: String? = null,
        val zeroWidth: Boolean = false,
    ) : ChatFragment

    data class Gif(
        override val text: String,
        val gifId: String,
        val url: String,
    ) : ChatFragment

    data class Mention(
        override val text: String,
        val userId: String,
        val userLogin: String,
        val userName: String,
    ) : ChatFragment

    data class Cheermote(
        override val text: String,
        val prefix: String,
        val bits: Int,
        val tier: Int,
    ) : ChatFragment

    data class Link(
        override val text: String,
        val url: String,
    ) : ChatFragment

    data class Unknown(
        override val text: String,
        val rawType: String,
    ) : ChatFragment
}

@Immutable
data class ReplyContext(
    val parentMessageId: String,
    val parentMessageBody: String? = null,
    val parentUserId: String? = null,
    val parentUserLogin: String? = null,
    val parentUserName: String? = null,
    val threadMessageId: String? = null,
    val threadUserId: String? = null,
    val threadUserLogin: String? = null,
    val threadUserName: String? = null,
)

@Immutable
data class ChatNotice(
    val type: String,
    val systemMessage: String? = null,
    val userMessage: String? = null,
    val subTier: String? = null,
    val isPrime: Boolean? = null,
    val durationMonths: Int? = null,
    val cumulativeMonths: Int? = null,
    val streakMonths: Int? = null,
    val isGift: Boolean? = null,
    val giftTotal: Int? = null,
    val cumulativeGiftTotal: Int? = null,
    val communityGiftId: String? = null,
    val gifterIsAnonymous: Boolean? = null,
    val gifterUserId: String? = null,
    val gifterUserLogin: String? = null,
    val gifterUserName: String? = null,
    val recipientUserId: String? = null,
    val recipientUserLogin: String? = null,
    val recipientUserName: String? = null,
    val raidUserId: String? = null,
    val raidUserLogin: String? = null,
    val raidUserName: String? = null,
    val raidViewerCount: Int? = null,
    val raidProfileImageUrl: String? = null,
    val announcementColor: String? = null,
    val isAnonymous: Boolean = false,
)

@Immutable
data class ChatScrollPosition(
    val channelId: String,
    val anchorMessageId: String? = null,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val isAtBottom: Boolean = true,
)

@Immutable
data class HistoryPagingState(
    val isLoading: Boolean = false,
    val endReached: Boolean = false,
    val loadedCount: Int = 0,
)

enum class ChatMessageType {
    CHAT,
    ACTION,
    SYSTEM,
    ANNOUNCEMENT,
    SUBSCRIPTION,
    RESUBSCRIPTION,
    GIFT_SUBSCRIPTION,
    RAID,
    CHEER,
    REWARD,
    MODERATION,
    UNKNOWN,
}

enum class ModerationAction {
    DELETE,
    TIMEOUT,
    BAN,
    CLEAR,
}

@Immutable
data class ModerationState(
    val action: ModerationAction? = null,
    val actorUserId: String? = null,
    val reason: String? = null,
    val atMillis: Long? = null,
)

@Immutable
data class MessageFlags(
    val isDeleted: Boolean = false,
    val isSystem: Boolean = false,
    val isAction: Boolean = false,
    val isFirstMessage: Boolean = false,
    val isReturningChatter: Boolean = false,
)

enum class OutgoingMessageState {
    NONE,
    SENDING,
    SENT,
    FAILED,
}

@Immutable
data class ChatRateLimitState(
    val message: String,
    val retryAtMillis: Long? = null,
)

@Immutable
data class ChatSendResult(
    val messageId: String?,
)

@Immutable
data class ChatReward(
    val id: String? = null,
    val title: String? = null,
    val cost: Long? = null,
)

@Immutable
data class ChatMessage(
    val id: String,
    val eventSubMessageId: String? = null,
    val channelId: String,
    val channelLogin: String,
    val author: ChatAuthor,
    val text: String,
    val fragments: List<ChatFragment> = listOf(ChatFragment.Text(text)),
    val timestamp: String,
    val timestampMillis: Long = timestamp.toEpochMillisOrNow(),
    val reply: ReplyContext? = null,
    val notice: ChatNotice? = null,
    val reward: ChatReward? = null,
    val type: ChatMessageType = ChatMessageType.CHAT,
    val flags: MessageFlags = MessageFlags(),
    val moderation: ModerationState = ModerationState(),
    val outgoingState: OutgoingMessageState = OutgoingMessageState.NONE,
    val outgoingError: String? = null,
    val clientNonce: String? = null,
    val serverMessageId: String? = null,
) {
    // Compatibility properties keep the current UI and moderation actions small while
    // the app migrates from the original flat message model to the richer domain model.
    val userId: String get() = author.id
    val userLogin: String get() = author.login
    val userDisplayName: String get() = author.displayName
    val color: String? get() = author.color
    val badges: List<ChatBadge> get() = author.badges
    val replyParentMessageId: String? get() = reply?.parentMessageId
    val replyParentUserName: String? get() = reply?.parentUserName
    val isAction: Boolean get() = flags.isAction || type == ChatMessageType.ACTION
    val isDeleted: Boolean get() = flags.isDeleted
    val isSystem: Boolean get() = flags.isSystem || type == ChatMessageType.SYSTEM || type == ChatMessageType.MODERATION
}



enum class ChannelUserRole {
    BROADCASTER,
    MODERATOR,
    VIP,
    SUBSCRIBER,
    VIEWER,
}

@Immutable
data class LocalModerationAction(
    val id: String,
    val channelId: String,
    val targetUserId: String?,
    val targetUserLogin: String?,
    val messageId: String?,
    val action: String,
    val durationSeconds: Int?,
    val reason: String?,
    val createdAtMillis: Long,
)

@Immutable
data class UserCardData(
    val channelId: String,
    val user: TwitchUser,
    val followerInfo: ChannelFollowerInfo = ChannelFollowerInfo(),
    val role: ChannelUserRole = ChannelUserRole.VIEWER,
    val canModerate: Boolean = false,
    val subscriberMonths: Int? = null,
    val subscriberTier: String? = null,
    val subscriptionStatusHidden: Boolean = false,
    val isCurrentlySubscribed: Boolean? = null,
    val sourceMessageId: String? = null,
    val recentMessages: List<ChatMessage> = emptyList(),
    val localActions: List<LocalModerationAction> = emptyList(),
)

@Immutable
data class UserCardUiState(
    val isLoading: Boolean = false,
    val data: UserCardData? = null,
    val errorMessage: String? = null,
)

@Immutable
data class PinnedChatMessage(
    val channelId: String,
    val messageId: String,
    val senderUserId: String,
    val senderUserLogin: String,
    val senderUserName: String,
    val pinnedByUserName: String? = null,
    val text: String,
    val fragments: List<ChatFragment> = emptyList(),
    val startsAt: String? = null,
    val endsAt: String? = null,
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    WAITING_WELCOME,
    CREATING_SUBSCRIPTIONS,
    CONNECTED,
    RECONNECTING,
    FAILED,
}

data class FerventioUiState(
    val clientId: String = "",
    val isBootstrapping: Boolean = true,
    val isAuthorizing: Boolean = false,
    val isRevokingDevice: Boolean = false,
    val isRevokingAllSessions: Boolean = false,
    val reauthorizationRequired: Boolean = false,
    val pendingExternalUri: String? = null,
    val session: TwitchSession? = null,
    val channels: List<ChatChannel> = emptyList(),
    val selectedChannelId: String? = null,
    val requestedMainSection: MainSection? = null,
    val pinnedChannelIds: List<String> = emptyList(),
    val channelTabTitles: Map<String, String> = emptyMap(),
    val recentChannelIds: List<String> = emptyList(),
    val channelAttention: Map<String, ChannelAttention> = emptyMap(),
    val attentionEntries: List<AttentionEntry> = emptyList(),
    val mentionUnreadCount: Int = 0,
    val highlightRules: List<HighlightRule> = emptyList(),
    val ignoreRules: List<IgnoreRule> = emptyList(),
    val savedMessageFilters: List<SavedMessageFilter> = emptyList(),
    val messageDecorationsById: Map<String, MessageDecoration> = emptyMap(),
    val workspaceLayout: WorkspaceLayout = WorkspaceLayout.default(),
    val visibleChannelIds: Set<String> = emptySet(),
    val messageNavigationTargets: Map<String, String> = emptyMap(),
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val historyPagingByChannel: Map<String, HistoryPagingState> = emptyMap(),
    val scrollPositionsByChannel: Map<String, ChatScrollPosition> = emptyMap(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String? = null,
    val connectionAttempt: Int = 0,
    val lastEventSubActivityAtMillis: Long? = null,
    val lastEventSubActivityType: String? = null,
    val eventSubConnectedAtMillis: Long? = null,
    val lastConnectionDurationMillis: Long? = null,
    val lastConnectionError: String? = null,
    val eventSubReconnectCount: Int = 0,
    val eventSubDuplicateCount: Int = 0,
    val eventSubDroppedEventCount: Int = 0,
    val eventSubMalformedEnvelopeCount: Int = 0,
    val eventSubNoticeChannelIds: Set<String> = emptySet(),
    val eventSubNoticeFailures: Map<String, String> = emptyMap(),
    val localHistoryEnabled: Boolean = true,
    val localHistoryLimit: Int = 500,
    val localHistoryRetentionDays: Int = 7,
    val localHistoryMaxSizeMb: Int = 0,
    val appLanguage: AppLanguage = AppLanguage.RUSSIAN,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val fontScalePercent: Int = 100,
    val messageDensity: MessageDensity = MessageDensity.NORMAL,
    val chatNameStyle: ChatNameStyle = ChatNameStyle.DISPLAY_NAME,
    val wrapMessageLines: Boolean = true,
    val mentionColorArgb: Long = MentionColors.GOLD,
    val autoScrollEnabled: Boolean = true,
    val showAvatars: Boolean = false,
    val showBadges: Boolean = true,
    val showTimestamps: Boolean = true,
    val showDeletedMessageContent: Boolean = false,
    val showSystemMessages: Boolean = true,
    val animateEmotes: Boolean = true,
    val emoteScalePercent: Int = 100,
    val betterTtvEnabled: Boolean = true,
    val frankerFaceZEnabled: Boolean = true,
    val sevenTvEnabled: Boolean = true,
    val badgeAssetsByChannel: Map<String, Map<String, ChatBadgeAsset>> = emptyMap(),
    val frankerFaceZBadgesByUserId: Map<String, List<ChatBadgeAsset>> = emptyMap(),
    val frankerFaceZChannelBadgesByChannel: Map<String, Map<String, List<ChatBadgeAsset>>> = emptyMap(),
    val cheermoteAssetsByChannel: Map<String, Map<String, List<CheermoteAsset>>> = emptyMap(),
    val emoteCatalogByChannel: Map<String, List<ThirdPartyEmoteAsset>> = emptyMap(),
    val recentEmoteKeys: List<String> = emptyList(),
    val favoriteEmoteKeys: Set<String> = emptySet(),
    val draftsByChannel: Map<String, String> = emptyMap(),
    val sentMessageHistoryByChannel: Map<String, List<String>> = emptyMap(),
    val customCommands: List<CustomCommand> = emptyList(),
    val customCommandStatusMessage: String? = null,
    val sendOnEnter: Boolean = true,
    val showComposerEmoteImages: Boolean = true,
    val userCardTimeoutPresetsSeconds: List<Int> = listOf(10, 60, 600, 3_600, 86_400),
    val userCardShowBanAction: Boolean = true,
    val userCardModerationActionOrder: List<String> = emptyList(),
    val replyComposerTargets: Map<String, String> = emptyMap(),
    val replyNotificationsEnabled: Boolean = true,
    val rateLimitsByChannel: Map<String, ChatRateLimitState> = emptyMap(),
    val pinnedMessagesByChannel: Map<String, PinnedChatMessage> = emptyMap(),
    val emoteLiveProviders: Set<String> = emptySet(),
    val emoteCatalogErrorMessage: String? = null,
    val betterTtvEmotesByChannel: Map<String, Map<String, ThirdPartyEmoteAsset>> = emptyMap(),
    val frankerFaceZEmotesByChannel: Map<String, Map<String, ThirdPartyEmoteAsset>> = emptyMap(),
    val sevenTvEmotesByChannel: Map<String, Map<String, ThirdPartyEmoteAsset>> = emptyMap(),
    val moderatedChannelIds: Set<String> = emptySet(),
    val userProfilesById: Map<String, TwitchUser> = emptyMap(),
    val userColorsById: Map<String, String> = emptyMap(),
    val userCard: UserCardUiState = UserCardUiState(),
    val moderation: ModerationUiState = ModerationUiState(),
    val isImageCacheClearing: Boolean = false,
    val imageCacheStatusMessage: String? = null,
    val backupStatusMessage: String? = null,
    val settingsSyncEnabled: Boolean = false,
    val settingsSyncRevision: Long = 0L,
    val settingsSyncLastSyncedAtMillis: Long = 0L,
    val settingsSyncStatus: SettingsSyncStatus = SettingsSyncStatus.DISABLED,
    val settingsSyncErrorMessage: String? = null,
    val settingsSyncConflict: SettingsSyncConflict? = null,
    val settingsSyncHistory: List<SettingsSyncHistoryEntry> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val isChannelsLoading: Boolean = false,
    val restoredHistoryMessageCount: Int = 0,
    val historyErrorMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isAuthenticated: Boolean get() = session != null
    val isAnonymous: Boolean get() = session == null

    val selectedChannel: ChatChannel?
        get() = channels.firstOrNull { it.id == selectedChannelId }

    val selectedMessages: List<ChatMessage>
        get() = selectedChannelId?.let(messagesByChannel::get).orEmpty()

    val selectedScrollPosition: ChatScrollPosition?
        get() = selectedChannelId?.let(scrollPositionsByChannel::get)
}

sealed interface ChatEvent {
    data class Message(val message: ChatMessage) : ChatEvent
    data class AutoModHeld(val message: AutoModHeldMessage) : ChatEvent
    data class AutoModUpdated(val message: AutoModHeldMessage) : ChatEvent
    data class ModerationPerformed(val action: RemoteModerationAction) : ChatEvent
    data class ChatSettingsUpdated(val settings: ModerationChatSettings) : ChatEvent
    data class MessageDeleted(
        val channelId: String,
        val messageId: String,
        val eventId: String? = null,
        val createdAt: String? = null,
    ) : ChatEvent
    data class UserMessagesCleared(
        val channelId: String,
        val userId: String,
        val userLogin: String? = null,
        val durationSeconds: Int? = null,
        val isPermanent: Boolean? = null,
        val eventId: String? = null,
        val createdAt: String? = null,
    ) : ChatEvent
    data class ChatCleared(
        val channelId: String,
        val eventId: String? = null,
        val createdAt: String? = null,
    ) : ChatEvent
}

fun String.toEpochMillisOrNow(nowMillis: Long = System.currentTimeMillis()): Long =
    takeIf(String::isNotBlank)
        ?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
        ?: nowMillis
