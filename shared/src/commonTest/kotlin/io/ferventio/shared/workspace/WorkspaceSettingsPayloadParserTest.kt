package io.ferventio.shared.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class WorkspaceSettingsPayloadParserTest {
    @Test
    fun readsOnlyChannelProjectionFromSettingsBackupPayload() {
        val result = WorkspaceSettingsPayloadParser.parse(
            """
            {
              "format": "ferventio-settings",
              "formatVersion": 1,
              "content": {
                "settings": {"themeMode":"DARK"},
                "channels": {
                  "logins": [" Alpha ", "beta", "ALPHA"],
                  "selectedLogin": " BETA ",
                  "pinnedChannelIds": [" 2 ", "2", "1"]
                },
                "futureField": {"ignored": true}
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("alpha", "beta"), result.logins)
        assertEquals("beta", result.selectedLogin)
        assertEquals(listOf("2", "1"), result.pinnedChannelIds)
    }

    @Test
    fun selectedLoginOutsidePersistedWorkspaceIsDropped() {
        val result = WorkspaceSettingsPayloadParser.parse(
            """
            {
              "content": {
                "channels": {
                  "logins": ["alpha"],
                  "selectedLogin": "missing"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(listOf("alpha"), result.logins)
        assertNull(result.selectedLogin)
        assertEquals(emptyList(), result.pinnedChannelIds)
    }

    @Test
    fun malformedOrMissingChannelPayloadIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkspaceSettingsPayloadParser.parse("not-json")
        }
        assertFailsWith<IllegalStateException> {
            WorkspaceSettingsPayloadParser.parse("{\"content\":{}}")
        }
    }
}
