package io.ferventio.shared.settings

import io.ferventio.app.domain.SavedMessageFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedSavedFiltersPayloadCodecTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesAndroidCompatibleSavedFiltersProjection() {
        val parsed = SharedSavedFiltersPayloadCodec.parse(payload())

        assertEquals(1, parsed.filters.size)
        assertEquals("filter-1", parsed.filters.single().id)
        assertEquals("Moderator messages", parsed.filters.single().name)
        assertEquals("badge.mod == true", parsed.filters.single().expression)
    }

    @Test
    fun replacePreservesUnrelatedBackupSectionsAndRecomputesHash() {
        val updated = SharedSavedFiltersPayloadCodec.replace(
            payload = payload(),
            snapshot = SharedSavedFiltersSnapshot(
                filters = listOf(
                    SavedMessageFilter(
                        id = "filter-2",
                        name = "Urgent",
                        expression = "text contains \"urgent\"",
                    ),
                ),
            ),
        )

        val root = json.parseToJsonElement(updated).jsonObject
        val content = root.getValue("content").jsonObject
        assertEquals(
            "highlight-marker",
            content.getValue("highlights").jsonArray.single().jsonObject
                .getValue("marker").jsonPrimitive.content,
        )
        assertEquals("keep-root", root.getValue("futureField").jsonPrimitive.content)
        assertEquals(
            SharedSavedFiltersPayloadCodec.contentHashForTesting(updated),
            root.getValue("contentHash").jsonPrimitive.content,
        )
        val parsed = SharedSavedFiltersPayloadCodec.parse(updated)
        assertEquals("filter-2", parsed.filters.single().id)
        assertEquals("Urgent", parsed.filters.single().name)
    }

    @Test
    fun malformedSavedFilterProjectionIsRejected() {
        val broken = payload().replace(
            "\"filters\":{\"schemaVersion\":1,\"filters\":[{\"id\":\"filter-1\",\"name\":\"Moderator messages\",\"expression\":\"badge.mod == true\"}]}",
            "\"filters\":{\"schemaVersion\":999,\"filters\":[]}",
        )

        assertFailsWith<IllegalArgumentException> {
            SharedSavedFiltersPayloadCodec.parse(broken)
        }
    }

    private fun payload(): String = """
        {
          "format":"ferventio-settings-backup",
          "formatVersion":2,
          "createdAt":"2026-08-20T10:00:00Z",
          "appVersion":"0.0.5",
          "contentHash":"old",
          "content":{
            "settings":{},
            "channels":{"logins":["example"]},
            "workspaces":null,
            "filters":{"schemaVersion":1,"filters":[{"id":"filter-1","name":"Moderator messages","expression":"badge.mod == true"}]},
            "highlights":[{"marker":"highlight-marker"}],
            "ignoreRules":[],
            "commands":{},
            "favouriteEmotes":[]
          },
          "futureField":"keep-root"
        }
    """.trimIndent()
}
