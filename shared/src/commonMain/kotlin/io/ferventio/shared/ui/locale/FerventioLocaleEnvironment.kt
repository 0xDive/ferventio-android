package io.ferventio.shared.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.key
import io.ferventio.app.domain.AppLanguage

internal expect object LocalFerventioAppLocale {
    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
internal fun FerventioLocaleEnvironment(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val locale = when (language) {
        AppLanguage.SYSTEM -> null
        AppLanguage.RUSSIAN -> "ru"
        AppLanguage.ENGLISH -> "en"
    }
    CompositionLocalProvider(LocalFerventioAppLocale provides locale) {
        key(locale) {
            content()
        }
    }
}
