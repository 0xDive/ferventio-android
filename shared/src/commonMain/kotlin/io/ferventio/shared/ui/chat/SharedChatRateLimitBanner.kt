package io.ferventio.shared.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.ChatRateLimitState
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_rate_limit_default
import io.ferventio.shared.generated.resources.chat_rate_limit_retry
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedChatRateLimitBanner(
    rateLimit: ChatRateLimitState,
    modifier: Modifier = Modifier,
) {
    val retryAtMillis = rateLimit.retryAtMillis
    var nowMillis by remember(retryAtMillis) {
        mutableStateOf(Clock.System.now().toEpochMilliseconds())
    }
    val remainingSeconds = retryAtMillis
        ?.let { retryAt -> ((retryAt - nowMillis + 999L) / 1_000L).coerceAtLeast(0L) }
    val message = rateLimit.message.trim().ifBlank {
        stringResource(Res.string.chat_rate_limit_default)
    }
    val retryText = remainingSeconds
        ?.takeIf { seconds -> seconds > 0L }
        ?.let { seconds ->
            stringResource(Res.string.chat_rate_limit_retry, seconds)
        }
    val displayText = listOfNotNull(message, retryText).joinToString(" · ")

    LaunchedEffect(retryAtMillis) {
        while (retryAtMillis != null && nowMillis < retryAtMillis) {
            delay(1_000L)
            nowMillis = Clock.System.now().toEpochMilliseconds()
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = displayText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
