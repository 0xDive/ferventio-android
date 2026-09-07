package io.ferventio.app.domain

import java.util.Locale

fun AppLanguage.matchesLocale(locale: Locale): Boolean = matchesLanguageCode(locale.language)

fun AppLanguage.resolve(locale: Locale): AppLanguage = resolveLanguageCode(locale.language)
