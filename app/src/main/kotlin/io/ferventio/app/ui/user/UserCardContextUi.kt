package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.R
import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.LocalModerationAction
import io.ferventio.app.domain.UserCardContextSummary

private data class UserCardContextStrings(
    val title: String,
    val accountAge: String,
    val followAge: String,
    val recentMessages: String,
    val over: String,
    val deletedRecentMessages: String,
    val moderationHistory: String,
    val lastAction: String,
    val lessThanOneDay: String,
    val daysShort: String,
    val monthsShort: String,
    val yearsShort: String,
    val secondsShort: String,
    val minutesShort: String,
    val hoursShort: String,
    val timeout: String,
    val ban: String,
    val warning: String,
)

@Composable
internal fun UserCardContextSection(
    summary: UserCardContextSummary,
    appLanguage: AppLanguage,
    modifier: Modifier = Modifier,
) {
    val resourceStrings = rememberAppResourceStrings(appLanguage)
    val strings = UserCardContextStrings(
        title = resourceStrings.string(R.string.ferventio_user_context_title),
        accountAge = resourceStrings.string(R.string.ferventio_user_context_account_age),
        followAge = resourceStrings.string(R.string.ferventio_user_context_follow_age),
        recentMessages = resourceStrings.string(R.string.ferventio_user_context_recent_messages),
        over = resourceStrings.string(R.string.ferventio_user_context_over),
        deletedRecentMessages = resourceStrings.string(R.string.ferventio_user_context_deleted_recent),
        moderationHistory = resourceStrings.string(R.string.ferventio_user_context_moderation_history),
        lastAction = resourceStrings.string(R.string.ferventio_user_context_last_action),
        lessThanOneDay = resourceStrings.string(R.string.ferventio_user_context_less_than_day),
        daysShort = resourceStrings.string(R.string.ferventio_user_context_days_short),
        monthsShort = resourceStrings.string(R.string.ferventio_user_context_months_short),
        yearsShort = resourceStrings.string(R.string.ferventio_user_context_years_short),
        secondsShort = resourceStrings.string(R.string.ferventio_user_context_seconds_short),
        minutesShort = resourceStrings.string(R.string.ferventio_user_context_minutes_short),
        hoursShort = resourceStrings.string(R.string.ferventio_user_context_hours_short),
        timeout = resourceStrings.string(R.string.ferventio_user_context_timeout),
        ban = resourceStrings.string(R.string.ferventio_user_context_ban),
        warning = resourceStrings.string(R.string.ferventio_user_context_warning),
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            LocalizedText(
                strings.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )

            summary.accountAgeDays?.let { days ->
                UserCardContextFact(strings.accountAge, formatAgeDays(days, strings))
            }
            summary.followAgeDays?.let { days ->
                UserCardContextFact(strings.followAge, formatAgeDays(days, strings))
            }

            if (summary.recentMessageCount > 0) {
                UserCardContextFact(
                    strings.recentMessages,
                    buildString {
                        append(summary.recentMessageCount)
                        summary.recentMessageWindowMillis?.let { window ->
                            append(" ${strings.over} ")
                            append(formatCompactDuration(window, strings))
                        }
                    },
                )
            }

            if (summary.deletedRecentMessageCount > 0) {
                UserCardContextFact(
                    strings.deletedRecentMessages,
                    summary.deletedRecentMessageCount.toString(),
                )
            }

            if (summary.moderationActionCount > 0) {
                UserCardContextFact(
                    strings.moderationHistory,
                    buildString {
                        append(summary.moderationActionCount)
                        val details = buildList {
                            if (summary.timeoutCount > 0) add("${strings.timeout} ×${summary.timeoutCount}")
                            if (summary.banCount > 0) add("${strings.ban} ×${summary.banCount}")
                            if (summary.warningCount > 0) add("${strings.warning} ×${summary.warningCount}")
                        }
                        if (details.isNotEmpty()) append(" · ${details.joinToString(" · ")}")
                    },
                )
                summary.lastModerationAction?.let { action ->
                    UserCardContextFact(strings.lastAction, formatModerationAction(action, strings))
                }
            }
        }
    }
}

@Composable
private fun UserCardContextFact(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LocalizedText(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        VerbatimText(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun formatAgeDays(days: Long, strings: UserCardContextStrings): String = when {
    days < 1 -> strings.lessThanOneDay
    days < 30 -> "$days ${strings.daysShort}"
    days < 365 -> "${days / 30} ${strings.monthsShort}"
    else -> {
        val years = days / 365
        val remainingMonths = (days % 365) / 30
        if (remainingMonths > 0) {
            "$years ${strings.yearsShort} $remainingMonths ${strings.monthsShort}"
        } else {
            "$years ${strings.yearsShort}"
        }
    }
}

private fun formatCompactDuration(
    durationMillis: Long,
    strings: UserCardContextStrings,
): String {
    val seconds = durationMillis.coerceAtLeast(0L) / 1_000L
    return when {
        seconds < 60 -> "${seconds.coerceAtLeast(1)} ${strings.secondsShort}"
        seconds < 3_600 -> "${seconds / 60} ${strings.minutesShort}"
        else -> "${seconds / 3_600} ${strings.hoursShort}"
    }
}

private fun formatModerationAction(
    action: LocalModerationAction,
    strings: UserCardContextStrings,
): String = buildString {
    append(
        when (action.action.uppercase()) {
            "TIMEOUT" -> strings.timeout
            "BAN" -> strings.ban
            "WARN" -> strings.warning
            else -> action.action
        },
    )
    action.durationSeconds?.takeIf { it > 0 }?.let { duration ->
        append(" · ")
        append(
            when {
                duration < 60 -> "$duration ${strings.secondsShort}"
                duration < 3_600 -> "${duration / 60} ${strings.minutesShort}"
                else -> "${duration / 3_600} ${strings.hoursShort}"
            },
        )
    }
    action.reason?.takeIf(String::isNotBlank)?.let { reason ->
        append(" · ")
        append(reason)
    }
}
