package io.ferventio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ferventio.app.application.FerventioController
import io.ferventio.app.domain.FerventioUiState
import java.text.NumberFormat
import java.util.Locale

/**
 * Compact Channel Points entry point rendered to the left of the message field.
 * It reuses the existing rewards sheet and redemption coordinator rather than
 * introducing another redemption path.
 */
@Composable
internal fun ChannelPointsComposerEntry(
    state: FerventioUiState,
    channelId: String,
    controller: FerventioController,
) {
    if (!state.isAuthenticated) return

    val viewerUserId = state.session?.userId
    val pointsState by controller.channelPointsState.collectAsStateWithLifecycle()
    val channelPoints = pointsState.channel(channelId)
    var showSheet by rememberSaveable(channelId, viewerUserId) { mutableStateOf(false) }
    var requestedInitialRefresh by remember(channelId, viewerUserId) { mutableStateOf(false) }

    LaunchedEffect(channelId, viewerUserId) {
        if (!requestedInitialRefresh) {
            requestedInitialRefresh = true
            controller.refreshChannelPoints(channelId)
        }
    }

    Surface(
        modifier = Modifier.clickable {
            showSheet = true
            controller.refreshChannelPoints(channelId)
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VerbatimText(
                "◇",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(4.dp))
            VerbatimText(
                formatChannelPointsBalance(channelPoints?.balance),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (channelPoints?.loading == true) {
                Spacer(Modifier.width(5.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
    Spacer(Modifier.width(6.dp))

    if (showSheet) {
        ChannelPointsSheet(
            state = channelPoints,
            appLanguage = state.appLanguage,
            onRefresh = { controller.refreshChannelPoints(channelId) },
            onRedeem = { reward, input ->
                controller.redeemChannelPointsReward(channelId, reward, input)
            },
            onDismiss = { showSheet = false },
        )
    }
}

private fun formatChannelPointsBalance(balance: Int?): String {
    if (balance == null) return "—"
    return NumberFormat.getIntegerInstance(Locale.US)
        .format(balance)
        .replace(',', ' ')
}
