package io.ferventio.shared.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.automod_approve
import io.ferventio.shared.generated.resources.automod_deny
import io.ferventio.shared.generated.resources.automod_detail_category
import io.ferventio.shared.generated.resources.automod_detail_level
import io.ferventio.shared.generated.resources.automod_publish_explanation
import io.ferventio.shared.generated.resources.automod_reason_automod
import io.ferventio.shared.generated.resources.automod_reason_automod_details
import io.ferventio.shared.generated.resources.automod_reason_blocked_term
import io.ferventio.shared.generated.resources.automod_reason_custom
import io.ferventio.shared.generated.resources.automod_reason_generic
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SharedAutoModReviewCard(
    message: AutoModHeldMessage,
    enabled: Boolean,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
) {
    val allowColor = MaterialTheme.colorScheme.primary
    val reason = autoModInlineReason(message)
    val publishExplanation = stringResource(Res.string.automod_publish_explanation)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = allowColor,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            ),
                        ) {
                            append("AutoMod: ")
                        }
                        append(reason)
                        append(" ")
                        append(publishExplanation)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(Res.string.automod_approve),
                    color = allowColor,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable(enabled = enabled, onClick = onApprove)
                        .padding(PaddingValues(horizontal = 5.dp, vertical = 4.dp)),
                )
                Text(
                    text = stringResource(Res.string.automod_deny),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .clickable(enabled = enabled, onClick = onDeny)
                        .padding(PaddingValues(horizontal = 5.dp, vertical = 4.dp)),
                )
            }
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = formatChatTimestamp(message.heldAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = message.userName.ifBlank { message.userLogin } + ": ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = autoModAnnotatedText(message),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun autoModInlineReason(message: AutoModHeldMessage): String {
    val rawReason = message.reason?.trim().orEmpty()
    return when (rawReason.lowercase()) {
        "blocked_term" -> stringResource(Res.string.automod_reason_blocked_term)
        "automod" -> {
            val details = listOfNotNull(
                message.category?.takeIf(String::isNotBlank)?.let { category ->
                    stringResource(Res.string.automod_detail_category, category)
                },
                message.level?.let { level ->
                    stringResource(Res.string.automod_detail_level, level)
                },
            ).joinToString(", ")
            if (details.isBlank()) {
                stringResource(Res.string.automod_reason_automod)
            } else {
                stringResource(Res.string.automod_reason_automod_details, details)
            }
        }
        "" -> stringResource(Res.string.automod_reason_generic)
        else -> stringResource(Res.string.automod_reason_custom, rawReason)
    }
}

@Composable
private fun autoModAnnotatedText(message: AutoModHeldMessage): AnnotatedString {
    val blockedBackground = MaterialTheme.colorScheme.errorContainer
    val blockedForeground = MaterialTheme.colorScheme.onErrorContainer
    return buildAnnotatedString {
        append(message.text)
        message.boundaries.forEach { boundary ->
            val start = boundary.start.coerceIn(0, message.text.length)
            val endExclusive = (boundary.endInclusive + 1).coerceIn(start, message.text.length)
            if (endExclusive > start) {
                addStyle(
                    SpanStyle(
                        background = blockedBackground,
                        color = blockedForeground,
                        fontWeight = FontWeight.Bold,
                    ),
                    start = start,
                    end = endExclusive,
                )
            }
        }
    }
}
