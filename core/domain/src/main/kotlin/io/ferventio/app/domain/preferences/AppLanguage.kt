package io.ferventio.app.domain

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

fun AppLanguage.matchesLanguageCode(languageCode: String): Boolean = when (this) {
    AppLanguage.SYSTEM -> true
    AppLanguage.RUSSIAN -> languageCode.equals("ru", ignoreCase = true)
    AppLanguage.ENGLISH -> languageCode.equals("en", ignoreCase = true)
}

fun AppLanguage.resolveLanguageCode(languageCode: String): AppLanguage = when (this) {
    AppLanguage.SYSTEM -> if (languageCode.equals("en", ignoreCase = true)) AppLanguage.ENGLISH else AppLanguage.RUSSIAN
    else -> this
}
