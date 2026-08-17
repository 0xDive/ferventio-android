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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
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
import io.ferventio.shared.generated.resources.user_card_badges
import io.ferventio.shared.generated.resources.user_card_close
import io.ferventio.shared.generated.resources.user_card_deleted_message
import io.ferventio.shared.generated.resources.user_card_id
import io.ferventio.shared.generated.resources.user_card_moderator_context
import io.ferventio.shared.generated.resources.user_card_open_twitch
import io.ferventio.shared.generated.resources.user_card_recent_messages
import io.ferventio.shared.generated.resources.user_card_role_broadcaster
import io.ferventio.shared.generated.resources.user_card_role_moderator
import io.ferventio.shared.generated.resources.user_card_role_subscriber
import io.ferventio.shared.generated.resources.user_card_role_viewer
import io.ferventio.shared.generated.resources.user_card_role_vip
import io.ferventio.shared.generated.resources.user_card_selected_message
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import io.ferventio.shared.ui.chat.SharedBadgeIcon
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
    val profileBadges = effectiveData.recentMessages
        .asReversed()
        .firstOrNull { it.badges.isNotEmpty() }
        ?.badges
        .orEmpty()
    val sourceMessage = effectiveData.sourceMessageId?.let { id ->
        effectiveData.recentMessages.firstOrNull { it.id == id }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "profile") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserCardAvatar(
                        imageUrl = effectiveData.user.profileImageUrl,
                        displayName = effectiveData.user.displayName.ifBlank { effectiveData.user.login },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = effectiveData.user.displayName.ifBlank { effectiveData.user.login },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (effectiveData.user.login.isNotBlank()) {
                            Text(
                                text = "@${effectiveData.user.login}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (effectiveData.user.id.isNotBlank()) {
                            Text(
                                text = stringResource(Res.string.user_card_id, effectiveData.user.id),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = userRoleLabel(effectiveData.role),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (profileBadges.isNotEmpty()) {
                item(key = "badges") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(Res.string.user_card_badges),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(
                                items = profileBadges,
                                key = { badge -> "${badge.setId}/${badge.id}" },
                            ) { badge ->
                                SharedBadgeIcon(
                                    badge = badge,
                                    asset = runtime.chat.badgeAsset(effectiveData.channelId, badge),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }

            if (hasUserCardRemoteDetails(effectiveData)) {
                item(key = "remote-details") {
                    UserCardRemoteDetails(effectiveData)
                }
            }

            if (effectiveData.canModerate) {
                item(key = "moderator-context") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(Res.string.user_card_moderator_context),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            sourceMessage?.let { selected ->
                item(key = "selected-message") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(Res.string.user_card_selected_message),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        UserCardMessageRow(selected)
                    }
                }
            }

            item(key = "recent-title") {
                Text(
                    text = stringResource(
                        Res.string.user_card_recent_messages,
                        effectiveData.recentMessages.size,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            items(
                items = effectiveData.recentMessages
                    .filterNot { it.id == effectiveData.sourceMessageId }
                    .takeLast(USER_CARD_VISIBLE_MESSAGE_LIMIT)
                    .asReversed(),
                key = { message -> "recent:${message.id}" },
            ) { message ->
                UserCardMessageRow(message)
            }

            item(key = "actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.user_card_close))
                    }
                    if (effectiveData.user.login.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    uriHandler.openUri("https://www.twitch.tv/${effectiveData.user.login}")
                                }
                            },
                        ) {
                            Text(stringResource(Res.string.user_card_open_twitch))
                        }
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
        modifier = Modifier.size(72.dp),
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
private fun UserCardMessageRow(message: ChatMessage) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = if (message.isDeleted) {
                stringResource(Res.string.user_card_deleted_message)
            } else {
                message.text
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontStyle = if (message.isDeleted || message.isAction) FontStyle.Italic else FontStyle.Normal,
            color = if (message.isDeleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
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
