package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ChatNoticeTextFormatterTest {
    @Test
    fun formatsAllRequiredNoticeTypes() {
        assertEquals(
            "Подписка · Tier 1 · 1 месяц",
            ChatNoticeTextFormatter.title(message(ChatMessageType.SUBSCRIPTION, ChatNotice(type = "sub", subTier = "1000", durationMonths = 1))),
        )
        assertEquals(
            "Повторная подписка · Tier 2 · всего 18 месяцев",
            ChatNoticeTextFormatter.title(message(ChatMessageType.RESUBSCRIPTION, ChatNotice(type = "resub", subTier = "2000", cumulativeMonths = 18))),
        )
        assertEquals(
            "Подарено 5 подписок · Tier 1",
            ChatNoticeTextFormatter.title(message(ChatMessageType.GIFT_SUBSCRIPTION, ChatNotice(type = "community_sub_gift", subTier = "1000", giftTotal = 5))),
        )
        assertEquals(
            "Рейд от Raider · 321 зритель",
            ChatNoticeTextFormatter.title(message(ChatMessageType.RAID, ChatNotice(type = "raid", raidUserName = "Raider", raidViewerCount = 321))),
        )
        assertEquals(
            "Объявление",
            ChatNoticeTextFormatter.title(message(ChatMessageType.ANNOUNCEMENT, ChatNotice(type = "announcement", announcementColor = "PURPLE"))),
        )
    }

    @Test
    fun keepsUserMessageSeparateFromSystemText() {
        val message = message(
            ChatMessageType.RESUBSCRIPTION,
            ChatNotice(
                type = "resub",
                systemMessage = "Viewer subscribed for 10 months!",
                userMessage = "Glad to be here!",
            ),
        )
        assertEquals("Viewer subscribed for 10 months!", ChatNoticeTextFormatter.body(message))
        assertEquals("Glad to be here!", ChatNoticeTextFormatter.userMessage(message))
    }

    private fun message(type: ChatMessageType, notice: ChatNotice) = ChatMessage(
        id = "id",
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(id = "user", login = "user", displayName = "User"),
        text = notice.systemMessage.orEmpty(),
        timestamp = "2026-07-21T10:00:00Z",
        type = type,
        notice = notice,
        flags = MessageFlags(isSystem = true),
    )
}
