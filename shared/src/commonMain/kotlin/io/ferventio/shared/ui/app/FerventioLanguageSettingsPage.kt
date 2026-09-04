package io.ferventio.shared.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.AppLanguage
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.settings_language
import io.ferventio.shared.generated.resources.settings_language_english
import io.ferventio.shared.generated.resources.settings_language_english_summary
import io.ferventio.shared.generated.resources.settings_language_no_results
import io.ferventio.shared.generated.resources.settings_language_russian
import io.ferventio.shared.generated.resources.settings_language_russian_summary
import io.ferventio.shared.generated.resources.settings_language_search
import io.ferventio.shared.generated.resources.settings_language_system
import io.ferventio.shared.generated.resources.settings_language_system_summary
import io.ferventio.shared.settings.SharedAppPreferences
import org.jetbrains.compose.resources.stringResource

private data class SharedLanguageOption(
    val language: AppLanguage,
    val label: String,
    val summary: String,
)

@Composable
internal fun FerventioLanguageSettingsPage(
    preferences: SharedAppPreferences,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val options = AppLanguage.entries.map { language ->
        SharedLanguageOption(
            language = language,
            label = sharedLanguageLabel(language),
            summary = sharedLanguageSummary(language),
        )
    }
    val normalizedQuery = query.trim().lowercase()
    val visibleOptions = if (normalizedQuery.isEmpty()) {
        options
    } else {
        options.filter { option ->
            buildString {
                append(option.label)
                append(' ')
                append(option.summary)
                append(' ')
                append(option.language.name)
            }.lowercase().contains(normalizedQuery)
        }
    }

    Text(
        text = stringResource(Res.string.settings_language),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    OutlinedTextField(
        value = query,
        onValueChange = { query = it.take(80) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(stringResource(Res.string.settings_language_search)) },
    )

    if (visibleOptions.isEmpty()) {
        Text(
            text = stringResource(Res.string.settings_language_no_results),
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            visibleOptions.forEachIndexed { index, option ->
                SharedLanguageRow(
                    option = option,
                    selected = option.language == preferences.appLanguage,
                    onClick = { onLanguageSelected(option.language) },
                )
                if (index != visibleOptions.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SharedLanguageRow(
    option: SharedLanguageOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = option.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun sharedLanguageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(Res.string.settings_language_system)
    AppLanguage.RUSSIAN -> stringResource(Res.string.settings_language_russian)
    AppLanguage.ENGLISH -> stringResource(Res.string.settings_language_english)
}

@Composable
private fun sharedLanguageSummary(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(Res.string.settings_language_system_summary)
    AppLanguage.RUSSIAN -> stringResource(Res.string.settings_language_russian_summary)
    AppLanguage.ENGLISH -> stringResource(Res.string.settings_language_english_summary)
}
