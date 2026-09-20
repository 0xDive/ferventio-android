package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatRateLimitState
import io.ferventio.app.domain.ChatScrollPosition
import io.ferventio.app.domain.CheermoteAsset
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.InteractiveChatOverlayReducer
import io.ferventio.app.domain.InteractiveChatOverlayState
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.OutgoingMessageState
import io.ferventio.app.domain.chatBadgeAssetKey
import kotlin.time.Clock

data class ChatRuntimeSnapshot(
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val scrollPositionsByChannel: Map<String, ChatScrollPosition> = emptyMap(),
    val globalBadgeAssets: Map<String, ChatBadgeAsset> = emptyMap(),
    val badgeAssetsByChannel: Map<String, Map<String, ChatBadgeAsset>> = emptyMap(),
    val cheermoteAssetsByChannel: Map<String, Map<String, List<CheermoteAsset>>> = emptyMap(),
    val interactiveState: InteractiveChatOverlayState = InteractiveChatOverlayState(),
    val autoModQueue: List<AutoModHeldMessage> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String? = null,
    val connectionAttempt: Int = 0,
    val connectionErrorMessage: String? = null,
    val authenticationRequired: Boolean = false,
)

/** Platform-neutral live-chat state shared by Android/iOS UI and common EventSub transport. */
class ChatRuntimeStateHolder(
    initialSnapshot: ChatRuntimeSnapshot = ChatRuntimeSnapshot(),
) {
    var messagesByChannel by mutableStateOf(emptyMap<String, List<ChatMessage>>())
        private set
    var scrollPositionsByChannel by mutableStateOf(emptyMap<String, ChatScrollPosition>())
        private set
    private var historyMessagesByChannel by mutableStateOf(emptyMap<String, List<ChatMessage>>())
    private val timelineCacheByChannel = mutableMapOf<String, TimelineCacheEntry>()
    private val liveMessageIdsByChannel = mutableMapOf<String, MutableSet<String>>()
    private val liveServerMessageIdsByChannel = mutableMapOf<String, MutableSet<String>>()
    var globalBadgeAssets by mutableStateOf(emptyMap<String, ChatBadgeAsset>())
        private set
    var badgeAssetsByChannel by mutableStateOf(emptyMap<String, Map<String, ChatBadgeAsset>>())
        private set
    var cheermoteAssetsByChannel by mutableStateOf(
        emptyMap<String, Map<String, List<CheermoteAsset>>>(),
    )
        private set
    var interactiveState by mutableStateOf(InteractiveChatOverlayState())
        private set
    var autoModQueue by mutableStateOf(emptyList<AutoModHeldMessage>())
        private set
    var rateLimitsByChannel by mutableStateOf(emptyMap<String, ChatRateLimitState>())
        private set
    var eventSubSessionId by mutableStateOf<String?>(null)
        private set
    var eventSubTransportLimitReached by mutableStateOf(false)
        private set
    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set
    var connectionDetail by mutableStateOf<String?>(null)
        private set
    var connectionAttempt by mutableStateOf(0)
        private set
    var connectionErrorMessage by mutableStateOf<String?>(null)
        private set
    var authenticationRequired by mutableStateOf(false)
        private set

    /** Snapshots intentionally contain only the canonical bounded live window. */
    val snapshot: ChatRuntimeSnapshot
        get() = ChatRuntimeSnapshot(
            messagesByChannel = messagesByChannel,
            scrollPositionsByChannel = scrollPositionsByChannel,
            globalBadgeAssets = globalBadgeAssets,
            badgeAssetsByChannel = badgeAssetsByChannel,
            cheermoteAssetsByChannel = cheermoteAssetsByChannel,
            interactiveState = interactiveState,
            autoModQueue = autoModQueue,
            connectionStatus = connectionStatus,
            connectionDetail = connectionDetail,
            connectionAttempt = connectionAttempt,
            connectionErrorMessage = connectionErrorMessage,
            authenticationRequired = authenticationRequired,
        )

    init {
        replaceAll(initialSnapshot.messagesByChannel)
        restoreScrollPositions(initialSnapshot.scrollPositionsByChannel)
        replaceGlobalBadgeAssets(initialSnapshot.globalBadgeAssets)
        initialSnapshot.badgeAssetsByChannel.forEach { (channelId, assets) ->
            replaceChannelBadgeAssets(channelId, assets)
        }
        initialSnapshot.cheermoteAssetsByChannel.forEach { (channelId, assets) ->
            replaceChannelCheermoteAssets(channelId, assets)
        }
        interactiveState = initialSnapshot.interactiveState
        autoModQueue = normalizeAutoModQueue(initialSnapshot.autoModQueue)
        updateConnection(
            status = initialSnapshot.connectionStatus,
            detail = initialSnapshot.connectionDetail,
            attempt = initialSnapshot.connectionAttempt,
            errorMessage = initialSnapshot.connectionErrorMessage,
        )
        authenticationRequired = initialSnapshot.authenticationRequired
    }

    /** Returns the durable history overlay merged with the canonical 5,000-message live window. */
    fun messages(channelId: String): List<ChatMessage> {
        val normalizedChannelId = channelId.trim()
        val history = historyMessagesByChannel[normalizedChannelId].orEmpty()
        val live = messagesByChannel[normalizedChannelId].orEmpty()
        val cached = timelineCacheByChannel[normalizedChannelId]
        if (cached != null && cached.history === history && cached.live === live) {
            return cached.merged
        }
        val merged = mergeTimelineMessages(
            history = history,
            live = live,
            liveIds = liveMessageIdsByChannel[normalizedChannelId].orEmpty(),
        )
        timelineCacheByChannel[normalizedChannelId] = TimelineCacheEntry(
            history = history,
            live = live,
            merged = merged,
        )
        return merged
    }

    fun scrollPosition(channelId: String): ChatScrollPosition? =
        scrollPositionsByChannel[channelId.trim()]

    fun rateLimit(channelId: String): ChatRateLimitState? =
        rateLimitsByChannel[channelId.trim()]

    fun updateRateLimit(channelId: String, rateLimit: ChatRateLimitState) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedMessage = rateLimit.message.trim().takeIf(String::isNotEmpty)
            ?: "Twitch send rate limit"
        val normalized = rateLimit.copy(message = normalizedMessage)
        if (rateLimitsByChannel[normalizedChannelId] == normalized) return
        rateLimitsByChannel = rateLimitsByChannel + (normalizedChannelId to normalized)
    }

    fun clearRateLimit(channelId: String) {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty() || normalizedChannelId !in rateLimitsByChannel) return
        rateLimitsByChannel = rateLimitsByChannel - normalizedChannelId
    }

    fun updateScrollPosition(position: ChatScrollPosition) {
        val channelId = requireChannelId(position.channelId)
        require(position.firstVisibleItemIndex >= 0) {
            "Chat scroll index must not be negative"
        }
        require(position.firstVisibleItemScrollOffset >= 0) {
            "Chat scroll offset must not be negative"
        }
        val normalized = position.copy(
            channelId = channelId,
            anchorMessageId = position.anchorMessageId
                ?.trim()
                ?.takeIf(String::isNotEmpty),
        )
        if (scrollPositionsByChannel[channelId] == normalized) return
        scrollPositionsByChannel = scrollPositionsByChannel + (channelId to normalized)
    }

    fun clearScrollPosition(channelId: String) {
        val normalized = channelId.trim()
        if (normalized.isEmpty() || normalized !in scrollPositionsByChannel) return
        scrollPositionsByChannel = scrollPositionsByChannel - normalized
    }

    fun cheermoteAssets(channelId: String): Map<String, List<CheermoteAsset>> =
        cheermoteAssetsByChannel[channelId.trim()].orEmpty()

    fun badgeAsset(channelId: String, badge: ChatBadge): ChatBadgeAsset? {
        val key = chatBadgeAssetKey(badge.setId, badge.id)
        return badgeAssetsByChannel[channelId.trim()]?.get(key) ?: globalBadgeAssets[key]
    }

    fun replaceGlobalBadgeAssets(value: Map<String, ChatBadgeAsset>) {
        globalBadgeAssets = normalizeBadgeAssets(value)
    }

    fun replaceChannelBadgeAssets(channelId: String, value: Map<String, ChatBadgeAsset>) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedAssets = normalizeBadgeAssets(value)
        val current = badgeAssetsByChannel[normalizedChannelId]
        badgeAssetsByChannel = when {
            normalizedAssets.isEmpty() && current == null -> badgeAssetsByChannel
            normalizedAssets.isEmpty() -> badgeAssetsByChannel - normalizedChannelId
            current == normalizedAssets -> badgeAssetsByChannel
            else -> badgeAssetsByChannel + (normalizedChannelId to normalizedAssets)
        }
    }

    fun replaceChannelCheermoteAssets(
        channelId: String,
        value: Map<String, List<CheermoteAsset>>,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedAssets = normalizeCheermoteAssets(value)
        val current = cheermoteAssetsByChannel[normalizedChannelId]
        cheermoteAssetsByChannel = when {
            normalizedAssets.isEmpty() && current == null -> cheermoteAssetsByChannel
            normalizedAssets.isEmpty() -> cheermoteAssetsByChannel - normalizedChannelId
            current == normalizedAssets -> cheermoteAssetsByChannel
            else -> cheermoteAssetsByChannel + (normalizedChannelId to normalizedAssets)
        }
    }

    fun replaceChannelMessages(channelId: String, messages: List<ChatMessage>) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalized = normalizeMessages(normalizedChannelId, messages)
        messagesByChannel = if (normalized.isEmpty()) {
            messagesByChannel - normalizedChannelId
        } else {
            messagesByChannel + (normalizedChannelId to normalized)
        }
        rebuildLiveMessageIndexes(normalizedChannelId, normalized)
        historyMessagesByChannel = historyMessagesByChannel - normalizedChannelId
    }

    fun applyInteractive(event: InteractiveChatOverlayEvent) {
        interactiveState = InteractiveChatOverlayReducer.reduce(interactiveState, event)
    }

    fun autoModHeldMessages(channelId: String): List<AutoModHeldMessage> {
        val normalizedChannelId = channelId.trim()
        if (normalizedChannelId.isEmpty()) return emptyList()
        return autoModQueue
            .asSequence()
            .filter { message ->
                message.channelId == normalizedChannelId &&
                    message.status == AutoModMessageStatus.HELD
            }
            .sortedWith(compareBy(AutoModHeldMessage::heldAtMillis, AutoModHeldMessage::messageId))
            .toList()
    }

    fun applyAutoMod(message: AutoModHeldMessage) {
        require(message.channelId.isNotBlank()) { "AutoMod channel id must not be blank" }
        require(message.messageId.isNotBlank()) { "AutoMod message id must not be blank" }
        val existingIndex = autoModQueue.indexOfFirst { it.messageId == message.messageId }
        if (existingIndex >= 0) {
            val current = autoModQueue[existingIndex]
            val merged = mergeAutoModMessage(current, message)
            if (merged == current) return
            autoModQueue = if (merged.heldAtMillis == current.heldAtMillis) {
                autoModQueue.toMutableList().apply {
                    this[existingIndex] = merged
                }
            } else {
                normalizeAutoModQueue(
                    autoModQueue.toMutableList().apply {
                        this[existingIndex] = merged
                    },
                )
            }
            return
        }
        autoModQueue = insertAutoModMessage(autoModQueue, message)
    }

    fun markAutoModDecision(
        messageId: String,
        status: AutoModMessageStatus,
        moderatorId: String? = null,
        moderatorLogin: String? = null,
        moderatorName: String? = null,
    ): Boolean {
        require(status != AutoModMessageStatus.HELD) { "AutoMod local decision must be terminal" }
        val normalizedMessageId = messageId.trim()
        if (normalizedMessageId.isEmpty()) return false
        val messageIndex = autoModQueue.indexOfFirst { it.messageId == normalizedMessageId }
        if (messageIndex < 0) return false
        val current = autoModQueue[messageIndex]
        val updated = current.copy(
            status = status,
            decidedByUserId = moderatorId?.trim()?.takeIf(String::isNotEmpty),
            decidedByUserLogin = moderatorLogin?.trim()?.takeIf(String::isNotEmpty),
            decidedByUserName = moderatorName?.trim()?.takeIf(String::isNotEmpty),
        )
        if (updated != current) {
            autoModQueue = autoModQueue.toMutableList().apply {
                this[messageIndex] = updated
            }
        }
        return true
    }

    /**
     * Safety net for a lost or delayed automod.message.update. Explicit Twitch terminal updates
     * remain authoritative; this only prevents a stale HELD card from staying actionable forever.
     */
    fun expireStaleAutoModHolds(olderThanEpochMillis: Long): Int {
        if (autoModQueue.isEmpty()) return 0
        var updated: MutableList<AutoModHeldMessage>? = null
        var expiredCount = 0
        for (index in autoModQueue.indices) {
            val current = autoModQueue[index]
            val heldAtMillis = current.heldAtMillis
            if (
                current.status == AutoModMessageStatus.HELD &&
                heldAtMillis > 0L &&
                heldAtMillis <= olderThanEpochMillis
            ) {
                val target = updated ?: autoModQueue.toMutableList().also { updated = it }
                target[index] = current.copy(status = AutoModMessageStatus.EXPIRED)
                expiredCount += 1
            }
        }
        updated?.let { autoModQueue = it }
        return expiredCount
    }

    fun append(message: ChatMessage) {
        requireMessage(message)
        val existing = messagesByChannel[message.channelId].orEmpty()
        val hasExistingId = liveMessageIdsByChannel[message.channelId]?.contains(message.id) == true
        val hasServerEchoCandidate =
            liveServerMessageIdsByChannel[message.channelId]?.contains(message.id) == true
        if (!hasExistingId && !hasServerEchoCandidate) {
            val updated = appendToBoundedLiveWindow(existing, message)
            messagesByChannel = messagesByChannel + (message.channelId to updated)
            updateLiveMessageIndexesForAppend(
                channelId = message.channelId,
                existing = existing,
                message = message,
            )
            removeHistoryMessage(message.channelId, message.id)
            return
        }
        var serverEchoIndex = -1
        var existingIndex = -1
        for (index in existing.indices) {
            val candidate = existing[index]
            if (existingIndex < 0 && candidate.id == message.id) {
                existingIndex = index
            }
            if (
                serverEchoIndex < 0 &&
                candidate.id != message.id &&
                candidate.serverMessageId != null &&
                candidate.serverMessageId == message.id
            ) {
                serverEchoIndex = index
            }
            if (serverEchoIndex >= 0 && existingIndex >= 0) break
        }

        val updated = when {
            serverEchoIndex >= 0 -> {
                val pending = existing[serverEchoIndex]
                existing.toMutableList().apply {
                    this[serverEchoIndex] = message.copy(
                        outgoingState = OutgoingMessageState.SENT,
                        clientNonce = pending.clientNonce,
                        serverMessageId = message.id,
                    )
                }
            }
            existingIndex >= 0 && existing[existingIndex] == message -> existing
            existingIndex >= 0 -> existing.toMutableList().apply {
                this[existingIndex] = message
            }
            else -> appendToBoundedLiveWindow(existing, message)
        }
        if (updated !== existing) {
            messagesByChannel = messagesByChannel + (message.channelId to updated)
            rebuildLiveMessageIndexes(message.channelId, updated)
        }
        removeHistoryMessage(message.channelId, message.id)
    }

    private fun appendToBoundedLiveWindow(
        existing: List<ChatMessage>,
        message: ChatMessage,
    ): List<ChatMessage> {
        val retainedExisting = (MAX_MESSAGES_PER_CHANNEL - 1).coerceAtLeast(0)
        val startIndex = (existing.size - retainedExisting).coerceAtLeast(0)
        return ArrayList<ChatMessage>(
            minOf(MAX_MESSAGES_PER_CHANNEL, existing.size + 1),
        ).apply {
            for (index in startIndex until existing.size) {
                add(existing[index])
            }
            add(message)
        }
    }

    fun markOutgoingSending(channelId: String, localMessageId: String): Boolean =
        updateOutgoingMessage(channelId, localMessageId) { message ->
            message.copy(
                outgoingState = OutgoingMessageState.SENDING,
                outgoingError = null,
                serverMessageId = null,
            )
        }

    fun markOutgoingSent(
        channelId: String,
        localMessageId: String,
        serverMessageId: String,
    ): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedLocalMessageId = requireMessageId(localMessageId)
        val normalizedServerMessageId = requireMessageId(serverMessageId)
        val existing = messagesByChannel[normalizedChannelId].orEmpty()
        var localIndex = -1
        var serverEchoIndex = -1
        for (index in existing.indices) {
            val message = existing[index]
            if (localIndex < 0 && message.id == normalizedLocalMessageId) {
                localIndex = index
            }
            if (
                serverEchoIndex < 0 &&
                message.id == normalizedServerMessageId &&
                message.id != normalizedLocalMessageId
            ) {
                serverEchoIndex = index
            }
            if (localIndex >= 0 && serverEchoIndex >= 0) break
        }
        if (localIndex < 0) return false
        val local = existing[localIndex]
        var liveChanged = false
        if (serverEchoIndex >= 0) {
            val updated = ArrayList<ChatMessage>((existing.size - 1).coerceAtLeast(0))
            for (index in existing.indices) {
                when (index) {
                    localIndex -> Unit
                    serverEchoIndex -> updated += existing[index].copy(
                        outgoingState = OutgoingMessageState.SENT,
                        outgoingError = null,
                        clientNonce = local.clientNonce,
                        serverMessageId = normalizedServerMessageId,
                    )
                    else -> updated += existing[index]
                }
            }
            messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
            liveChanged = true
        } else {
            val updatedLocal = local.copy(
                outgoingState = OutgoingMessageState.SENT,
                outgoingError = null,
                serverMessageId = normalizedServerMessageId,
            )
            if (updatedLocal != local) {
                val updated = existing.toMutableList()
                updated[localIndex] = updatedLocal
                messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
                liveChanged = true
            }
        }
        if (liveChanged) {
            rebuildLiveMessageIndexes(
                normalizedChannelId,
                messagesByChannel[normalizedChannelId].orEmpty(),
            )
        }
        removeHistoryMessage(normalizedChannelId, normalizedServerMessageId)
        return true
    }

    fun markOutgoingFailed(
        channelId: String,
        localMessageId: String,
        errorMessage: String?,
    ): Boolean = updateOutgoingMessage(channelId, localMessageId) { message ->
        message.copy(
            outgoingState = OutgoingMessageState.FAILED,
            outgoingError = errorMessage?.trim()?.takeIf(String::isNotEmpty),
            serverMessageId = null,
        )
    }

    private fun updateOutgoingMessage(
        channelId: String,
        localMessageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedLocalMessageId = requireMessageId(localMessageId)
        val existing = messagesByChannel[normalizedChannelId].orEmpty()
        val messageIndex = existing.indexOfFirst { it.id == normalizedLocalMessageId }
        if (messageIndex < 0) return false
        val current = existing[messageIndex]
        val updatedMessage = transform(current)
        if (updatedMessage != current) {
            val updated = existing.toMutableList()
            updated[messageIndex] = updatedMessage
            messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
            rebuildLiveMessageIndexes(normalizedChannelId, updated)
        }
        return true
    }

    /** Adds durable history without consuming the canonical live-message capacity. */
    fun prependHistory(channelId: String, messages: List<ChatMessage>): Int {
        val normalizedChannelId = requireChannelId(channelId)
        if (messages.isEmpty()) return 0
        val liveIds = liveMessageIdsByChannel[normalizedChannelId].orEmpty()
        val existing = historyMessagesByChannel[normalizedChannelId].orEmpty()
        val merged = mergeHistoryMessages(
            channelId = normalizedChannelId,
            existing = existing,
            incoming = messages,
            liveIds = liveIds,
        )
        if (merged.messages != existing) {
            historyMessagesByChannel = if (merged.messages.isEmpty()) {
                historyMessagesByChannel - normalizedChannelId
            } else {
                historyMessagesByChannel + (normalizedChannelId to merged.messages)
            }
        }
        return merged.acceptedCount
    }

    fun markMessageDeleted(
        channelId: String,
        messageId: String,
        atMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedMessageId = requireMessageId(messageId)
        val live = messagesByChannel[normalizedChannelId].orEmpty().mapMessagesIfChanged { message ->
            if (
                message.id != normalizedMessageId ||
                !shouldApplyModeration(message, ModerationAction.DELETE)
            ) {
                message
            } else {
                deletedMessage(message, ModerationAction.DELETE, atMillis)
            }
        }
        val history = historyMessagesByChannel[normalizedChannelId].orEmpty()
            .mapMessagesIfChanged { message ->
                if (
                    message.id != normalizedMessageId ||
                    !shouldApplyModeration(message, ModerationAction.DELETE)
                ) {
                    message
                } else {
                    deletedMessage(message, ModerationAction.DELETE, atMillis)
                }
            }
        if (live != null) messagesByChannel = messagesByChannel + (normalizedChannelId to live)
        if (history != null) {
            historyMessagesByChannel = historyMessagesByChannel + (normalizedChannelId to history)
        }
        return live != null || history != null
    }

    fun markUserMessagesDeleted(
        channelId: String,
        userId: String,
        atMillis: Long = Clock.System.now().toEpochMilliseconds(),
        action: ModerationAction = ModerationAction.TIMEOUT,
    ): Int {
        require(action == ModerationAction.TIMEOUT || action == ModerationAction.BAN) {
            "User message clearing action must be TIMEOUT or BAN"
        }
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedUserId = userId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat user id must not be blank")
        val changedIds = hashSetOf<String>()
        val live = messagesByChannel[normalizedChannelId].orEmpty().mapMessagesIfChanged { message ->
            if (
                message.userId != normalizedUserId ||
                !shouldApplyModeration(message, action)
            ) {
                message
            } else {
                changedIds += message.id
                deletedMessage(message, action, atMillis)
            }
        }
        val history = historyMessagesByChannel[normalizedChannelId].orEmpty()
            .mapMessagesIfChanged { message ->
                if (
                    message.userId != normalizedUserId ||
                    !shouldApplyModeration(message, action)
                ) {
                    message
                } else {
                    changedIds += message.id
                    deletedMessage(message, action, atMillis)
                }
            }
        if (live != null) messagesByChannel = messagesByChannel + (normalizedChannelId to live)
        if (history != null) {
            historyMessagesByChannel = historyMessagesByChannel + (normalizedChannelId to history)
        }
        return changedIds.size
    }

    fun clearChannelMessages(channelId: String): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val existed = normalizedChannelId in messagesByChannel || normalizedChannelId in historyMessagesByChannel
        if (!existed) return false
        messagesByChannel = messagesByChannel - normalizedChannelId
        liveMessageIdsByChannel.remove(normalizedChannelId)
        liveServerMessageIdsByChannel.remove(normalizedChannelId)
        historyMessagesByChannel = historyMessagesByChannel - normalizedChannelId
        scrollPositionsByChannel = scrollPositionsByChannel - normalizedChannelId
        return true
    }

    fun removeChannel(channelId: String) {
        val normalized = channelId.trim()
        if (normalized.isEmpty()) return
        messagesByChannel = messagesByChannel - normalized
        liveMessageIdsByChannel.remove(normalized)
        liveServerMessageIdsByChannel.remove(normalized)
        historyMessagesByChannel = historyMessagesByChannel - normalized
        scrollPositionsByChannel = scrollPositionsByChannel - normalized
        badgeAssetsByChannel = badgeAssetsByChannel - normalized
        cheermoteAssetsByChannel = cheermoteAssetsByChannel - normalized
        autoModQueue = autoModQueue.filterNot { it.channelId == normalized }
        rateLimitsByChannel = rateLimitsByChannel - normalized
        timelineCacheByChannel.remove(normalized)
        applyInteractive(InteractiveChatOverlayEvent.ClearChannel(normalized))
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        messagesByChannel = messagesByChannel.retainAllowedKeys(allowed)
        liveMessageIdsByChannel.keys.retainAll(allowed)
        liveServerMessageIdsByChannel.keys.retainAll(allowed)
        historyMessagesByChannel = historyMessagesByChannel.retainAllowedKeys(allowed)
        scrollPositionsByChannel = scrollPositionsByChannel.retainAllowedKeys(allowed)
        badgeAssetsByChannel = badgeAssetsByChannel.retainAllowedKeys(allowed)
        cheermoteAssetsByChannel = cheermoteAssetsByChannel.retainAllowedKeys(allowed)
        if (autoModQueue.any { it.channelId !in allowed }) {
            autoModQueue = autoModQueue.filter { it.channelId in allowed }
        }
        rateLimitsByChannel = rateLimitsByChannel.retainAllowedKeys(allowed)
        timelineCacheByChannel.keys.retainAll(allowed)
        val interactiveChannels = interactiveState.pollsByChannel.keys +
            interactiveState.predictionsByChannel.keys + interactiveState.mutationsByChannel.keys
        interactiveChannels.filterNot(allowed::contains).forEach { channelId ->
            applyInteractive(InteractiveChatOverlayEvent.ClearChannel(channelId))
        }
    }

    fun updateEventSubSessionId(sessionId: String?) {
        eventSubSessionId = sessionId?.trim()?.takeIf(String::isNotEmpty)
    }

    fun clearEventSubSessionId(expectedSessionId: String? = null) {
        val expected = expectedSessionId?.trim()?.takeIf(String::isNotEmpty)
        if (expected == null || eventSubSessionId == expected) {
            eventSubSessionId = null
        }
    }

    fun markEventSubTransportLimitReached(errorMessage: String? = null) {
        eventSubTransportLimitReached = true
        connectionErrorMessage = errorMessage?.trim()?.takeIf(String::isNotEmpty)
            ?: connectionErrorMessage
    }

    fun updateConnection(
        status: ConnectionStatus,
        detail: String? = null,
        attempt: Int = 0,
        errorMessage: String? = null,
    ) {
        require(attempt >= 0) { "Connection attempt must not be negative" }
        if (authenticationRequired && status != ConnectionStatus.FAILED) return
        connectionStatus = status
        if (status == ConnectionStatus.CONNECTED) {
            eventSubTransportLimitReached = false
        }
        connectionDetail = detail?.trim()?.takeIf { it.isNotEmpty() }
        connectionAttempt = attempt
        connectionErrorMessage = errorMessage?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun markAuthenticationRequired(errorMessage: String? = null) {
        authenticationRequired = true
        updateConnection(
            status = ConnectionStatus.FAILED,
            attempt = connectionAttempt,
            errorMessage = errorMessage ?: "Twitch authentication must be refreshed",
        )
    }

    fun clearAuthenticationRequired() {
        authenticationRequired = false
    }

    fun clear() {
        messagesByChannel = emptyMap()
        liveMessageIdsByChannel.clear()
        liveServerMessageIdsByChannel.clear()
        historyMessagesByChannel = emptyMap()
        scrollPositionsByChannel = emptyMap()
        globalBadgeAssets = emptyMap()
        badgeAssetsByChannel = emptyMap()
        cheermoteAssetsByChannel = emptyMap()
        interactiveState = InteractiveChatOverlayState()
        autoModQueue = emptyList()
        rateLimitsByChannel = emptyMap()
        timelineCacheByChannel.clear()
        eventSubSessionId = null
        eventSubTransportLimitReached = false
        authenticationRequired = false
        updateConnection(ConnectionStatus.DISCONNECTED)
    }

    private fun restoreScrollPositions(
        value: Map<String, ChatScrollPosition>,
    ) {
        scrollPositionsByChannel = buildMap {
            value.values.forEach { position ->
                val channelId = position.channelId.trim()
                if (
                    channelId.isNotEmpty() &&
                    position.firstVisibleItemIndex >= 0 &&
                    position.firstVisibleItemScrollOffset >= 0
                ) {
                    put(
                        channelId,
                        position.copy(
                            channelId = channelId,
                            anchorMessageId = position.anchorMessageId
                                ?.trim()
                                ?.takeIf(String::isNotEmpty),
                        ),
                    )
                }
            }
        }
    }

    private fun replaceAll(value: Map<String, List<ChatMessage>>) {
        val normalized = linkedMapOf<String, List<ChatMessage>>()
        value.forEach { (channelId, messages) ->
            val id = requireChannelId(channelId)
            val channelMessages = normalizeMessages(id, messages)
            if (channelMessages.isNotEmpty()) normalized[id] = channelMessages
        }
        messagesByChannel = normalized
        rebuildAllLiveMessageIndexes()
        historyMessagesByChannel = emptyMap()
    }

    private fun rebuildAllLiveMessageIndexes() {
        liveMessageIdsByChannel.clear()
        liveServerMessageIdsByChannel.clear()
        messagesByChannel.forEach { (channelId, messages) ->
            rebuildLiveMessageIndexes(channelId, messages)
        }
    }

    private fun rebuildLiveMessageIndexes(
        channelId: String,
        messages: List<ChatMessage>,
    ) {
        if (messages.isEmpty()) {
            liveMessageIdsByChannel.remove(channelId)
            liveServerMessageIdsByChannel.remove(channelId)
            return
        }
        val ids = HashSet<String>(messages.size)
        val serverIds = HashSet<String>()
        messages.forEach { message ->
            ids += message.id
            message.serverMessageId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let(serverIds::add)
        }
        liveMessageIdsByChannel[channelId] = ids
        if (serverIds.isEmpty()) {
            liveServerMessageIdsByChannel.remove(channelId)
        } else {
            liveServerMessageIdsByChannel[channelId] = serverIds
        }
    }

    private fun updateLiveMessageIndexesForAppend(
        channelId: String,
        existing: List<ChatMessage>,
        message: ChatMessage,
    ) {
        val ids = liveMessageIdsByChannel.getOrPut(channelId) { hashSetOf() }
        val serverIds = liveServerMessageIdsByChannel.getOrPut(channelId) { hashSetOf() }
        val retainedExisting = (MAX_MESSAGES_PER_CHANNEL - 1).coerceAtLeast(0)
        val startIndex = (existing.size - retainedExisting).coerceAtLeast(0)
        for (index in 0 until startIndex) {
            val dropped = existing[index]
            ids.remove(dropped.id)
            dropped.serverMessageId?.let(serverIds::remove)
        }
        ids += message.id
        message.serverMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(serverIds::add)
        if (serverIds.isEmpty()) {
            liveServerMessageIdsByChannel.remove(channelId)
        }
    }

    private fun normalizeMessages(channelId: String, messages: List<ChatMessage>): List<ChatMessage> {
        val byId = linkedMapOf<String, ChatMessage>()
        messages.forEach { message ->
            requireMessage(message)
            require(message.channelId == channelId) {
                "Chat message channel does not match its runtime bucket"
            }
            byId[message.id] = message
        }
        return byId.values
            .sortedWith(MESSAGE_ORDER)
            .takeLast(MAX_MESSAGES_PER_CHANNEL)
    }

    private fun mergeHistoryMessages(
        channelId: String,
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
        liveIds: Set<String>,
    ): HistoryMergeResult {
        val byId = linkedMapOf<String, ChatMessage>()
        existing.forEach { message ->
            if (message.id !in liveIds) byId[message.id] = message
        }
        val incomingIds = linkedSetOf<String>()
        incoming.forEach { message ->
            requireMessage(message)
            require(message.channelId == channelId) {
                "Chat history message channel does not match its runtime bucket"
            }
            if (message.id !in liveIds) {
                byId[message.id] = message
                incomingIds += message.id
            }
        }
        val sorted = byId.values.sortedWith(MESSAGE_ORDER)
        val retained = if (sorted.size <= MAX_HISTORY_MESSAGES_PER_CHANNEL) {
            sorted
        } else {
            val required = sorted
                .filter { it.id in incomingIds }
                .takeLast(MAX_HISTORY_MESSAGES_PER_CHANNEL)
            val requiredIds = required.mapTo(hashSetOf(), ChatMessage::id)
            val remaining = sorted
                .asSequence()
                .filterNot { it.id in requiredIds }
                .toList()
                .takeLast((MAX_HISTORY_MESSAGES_PER_CHANNEL - required.size).coerceAtLeast(0))
            (remaining + required).sortedWith(MESSAGE_ORDER)
        }
        val retainedIds = retained.mapTo(hashSetOf(), ChatMessage::id)
        return HistoryMergeResult(
            messages = retained,
            acceptedCount = incomingIds.count(retainedIds::contains),
        )
    }

    private fun mergeTimelineMessages(
        history: List<ChatMessage>,
        live: List<ChatMessage>,
        liveIds: Set<String>,
    ): List<ChatMessage> {
        if (history.isEmpty()) return live
        if (live.isEmpty()) return history
        val historyWithoutLiveDuplicates = history.filterNot { it.id in liveIds }
        if (historyWithoutLiveDuplicates.isEmpty()) return live

        val result = ArrayList<ChatMessage>(historyWithoutLiveDuplicates.size + live.size)
        var historyIndex = 0
        var liveIndex = 0
        while (historyIndex < historyWithoutLiveDuplicates.size && liveIndex < live.size) {
            val historical = historyWithoutLiveDuplicates[historyIndex]
            val liveMessage = live[liveIndex]
            if (MESSAGE_ORDER.compare(historical, liveMessage) <= 0) {
                result += historical
                historyIndex += 1
            } else {
                result += liveMessage
                liveIndex += 1
            }
        }
        while (historyIndex < historyWithoutLiveDuplicates.size) {
            result += historyWithoutLiveDuplicates[historyIndex++]
        }
        while (liveIndex < live.size) {
            result += live[liveIndex++]
        }
        return result
    }

    private fun removeHistoryMessage(channelId: String, messageId: String) {
        val normalizedChannelId = channelId.trim()
        val existing = historyMessagesByChannel[normalizedChannelId].orEmpty()
        val messageIndex = existing.indexOfFirst { it.id == messageId }
        if (messageIndex < 0) return
        historyMessagesByChannel = if (existing.size == 1) {
            historyMessagesByChannel - normalizedChannelId
        } else {
            val updated = existing.toMutableList()
            updated.removeAt(messageIndex)
            historyMessagesByChannel + (normalizedChannelId to updated)
        }
    }

    private inline fun List<ChatMessage>.mapMessagesIfChanged(
        transform: (ChatMessage) -> ChatMessage,
    ): List<ChatMessage>? {
        var updated: MutableList<ChatMessage>? = null
        for (index in indices) {
            val current = this[index]
            val replacement = transform(current)
            if (replacement !== current) {
                val target = updated ?: toMutableList().also { updated = it }
                target[index] = replacement
            }
        }
        return updated
    }

    private fun <T> Map<String, T>.retainAllowedKeys(
        allowed: Set<String>,
    ): Map<String, T> = if (keys.all(allowed::contains)) {
        this
    } else {
        filterKeys(allowed::contains)
    }

    private fun deletedMessage(
        message: ChatMessage,
        action: ModerationAction,
        atMillis: Long,
    ): ChatMessage = message.copy(
        flags = message.flags.copy(isDeleted = true),
        moderation = ModerationState(action, atMillis = atMillis),
    )

    private fun shouldApplyModeration(
        message: ChatMessage,
        action: ModerationAction,
    ): Boolean = !message.isDeleted ||
        moderationPriority(action) > moderationPriority(message.moderation.action)

    private fun moderationPriority(action: ModerationAction?): Int = when (action) {
        ModerationAction.BAN -> 3
        ModerationAction.TIMEOUT -> 2
        ModerationAction.DELETE -> 1
        ModerationAction.CLEAR,
        null -> 0
    }

    private fun normalizeBadgeAssets(value: Map<String, ChatBadgeAsset>): Map<String, ChatBadgeAsset> =
        value.values
            .filter { asset -> asset.setId.isNotBlank() && asset.id.isNotBlank() }
            .associateBy(ChatBadgeAsset::key)

    private fun insertAutoModMessage(
        existing: List<AutoModHeldMessage>,
        message: AutoModHeldMessage,
    ): List<AutoModHeldMessage> {
        var low = 0
        var high = existing.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (AUTOMOD_ORDER.compare(existing[mid], message) <= 0) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        val insertionIndex = low
        if (existing.size >= MAX_AUTOMOD_QUEUE_ITEMS && insertionIndex >= MAX_AUTOMOD_QUEUE_ITEMS) {
            return existing
        }
        val targetSize = minOf(MAX_AUTOMOD_QUEUE_ITEMS, existing.size + 1)
        return ArrayList<AutoModHeldMessage>(targetSize).apply {
            for (index in 0 until targetSize) {
                when {
                    index < insertionIndex -> add(existing[index])
                    index == insertionIndex -> add(message)
                    else -> add(existing[index - 1])
                }
            }
        }
    }

    private fun normalizeAutoModQueue(value: List<AutoModHeldMessage>): List<AutoModHeldMessage> {
        val byId = linkedMapOf<String, AutoModHeldMessage>()
        value.forEach { message ->
            if (message.channelId.isNotBlank() && message.messageId.isNotBlank()) {
                byId[message.messageId] = message
            }
        }
        return byId.values
            .sortedWith(AUTOMOD_ORDER)
            .take(MAX_AUTOMOD_QUEUE_ITEMS)
    }

    private fun mergeAutoModMessage(
        current: AutoModHeldMessage,
        incoming: AutoModHeldMessage,
    ): AutoModHeldMessage = current.copy(
        channelId = incoming.channelId.ifBlank { current.channelId },
        channelLogin = incoming.channelLogin.ifBlank { current.channelLogin },
        channelName = incoming.channelName.ifBlank { current.channelName },
        userId = incoming.userId.ifBlank { current.userId },
        userLogin = incoming.userLogin.ifBlank { current.userLogin },
        userName = incoming.userName.ifBlank { current.userName },
        text = incoming.text.ifBlank { current.text },
        fragments = incoming.fragments.ifEmpty { current.fragments },
        reason = incoming.reason ?: current.reason,
        category = incoming.category ?: current.category,
        level = incoming.level ?: current.level,
        boundaries = incoming.boundaries.ifEmpty { current.boundaries },
        heldAt = incoming.heldAt.ifBlank { current.heldAt },
        status = incoming.status,
        decidedByUserId = incoming.decidedByUserId ?: current.decidedByUserId,
        decidedByUserLogin = incoming.decidedByUserLogin ?: current.decidedByUserLogin,
        decidedByUserName = incoming.decidedByUserName ?: current.decidedByUserName,
    )

    private fun normalizeCheermoteAssets(
        value: Map<String, List<CheermoteAsset>>,
    ): Map<String, List<CheermoteAsset>> = value.values
        .asSequence()
        .flatten()
        .filter { asset ->
            asset.prefix.isNotBlank() &&
                asset.minBits >= 0 &&
                (!asset.staticImageUrl.isNullOrBlank() || !asset.animatedImageUrl.isNullOrBlank())
        }
        .groupBy { asset -> asset.prefix.trim().lowercase() }
        .mapValues { (_, assets) ->
            assets
                .distinctBy(CheermoteAsset::minBits)
                .sortedBy(CheermoteAsset::minBits)
        }

    private fun requireMessage(message: ChatMessage) {
        require(message.id.isNotBlank()) { "Chat message id must not be blank" }
        require(message.channelId.isNotBlank()) { "Chat message channel id must not be blank" }
    }

    private fun requireMessageId(value: String): String =
        value.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat message id must not be blank")

    private fun requireChannelId(value: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Chat channel id must not be blank")

    private data class TimelineCacheEntry(
        val history: List<ChatMessage>,
        val live: List<ChatMessage>,
        val merged: List<ChatMessage>,
    )

    private data class HistoryMergeResult(
        val messages: List<ChatMessage>,
        val acceptedCount: Int,
    )

    private companion object {
        val MESSAGE_ORDER = compareBy<ChatMessage>(ChatMessage::timestampMillis, ChatMessage::id)
        val AUTOMOD_ORDER = compareByDescending<AutoModHeldMessage> { it.heldAtMillis }
            .thenBy(AutoModHeldMessage::messageId)
        const val MAX_MESSAGES_PER_CHANNEL = 5_000
        const val MAX_HISTORY_MESSAGES_PER_CHANNEL = 5_000
        const val MAX_AUTOMOD_QUEUE_ITEMS = 200
    }
}
