package io.ferventio.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FerventioSharedLegalContentTest {
    @Test
    fun runtimeNoticesAreUniqueAndReferenceEmbeddedLicenses() {
        val notices = FerventioSharedLegalContent.openSourceNotices
        val licenses = FerventioSharedLegalContent.licenseTexts
        val licenseIds = licenses.mapTo(linkedSetOf(), FerventioLicenseText::id)

        assertEquals(notices.size, notices.mapTo(linkedSetOf(), FerventioOpenSourceNotice::id).size)
        assertEquals(licenses.size, licenseIds.size)
        notices.forEach { notice ->
            assertTrue(notice.id.isNotBlank())
            assertTrue(notice.name.isNotBlank())
            assertTrue(notice.version.isNotBlank())
            assertTrue(notice.projectUrl.startsWith("https://"))
            assertTrue(notice.licenseId in licenseIds)
        }
        licenses.forEach { license ->
            assertTrue(license.id.isNotBlank())
            assertTrue(license.name.isNotBlank())
            assertTrue(license.text.isNotBlank())
        }
    }

    @Test
    fun graphicsRuntimeIncludesSkikoAndSkia() {
        val ids = FerventioSharedLegalContent.openSourceNotices.mapTo(linkedSetOf()) { it.id }

        assertTrue("compose-multiplatform" in ids)
        assertTrue("skiko" in ids)
        assertTrue("skia" in ids)
    }
}
