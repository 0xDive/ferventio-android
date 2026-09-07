package io.ferventio.shared.ui.moderation

import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.chat.ChatRuntimeStateHolder

internal fun canPreviewNuke(
    channelId: String,
    moderatorChannelIds: Set<String>,
): Boolean = channelId.isNotBlank() && channelId in moderatorChannelIds

/**
 * Nuke is a live moderation action. Durable history loaded for search/paging must never add stale
 * users or messages to the destructive target set.
 */
internal fun nukePreviewMessages(
    chat: ChatRuntimeStateHolder,
    channelId: String,
): List<ChatMessage> = chat.messagesByChannel[channelId.trim()].orEmpty()
