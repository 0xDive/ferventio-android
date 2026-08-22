package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.licenses_desc_coil
import io.ferventio.shared.generated.resources.licenses_desc_compose_multiplatform
import io.ferventio.shared.generated.resources.licenses_desc_coroutines
import io.ferventio.shared.generated.resources.licenses_desc_kotlin
import io.ferventio.shared.generated.resources.licenses_desc_ktor
import io.ferventio.shared.generated.resources.licenses_desc_serialization
import io.ferventio.shared.generated.resources.licenses_desc_skia
import io.ferventio.shared.generated.resources.licenses_desc_skiko
import io.ferventio.shared.generated.resources.licenses_full_text_available
import io.ferventio.shared.generated.resources.licenses_full_texts_title
import io.ferventio.shared.generated.resources.licenses_hide_text
import io.ferventio.shared.generated.resources.licenses_intro
import io.ferventio.shared.generated.resources.licenses_license_format
import io.ferventio.shared.generated.resources.licenses_open_project
import io.ferventio.shared.generated.resources.licenses_runtime_title
import io.ferventio.shared.generated.resources.licenses_show_text
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioLicensesSettingsPage() {
    val uriHandler = LocalUriHandler.current

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(Res.string.licenses_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LicenseSection(title = stringResource(Res.string.licenses_runtime_title)) {
            FerventioSharedLegalContent.openSourceNotices.forEachIndexed { index, notice ->
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = notice.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = notice.version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = noticeDescription(notice.id),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(Res.string.licenses_license_format, notice.licenseId),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(
                        onClick = { uriHandler.openUri(notice.projectUrl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(Res.string.licenses_open_project))
                    }
                }
                if (index != FerventioSharedLegalContent.openSourceNotices.lastIndex) {
                    HorizontalDivider()
                }
            }
        }

        LicenseSection(title = stringResource(Res.string.licenses_full_texts_title)) {
            FerventioSharedLegalContent.licenseTexts.forEachIndexed { index, license ->
                LicenseTextRow(license)
                if (index != FerventioSharedLegalContent.licenseTexts.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun noticeDescription(id: String): String = stringResource(
    when (id) {
        "compose-multiplatform" -> Res.string.licenses_desc_compose_multiplatform
        "kotlin" -> Res.string.licenses_desc_kotlin
        "coroutines" -> Res.string.licenses_desc_coroutines
        "serialization" -> Res.string.licenses_desc_serialization
        "ktor" -> Res.string.licenses_desc_ktor
        "coil" -> Res.string.licenses_desc_coil
        "skiko" -> Res.string.licenses_desc_skiko
        "skia" -> Res.string.licenses_desc_skia
        else -> error("Unknown open-source notice: $id")
    },
)

@Composable
private fun LicenseTextRow(license: FerventioLicenseText) {
    var expanded by rememberSaveable(license.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = license.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(Res.string.licenses_full_text_available),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (expanded) Res.string.licenses_hide_text else Res.string.licenses_show_text,
                ),
            )
        }
        if (expanded) {
            HorizontalDivider()
            Text(
                text = license.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LicenseSection(
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
