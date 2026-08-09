package io.ferventio.app.application

import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import org.junit.Assert.assertEquals
import org.junit.Test

class CommunityChattersLoaderTest {
    @Test
    fun `categorized roles keep canonical user identity`() {
        val canonical = listOf(
            ModerationUser(id = "1", login = "Owner", displayName = "Owner Display"),
            ModerationUser(id = "2", login = "mod", displayName = "Mod Display"),
            ModerationUser(id = "3", login = "extra", displayName = "Extra Display"),
        )
        val categorized = listOf(
            ModerationUser(
                id = "gql:owner",
                login = "owner",
                displayName = "owner",
                group = ModerationUserGroup.BROADCASTER,
            ),
            ModerationUser(
                id = "gql:mod",
                login = "mod",
                displayName = "mod",
                group = ModerationUserGroup.MODERATOR,
            ),
        )

        val merged = mergeCategorizedChatters(canonical, categorized)

        assertEquals(listOf("1", "2", "3"), merged.map { it.id })
        assertEquals(
            listOf(
                ModerationUserGroup.BROADCASTER,
                ModerationUserGroup.MODERATOR,
                ModerationUserGroup.VIEWER,
            ),
            merged.map { it.group },
        )
        assertEquals(listOf("Owner Display", "Mod Display", "Extra Display"), merged.map { it.displayName })
    }

    @Test
    fun `community roles group non moderator channel chatters`() {
        val canonical = listOf(
            ModerationUser(id = "10", login = "streamer", displayName = "Streamer"),
            ModerationUser(id = "11", login = "vip_user", displayName = "VIP User"),
            ModerationUser(id = "12", login = "mod_user", displayName = "Mod User"),
            ModerationUser(id = "13", login = "viewer", displayName = "Viewer"),
        )
        val categorized = listOf(
            ModerationUser(
                id = "gql:streamer",
                login = "streamer",
                displayName = "streamer",
                group = ModerationUserGroup.BROADCASTER,
            ),
            ModerationUser(
                id = "gql:vip_user",
                login = "vip_user",
                displayName = "vip_user",
                group = ModerationUserGroup.VIP,
            ),
            ModerationUser(
                id = "gql:mod_user",
                login = "mod_user",
                displayName = "mod_user",
                group = ModerationUserGroup.MODERATOR,
            ),
            ModerationUser(
                id = "gql:viewer",
                login = "viewer",
                displayName = "viewer",
                group = ModerationUserGroup.VIEWER,
            ),
        )

        val mergedByLogin = mergeCategorizedChatters(canonical, categorized).associateBy { it.login.lowercase() }

        assertEquals(ModerationUserGroup.BROADCASTER, mergedByLogin.getValue("streamer").group)
        assertEquals(ModerationUserGroup.VIP, mergedByLogin.getValue("vip_user").group)
        assertEquals(ModerationUserGroup.MODERATOR, mergedByLogin.getValue("mod_user").group)
        assertEquals(ModerationUserGroup.VIEWER, mergedByLogin.getValue("viewer").group)
        assertEquals("10", mergedByLogin.getValue("streamer").id)
        assertEquals("11", mergedByLogin.getValue("vip_user").id)
        assertEquals("12", mergedByLogin.getValue("mod_user").id)
        assertEquals("13", mergedByLogin.getValue("viewer").id)
    }

    @Test
    fun `uncategorized canonical chatters are ordinary viewers`() {
        val canonical = listOf(
            ModerationUser(id = "1", login = "one", displayName = "One"),
            ModerationUser(id = "2", login = "two", displayName = "Two"),
        )

        val merged = mergeCategorizedChatters(canonical, emptyList())

        assertEquals(
            listOf(ModerationUserGroup.VIEWER, ModerationUserGroup.VIEWER),
            merged.map { it.group },
        )
    }

    @Test
    fun `existing canonical role is preserved when category hint is unknown`() {
        val canonical = listOf(
            ModerationUser(
                id = "1",
                login = "vip",
                displayName = "VIP",
                group = ModerationUserGroup.VIP,
            ),
        )
        val categorized = listOf(
            ModerationUser(
                id = "gql:vip",
                login = "vip",
                displayName = "vip",
                group = ModerationUserGroup.UNKNOWN,
            ),
        )

        val merged = mergeCategorizedChatters(canonical, categorized)

        assertEquals(ModerationUserGroup.VIP, merged.single().group)
        assertEquals("1", merged.single().id)
    }
}
