package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.about_github
import io.ferventio.shared.generated.resources.about_github_summary
import io.ferventio.shared.generated.resources.about_legal_information
import io.ferventio.shared.generated.resources.about_links_title
import io.ferventio.shared.generated.resources.about_open_source_licenses
import io.ferventio.shared.generated.resources.about_privacy_policy
import io.ferventio.shared.generated.resources.about_project_description
import io.ferventio.shared.generated.resources.about_project_title
import io.ferventio.shared.generated.resources.about_summary
import io.ferventio.shared.generated.resources.about_telegram_channel
import io.ferventio.shared.generated.resources.about_telegram_channel_summary
import io.ferventio.shared.generated.resources.about_telegram_chat
import io.ferventio.shared.generated.resources.about_telegram_chat_summary
import io.ferventio.shared.generated.resources.about_translation_project
import io.ferventio.shared.generated.resources.about_translation_project_summary
import io.ferventio.shared.generated.resources.about_version
import io.ferventio.shared.generated.resources.about_website
import io.ferventio.shared.generated.resources.about_website_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioAboutSettingsPage(
    info: FerventioAboutInfo,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val links = buildList {
        if (info.websiteUrl.isNotBlank()) {
            add(AboutLink(Res.string.about_website, Res.string.about_website_summary, info.websiteUrl))
        }
        if (info.githubUrl.isNotBlank()) {
            add(AboutLink(Res.string.about_github, Res.string.about_github_summary, info.githubUrl))
        }
        if (info.telegramChannelUrl.isNotBlank()) {
            add(
                AboutLink(
                    Res.string.about_telegram_channel,
                    Res.string.about_telegram_channel_summary,
                    info.telegramChannelUrl,
                ),
            )
        }
        if (info.telegramChatUrl.isNotBlank()) {
            add(
                AboutLink(
                    Res.string.about_telegram_chat,
                    Res.string.about_telegram_chat_summary,
                    info.telegramChatUrl,
                ),
            )
        }
        if (info.translationsUrl.isNotBlank()) {
            add(
                AboutLink(
                    Res.string.about_translation_project,
                    Res.string.about_translation_project_summary,
                    info.translationsUrl,
                ),
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AboutSection(title = stringResource(Res.string.about_project_title)) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(Res.string.about_version, info.versionName.ifBlank { "—" }),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(Res.string.about_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(Res.string.about_project_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (links.isNotEmpty()) {
            AboutSection(title = stringResource(Res.string.about_links_title)) {
                links.forEachIndexed { index, link ->
                    AboutActionRow(
                        title = stringResource(link.title),
                        summary = stringResource(link.summary),
                        onClick = { uriHandler.openUri(link.url) },
                    )
                    if (index != links.lastIndex) HorizontalDivider()
                }
            }
        }

        AboutSection(title = stringResource(Res.string.about_legal_information)) {
            if (info.showPrivacyPolicyInApp) {
                AboutActionRow(
                    title = stringResource(Res.string.about_privacy_policy),
                    onClick = onOpenPrivacyPolicy,
                )
                HorizontalDivider()
            }
            AboutActionRow(
                title = stringResource(Res.string.about_open_source_licenses),
                onClick = onOpenLicenses,
            )
        }
    }
}

private data class AboutLink(
    val title: StringResource,
    val summary: StringResource,
    val url: String,
)

@Composable
private fun AboutSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun AboutActionRow(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
