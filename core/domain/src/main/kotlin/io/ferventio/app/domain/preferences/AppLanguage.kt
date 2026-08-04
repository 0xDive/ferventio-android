package io.ferventio.app.domain

import java.util.Locale

enum class AppLanguage(val storageValue: String) {
    SYSTEM("system"),
    RUSSIAN("ru"),
    ENGLISH("en");

    companion object {
        fun fromStorageValue(value: String?): AppLanguage = entries.firstOrNull {
            it.storageValue.equals(value, ignoreCase = true)
        } ?: RUSSIAN
    }
}

fun AppLanguage.matchesLocale(locale: Locale): Boolean = when (this) {
    AppLanguage.SYSTEM -> true
    AppLanguage.RUSSIAN -> locale.language.equals("ru", ignoreCase = true)
    AppLanguage.ENGLISH -> locale.language.equals("en", ignoreCase = true)
}

fun AppLanguage.resolve(locale: Locale): AppLanguage = when (this) {
    AppLanguage.SYSTEM -> if (locale.language.equals("en", ignoreCase = true)) AppLanguage.ENGLISH else AppLanguage.RUSSIAN
    else -> this
}
