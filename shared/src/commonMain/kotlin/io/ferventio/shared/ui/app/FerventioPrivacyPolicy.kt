package io.ferventio.shared.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

internal const val FERVENTIO_PRIVACY_POLICY_EFFECTIVE_DATE = "2026-08-01"

internal enum class FerventioPrivacyPlatform {
    ANDROID,
    IOS,
    OTHER,
}

internal enum class FerventioPrivacyPushTransport {
    APNS,
    FCM,
    EMBEDDED_SOCKET,
    NONE,
    OTHER,
}

internal enum class FerventioPrivacyCrashReporting {
    NONE,
    LOCAL_ONLY,
    FIREBASE_CRASHLYTICS,
    OTHER,
}

@Immutable
internal data class FerventioPrivacyPlatformInfo(
    val platform: FerventioPrivacyPlatform,
    val pushTransport: FerventioPrivacyPushTransport,
    val crashReporting: FerventioPrivacyCrashReporting,
)

internal val LocalFerventioPrivacyPlatformInfo =
    staticCompositionLocalOf<FerventioPrivacyPlatformInfo?> { null }

@Composable
internal fun ProvideFerventioPrivacyPlatformInfo(
    info: FerventioPrivacyPlatformInfo,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFerventioPrivacyPlatformInfo provides info, content = content)
}
