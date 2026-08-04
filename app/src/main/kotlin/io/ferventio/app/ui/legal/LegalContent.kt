package io.ferventio.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyPolicyCard(section: PrivacyPolicySection) {
    SettingsSection(section.title) {
        section.paragraphs.forEachIndexed { index, paragraph ->
            if (index > 0) Spacer(Modifier.height(2.dp))
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun OpenSourceNoticeCard(
    notice: OpenSourceNotice,
    onOpenUrl: (String) -> Unit,
) {
    SettingsSection(notice.name) {
        Text(notice.version, fontWeight = FontWeight.SemiBold)
        Text(
            notice.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Лицензия: ${notice.licenseId}")
        OutlinedButton(
            onClick = { onOpenUrl(notice.projectUrl) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Открыть страницу проекта")
        }
    }
}

@Composable
internal fun LicenseTextCard(license: LicenseText) {
    var expanded by rememberSaveable(license.id) { mutableStateOf(false) }
    SettingsSection(license.name) {
        Text(
            "Полный текст лицензии включён в приложение и доступен офлайн.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (expanded) "Скрыть текст лицензии" else "Показать текст лицензии")
        }
        if (expanded) {
            HorizontalDivider()
            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    license.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
