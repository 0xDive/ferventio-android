package io.ferventio.shared

import io.ferventio.shared.ui.app.FerventioAboutInfo
import platform.Foundation.NSBundle
import platform.Foundation.NSNumber

internal fun currentIosAboutInfo(
    bundle: NSBundle = NSBundle.mainBundle,
): FerventioAboutInfo = FerventioAboutInfo(
    versionName = bundle.stringValue("CFBundleShortVersionString"),
    websiteUrl = bundle.stringValue("FerventioAppWebsiteURL"),
    githubUrl = bundle.stringValue("FerventioAppGitHubURL"),
    telegramChannelUrl = bundle.stringValue("FerventioAppTelegramChannelURL"),
    telegramChatUrl = bundle.stringValue("FerventioAppTelegramChatURL"),
    translationsUrl = bundle.stringValue("FerventioAppTranslationsURL"),
    privacyOperatorName = bundle.stringValue("FerventioPrivacyOperatorName"),
    privacyContact = bundle.stringValue("FerventioPrivacyContact"),
    privacyPolicyUrl = bundle.stringValue("FerventioPrivacyPolicyURL"),
    showPrivacyPolicyInApp = bundle.booleanValue("FerventioShowPrivacyPolicyInApp"),
)

private fun NSBundle.stringValue(key: String): String =
    (objectForInfoDictionaryKey(key) as? String)?.trim().orEmpty()

private fun NSBundle.booleanValue(key: String): Boolean {
    val raw = objectForInfoDictionaryKey(key)
    return when (raw) {
        is NSNumber -> raw.boolValue
        is String -> raw.trim().lowercase() in setOf("1", "true", "yes")
        else -> false
    }
}
