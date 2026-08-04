package io.ferventio.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ferventio.app.crash.CrashReporter
import io.ferventio.app.data.SecureTokenStore
import io.ferventio.app.data.SettingsStore
import io.ferventio.app.domain.ConnectionStatus
import io.ferventio.app.domain.FerventioUiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FerventioInstrumentedContractTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val application: FerventioApplication
        get() = context.applicationContext as FerventioApplication
    private val controller
        get() = application.container.controller

    @Before
    fun resetBeforeTest() {
        resetToAnonymous(emptyList())
        CrashReporter.clearLocalReports()
    }

    @After
    fun resetAfterTest() {
        CrashReporter.clearLocalReports()
        resetToAnonymous(emptyList())
    }

    @Test
    fun activityLaunchReachesStableAnonymousStateWithoutStoredCredential() {
        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(BuildConfig.APPLICATION_ID, activity.packageName)
            }
        }

        val state = awaitState { !it.isBootstrapping }
        assertTrue(state.isAnonymous)
        assertFalse(state.reauthorizationRequired)
        assertTrue(state.channels.isEmpty())
        assertEquals(ConnectionStatus.DISCONNECTED, state.connectionStatus)
    }

    @Test
    fun foreignOauthCallbackIsConsumedWithoutReplacingPendingAuthorization() {
        val settings = SettingsStore(context)
        settings.savePendingAuth(
            state = EXPECTED_OAUTH_STATE,
            expiresAtMillis = System.currentTimeMillis() + 60_000L,
            serverUrl = "https://auth.example.test",
        )
        val callback = Uri.parse(
            "${BuildConfig.APPLICATION_ID}://oauth/callback" +
                "?code=untrusted-code&state=unexpected-state",
        )

        ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java)
                .setData(callback)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertEquals(callback, activity.intent.data)
                assertTrue(
                    activity.intent.getBooleanExtra(
                        MainActivity.EXTRA_AUTH_CALLBACK_CONSUMED,
                        false,
                    ),
                )
            }
        }

        assertEquals(EXPECTED_OAUTH_STATE, settings.pendingAuthState)
        assertEquals("https://auth.example.test", settings.pendingAuthServerUrl)
        assertNull(SecureTokenStore(context).load())
        assertTrue(controller.state.value.isAnonymous)
    }

    @Test
    fun anonymousChatBootstrapRestoresPersistedChannelBeforeTransportResult() {
        resetToAnonymous(listOf("twitchdev"))
        val state = awaitState { current ->
            !current.isBootstrapping &&
                current.channels.singleOrNull()?.login == "twitchdev" &&
                current.connectionStatus != ConnectionStatus.DISCONNECTED
        }

        assertTrue(state.isAnonymous)
        assertFalse(state.isBootstrapping)
        assertEquals(1, state.channels.size)
        assertEquals("twitchdev", state.channels.single().login)
        assertEquals("irc:twitchdev", state.channels.single().id)
        assertEquals(state.channels.single().id, state.selectedChannelId)
        assertTrue(
            state.connectionStatus in setOf(
                ConnectionStatus.CONNECTING,
                ConnectionStatus.WAITING_WELCOME,
                ConnectionStatus.CONNECTED,
                ConnectionStatus.RECONNECTING,
                ConnectionStatus.FAILED,
            ),
        )
    }

    @Test
    fun flavorContractSelectsExpectedPushAndCrashTransport() {
        when (BuildConfig.FLAVOR) {
            "foss" -> {
                assertEquals("embedded_socket", BuildConfig.PUSH_TRANSPORT)
                assertTrue(BuildConfig.LOCAL_CRASH_REPORTING)

                CrashReporter.recordNonFatal(
                    component = "instrumented_flavor_contract",
                    error = IllegalStateException("Authorization: Bearer instrumented-secret"),
                )
                val export = CrashReporter.exportLocalReports()
                assertEquals(1, export.reportCount)
                assertTrue(export.content.contains("instrumented_flavor_contract"))
                assertFalse(export.content.contains("instrumented-secret"))
                assertTrue(export.content.contains("<redacted>"))
            }

            "play" -> {
                assertEquals("fcm", BuildConfig.PUSH_TRANSPORT)
                assertFalse(BuildConfig.LOCAL_CRASH_REPORTING)

                CrashReporter.recordNonFatal(
                    component = "instrumented_flavor_contract",
                    error = IllegalStateException("play-debug-no-upload"),
                )
                assertEquals(0, CrashReporter.exportLocalReports().reportCount)
            }

            else -> error("Unexpected push flavor: ${BuildConfig.FLAVOR}")
        }
    }

    private fun resetToAnonymous(channelLogins: List<String>): FerventioUiState {
        check(
            context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit(),
        ) { "Unable to clear instrumented settings" }
        SecureTokenStore(context).clear()

        val settings = SettingsStore(context)
        settings.channelLogins = channelLogins
        settings.selectedChannelLogin = channelLogins.firstOrNull()
        settings.markChannelsExplicitlyEmpty(channelLogins.isEmpty())

        controller.bootstrap()
        return awaitState { state ->
            !state.isBootstrapping &&
                state.isAnonymous &&
                state.channels.map { it.login } == channelLogins
        }
    }

    private fun awaitState(
        timeoutMillis: Long = STATE_TIMEOUT_MILLIS,
        predicate: (FerventioUiState) -> Boolean,
    ): FerventioUiState {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            val current = controller.state.value
            if (predicate(current)) return current
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        }
        val current = controller.state.value
        error("Timed out waiting for instrumented app state: $current")
    }

    private companion object {
        const val SETTINGS_FILE_NAME = "ferventio_settings"
        const val EXPECTED_OAUTH_STATE = "expected-instrumented-oauth-state"
        const val STATE_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 25L
    }
}
