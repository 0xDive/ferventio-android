package io.ferventio.shared.ui.user

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.ferventio.app.domain.ChannelUserRole
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.user_card_copy_username
import io.ferventio.shared.generated.resources.user_card_deleted_message
import io.ferventio.shared.generated.resources.user_card_done
import io.ferventio.shared.generated.resources.user_card_more_actions
import io.ferventio.shared.generated.resources.user_card_open_twitch
import io.ferventio.shared.generated.resources.user_card_recent_messages
import io.ferventio.shared.generated.resources.user_card_role_broadcaster
import io.ferventio.shared.generated.resources.user_card_role_moderator
import io.ferventio.shared.generated.resources.user_card_role_subscriber
import io.ferventio.shared.generated.resources.user_card_role_viewer
import io.ferventio.shared.generated.resources.user_card_role_vip
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.chat.SharedBadgeIcon
import io.ferventio.shared.ui.chat.formatChatTimestamp
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedUserCardSheet(
    data: UserCardData,
    onDismiss: () -> Unit,
) {
    val effectiveData = rememberRemoteUserCardData(data)
    val runtime = LocalFerventioRuntimeState.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var moreExpanded by remember(effectiveData.user.id, effectiveData.user.login) {
        mutableStateOf(false)
    }

    val liveMessages = runtime.chat.messages(effectiveData.channelId)
    val displayedRecentMessages = effectiveData.recentMessages.map { cached ->
        liveMessages.firstOrNull { live -> live.id == cached.id } ?: cached
    }
    val liveData = effectiveData.copy(recentMessages = displayedRecentMessages)
    val profileBadges = displayedRecentMessages
        .asReversed()
        .flatMap(ChatMessage::badges)
        .distinctBy { badge -> badge.setId + "/" + badge.id }
        .take(MAX_HEADER_BADGES)
    val moderationAvailability = userCardModerationAvailability(
        data = liveData,
        authenticatedUserId = runtime.authentication.state.authentication
            ?.accessLease
            ?.session
            ?.userId,
    )
    val visibleRecentMessages = userCardRecentMessagesForDisplay(
        messages = displayedRecentMessages,
        selectedMessageId = effectiveData.sourceMessageId,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            IconButton(onClick = { moreExpanded = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(
                                        Res.string.user_card_more_actions,
                                    ),
                                )
                            }
                            DropdownMenu(
                                expanded = moreExpanded,
                                onDismissRequest = { moreExpanded = false },
                            ) {
                                if (effectiveData.user.login.isNotBlank()) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(Res.string.user_card_copy_username))
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        },
                                        onClick = {
                                            moreExpanded = false
                                            @Suppress("DEPRECATION")
                                            clipboard.setText(
                                                AnnotatedString(effectiveData.user.login),
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(Res.string.user_card_open_twitch))
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        },
                                        onClick = {
                                            moreExpanded = false
                                            runCatching {
                                                uriHandler.openUri(
                                                    "https://www.twitch.tv/" +
                                                        effectiveData.user.login,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text(
                                text = stringResource(Res.string.user_card_done),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    UserCardAvatar(
                        imageUrl = effectiveData.user.profileImageUrl,
                        displayName = effectiveData.user.displayName.ifBlank {
                            effectiveData.user.login
                        },
                    )
                    Text(
                        text = effectiveData.user.displayName.ifBlank {
                            effectiveData.user.login
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            start = 24.dp,
                            end = 24.dp,
                            top = 12.dp,
                        ),
                    )
                    if (effectiveData.user.login.isNotBlank()) {
                        Text(
                            text = "@${effectiveData.user.login}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        Text(
                            text = userRoleLabel(effectiveData.role),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    if (profileBadges.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            profileBadges.forEach { badge ->
                                SharedBadgeIcon(
                                    badge = badge,
                                    asset = runtime.chat.badgeAsset(
                                        effectiveData.channelId,
                                        badge,
                                    ),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (hasUserCardRemoteDetails(effectiveData)) {
                item(key = "remote-details") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        UserCardRemoteDetails(data = effectiveData)
                    }
                }
            }

            item(key = "recent") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            Res.string.user_card_recent_messages,
                            visibleRecentMessages.size,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    UserCardRecentMessagesCard(
                        messages = visibleRecentMessages,
                        selectedMessageId = effectiveData.sourceMessageId,
                    )
                }
            }

            if (
                moderationAvailability.canModerateUser ||
                moderationAvailability.canDeleteSourceMessage
            ) {
                item(key = "moderation") {
                    UserCardModerationActions(
                        data = liveData,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

internal fun userCardRecentMessagesForDisplay(
    messages: List<ChatMessage>,
    selectedMessageId: String?,
): List<ChatMessage> {
    val recent = messages.takeLast(USER_CARD_VISIBLE_MESSAGE_LIMIT).asReversed()
    val selectedId = selectedMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return recent
    if (recent.any { message -> message.id == selectedId }) return recent
    val selected = messages.firstOrNull { message -> message.id == selectedId } ?: return recent
    return buildList {
        add(selected)
        addAll(recent.take((USER_CARD_VISIBLE_MESSAGE_LIMIT - 1).coerceAtLeast(0)))
    }
}

@Composable
private fun UserCardRecentMessagesCard(
    messages: List<ChatMessage>,
    selectedMessageId: String?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        if (messages.isEmpty()) {
            Text(
                text = "—",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column {
                messages.forEachIndexed { index, message ->
                    UserCardMessageRow(
                        message = message,
                        highlighted = message.id == selectedMessageId,
                    )
                    if (index != messages.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserCardAvatar(
    imageUrl: String?,
    displayName: String,
) {
    val painter = rememberAsyncImagePainter(model = imageUrl)
    val state by painter.state.collectAsState()
    Surface(
        modifier = Modifier.size(88.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (!imageUrl.isNullOrBlank() && state is AsyncImagePainter.State.Success) {
                Image(
                    painter = painter,
                    contentDescription = displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun UserCardMessageRow(
    message: ChatMessage,
    highlighted: Boolean,
) {
    val text = if (message.isDeleted) {
        stringResource(Res.string.user_card_deleted_message)
    } else {
        message.text
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (highlighted) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (message.isDeleted || message.isAction) {
                    FontStyle.Italic
                } else {
                    FontStyle.Normal
                },
                color = if (message.isDeleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = formatChatTimestamp(message.timestampMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun userRoleLabel(role: ChannelUserRole): String = when (role) {
    ChannelUserRole.BROADCASTER -> stringResource(Res.string.user_card_role_broadcaster)
    ChannelUserRole.MODERATOR -> stringResource(Res.string.user_card_role_moderator)
    ChannelUserRole.VIP -> stringResource(Res.string.user_card_role_vip)
    ChannelUserRole.SUBSCRIBER -> stringResource(Res.string.user_card_role_subscriber)
    ChannelUserRole.VIEWER -> stringResource(Res.string.user_card_role_viewer)
}

private const val USER_CARD_VISIBLE_MESSAGE_LIMIT = 12
private const val MAX_HEADER_BADGES = 5
