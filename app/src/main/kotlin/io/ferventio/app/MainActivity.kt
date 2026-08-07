package io.ferventio.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.ferventio.app.crash.CrashReporter
import io.ferventio.app.crash.LocalCrashReportExport
import io.ferventio.app.data.BackupFileIo
import io.ferventio.app.performance.PerformanceRuntimeState
import io.ferventio.app.push.NotificationPresenter
import io.ferventio.app.ui.FerventioApp
import io.ferventio.app.ui.resolveAppString
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as FerventioApplication).container }
    private var pendingCrashReportExport: LocalCrashReportExport? = null

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val content = container.controller.exportSettingsBackup()
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                BackupFileIo.writeUtf8(output, content)
            } ?: error("Не удалось открыть файл для записи")
        }.onSuccess {
            container.controller.reportBackupExported(uri.lastPathSegment)
        }.onFailure { error ->
            container.controller.reportBackupError("Экспорт: ${error.message ?: "неизвестная ошибка"}")
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                BackupFileIo.readUtf8Limited(input)
            } ?: error("Не удалось открыть файл")
        }.onSuccess(container.controller::importSettingsBackup)
            .onFailure { error ->
                container.controller.reportBackupError("Импорт: ${error.message ?: "неизвестная ошибка"}")
            }
    }


    private val createCrashReportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingCrashReportExport
        pendingCrashReportExport = null
        if (uri == null || export == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(export.content)
            } ?: error("Не удалось открыть файл для записи")
        }.onSuccess {
            Toast.makeText(
                this,
                localized("Экспортировано отчётов: ${export.reportCount}"),
                Toast.LENGTH_LONG,
            ).show()
        }.onFailure { error ->
            Toast.makeText(
                this,
                localized("Ошибка экспорта: ${error.message ?: "неизвестная ошибка"}"),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            container.pushCoordinator.ensureAutomaticRegistration(this)
        } else {
            container.pushCoordinator.onPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            FerventioApp(
                controller = container.controller,
                pushCoordinator = container.pushCoordinator,
                onExportSettings = ::exportSettings,
                onImportSettings = ::importSettings,
                onExportCrashReports = ::exportCrashReports,
                onClearCrashReports = ::clearCrashReports,
            )
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.controller.state
                    .map { it.isAuthenticated }
                    .distinctUntilChanged()
                    .collect { authenticated ->
                        if (authenticated) ensureAutomaticNotifications()
                    }
            }
        }
        processAuthIntent(intent)
        processNotificationIntent(intent)
        processPerformanceIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processAuthIntent(intent)
        processNotificationIntent(intent)
        processPerformanceIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.controller.onAppForegrounded()
        ensureAutomaticNotifications()
    }

    override fun onStop() {
        container.controller.onAppBackgrounded()
        super.onStop()
    }



    private fun ensureAutomaticNotifications() {
        if (!container.controller.state.value.isAuthenticated) return
        when {
            hasNotificationPermission() -> container.pushCoordinator.refresh(this)
            container.pushCoordinator.shouldRequestNotificationPermission() -> {
                container.pushCoordinator.markNotificationPermissionRequested()
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> container.pushCoordinator.refresh(this)
        }
    }

    private fun processAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != BuildConfig.APPLICATION_ID || data.host != "oauth" || data.path != "/callback") return
        if (intent.getBooleanExtra(EXTRA_AUTH_CALLBACK_CONSUMED, false)) return
        container.controller.handleAuthorizationCallback(
            code = data.getQueryParameter("code"),
            state = data.getQueryParameter("state"),
            errorCode = data.getQueryParameter("error"),
        )
        // Keep the original data URI intact: ActivityScenario matches lifecycle callbacks by
        // action/data/type/component. Extras are ignored by that matching logic, so this marker
        // prevents duplicate callback handling without breaking Activity teardown or recreation.
        intent.putExtra(EXTRA_AUTH_CALLBACK_CONSUMED, true)
    }

    private fun processNotificationIntent(intent: Intent?) {
        val destination = intent?.getStringExtra(NotificationPresenter.EXTRA_DESTINATION)
        if (destination == "push_settings") {
            container.controller.openSettings()
            clearNotificationExtras(intent)
            return
        }
        val channelId = intent?.getStringExtra(NotificationPresenter.EXTRA_CHANNEL_ID)
            ?.takeIf(String::isNotBlank)
            ?: return
        val messageId = intent.getStringExtra(NotificationPresenter.EXTRA_MESSAGE_ID)
            ?.takeIf(String::isNotBlank)
        if (destination == "moderation") {
            container.controller.openModeration(channelId)
        } else if (destination == "mentions") {
            container.controller.openMentions()
        } else if (messageId != null) {
            container.controller.navigateToMessage(channelId, messageId)
        } else {
            container.controller.selectChannel(channelId)
        }
        clearNotificationExtras(intent)
    }

    private fun processPerformanceIntent(intent: Intent?) {
        if (intent == null || !isPerformanceIntentAllowed(intent)) return
        val initialMessages = intent.getIntExtra(EXTRA_PERFORMANCE_INITIAL_MESSAGES, 0)
        if (initialMessages <= 0) return
        PerformanceRuntimeState.enable()
        container.controller.startPerformanceScenario(
            initialMessageCount = initialMessages,
            messagesPerSecond = intent.getIntExtra(EXTRA_PERFORMANCE_MESSAGES_PER_SECOND, 0),
            durationSeconds = intent.getIntExtra(EXTRA_PERFORMANCE_DURATION_SECONDS, 0),
        )
        intent.action = null
        intent.removeExtra(EXTRA_PERFORMANCE_INITIAL_MESSAGES)
        intent.removeExtra(EXTRA_PERFORMANCE_MESSAGES_PER_SECOND)
        intent.removeExtra(EXTRA_PERFORMANCE_DURATION_SECONDS)
    }

    private fun isPerformanceIntentAllowed(intent: Intent): Boolean {
        if (intent.action != ACTION_PERFORMANCE_TEST) return false
        // Production fossRelease/playRelease stay locked. Baseline Profile creates
        // benchmarkRelease and nonMinifiedRelease target build types at build time.
        return BuildConfig.DEBUG || BuildConfig.BUILD_TYPE != "release"
    }

    private fun clearNotificationExtras(intent: Intent) {
        intent.removeExtra(NotificationPresenter.EXTRA_CHANNEL_ID)
        intent.removeExtra(NotificationPresenter.EXTRA_CHANNEL_LOGIN)
        intent.removeExtra(NotificationPresenter.EXTRA_MESSAGE_ID)
        intent.removeExtra(NotificationPresenter.EXTRA_DESTINATION)
    }

    private fun exportSettings() {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        createBackupLauncher.launch("ferventio-settings-$timestamp.json")
    }

    private fun importSettings() {
        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    private fun exportCrashReports() {
        val export = CrashReporter.exportLocalReports()
        if (export.reportCount == 0) {
            Toast.makeText(this, localized("Локальных отчётов пока нет"), Toast.LENGTH_SHORT).show()
            return
        }
        pendingCrashReportExport = export
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        createCrashReportLauncher.launch("ferventio-crash-reports-$timestamp.json")
    }

    private fun clearCrashReports() {
        val deleted = CrashReporter.clearLocalReports()
        Toast.makeText(
            this,
            localized(if (deleted == 0) "Локальных отчётов нет" else "Удалено отчётов: $deleted"),
            Toast.LENGTH_SHORT,
        ).show()
    }


    private fun localized(source: String): String = resolveAppString(
        context = this,
        appLanguage = container.controller.state.value.appLanguage,
        source = source,
    )

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_PERFORMANCE_TEST =
            "io.ferventio.app.action.PERFORMANCE_TEST"
        const val EXTRA_PERFORMANCE_INITIAL_MESSAGES =
            "io.ferventio.app.extra.PERFORMANCE_INITIAL_MESSAGES"
        const val EXTRA_PERFORMANCE_MESSAGES_PER_SECOND =
            "io.ferventio.app.extra.PERFORMANCE_MESSAGES_PER_SECOND"
        const val EXTRA_PERFORMANCE_DURATION_SECONDS =
            "io.ferventio.app.extra.PERFORMANCE_DURATION_SECONDS"
        internal const val EXTRA_AUTH_CALLBACK_CONSUMED =
            "io.ferventio.app.extra.AUTH_CALLBACK_CONSUMED"
    }
}
