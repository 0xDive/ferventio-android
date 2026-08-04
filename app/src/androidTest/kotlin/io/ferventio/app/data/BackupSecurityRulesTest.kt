package io.ferventio.app.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ferventio.app.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupSecurityRulesTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun legacyAndModernBackupRulesExcludeCredentialsSettingsAndLocalCrashReports() {
        val legacy = excludesFrom(R.xml.backup_rules)
        val modern = excludesFrom(R.xml.data_extraction_rules)
        val expected = setOf(
            "sharedpref:ferventio_token.xml",
            "sharedpref:ferventio_settings.xml",
            "sharedpref:ferventio_device_credentials.xml",
            "file:foss-crash-reports",
        )

        assertTrue(legacy.getValue("full-backup-content").containsAll(expected))
        assertTrue(modern.getValue("cloud-backup").containsAll(expected))
        assertTrue(modern.getValue("device-transfer").containsAll(expected))
        assertTrue(modern.keys.containsAll(setOf("cloud-backup", "device-transfer")))
    }

    private fun excludesFrom(resourceId: Int): Map<String, Set<String>> {
        val result = linkedMapOf<String, MutableSet<String>>()
        val parser = context.resources.getXml(resourceId)
        var section: String? = null
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "full-backup-content", "cloud-backup", "device-transfer" -> {
                            section = parser.name
                            result.getOrPut(requireNotNull(section)) { linkedSetOf() }
                        }
                        "exclude" -> {
                            val current = requireNotNull(section) { "exclude outside backup section" }
                            val domain = parser.getAttributeValue(null, "domain")
                            val path = parser.getAttributeValue(null, "path")
                            result.getOrPut(current) { linkedSetOf() } += "$domain:$path"
                        }
                    }
                    XmlPullParser.END_TAG -> if (parser.name == section) section = null
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return result.mapValues { (_, values) -> values.toSet() }
    }
}
