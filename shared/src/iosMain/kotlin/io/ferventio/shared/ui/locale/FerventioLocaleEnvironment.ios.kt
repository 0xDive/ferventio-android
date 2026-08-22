package io.ferventio.shared.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults

@OptIn(InternalComposeUiApi::class)
internal actual object LocalFerventioAppLocale {
    private const val LANGUAGE_KEY = "AppleLanguages"
    private val defaultLanguage = NSLocale.preferredLanguages.first() as String
    private val localLanguage = staticCompositionLocalOf { defaultLanguage }

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val language = value ?: defaultLanguage
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANGUAGE_KEY)
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(language), LANGUAGE_KEY)
        }
        return localLanguage.provides(language)
    }
}
