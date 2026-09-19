package io.ferventio.shared.ui.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.user_card_block
import io.ferventio.shared.generated.resources.user_card_block_confirm_body
import io.ferventio.shared.generated.resources.user_card_block_confirm_title
import io.ferventio.shared.generated.resources.user_card_block_done
import io.ferventio.shared.generated.resources.user_card_block_failed
import io.ferventio.shared.generated.resources.user_card_cancel
import io.ferventio.shared.generated.resources.user_card_confirm
import io.ferventio.shared.generated.resources.user_card_safety
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

internal fun canBlockUserCardUser(
    data: UserCardData,
    authenticatedUserId: String?,
): Boolean {
    val targetId = data.user.id.trim()
    val ownId = authenticatedUserId?.trim().orEmpty()
    return targetId.isNotEmpty() && ownId.isNotEmpty() && targetId != ownId
}

@Composable
internal fun UserCardSafetyActions(
    data: UserCardData,
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication ?: return
    if (!canBlockUserCardUser(data, authentication.accessLease?.session?.userId)) return

    val scope = rememberCoroutineScope()
    val targetLabel = data.user.displayName
        .trim()
        .takeIf(String::isNotEmpty)
        ?: data.user.login.trim().takeIf(String::isNotEmpty)
        ?: data.user.id
    var confirmBlock by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var mutationInFlight by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var blocked by remember(data.channelId, data.user.id) { mutableStateOf(false) }
    var errorMessage by remember(data.channelId, data.user.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.user_card_safety),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = !mutationInFlight && !blocked,
                            role = Role.Button,
                            onClick = { confirmBlock = true },
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = if (blocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = if (blocked) {
                            stringResource(Res.string.user_card_block_done, targetLabel)
                        } else {
                            stringResource(Res.string.user_card_block, targetLabel)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (blocked) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                errorMessage?.let { message ->
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = stringResource(Res.string.user_card_block_failed, message),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmBlock) {
        AlertDialog(
            onDismissRequest = {
                if (!mutationInFlight) confirmBlock = false
            },
            title = {
                Text(stringResource(Res.string.user_card_block_confirm_title, targetLabel))
            },
            text = {
                Text(stringResource(Res.string.user_card_block_confirm_body))
            },
            confirmButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = {
                        confirmBlock = false
                        mutationInFlight = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val currentAuthentication =
                                    runtime.authentication.state.authentication
                                        ?: error("Twitch authentication is unavailable")
                                runtime.userSafety.blockUser(
                                    authentication = currentAuthentication,
                                    targetUserId = data.user.id,
                                )
                                blocked = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Throwable) {
                                errorMessage = error.message
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?: "Unknown Twitch user safety error"
                            } finally {
                                mutationInFlight = false
                            }
                        }
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.user_card_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !mutationInFlight,
                    onClick = { confirmBlock = false },
                ) {
                    Text(stringResource(Res.string.user_card_cancel))
                }
            },
        )
    }
}
