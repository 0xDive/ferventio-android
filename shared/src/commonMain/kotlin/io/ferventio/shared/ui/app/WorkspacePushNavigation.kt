package io.ferventio.shared.ui.app

import io.ferventio.app.domain.ChatChannel
import io.ferventio.shared.push.PushChannelReference
import io.ferventio.shared.push.PushNavigationTarget

internal sealed interface WorkspacePushNavigationAction {
    data object OpenSettings : WorkspacePushNavigationAction

    data class OpenMentions(
        val channelId: String,
    ) : WorkspacePushNavigationAction

    data class OpenModeration(
        val channelId: String,
    ) : WorkspacePushNavigationAction

    data class OpenMessage(
        val channelId: String,
        val messageId: String,
    ) : WorkspacePushNavigationAction

    data class SelectChannel(
        val channelId: String,
    ) : WorkspacePushNavigationAction
}

internal fun resolveWorkspacePushNavigationAction(
    target: PushNavigationTarget,
    channels: List<ChatChannel>,
): WorkspacePushNavigationAction? = when (target) {
    PushNavigationTarget.PushSettings -> WorkspacePushNavigationAction.OpenSettings

    is PushNavigationTarget.Mentions -> resolvePushChannelId(target.channel, channels)?.let { channelId ->
        WorkspacePushNavigationAction.OpenMentions(channelId)
    }

    is PushNavigationTarget.Moderation -> resolvePushChannelId(target.channel, channels)?.let { channelId ->
        WorkspacePushNavigationAction.OpenModeration(channelId)
    }

    is PushNavigationTarget.Message -> resolvePushChannelId(target.channel, channels)?.let { channelId ->
        WorkspacePushNavigationAction.OpenMessage(channelId, target.messageId)
    }

    is PushNavigationTarget.Channel -> resolvePushChannelId(target.channel, channels)?.let { channelId ->
        WorkspacePushNavigationAction.SelectChannel(channelId)
    }
}

private fun resolvePushChannelId(
    reference: PushChannelReference,
    channels: List<ChatChannel>,
): String? {
    val id = reference.id?.trim()?.takeIf(String::isNotEmpty)
    if (id != null) {
        channels.firstOrNull { channel -> channel.id == id }?.let { return it.id }
    }

    val login = reference.login
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return channels.firstOrNull { channel -> channel.login.trim().lowercase() == login }?.id
}
