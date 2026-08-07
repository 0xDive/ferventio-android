package io.ferventio.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.ferventio.app.domain.AppLanguage
import java.util.Locale

internal class AppResourceStrings internal constructor(
    private val context: Context,
) {
    fun string(
        @StringRes resourceId: Int,
        vararg formatArgs: Any,
    ): String = if (formatArgs.isEmpty()) {
        context.getString(resourceId)
    } else {
        context.getString(resourceId, *formatArgs)
    }

    fun quantity(
        @PluralsRes resourceId: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = context.resources.getQuantityString(
        resourceId,
        quantity,
        *formatArgs,
    )

    fun legacy(source: String): String = LegacyUiStringCatalog.resolve(context, source)
}

internal fun appResourceStrings(
    context: Context,
    appLanguage: AppLanguage,
): AppResourceStrings {
    val localizedContext = if (appLanguage == AppLanguage.SYSTEM) {
        context
    } else {
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(appLanguage.storageValue))
            setLayoutDirection(Locale.forLanguageTag(appLanguage.storageValue))
        }
        context.createConfigurationContext(configuration)
    }
    return AppResourceStrings(localizedContext)
}

internal fun resolveAppString(
    context: Context,
    appLanguage: AppLanguage,
    source: String,
): String = appResourceStrings(context, appLanguage).legacy(source)

@Composable
internal fun rememberAppResourceStrings(appLanguage: AppLanguage): AppResourceStrings {
    val context = LocalContext.current
    val systemLocaleTags = LocalConfiguration.current.locales.toLanguageTags()
    return remember(context, appLanguage, systemLocaleTags) {
        appResourceStrings(context, appLanguage)
    }
}
