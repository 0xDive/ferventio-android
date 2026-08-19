package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatMessage
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
    val globalBadgeAssets: Map<String, ChatBadgeAsset> = emptyMap(),
    val badgeAssetsByChannel: Map<String, Map<String, ChatBadgeAsset>> = emptyMap(),
    val cheermoteAssetsByChannel: Map<String, Map<String, List<CheermoteAsset>>> = emptyMap(),
    val interactiveState: InteractiveChatOverlayState = InteractiveChatOverlayState(),
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
    private var historyMessagesByChannel by mutableStateOf(emptyMap<String, List<ChatMessage>>())
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
            globalBadgeAssets = globalBadgeAssets,
            badgeAssetsByChannel = badgeAssetsByChannel,
            cheermoteAssetsByChannel = cheermoteAssetsByChannel,
            interactiveState = interactiveState,
            connectionStatus = connectionStatus,
            connectionDetail = connectionDetail,
            connectionAttempt = connectionAttempt,
            connectionErrorMessage = connectionErrorMessage,
            authenticationRequired = authenticationRequired,
        )

    init {
        replaceAll(initialSnapshot.messagesByChannel)
        replaceGlobalBadgeAssets(initialSnapshot.globalBadgeAssets)
        initialSnapshot.badgeAssetsByChannel.forEach { (channelId, assets) ->
            replaceChannelBadgeAssets(channelId, assets)
        }
        initialSnapshot.cheermoteAssetsByChannel.forEach { (channelId, assets) ->
            replaceChannelCheermoteAssets(channelId, assets)
        }
        interactiveState = initialSnapshot.interactiveState
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
        return mergeTimelineMessages(
            history = historyMessagesByChannel[normalizedChannelId].orEmpty(),
            live = messagesByChannel[normalizedChannelId].orEmpty(),
        )
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
        badgeAssetsByChannel = if (normalizedAssets.isEmpty()) {
            badgeAssetsByChannel - normalizedChannelId
        } else {
            badgeAssetsByChannel + (normalizedChannelId to normalizedAssets)
        }
    }

    fun replaceChannelCheermoteAssets(
        channelId: String,
        value: Map<String, List<CheermoteAsset>>,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedAssets = normalizeCheermoteAssets(value)
        cheermoteAssetsByChannel = if (normalizedAssets.isEmpty()) {
            cheermoteAssetsByChannel - normalizedChannelId
        } else {
            cheermoteAssetsByChannel + (normalizedChannelId to normalizedAssets)
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
        historyMessagesByChannel = historyMessagesByChannel - normalizedChannelId
    }

    fun applyInteractive(event: InteractiveChatOverlayEvent) {
        interactiveState = InteractiveChatOverlayReducer.reduce(interactiveState, event)
    }

    fun append(message: ChatMessage) {
        requireMessage(message)
        val existing = messagesByChannel[message.channelId].orEmpty()
        val serverEchoIndex = existing.indexOfFirst { pending ->
            pending.id != message.id &&
                pending.serverMessageId != null &&
                pending.serverMessageId == message.id
        }
        val existingIndex = existing.indexOfFirst { it.id == message.id }
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
            existingIndex >= 0 -> existing.toMutableList().apply {
                this[existingIndex] = message
            }
            else -> (existing + message).takeLast(MAX_MESSAGES_PER_CHANNEL).toMutableList()
        }
        messagesByChannel = messagesByChannel + (message.channelId to updated)
        removeHistoryMessage(message.channelId, message.id)
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
        val local = existing.firstOrNull { it.id == normalizedLocalMessageId } ?: return false
        val serverEcho = existing.firstOrNull {
            it.id == normalizedServerMessageId && it.id != normalizedLocalMessageId
        }
        val updated = if (serverEcho != null) {
            existing.mapNotNull { message ->
                when (message.id) {
                    normalizedLocalMessageId -> null
                    normalizedServerMessageId -> message.copy(
                        outgoingState = OutgoingMessageState.SENT,
                        outgoingError = null,
                        clientNonce = local.clientNonce,
                        serverMessageId = normalizedServerMessageId,
                    )
                    else -> message
                }
            }
        } else {
            existing.map { message ->
                if (message.id == normalizedLocalMessageId) {
                    message.copy(
                        outgoingState = OutgoingMessageState.SENT,
                        outgoingError = null,
                        serverMessageId = normalizedServerMessageId,
                    )
                } else {
                    message
                }
            }
        }
        messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
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
        var changed = false
        val updated = messagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (message.id == normalizedLocalMessageId) {
                changed = true
                transform(message)
            } else {
                message
            }
        }
        if (changed) messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
        return changed
    }

    /** Adds durable history without consuming the canonical live-message capacity. */
    fun prependHistory(channelId: String, messages: List<ChatMessage>): Int {
        val normalizedChannelId = requireChannelId(channelId)
        if (messages.isEmpty()) return 0
        val liveIds = messagesByChannel[normalizedChannelId].orEmpty()
            .mapTo(hashSetOf(), ChatMessage::id)
        val existing = historyMessagesByChannel[normalizedChannelId].orEmpty()
        val merged = mergeHistoryMessages(
            channelId = normalizedChannelId,
            existing = existing,
            incoming = messages,
            liveIds = liveIds,
        )
        historyMessagesByChannel = if (merged.messages.isEmpty()) {
            historyMessagesByChannel - normalizedChannelId
        } else {
            historyMessagesByChannel + (normalizedChannelId to merged.messages)
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
        var liveChanged = false
        var historyChanged = false
        val live = messagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (
                message.id != normalizedMessageId ||
                !shouldApplyModeration(message, ModerationAction.DELETE)
            ) {
                message
            } else {
                liveChanged = true
                deletedMessage(message, ModerationAction.DELETE, atMillis)
            }
        }
        val history = historyMessagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (
                message.id != normalizedMessageId ||
                !shouldApplyModeration(message, ModerationAction.DELETE)
            ) {
                message
            } else {
                historyChanged = true
                deletedMessage(message, ModerationAction.DELETE, atMillis)
            }
        }
        if (liveChanged) messagesByChannel = messagesByChannel + (normalizedChannelId to live)
        if (historyChanged) {
            historyMessagesByChannel = historyMessagesByChannel + (normalizedChannelId to history)
        }
        return liveChanged || historyChanged
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
        var liveChanged = false
        var historyChanged = false
        val live = messagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (
                message.userId != normalizedUserId ||
                !shouldApplyModeration(message, action)
            ) {
                message
            } else {
                liveChanged = true
                changedIds += message.id
                deletedMessage(message, action, atMillis)
            }
        }
        val history = historyMessagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (
                message.userId != normalizedUserId ||
                !shouldApplyModeration(message, action)
            ) {
                message
            } else {
                historyChanged = true
                changedIds += message.id
                deletedMessage(message, action, atMillis)
            }
        }
        if (liveChanged) messagesByChannel = messagesByChannel + (normalizedChannelId to live)
        if (historyChanged) {
            historyMessagesByChannel = historyMessagesByChannel + (normalizedChannelId to history)
        }
        return changedIds.size
    }

    fun clearChannelMessages(channelId: String): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val existed = normalizedChannelId in messagesByChannel || normalizedChannelId in historyMessagesByChannel
        if (!existed) return false
        messagesByChannel = messagesByChannel - normalizedChannelId
        historyMessagesByChannel = historyMessagesByChannel - normalizedChannelId
        return true
    }

    fun removeChannel(channelId: String) {
        val normalized = channelId.trim()
        if (normalized.isEmpty()) return
        messagesByChannel = messagesByChannel - normalized
        historyMessagesByChannel = historyMessagesByChannel - normalized
        badgeAssetsByChannel = badgeAssetsByChannel - normalized
        cheermoteAssetsByChannel = cheermoteAssetsByChannel - normalized
        applyInteractive(InteractiveChatOverlayEvent.ClearChannel(normalized))
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        messagesByChannel = messagesByChannel.filterKeys(allowed::contains)
        historyMessagesByChannel = historyMessagesByChannel.filterKeys(allowed::contains)
        badgeAssetsByChannel = badgeAssetsByChannel.filterKeys(allowed::contains)
        cheermoteAssetsByChannel = cheermoteAssetsByChannel.filterKeys(allowed::contains)
        val interactiveChannels = interactiveState.pollsByChannel.keys +
            interactiveState.predictionsByChannel.keys + interactiveState.mutationsByChannel.keys
        interactiveChannels.filterNot(allowed::contains).forEach { channelId ->
            applyInteractive(InteractiveChatOverlayEvent.ClearChannel(channelId))
        }
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
        historyMessagesByChannel = emptyMap()
        globalBadgeAssets = emptyMap()
        badgeAssetsByChannel = emptyMap()
        cheermoteAssetsByChannel = emptyMap()
        interactiveState = InteractiveChatOverlayState()
        authenticationRequired = false
        updateConnection(ConnectionStatus.DISCONNECTED)
    }

    private fun replaceAll(value: Map<String, List<ChatMessage>>) {
        val normalized = linkedMapOf<String, List<ChatMessage>>()
        value.forEach { (channelId, messages) ->
            val id = requireChannelId(channelId)
            val channelMessages = normalizeMessages(id, messages)
            if (channelMessages.isNotEmpty()) normalized[id] = channelMessages
        }
        messagesByChannel = normalized
        historyMessagesByChannel = emptyMap()
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
    ): List<ChatMessage> {
        if (history.isEmpty()) return live
        if (live.isEmpty()) return history
        val liveIds = live.mapTo(hashSetOf(), ChatMessage::id)
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
        if (existing.none { it.id == messageId }) return
        val updated = existing.filterNot { it.id == messageId }
        historyMessagesByChannel = if (updated.isEmpty()) {
            historyMessagesByChannel - normalizedChannelId
        } else {
            historyMessagesByChannel + (normalizedChannelId to updated)
        }
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

    private fun normalizeCheermoteAssets(
        value: Map<String, List<CheermoteAsset>>,
    ): Map<String, List<CheermoteAsset>> = value.values
        .flatten()
        .asSequence()
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

    private data class HistoryMergeResult(
        val messages: List<ChatMessage>,
        val acceptedCount: Int,
    )

    private companion object {
        val MESSAGE_ORDER = compareBy<ChatMessage>(ChatMessage::timestampMillis, ChatMessage::id)
        const val MAX_MESSAGES_PER_CHANNEL = 5_000
        const val MAX_HISTORY_MESSAGES_PER_CHANNEL = 5_000
    }
}
