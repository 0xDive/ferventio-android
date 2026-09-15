package io.ferventio.shared.settings

import io.ferventio.app.domain.AppLanguage
import io.ferventio.app.domain.AppThemeMode
import io.ferventio.app.domain.ChatNameStyle
import io.ferventio.app.domain.MessageDensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnonymousDisplayPreferencesCoordinatorTest {
    @Test
    fun restoreProjectsOnlySignedOutDisplayFields() {
        val state = SharedAppSettingsStateHolder(
            SharedAppPreferences(
                recentMessagesEnabled = true,
                localHistoryEnabled = false,
                sendOnEnter = false,
                replyNotificationsEnabled = false,
            ),
        )
        val store = FakeStore(
            AnonymousDisplayPreferences(
                appLanguage = AppLanguage.ENGLISH,
                themeMode = AppThemeMode.AMOLED,
                fontScalePercent = 135,
                messageDensity = MessageDensity.COMPACT,
                showAvatars = true,
                showBadges = false,
                showTimestamps = false,
                nameStyle = ChatNameStyle.LOGIN,
                wrapMessageLines = false,
                showDeletedMessageContent = true,
                showSystemMessages = false,
                mentionColorArgb = 0xFF123456,
                autoScrollEnabled = false,
                repeatCollapseEnabled = false,
                animateEmotes = false,
                emoteScalePercent = 145,
                betterTtvEnabled = false,
                frankerFaceZEnabled = false,
                sevenTvEnabled = false,
            ),
        )

        val restored = AnonymousDisplayPreferencesCoordinator(store).restore(state)

        assertEquals(AppLanguage.ENGLISH, restored.appLanguage)
        assertEquals(AppThemeMode.AMOLED, state.preferences.themeMode)
        assertEquals(135, state.preferences.fontScalePercent)
        assertEquals(MessageDensity.COMPACT, state.preferences.messageDensity)
        assertTrue(state.preferences.showAvatars)
        assertFalse(state.preferences.showBadges)
        assertFalse(state.preferences.showTimestamps)
        assertEquals(ChatNameStyle.LOGIN, state.preferences.nameStyle)
        assertFalse(state.preferences.wrapMessageLines)
        assertTrue(state.preferences.showDeletedMessageContent)
        assertFalse(state.preferences.showSystemMessages)
        assertEquals(0xFF123456, state.preferences.mentionColorArgb)
        assertFalse(state.preferences.autoScrollEnabled)
        assertFalse(state.preferences.repeatCollapseEnabled)
        assertFalse(state.preferences.animateEmotes)
        assertEquals(145, state.preferences.emoteScalePercent)
        assertFalse(state.preferences.betterTtvEnabled)
        assertFalse(state.preferences.frankerFaceZEnabled)
        assertFalse(state.preferences.sevenTvEnabled)

        assertTrue(state.preferences.recentMessagesEnabled)
        assertFalse(state.preferences.localHistoryEnabled)
        assertFalse(state.preferences.sendOnEnter)
        assertFalse(state.preferences.replyNotificationsEnabled)
    }

    @Test
    fun saveNormalizesBeforePersisting() {
        val store = FakeStore()
        val coordinator = AnonymousDisplayPreferencesCoordinator(store)

        val saved = coordinator.save(
            SharedAppPreferences(
                fontScalePercent = 10,
                mentionColorArgb = -1L,
                emoteScalePercent = 500,
            ),
        )

        assertEquals(80, saved.fontScalePercent)
        assertEquals(0L, saved.mentionColorArgb)
        assertEquals(200, saved.emoteScalePercent)
        assertEquals(saved, store.value)
    }

    private class FakeStore(
        var value: AnonymousDisplayPreferences = AnonymousDisplayPreferences(),
    ) : AnonymousDisplayPreferencesStore {
        override fun load(): AnonymousDisplayPreferences = value

        override fun save(preferences: AnonymousDisplayPreferences) {
            value = preferences
        }
    }
}
