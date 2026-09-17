package io.ferventio.shared.moderation

import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunityChattersPolicyTest {
    @Test
    fun categorizedRolesKeepCanonicalUserIdentity() {
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
    fun communityRolesPreserveCanonicalIdentityForKnownUsers() {
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

        val merged = mergeCategorizedChatters(canonical, categorized).associateBy { it.login.lowercase() }

        assertEquals(ModerationUserGroup.BROADCASTER, merged.getValue("streamer").group)
        assertEquals(ModerationUserGroup.VIP, merged.getValue("vip_user").group)
        assertEquals(ModerationUserGroup.MODERATOR, merged.getValue("mod_user").group)
        assertEquals(ModerationUserGroup.VIEWER, merged.getValue("viewer").group)
        assertEquals("10", merged.getValue("streamer").id)
        assertEquals("11", merged.getValue("vip_user").id)
        assertEquals("12", merged.getValue("mod_user").id)
        assertEquals("13", merged.getValue("viewer").id)
    }

    @Test
    fun uncategorizedCanonicalChattersBecomeViewers() {
        val merged = mergeCategorizedChatters(
            canonical = listOf(
                ModerationUser(id = "1", login = "one", displayName = "One"),
                ModerationUser(id = "2", login = "two", displayName = "Two"),
            ),
            categorized = emptyList(),
        )

        assertEquals(
            listOf(ModerationUserGroup.VIEWER, ModerationUserGroup.VIEWER),
            merged.map { it.group },
        )
    }

    @Test
    fun unknownCategoryHintDoesNotEraseCanonicalRole() {
        val merged = mergeCategorizedChatters(
            canonical = listOf(
                ModerationUser(
                    id = "1",
                    login = "vip",
                    displayName = "VIP",
                    group = ModerationUserGroup.VIP,
                ),
            ),
            categorized = listOf(
                ModerationUser(
                    id = "gql:vip",
                    login = "vip",
                    displayName = "vip",
                    group = ModerationUserGroup.UNKNOWN,
                ),
            ),
        )

        assertEquals(ModerationUserGroup.VIP, merged.single().group)
        assertEquals("1", merged.single().id)
    }
}
