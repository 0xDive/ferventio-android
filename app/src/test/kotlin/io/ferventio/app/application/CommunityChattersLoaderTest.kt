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
                ModerationUserGroup.UNKNOWN,
            ),
            merged.map { it.group },
        )
        assertEquals(listOf("Owner Display", "Mod Display", "Extra Display"), merged.map { it.displayName })
    }
}
