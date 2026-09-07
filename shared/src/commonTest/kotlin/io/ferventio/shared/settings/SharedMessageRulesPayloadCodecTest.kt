package io.ferventio.shared.settings

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.IgnoreRuleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SharedMessageRulesPayloadCodecTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesAndroidCompatibleRuleProjection() {
        val parsed = SharedMessageRulesPayloadCodec.parse(payload())

        assertEquals(1, parsed.highlightRules.size)
        assertEquals(HighlightRuleType.WORD, parsed.highlightRules.single().type)
        assertEquals("urgent", parsed.highlightRules.single().pattern)
        assertEquals(1, parsed.ignoreRules.size)
        assertEquals(IgnoreRuleType.BOT_COMMAND, parsed.ignoreRules.single().type)
        assertEquals(IgnoreDisplayMode.COLLAPSE, parsed.ignoreRules.single().displayMode)
    }

    @Test
    fun replacePreservesUnrelatedBackupSectionsAndRecomputesHash() {
        val updated = SharedMessageRulesPayloadCodec.replace(
            payload = payload(),
            rules = SharedMessageRulesSnapshot(
                highlightRules = listOf(
                    HighlightRule(
                        id = "highlight-2",
                        type = HighlightRuleType.REGEX,
                        pattern = "foo.*bar",
                        colorArgb = 0xFF112233,
                        playSound = true,
                        push = true,
                        addToMentions = false,
                        filteredSplit = true,
                    ),
                ),
                ignoreRules = listOf(
                    IgnoreRule(
                        id = "ignore-2",
                        type = IgnoreRuleType.USER,
                        pattern = "spammer",
                        displayMode = IgnoreDisplayMode.TAP_TO_REVEAL,
                    ),
                ),
            ),
        )

        val root = json.parseToJsonElement(updated).jsonObject
        val content = root.getValue("content").jsonObject
        assertEquals("keep-me", content.getValue("filters").jsonObject.getValue("marker").jsonPrimitive.content)
        assertEquals("keep-root", root.getValue("futureField").jsonPrimitive.content)
        assertEquals(
            SharedMessageRulesPayloadCodec.contentHashForTesting(updated),
            root.getValue("contentHash").jsonPrimitive.content,
        )
        val parsed = SharedMessageRulesPayloadCodec.parse(updated)
        assertEquals("highlight-2", parsed.highlightRules.single().id)
        assertTrue(parsed.highlightRules.single().push)
        assertEquals(IgnoreDisplayMode.TAP_TO_REVEAL, parsed.ignoreRules.single().displayMode)
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
            "filters":{"marker":"keep-me"},
            "highlights":[{
              "id":"highlight-1",
              "type":"WORD",
              "pattern":"urgent",
              "enabled":true,
              "caseSensitive":false,
              "colorArgb":4294953047,
              "playSound":false,
              "push":false,
              "addToMentions":true,
              "filteredSplit":false
            }],
            "ignoreRules":[{
              "id":"ignore-1",
              "type":"BOT_COMMAND",
              "pattern":"!",
              "enabled":true,
              "caseSensitive":false,
              "displayMode":"COLLAPSE"
            }],
            "commands":{},
            "favouriteEmotes":[]
          },
          "futureField":"keep-root"
        }
    """.trimIndent()
}
