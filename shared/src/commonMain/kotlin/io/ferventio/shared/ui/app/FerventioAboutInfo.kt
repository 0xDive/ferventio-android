package io.ferventio.shared.ui.app

import androidx.compose.runtime.Immutable

@Immutable
data class FerventioAboutInfo(
    val versionName: String = "",
    val websiteUrl: String = "",
    val githubUrl: String = "",
    val telegramChannelUrl: String = "",
    val telegramChatUrl: String = "",
    val translationsUrl: String = "",
    val privacyOperatorName: String = "",
    val privacyContact: String = "",
    val privacyPolicyUrl: String = "",
    val showPrivacyPolicyInApp: Boolean = false,
) {
    val hasConfiguredLinks: Boolean
        get() = websiteUrl.isNotBlank() ||
            githubUrl.isNotBlank() ||
            telegramChannelUrl.isNotBlank() ||
            telegramChatUrl.isNotBlank() ||
            translationsUrl.isNotBlank()
}
