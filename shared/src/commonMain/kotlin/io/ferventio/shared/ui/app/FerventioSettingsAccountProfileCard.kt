package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.auth_sign_out
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioSettingsAccountProfileCard(
    onOpenAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val actions = LocalFerventioAccountActions.current
    val authentication = runtime.authentication.state.authentication ?: return
    val session = authentication.accessLease?.session ?: return
    if (!actions.accountManagementAvailable) return

    val profile = rememberAccountTwitchProfile()
    val mutation = runtime.account.state
    val displayName = profile?.displayName
        ?.takeIf(String::isNotBlank)
        ?: session.login?.takeIf(String::isNotBlank)
        ?: "Twitch"
    val login = session.login?.takeIf(String::isNotBlank)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAccount),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val imageUrl = profile?.profileImageUrl?.takeIf(String::isNotBlank)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(MaterialTheme.shapes.extraLarge),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (login != null) {
                    Text(
                        text = "@$login",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            TextButton(
                onClick = onSignOut,
                enabled = !mutation.mutationInFlight,
            ) {
                Text(stringResource(Res.string.auth_sign_out))
            }
        }
    }
}
