package io.ferventio.shared.ui.app

internal fun currentIosPrivacyPlatformInfo(): FerventioPrivacyPlatformInfo =
    FerventioPrivacyPlatformInfo(
        platform = FerventioPrivacyPlatform.IOS,
        pushTransport = FerventioPrivacyPushTransport.APNS,
        crashReporting = FerventioPrivacyCrashReporting.NONE,
    )
