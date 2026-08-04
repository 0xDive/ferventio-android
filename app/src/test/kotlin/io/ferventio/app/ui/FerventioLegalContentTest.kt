package io.ferventio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FerventioLegalContentTest {
    @Test
    fun `privacy policy covers local server third party retention and controls`() {
        val sections = FerventioLegalContent.privacySections(
            localCrashReporting = true,
            pushTransport = "embedded_socket",
            operatorName = "Example Operator",
            privacyContact = "privacy@example.test",
        )
        val text = sections.flatMap(PrivacyPolicySection::paragraphs).joinToString("\n")

        assertEquals(PRIVACY_POLICY_EFFECTIVE_DATE, "01.08.2026")
        assertTrue(text.contains("Example Operator"))
        assertTrue(text.contains("privacy@example.test"))
        assertTrue(text.contains("Android Keystore"))
        assertTrue(text.contains("текущий Twitch access token"))
        assertTrue(text.contains("refresh token хранится только"))
        assertTrue(text.contains("Twitch"))
        assertTrue(text.contains("BTTV"))
        assertTrue(text.contains("Ferventio Server"))
        assertTrue(text.contains("20 отчётов за 30 дней"))
        assertTrue(text.contains("отзыв всех серверных сессий"))
        assertTrue(text.contains("не продаёт персональные данные"))
    }

    @Test
    fun `crash disclosure follows build flavor`() {
        val fossText = policyText(localCrashReporting = true, pushTransport = "embedded_socket")
        val playText = policyText(localCrashReporting = false, pushTransport = "fcm")

        assertTrue(fossText.contains("не отправляет crash reports автоматически"))
        assertTrue(fossText.contains("не использует Firebase Cloud Messaging"))
        assertFalse(fossText.contains("Production Play-сборка может передавать"))

        assertTrue(playText.contains("Firebase Crashlytics"))
        assertTrue(playText.contains("FCM token"))
        assertFalse(playText.contains("FOSS-сборка не отправляет"))
    }

    @Test
    fun `license catalog is deterministic complete and flavor aware`() {
        val foss = FerventioLegalContent.openSourceNotices(includePlayLibraries = false)
        val play = FerventioLegalContent.openSourceNotices(includePlayLibraries = true)
        val knownLicenses = FerventioLegalContent.licenseTexts.map(LicenseText::id).toSet()

        assertEquals(foss.map(OpenSourceNotice::id).distinct(), foss.map(OpenSourceNotice::id))
        assertEquals(play.map(OpenSourceNotice::id).distinct(), play.map(OpenSourceNotice::id))
        assertTrue(foss.all { it.licenseId in knownLicenses })
        assertTrue(play.all { it.licenseId in knownLicenses })
        assertFalse(foss.any(OpenSourceNotice::playOnly))
        assertTrue(play.any { it.id == "firebase" && it.playOnly })
        assertTrue(play.any { it.id == "protobuf" && it.licenseId == FerventioLegalContent.BSD_3_CLAUSE })
        assertTrue(play.size > foss.size)
    }

    @Test
    fun `bundled license texts are full and offline`() {
        val texts = FerventioLegalContent.licenseTexts.associateBy(LicenseText::id)

        assertTrue(texts.getValue(FerventioLegalContent.APACHE_2_0).text.contains("TERMS AND CONDITIONS"))
        assertTrue(texts.getValue(FerventioLegalContent.APACHE_2_0).text.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(texts.getValue(FerventioLegalContent.BSD_3_CLAUSE).text.contains("Redistribution and use"))
        assertTrue(texts.values.all { it.text.length > 500 })
    }

    private fun policyText(localCrashReporting: Boolean, pushTransport: String): String =
        FerventioLegalContent.privacySections(
            localCrashReporting = localCrashReporting,
            pushTransport = pushTransport,
            operatorName = "Ferventio",
            privacyContact = "privacy@example.test",
        ).flatMap(PrivacyPolicySection::paragraphs).joinToString("\n")
}
