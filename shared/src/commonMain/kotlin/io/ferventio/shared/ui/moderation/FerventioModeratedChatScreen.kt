package io.ferventio.shared.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConfirmedModerationCommand
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.chat.TwitchChatManagementClient
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_command_error_title
import io.ferventio.shared.generated.resources.chat_command_moderator_required
import io.ferventio.shared.generated.resources.nuke_preview_action
import io.ferventio.shared.generated.resources.quick_moderation_auth_required
import io.ferventio.shared.generated.resources.quick_moderation_error_title
import io.ferventio.shared.generated.resources.quick_moderation_failed
import io.ferventio.shared.generated.resources.quick_moderation_ok
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.chat.FerventioChatTimeline
import io.ferventio.shared.ui.chat.InteractiveChatOverlayCards
import io.ferventio.shared.ui.chat.SharedChatComposer
import io.ferventio.shared.ui.chat.SharedMessageActionsSheet
import io.ferventio.shared.ui.chat.SharedReplyThreadSheet
import io.ferventio.shared.ui.chat.rememberThirdPartyEmoteCatalog
import io.ferventio.shared.ui.chat.resolveSharedReplyThreadMessages
import io.ferventio.shared.ui.chat.rememberTwitchUserEmoteCatalog
import io.ferventio.shared.ui.user.SharedUserCardSheet
import io.ferventio.shared.ui.user.projectLocalUserCard
import io.ferventio.shared.ui.user.resolveLocalUserRole
import io.ferventio.shared.user.TwitchUserCardClient
import io.ferventio.shared.user.withRemoteEnrichment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@Composable
fun FerventioModeratedChatScreen(
    channel: ChatChannel,
    moderatorChannelIds: Set<String>,
    filterQuery: String = "",
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val userCardClient = remember { TwitchUserCardClient() }
    val chatManagementClient = remember { TwitchChatManagementClient() }
    val canModerateChannel = canPreviewNuke(channel.id, moderatorChannelIds)
    val canWriteChat = runtime.authentication.state.authentication != null
    val authenticationRequiredText = stringResource(Res.string.quick_moderation_auth_required)
    val moderatorRequiredText = stringResource(Res.string.chat_command_moderator_required)
    val preferences = runtime.settings.preferences
    val thirdPartyEmotes = rememberThirdPartyEmoteCatalog(
        channelId = channel.id,
        betterTtvEnabled = preferences.betterTtvEnabled,
        frankerFaceZEnabled = preferences.frankerFaceZEnabled,
        sevenTvEnabled = preferences.sevenTvEnabled,
    )
    val twitchEmotes = rememberTwitchUserEmoteCatalog(
        authentication = runtime.authentication.state.authentication,
        broadcasterId = channel.id,
    )
    val composerEmotes = remember(twitchEmotes, thirdPartyEmotes) {
        twitchEmotes + thirdPartyEmotes.values
    }
    DisposableEffect(userCardClient, chatManagementClient) {
        onDispose {
            userCardClient.close()
            chatManagementClient.close()
        }
    }
    var showNukePreview by remember(channel.id) { mutableStateOf(false) }
    var selectedUserMessage by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var commandUserCardData by remember(channel.id) { mutableStateOf<UserCardData?>(null) }
    var commandError by remember(channel.id) { mutableStateOf<String?>(null) }
    var replyTarget by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var messageActionsTarget by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var threadTarget by remember(channel.id) { mutableStateOf<ChatMessage?>(null) }
    var quickModerationError by remember(channel.id) { mutableStateOf<String?>(null) }

    fun deleteMessage(message: ChatMessage) {
        val authentication = runtime.authentication.state.authentication
        if (authentication == null) {
            quickModerationError = authenticationRequiredText
            return
        }
        val availability = quickModerationAvailability(
            message = message,
            ownUserId = authentication.accessLease?.session?.userId,
            canModerate = canModerateChannel,
            preferences = runtime.localUiPreferences.preferences,
        )
        if (!availability.canDelete) return
        val messageId = message.serverMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: message.id
        scope.launch {
            try {
                runtime.moderation.deleteChatMessage(
                    authentication = authentication,
                    broadcasterId = channel.id,
                    messageId = messageId,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                quickModerationError = error.message.orEmpty().ifBlank { "Twitch error" }
            }
        }
    }

    fun openUserCardCommand(login: String): Boolean {
        val authentication = runtime.authentication.state.authentication
        if (authentication == null) {
            commandError = authenticationRequiredText
            return true
        }
        selectedUserMessage = null
        scope.launch {
            try {
                val remote = userCardClient.enrich(
                    authentication = authentication,
                    userId = "",
                    userLogin = login,
                    channelLogin = channel.login,
                )
                val user = remote.user ?: error("Twitch user @$login was not found")
                val recentMessages = runtime.chat.messages(channel.id)
                    .filter { message ->
                        (user.id.isNotBlank() && message.userId == user.id) ||
                            message.userLogin.equals(user.login, ignoreCase = true)
                    }
                    .takeLast(20)
                val base = UserCardData(
                    channelId = channel.id,
                    user = user,
                    role = resolveLocalUserRole(recentMessages.flatMap(ChatMessage::badges)),
                    canModerate = canModerateChannel,
                    recentMessages = recentMessages,
                )
                commandUserCardData = base.withRemoteEnrichment(remote)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                commandError = error.message.orEmpty().ifBlank { "Twitch error" }
            }
        }
        return true
    }

    fun executeModerationCommand(command: ConfirmedModerationCommand): Boolean {
        val authentication = runtime.authentication.state.authentication
        if (authentication == null) {
            commandError = authenticationRequiredText
            return true
        }
        if (!canModerateChannel) {
            commandError = moderatorRequiredText
            return true
        }
        scope.launch {
            try {
                when (command) {
                    is ConfirmedModerationCommand.Ban -> {
                        val user = userCardClient.loadUser(authentication, "", command.userLogin)
                        runtime.moderation.banUser(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            targetUserId = user.id,
                            reason = command.reason?.takeIf(String::isNotBlank)
                                ?: "Ferventio moderation",
                        )
                    }
                    is ConfirmedModerationCommand.Timeout -> {
                        val user = userCardClient.loadUser(authentication, "", command.userLogin)
                        runtime.moderation.timeoutUser(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            targetUserId = user.id,
                            durationSeconds = command.durationSeconds,
                            reason = command.reason?.takeIf(String::isNotBlank)
                                ?: "Ferventio moderation",
                        )
                    }
                    is ConfirmedModerationCommand.Unban -> {
                        val user = userCardClient.loadUser(authentication, "", command.userLogin)
                        runtime.moderation.unbanUser(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            targetUserId = user.id,
                        )
                    }
                    is ConfirmedModerationCommand.Delete -> {
                        val message = runtime.chat.messages(channel.id).firstOrNull { candidate ->
                            candidate.id == command.messageId ||
                                candidate.serverMessageId == command.messageId
                        }
                        val twitchMessageId = message?.serverMessageId
                            ?.takeIf(String::isNotBlank)
                            ?: command.messageId
                        runtime.moderation.deleteChatMessage(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            messageId = twitchMessageId,
                        )
                    }
                    ConfirmedModerationCommand.Clear -> {
                        runtime.moderation.clearChatMessages(authentication, channel.id)
                    }
                    is ConfirmedModerationCommand.Slow -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            slowMode = true,
                            slowModeWaitSeconds = command.seconds,
                        )
                    }
                    ConfirmedModerationCommand.SlowOff -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            slowMode = false,
                        )
                    }
                    is ConfirmedModerationCommand.Followers -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            followerMode = true,
                            followerModeDurationMinutes = command.minutes,
                        )
                    }
                    ConfirmedModerationCommand.FollowersOff -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            followerMode = false,
                        )
                    }
                    ConfirmedModerationCommand.Subscribers -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            subscriberMode = true,
                        )
                    }
                    ConfirmedModerationCommand.SubscribersOff -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            subscriberMode = false,
                        )
                    }
                    ConfirmedModerationCommand.EmoteOnly -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            emoteMode = true,
                        )
                    }
                    ConfirmedModerationCommand.EmoteOnlyOff -> {
                        chatManagementClient.updateChatSettings(
                            authentication = authentication,
                            broadcasterId = channel.id,
                            emoteMode = false,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                commandError = error.message.orEmpty().ifBlank { "Twitch error" }
            }
        }
        return true
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (canModerateChannel) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showNukePreview = true }) {
                        Text(stringResource(Res.string.nuke_preview_action))
                    }
                }
            }
        }

        InteractiveChatOverlayCards(channelId = channel.id)

        FerventioChatTimeline(
            channel = channel,
            filterQuery = filterQuery,
            modifier = Modifier.weight(1f),
            canModerate = canModerateChannel,
            onAuthorClick = { message -> selectedUserMessage = message },
            onReplyRequest = if (canWriteChat) {
                { message -> replyTarget = message }
            } else {
                null
            },
            onMessageLongPress = { message -> messageActionsTarget = message },
            onRetryMessage = { message ->
                runtime.authentication.state.authentication?.let { authentication ->
                    scope.launch {
                        try {
                            runtime.chatMessages.retry(
                                authentication = authentication,
                                channel = channel,
                                failedMessage = message,
                            )
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Throwable) {
                            // The optimistic row owns and displays the retry error state.
                        }
                    }
                }
            },
            onQuickBan = { message ->
                val authentication = runtime.authentication.state.authentication
                if (authentication == null) {
                    quickModerationError = authenticationRequiredText
                } else {
                    val availability = quickModerationAvailability(
                        message = message,
                        ownUserId = authentication.accessLease?.session?.userId,
                        canModerate = canModerateChannel,
                        preferences = runtime.localUiPreferences.preferences,
                    )
                    if (availability.canBan) {
                        scope.launch {
                            try {
                                runtime.moderation.banUser(
                                    authentication = authentication,
                                    broadcasterId = channel.id,
                                    targetUserId = message.userId,
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                quickModerationError = error.message.orEmpty().ifBlank { "Twitch error" }
                            }
                        }
                    }
                }
            },
            onQuickDelete = ::deleteMessage,
            providedThirdPartyEmotes = thirdPartyEmotes,
        )

        SharedChatComposer(
            channel = channel,
            replyTarget = replyTarget,
            onCancelReply = { replyTarget = null },
            onSent = { replyTarget = null },
            onUserCardCommand = ::openUserCardCommand,
            onModerationCommand = ::executeModerationCommand,
            emotes = composerEmotes,
        )
    }

    messageActionsTarget?.let { target ->
        val authentication = runtime.authentication.state.authentication
        val availability = quickModerationAvailability(
            message = target,
            ownUserId = authentication?.accessLease?.session?.userId,
            canModerate = canModerateChannel,
            preferences = runtime.localUiPreferences.preferences,
        )
        val canonicalMessageId = target.serverMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: target.id
        SharedMessageActionsSheet(
            message = target,
            canReply = canWriteChat &&
                !target.isSystem &&
                !target.isDeleted &&
                !canonicalMessageId.startsWith("local-"),
            canOpenThread = !target.isSystem,
            canOpenUser = target.userId.isNotBlank() || target.userLogin.isNotBlank(),
            canDelete = availability.canDelete,
            onDismiss = { messageActionsTarget = null },
            onReply = {
                messageActionsTarget = null
                replyTarget = target
            },
            onOpenThread = {
                messageActionsTarget = null
                threadTarget = target
            },
            onCopy = {
                clipboard.setText(AnnotatedString(target.text))
                messageActionsTarget = null
            },
            onOpenUser = {
                messageActionsTarget = null
                selectedUserMessage = target
            },
            onDelete = {
                messageActionsTarget = null
                deleteMessage(target)
            },
        )
    }

    threadTarget?.let { target ->
        val messages = runtime.chat.messages(channel.id)
        val threadMessages = remember(target.id, messages) {
            resolveSharedReplyThreadMessages(target, messages)
        }
        SharedReplyThreadSheet(
            target = target,
            messages = threadMessages,
            canReply = canWriteChat,
            onDismiss = { threadTarget = null },
            onReply = { message ->
                threadTarget = null
                replyTarget = message
            },
            onOpenUser = { message ->
                threadTarget = null
                selectedUserMessage = message
            },
        )
    }

    if (showNukePreview && canModerateChannel) {
        NukePreviewSheet(
            channelId = channel.id,
            messages = nukePreviewMessages(runtime.chat, channel.id),
            onDismiss = { showNukePreview = false },
        )
    }

    commandUserCardData?.let { data ->
        SharedUserCardSheet(
            data = data,
            onDismiss = { commandUserCardData = null },
        )
    }

    selectedUserMessage?.let { sourceMessage ->
        val messages = runtime.chat.messages(channel.id)
        val data = remember(sourceMessage.id, messages, canModerateChannel) {
            projectLocalUserCard(
                sourceMessage = sourceMessage,
                channelMessages = messages,
                canModerate = canModerateChannel,
            )
        }
        SharedUserCardSheet(
            data = data,
            onDismiss = { selectedUserMessage = null },
        )
    }

    commandError?.let { error ->
        AlertDialog(
            onDismissRequest = { commandError = null },
            title = { Text(stringResource(Res.string.chat_command_error_title)) },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { commandError = null }) {
                    Text(stringResource(Res.string.quick_moderation_ok))
                }
            },
        )
    }

    quickModerationError?.let { error ->
        AlertDialog(
            onDismissRequest = { quickModerationError = null },
            title = { Text(stringResource(Res.string.quick_moderation_error_title)) },
            text = { Text(stringResource(Res.string.quick_moderation_failed, error)) },
            confirmButton = {
                TextButton(onClick = { quickModerationError = null }) {
                    Text(stringResource(Res.string.quick_moderation_ok))
                }
            },
        )
    }
}
