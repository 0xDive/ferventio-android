package io.ferventio.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ferventio.app.crash.CrashReporter
import io.ferventio.app.data.SecureTokenStore
import io.ferventio.app.data.SettingsStore
import io.ferventio.app.domain.FerventioUiState
import io.ferventio.app.ui.ADD_CHANNEL_CONFIRM_TEST_TAG
import io.ferventio.app.ui.ADD_CHANNEL_INPUT_TEST_TAG
import io.ferventio.app.ui.PRIVACY_POLICY_EFFECTIVE_DATE
import io.ferventio.app.ui.SETTINGS_HOME_LIST_TEST_TAG
import io.ferventio.app.ui.SETTINGS_PAGE_LIST_TEST_TAG
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FerventioComposeUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val application: FerventioApplication
        get() = context.applicationContext as FerventioApplication
    private val controller
        get() = application.container.controller

    @Before
    fun resetBeforeTest() {
        resetToAnonymous(emptyList())
        CrashReporter.clearLocalReports()
        composeRule.waitForIdle()
    }

    @After
    fun resetAfterTest() {
        CrashReporter.clearLocalReports()
        resetToAnonymous(emptyList())
    }

    @Test
    fun emptyStateValidatesAddChannelDialogAndAddsNormalizedChannel() {
        composeRule.onNodeWithText("Пока нет каналов").assertIsDisplayed()
        composeRule.onNodeWithText("Добавить канал").performClick()

        composeRule.onNodeWithTag(ADD_CHANNEL_INPUT_TEST_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(ADD_CHANNEL_CONFIRM_TEST_TAG).performClick()
        composeRule.onNodeWithTag(ADD_CHANNEL_INPUT_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(ADD_CHANNEL_INPUT_TEST_TAG)
            .performTextInput(" #TwitchDev ")
        composeRule.onNodeWithTag(ADD_CHANNEL_CONFIRM_TEST_TAG).performClick()

        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            controller.state.value.channels.singleOrNull()?.login == "twitchdev"
        }
        composeRule.onNodeWithTag(ADD_CHANNEL_INPUT_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Пока нет каналов").assertDoesNotExist()
        assertEquals("irc:twitchdev", controller.state.value.channels.single().id)
    }

    @Test
    fun appearancePageExposesPersistentSystemMessagesToggle() {
        openSettings()

        openSettingsPage("Сообщения и оформление")

        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText("Системные сообщения в чате"))
        composeRule.onNodeWithText("Системные сообщения в чате").assertIsDisplayed()
        assertEquals(true, controller.state.value.showSystemMessages)

        composeRule.onNode(
            hasText("Системные сообщения в чате") and hasClickAction(),
        ).performClick()
        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            !controller.state.value.showSystemMessages
        }
        assertEquals(false, SettingsStore(context).showSystemMessages)
    }

    @Test
    fun settingsLanguageOpensFromRootAndDiagnosticsIsHidden() {
        openSettings()

        composeRule.onNodeWithText("Диагностика").assertDoesNotExist()
        openSettingsPage("Язык приложения")

        composeRule.onNodeWithText("Поиск языка").assertIsDisplayed()
        composeRule.onNodeWithText("Системный").assertIsDisplayed()
        composeRule.onNodeWithText("Русский").assertIsDisplayed()
        composeRule.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun notificationsPageUsesAutomaticRegistrationWithoutServerEditor() {
        openSettings()

        openSettingsPage("Уведомления")

        val automaticRegistrationText =
            "Уведомления подключаются автоматически после входа в Twitch. " +
                "Вводить адрес сервера или отдельно включать push не нужно."
        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText(automaticRegistrationText))
        composeRule.onNodeWithText(automaticRegistrationText).assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText("Настройки уведомлений Android"))
        composeRule.onNodeWithText("Настройки уведомлений Android").assertIsDisplayed()
        composeRule.onNodeWithText("Сервер Ferventio").assertDoesNotExist()
        composeRule.onNodeWithText("Подключить push").assertDoesNotExist()
    }

    @Test
    fun aboutPageExposesOnlyFlavorAppropriateCrashUi() {
        openSettings()

        openSettingsPage("О приложении")

        val versionText = "Версия ${BuildConfig.VERSION_NAME}"
        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText(versionText))
        composeRule.onNodeWithText(versionText).assertIsDisplayed()

        if (BuildConfig.LOCAL_CRASH_REPORTING) {
            composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
                .performScrollToNode(hasText("Локальные отчёты о сбоях"))
            composeRule.onNodeWithText("Локальные отчёты о сбоях").assertIsDisplayed()
            composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
                .performScrollToNode(hasText("Удалить локальные отчёты"))
            composeRule.onNodeWithText("Экспортировать отчёты").assertIsDisplayed()
            composeRule.onNodeWithText("Удалить локальные отчёты").performClick()
            composeRule.onNodeWithText("Удалить локальные отчёты?").assertIsDisplayed()
            composeRule.onNodeWithText("Отмена").performClick()
            composeRule.onNodeWithText("Удалить локальные отчёты?").assertDoesNotExist()
        } else {
            composeRule.onNodeWithText("Локальные отчёты о сбоях").assertDoesNotExist()
            composeRule.onNodeWithText("Экспортировать отчёты").assertDoesNotExist()
            composeRule.onNodeWithText("Удалить локальные отчёты").assertDoesNotExist()
        }
    }


    @Test
    fun aboutPageRespectsPrivacyVisibilityAndOpensFlavorAwareOpenSourceLicenses() {
        openSettings()

        openSettingsPage("О приложении")

        if (BuildConfig.SHOW_PRIVACY_POLICY_IN_APP) {
            composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
                .performScrollToNode(hasText("Политика конфиденциальности"))
            composeRule.onNodeWithText("Политика конфиденциальности").performClick()
            composeRule.onNodeWithText("Действует с $PRIVACY_POLICY_EFFECTIVE_DATE").assertIsDisplayed()
            composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
                .performScrollToNode(hasText("4. Передача третьим сторонам"))
            composeRule.onNodeWithText("4. Передача третьим сторонам").assertIsDisplayed()
            composeRule.onNodeWithContentDescription("Назад").performClick()
            openSettingsPage("О приложении")
        } else {
            composeRule.onNodeWithText("Политика конфиденциальности").assertDoesNotExist()
            composeRule.onNodeWithText("Открыть опубликованную web-версию").assertDoesNotExist()
        }

        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText("Лицензии open-source"))
        composeRule.onNodeWithText("Лицензии open-source").performClick()
        composeRule.onNodeWithText("AndroidX / Jetpack / Compose / Room").assertIsDisplayed()
        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
            .performScrollToNode(hasText("Apache License 2.0"))
        composeRule.onNodeWithText("Apache License 2.0").assertIsDisplayed()

        if (BuildConfig.PUSH_TRANSPORT == "fcm") {
            composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG)
                .performScrollToNode(hasText("Firebase Android SDK"))
            composeRule.onNodeWithText("Firebase Android SDK").assertIsDisplayed()
        } else {
            composeRule.onNodeWithText("Firebase Android SDK").assertDoesNotExist()
            composeRule.onNodeWithText("Protocol Buffers Lite").assertDoesNotExist()
        }
    }

    private fun openSettingsPage(title: String) {
        composeRule.onNodeWithTag(SETTINGS_HOME_LIST_TEST_TAG)
            .performScrollToNode(hasText(title))
        composeRule.onNode(hasText(title) and hasClickAction()).performClick()
        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag(SETTINGS_PAGE_LIST_TEST_TAG).assertExists()
    }

    private fun openSettings() {
        composeRule.onNodeWithContentDescription("Меню").performClick()
        composeRule.onNodeWithText("Настройки").performClick()
        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            runCatching {
                composeRule.onNodeWithTag(SETTINGS_HOME_LIST_TEST_TAG).fetchSemanticsNode()
            }.isSuccess
        }
        composeRule.onNodeWithTag(SETTINGS_HOME_LIST_TEST_TAG).assertExists()
    }

    private fun resetToAnonymous(channelLogins: List<String>): FerventioUiState {
        check(
            context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit(),
        ) { "Unable to clear Compose UI test settings" }
        SecureTokenStore(context).clear()

        val settings = SettingsStore(context)
        settings.channelLogins = channelLogins
        settings.selectedChannelLogin = channelLogins.firstOrNull()
        settings.markChannelsExplicitlyEmpty(channelLogins.isEmpty())

        controller.bootstrap()
        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            val state = controller.state.value
            !state.isBootstrapping &&
                state.isAnonymous &&
                state.channels.map { it.login } == channelLogins
        }
        controller.openChats()
        composeRule.waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) {
            controller.state.value.requestedMainSection == null
        }
        composeRule.waitForIdle()
        return controller.state.value
    }

    private companion object {
        const val SETTINGS_FILE_NAME = "ferventio_settings"
        const val STATE_TIMEOUT_MILLIS = 10_000L
    }
}
