package io.ferventio.shared.ui.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

internal actual object LocalFerventioAppLocale {
    private var defaultLocale: Locale? = null

    @Suppress("DEPRECATION")
    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        if (defaultLocale == null) defaultLocale = Locale.getDefault()
        val locale = value?.let(Locale::forLanguageTag) ?: defaultLocale!!
        Locale.setDefault(locale)
        val configuration = Configuration(LocalConfiguration.current).apply {
            setLocale(locale)
        }
        val resources = LocalContext.current.resources
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration.provides(configuration)
    }
}
