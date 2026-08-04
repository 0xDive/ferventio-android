package io.ferventio.app.domain

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class UserCardModerationTextTest {
    @Test
    fun russianDurationsUseReadableSeparatedUnits() {
        assertEquals("1 д", UserCardModerationText.formatCompactDuration(86_400, AppLanguage.RUSSIAN, Locale.ENGLISH))
        assertEquals("1 ч", UserCardModerationText.formatCompactDuration(3_600, AppLanguage.RUSSIAN, Locale.ENGLISH))
        assertEquals("10 мин", UserCardModerationText.formatCompactDuration(600, AppLanguage.RUSSIAN, Locale.ENGLISH))
        assertEquals("Таймаут 1 д", UserCardModerationText.actionLabel("timeout:86400", AppLanguage.RUSSIAN, Locale.ENGLISH))
        assertEquals("Предупредить", UserCardModerationText.actionLabel("warn", AppLanguage.RUSSIAN, Locale.ENGLISH))
    }

    @Test
    fun englishDurationsUseTwitchStyleAsciiUnits() {
        assertEquals("1d", UserCardModerationText.formatCompactDuration(86_400, AppLanguage.ENGLISH, Locale.forLanguageTag("ru")))
        assertEquals("1h", UserCardModerationText.formatCompactDuration(3_600, AppLanguage.ENGLISH, Locale.forLanguageTag("ru")))
        assertEquals("10m", UserCardModerationText.formatCompactDuration(600, AppLanguage.ENGLISH, Locale.forLanguageTag("ru")))
        assertEquals("Timeout 1d", UserCardModerationText.actionLabel("timeout:86400", AppLanguage.ENGLISH, Locale.forLanguageTag("ru")))
        assertEquals("Warn", UserCardModerationText.actionLabel("warn", AppLanguage.ENGLISH, Locale.forLanguageTag("ru")))
    }

    @Test
    fun systemLanguageFollowsLocale() {
        assertEquals("1d", UserCardModerationText.formatCompactDuration(86_400, AppLanguage.SYSTEM, Locale.ENGLISH))
        assertEquals("1 д", UserCardModerationText.formatCompactDuration(86_400, AppLanguage.SYSTEM, Locale.forLanguageTag("ru")))
    }
}
