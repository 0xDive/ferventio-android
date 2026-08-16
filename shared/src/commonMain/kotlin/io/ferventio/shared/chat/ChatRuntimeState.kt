package io.ferventio.shared.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConnectionStatus

data class ChatRuntimeSnapshot(
    val messagesByChannel: Map<String, List<ChatMessage>> = emptyMap(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionDetail: String? = null,
    val connectionAttempt: Int = 0,
    val connectionErrorMessage: String? = null,
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

    var connectionStatus by mutableStateOf(ConnectionStatus.DISCONNECTED)
        private set

    var connectionDetail by mutableStateOf<String?>(null)
        private set

    var connectionAttempt by mutableStateOf(0)
        private set

    var connectionErrorMessage by mutableStateOf<String?>(null)
        private set

    val snapshot: ChatRuntimeSnapshot
        get() = ChatRuntimeSnapshot(
            messagesByChannel = messagesByChannel,
            connectionStatus = connectionStatus,
            connectionDetail = connectionDetail,
            connectionAttempt = connectionAttempt,
            connectionErrorMessage = connectionErrorMessage,
        )

    init {
        replaceAll(initialSnapshot.messagesByChannel)
        updateConnection(
            status = initialSnapshot.connectionStatus,
            detail = initialSnapshot.connectionDetail,
            attempt = initialSnapshot.connectionAttempt,
            errorMessage = initialSnapshot.connectionErrorMessage,
        )
    }

    fun messages(channelId: String): List<ChatMessage> =
        messagesByChannel[channelId.trim()].orEmpty()

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

    fun removeChannel(channelId: String) {
        val normalized = channelId.trim()
        if (normalized.isEmpty()) return
        messagesByChannel = messagesByChannel - normalized
    }

    fun retainChannels(channelIds: Iterable<String>) {
        val allowed = channelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        messagesByChannel = messagesByChannel.filterKeys(allowed::contains)
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

    fun clear() {
        messagesByChannel = emptyMap()
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
