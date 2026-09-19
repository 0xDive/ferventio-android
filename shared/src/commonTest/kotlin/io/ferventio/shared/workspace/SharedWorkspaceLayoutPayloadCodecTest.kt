package io.ferventio.shared.workspace

import io.ferventio.app.domain.FilteredSplit
import io.ferventio.app.domain.savedFilterReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SharedWorkspaceLayoutPayloadCodecTest {
    @Test
    fun parsesAndroidCompatibleWorkspaceLayout() {
        val layout = SharedWorkspaceLayoutPayloadCodec.parse(
            payload = payload(
                workspaces = """
                    {
                      "schemaVersion":2,
                      "activeWorkspaceId":"workspace-1",
                      "workspaces":[{
                        "id":"workspace-1",
                        "name":"Moderation",
                        "activeTabId":"tab-1",
                        "tabs":[{
                          "id":"tab-1",
                          "title":"Urgent",
                          "activeSplitId":"split-1",
                          "primaryFraction":0.6,
                          "splits":[{
                            "type":"filtered",
                            "id":"split-1",
                            "channelId":"channel-1",
                            "filterQuery":"${savedFilterReference("filter-1")}"
                          }]
                        }]
                      }]
                    }
                """.trimIndent(),
            ),
        )

        assertEquals("workspace-1", layout.activeWorkspaceId)
        assertEquals("Urgent", layout.activeTab?.title)
        val split = assertIs<FilteredSplit>(layout.activeTab?.activeSplit)
        assertEquals("channel-1", split.channelId)
        assertEquals(savedFilterReference("filter-1"), split.filterQuery)
    }

    @Test
    fun nullWorkspaceProjectionUsesFallbackChannel() {
        val layout = SharedWorkspaceLayoutPayloadCodec.parse(
            payload = payload(workspaces = "null"),
            fallbackChannelId = "channel-fallback",
        )

        assertEquals("channel-fallback", layout.activeTab?.activeSplit?.channelId)
    }

    private fun payload(workspaces: String): String = """
        {
          "format":"ferventio-settings-backup",
          "formatVersion":2,
          "createdAt":"2026-08-21T00:00:00Z",
          "appVersion":"0.0.5",
          "contentHash":"fixture",
          "content":{
            "settings":{},
            "channels":{"logins":[]},
            "workspaces":$workspaces,
            "filters":{},
            "highlights":[],
            "ignoreRules":[],
            "commands":{},
            "favouriteEmotes":[]
          }
        }
    """.trimIndent()
}
