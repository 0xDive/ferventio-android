package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ChatBadge
import io.ferventio.app.domain.ChatBadgeAsset
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.ModerationAction
import io.ferventio.app.domain.ModerationState
import io.ferventio.app.domain.chatBadgeAssetKey
import kotlin.time.Clock

data class ChatRuntimeSnapshot(
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val globalBadgeAssets: Map<String, ChatBadgeAsset> = emptyMap(),
    val badgeAssetsByChannel: Map<String, Map<String, ChatBadgeAsset>> = emptyMap(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String? = null,
    val connectionAttempt: Int = 0,
    val connectionErrorMessage: String? = null,
    val authenticationRequired: Boolean = false,
)

/**
 * Platform-neutral live-chat state shared by Android/iOS UI and future KMP EventSub transport.
 *
 * Message ordering and the 5k per-channel memory window match the current Android controller.
 */
class ChatRuntimeStateHolder(
    initialSnapshot: ChatRuntimeSnapshot = ChatRuntimeSnapshot(),
) {
    var messagesByChannel by mutableStateOf(emptyMap<String, List<ChatMessage>>())
        private set

    var globalBadgeAssets by mutableStateOf(emptyMap<String, ChatBadgeAsset>())
        private set

    var badgeAssetsByChannel by mutableStateOf(emptyMap<String, Map<String, ChatBadgeAsset>>())
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

    val snapshot: ChatRuntimeSnapshot
        get() = ChatRuntimeSnapshot(
            messagesByChannel = messagesByChannel,
            globalBadgeAssets = globalBadgeAssets,
            badgeAssetsByChannel = badgeAssetsByChannel,
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
        updateConnection(
            status = initialSnapshot.connectionStatus,
            detail = initialSnapshot.connectionDetail,
            attempt = initialSnapshot.connectionAttempt,
            errorMessage = initialSnapshot.connectionErrorMessage,
        )
        authenticationRequired = initialSnapshot.authenticationRequired
    }

    fun messages(channelId: String): List<ChatMessage> =
        messagesByChannel[channelId.trim()].orEmpty()

    fun badgeAsset(
        channelId: String,
        badge: ChatBadge,
    ): ChatBadgeAsset? {
        val key = chatBadgeAssetKey(badge.setId, badge.id)
        return badgeAssetsByChannel[channelId.trim()]?.get(key)
            ?: globalBadgeAssets[key]
    }

    fun replaceGlobalBadgeAssets(value: Map<String, ChatBadgeAsset>) {
        globalBadgeAssets = normalizeBadgeAssets(value)
    }

    fun replaceChannelBadgeAssets(
        channelId: String,
        value: Map<String, ChatBadgeAsset>,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedAssets = normalizeBadgeAssets(value)
        badgeAssetsByChannel = if (normalizedAssets.isEmpty()) {
            badgeAssetsByChannel - normalizedChannelId
        } else {
            badgeAssetsByChannel + (normalizedChannelId to normalizedAssets)
        }
    }

    fun replaceChannelMessages(
        channelId: String,
        messages: List<ChatMessage>,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val normalized = normalizeMessages(normalizedChannelId, messages)
        messagesByChannel = if (normalized.isEmpty()) {
            messagesByChannel - normalizedChannelId
        } else {
            messagesByChannel + (normalizedChannelId to normalized)
        }
    }

    fun append(message: ChatMessage) {
        requireMessage(message)
        val existing = messagesByChannel[message.channelId].orEmpty()
        val existingIndex = existing.indexOfFirst { it.id == message.id }
        val updated = if (existingIndex >= 0) {
            existing.toMutableList().apply { this[existingIndex] = message }
        } else {
            (existing + message).takeLast(MAX_MESSAGES_PER_CHANNEL).toMutableList()
        }
        messagesByChannel = messagesByChannel + (message.channelId to updated)
    }

    fun prependHistory(
        channelId: String,
        messages: List<ChatMessage>,
    ) {
        val normalizedChannelId = requireChannelId(channelId)
        val existing = messagesByChannel[normalizedChannelId].orEmpty()
        replaceChannelMessages(
            channelId = normalizedChannelId,
            messages = messages + existing,
        )
    }

    fun markMessageDeleted(
        channelId: String,
        messageId: String,
        atMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedMessageId = messageId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat message id must not be blank")
        var changed = false
        val updated = messagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (message.id != normalizedMessageId) {
                message
            } else {
                changed = true
                message.copy(
                    flags = message.flags.copy(isDeleted = true),
                    moderation = ModerationState(
                        action = ModerationAction.DELETE,
                        atMillis = atMillis,
                    ),
                )
            }
        }
        if (changed) {
            messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
        }
        return changed
    }

    fun markUserMessagesDeleted(
        channelId: String,
        userId: String,
        atMillis: Long = Clock.System.now().toEpochMilliseconds(),
    ): Int {
        val normalizedChannelId = requireChannelId(channelId)
        val normalizedUserId = userId.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Chat user id must not be blank")
        var changed = 0
        val updated = messagesByChannel[normalizedChannelId].orEmpty().map { message ->
            if (message.userId != normalizedUserId) {
                message
            } else {
                changed += 1
                message.copy(
                    flags = message.flags.copy(isDeleted = true),
                    moderation = ModerationState(
                        action = ModerationAction.TIMEOUT,
                        atMillis = atMillis,
                    ),
                )
            }
        }
        if (changed > 0) {
            messagesByChannel = messagesByChannel + (normalizedChannelId to updated)
        }
        return changed
    }

    fun clearChannelMessages(channelId: String): Boolean {
        val normalizedChannelId = requireChannelId(channelId)
        if (normalizedChannelId !in messagesByChannel) return false
        messagesByChannel = messagesByChannel - normalizedChannelId
        return true
    }

    fun removeChannel(channelId: String) {
        val normalized = channelId.trim()
        if (normalized.isEmpty()) return
        messagesByChannel = messagesByChannel - normalized
        badgeAssetsByChannel = badgeAssetsByChannel - normalized
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        messagesByChannel = messagesByChannel.filterKeys(allowed::contains)
        badgeAssetsByChannel = badgeAssetsByChannel.filterKeys(allowed::contains)
    }

    fun updateConnection(
        status: ConnectionStatus,
        detail: String? = null,
        attempt: Int = 0,
        errorMessage: String? = null,
    ) {
        require(attempt >= 0) { "Connection attempt must not be negative" }
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
        globalBadgeAssets = emptyMap()
        badgeAssetsByChannel = emptyMap()
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
    }

    private fun normalizeMessages(
        channelId: String,
        messages: List<ChatMessage>,
    ): List<ChatMessage> {
        val byId = linkedMapOf<String, ChatMessage>()
        messages.forEach { message ->
            requireMessage(message)
            require(message.channelId == channelId) {
                "Chat message channel does not match its runtime bucket"
            }
            byId[message.id] = message
        }
        return byId.values
            .sortedWith(compareBy(ChatMessage::timestampMillis, ChatMessage::id))
            .takeLast(MAX_MESSAGES_PER_CHANNEL)
    }

    private fun normalizeBadgeAssets(value: Map<String, ChatBadgeAsset>): Map<String, ChatBadgeAsset> =
        value.values
            .filter { asset -> asset.setId.isNotBlank() && asset.id.isNotBlank() }
            .associateBy(ChatBadgeAsset::key)

    private fun requireMessage(message: ChatMessage) {
        require(message.id.isNotBlank()) { "Chat message id must not be blank" }
        require(message.channelId.isNotBlank()) { "Chat message channel id must not be blank" }
    }

    private fun requireChannelId(value: String): String =
        value.trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Chat channel id must not be blank")

    private companion object {
        const val MAX_MESSAGES_PER_CHANNEL = 5_000
    }
}
