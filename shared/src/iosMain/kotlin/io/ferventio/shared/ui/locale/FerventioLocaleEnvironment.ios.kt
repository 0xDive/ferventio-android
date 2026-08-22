package io.ferventio.shared.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import platform.Foundation.NSUserDefaults

@OptIn(InternalComposeUiApi::class)
internal actual object LocalFerventioAppLocale {
    private const val LANGUAGE_KEY = "AppleLanguages"
    private val localLanguage = staticCompositionLocalOf { systemLanguage() }

    private fun systemLanguage(): String =
        NSUserDefaults.standardUserDefaults
            .stringArrayForKey(LANGUAGE_KEY)
            ?.firstOrNull() as? String
            ?: "en"

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val language = if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANGUAGE_KEY)
            systemLanguage()
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(value), LANGUAGE_KEY)
            value
        }
        return localLanguage.provides(language)
    }
}
