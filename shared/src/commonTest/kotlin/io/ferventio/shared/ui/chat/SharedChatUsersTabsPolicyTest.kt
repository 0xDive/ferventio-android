package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ModerationPeopleTab
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedChatUsersTabsPolicyTest {
    @Test
    fun moderatorWithoutOwnershipGetsChattersOnly() {
        assertEquals(
            listOf(ModerationPeopleTab.CHATTERS),
            chatUsersAvailableTabs(isOwner = false),
        )
    }

    @Test
    fun channelOwnerGetsAllPeopleTabs() {
        assertEquals(
            ModerationPeopleTab.entries,
            chatUsersAvailableTabs(isOwner = true),
        )
    }
}
