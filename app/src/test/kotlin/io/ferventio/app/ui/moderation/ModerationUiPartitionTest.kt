package io.ferventio.app.ui

import io.ferventio.app.domain.AutoModHeldMessage
import io.ferventio.app.domain.AutoModMessageStatus
import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup
import kotlin.test.Test
import kotlin.test.assertEquals

class ModerationUiPartitionTest {
    @Test
    fun autoModPartitionFiltersChannelInOnePassAndCapsRecentItems() {
        val queue = listOf(
            autoMod("held-1", "channel", AutoModMessageStatus.HELD),
            autoMod("other", "other", AutoModMessageStatus.HELD),
            autoMod("approved", "channel", AutoModMessageStatus.APPROVED),
            autoMod("denied", "channel", AutoModMessageStatus.DENIED),
            autoMod("expired", "channel", AutoModMessageStatus.EXPIRED),
        )

        val partition = partitionAutoModQueue(
            queue = queue,
            channelId = "channel",
            recentLimit = 2,
        )

        assertEquals(listOf("held-1"), partition.held.map(AutoModHeldMessage::messageId))
        assertEquals(
            listOf("approved", "denied"),
            partition.recent.map(AutoModHeldMessage::messageId),
        )
    }

    @Test
    fun chatterGroupingPreservesPriorityAndInputOrderInsideGroups() {
        val users = listOf(
            user("viewer-1", ModerationUserGroup.VIEWER),
            user("mod-1", ModerationUserGroup.MODERATOR),
            user("owner", ModerationUserGroup.BROADCASTER),
            user("viewer-2", ModerationUserGroup.VIEWER),
            user("vip", ModerationUserGroup.VIP),
        )

        val grouped = groupModerationChatters(users)

        assertEquals(
            listOf(
                ModerationUserGroup.BROADCASTER,
                ModerationUserGroup.MODERATOR,
                ModerationUserGroup.VIP,
                ModerationUserGroup.VIEWER,
            ),
            grouped.map { it.first },
        )
        assertEquals(
            listOf("viewer-1", "viewer-2"),
            grouped.last().second.map(ModerationUser::id),
        )
    }

    private fun autoMod(
        messageId: String,
        channelId: String,
        status: AutoModMessageStatus,
    ) = AutoModHeldMessage(
        channelId = channelId,
        channelLogin = channelId,
        channelName = channelId,
        userId = "user-$messageId",
        userLogin = "user_$messageId",
        userName = "User $messageId",
        messageId = messageId,
        text = messageId,
        status = status,
    )

    private fun user(
        id: String,
        group: ModerationUserGroup,
    ) = ModerationUser(
        id = id,
        login = id.replace('-', '_'),
        displayName = id,
        group = group,
    )
}
