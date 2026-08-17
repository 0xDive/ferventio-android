package io.ferventio.shared.ui.moderation

internal fun canPreviewNuke(
    channelId: String,
    moderatorChannelIds: Set<String>,
): Boolean = channelId.isNotBlank() && channelId in moderatorChannelIds
