package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FerventioAboutInfoTest {
    @Test
    fun configuredLinksIgnorePrivacyOnlyMetadata() {
        val info = FerventioAboutInfo(
            privacyOperatorName = "Ferventio",
            privacyContact = "privacy@example.invalid",
            privacyPolicyUrl = "https://example.invalid/privacy",
            showPrivacyPolicyInApp = true,
        )

        assertFalse(info.hasConfiguredLinks)
    }

    @Test
    fun configuredLinksDetectAnyVisibleProjectLink() {
        assertTrue(FerventioAboutInfo(websiteUrl = "https://example.invalid").hasConfiguredLinks)
        assertTrue(FerventioAboutInfo(githubUrl = "https://example.invalid/source").hasConfiguredLinks)
        assertTrue(FerventioAboutInfo(telegramChannelUrl = "https://example.invalid/channel").hasConfiguredLinks)
        assertTrue(FerventioAboutInfo(telegramChatUrl = "https://example.invalid/chat").hasConfiguredLinks)
        assertTrue(FerventioAboutInfo(translationsUrl = "https://example.invalid/translate").hasConfiguredLinks)
    }

    @Test
    fun privacyPageIsOptInByDefault() {
        assertFalse(FerventioAboutInfo().showPrivacyPolicyInApp)
    }
}
