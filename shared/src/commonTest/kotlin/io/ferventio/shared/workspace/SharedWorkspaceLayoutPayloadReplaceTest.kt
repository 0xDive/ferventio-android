package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatSplit
import io.ferventio.app.domain.Workspace
import io.ferventio.app.domain.WorkspaceLayout
import io.ferventio.app.domain.WorkspaceTab
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedWorkspaceLayoutPayloadReplaceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun replacePreservesOtherBackupSectionsAndRecomputesHash() {
        val layout = WorkspaceLayout(
            workspaces = listOf(
                Workspace(
                    id = "workspace-ios",
                    name = "iOS",
                    tabs = listOf(
                        WorkspaceTab(
                            id = "tab-ios",
                            title = "Chat",
                            splits = listOf(ChatSplit("split-ios", "channel-1")),
                            activeSplitId = "split-ios",
                        ),
                    ),
                    activeTabId = "tab-ios",
                ),
            ),
            activeWorkspaceId = "workspace-ios",
        )

        val replaced = SharedWorkspaceLayoutPayloadCodec.replace(payload(), layout)
        val root = json.parseToJsonElement(replaced).jsonObject
        val content = root.getValue("content").jsonObject

        assertEquals(layout, SharedWorkspaceLayoutPayloadCodec.parse(replaced))
        assertEquals("future-value", root.getValue("futureDocumentField").jsonPrimitive.content)
        assertEquals("alpha", content.getValue("channels").jsonObject
            .getValue("logins").jsonArray.single().jsonPrimitive.content)
        assertEquals(1, content.getValue("filters").jsonObject
            .getValue("schemaVersion").jsonPrimitive.content.toInt())
        assertEquals(
            SharedWorkspaceLayoutPayloadCodec.contentHashForTesting(replaced),
            root.getValue("contentHash").jsonPrimitive.content,
        )
    }

    private fun payload(): String = """
        {
          "format":"ferventio-settings-backup",
          "formatVersion":2,
          "createdAt":"2026-08-21T00:00:00Z",
          "appVersion":"0.0.5",
          "contentHash":"fixture",
          "futureDocumentField":"future-value",
          "content":{
            "settings":{"themeMode":"DARK"},
            "channels":{"logins":["alpha"],"pinnedChannelIds":[],"tabTitles":{}},
            "workspaces":null,
            "filters":{"schemaVersion":1,"filters":[]},
            "highlights":[],
            "ignoreRules":[],
            "commands":{},
            "favouriteEmotes":[]
          }
        }
    """.trimIndent()
}
