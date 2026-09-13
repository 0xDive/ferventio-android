package io.ferventio.shared.chat

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatMessageType
import io.ferventio.app.domain.twitch.TwitchIrcEvent
import io.ferventio.app.domain.twitch.TwitchIrcParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TwitchIrcUserNoticeParserTest {
    @Test
    fun resubMapsToCanonicalNoticeMessage() {
        val message = parseMessage(
            """@badge-info=;badges=staff/1,broadcaster/1;color=#008000;display-name=ronni;emotes=;id=resub-1;login=ronni;msg-id=resub;msg-param-cumulative-months=6;msg-param-streak-months=2;msg-param-should-share-streak=1;msg-param-sub-plan=Prime;room-id=12345678;system-msg=ronni\shas\ssubscribed\sfor\s6\smonths!;tmi-sent-ts=1507246572675;user-id=87654321 :tmi.twitch.tv USERNOTICE #dallas :Great stream -- keep it up!""",
        )

        assertEquals(ChatMessageType.RESUBSCRIPTION, message.type)
        assertTrue(message.isSystem)
        assertEquals("12345678", message.channelId)
        assertEquals("ronni has subscribed for 6 months!", message.notice?.systemMessage)
        assertEquals("Great stream -- keep it up!", message.notice?.userMessage)
        assertEquals(6, message.notice?.cumulativeMonths)
        assertEquals(2, message.notice?.streakMonths)
        assertEquals("Prime", message.notice?.subTier)
        assertEquals(true, message.notice?.isPrime)
        assertEquals(listOf("staff", "broadcaster"), message.badges.map { it.setId })
    }

    @Test
    fun subGiftMapsCurrentRecipientAndGiftMetadata() {
        val message = parseMessage(
            """@badges=staff/1;color=#0000FF;display-name=TWW2;id=gift-1;login=tww2;msg-id=subgift;msg-param-gift-months=3;msg-param-months=12;msg-param-origin-id=community-wave-1;msg-param-recipient-display-name=Mr_Woodchuck;msg-param-recipient-id=55554444;msg-param-recipient-user-name=mr_woodchuck;msg-param-sender-count=38;msg-param-sub-plan=1000;room-id=19571752;system-msg=TWW2\sgifted\s3\smonths\sto\sMr_Woodchuck!;tmi-sent-ts=1521159445153;user-id=87654321 :tmi.twitch.tv USERNOTICE #forstycup""",
        )

        assertEquals(ChatMessageType.GIFT_SUBSCRIPTION, message.type)
        assertEquals(true, message.notice?.isGift)
        assertEquals(false, message.notice?.gifterIsAnonymous)
        assertEquals("87654321", message.notice?.gifterUserId)
        assertEquals("tww2", message.notice?.gifterUserLogin)
        assertEquals("TWW2", message.notice?.gifterUserName)
        assertEquals("55554444", message.notice?.recipientUserId)
        assertEquals("mr_woodchuck", message.notice?.recipientUserLogin)
        assertEquals("Mr_Woodchuck", message.notice?.recipientUserName)
        assertEquals(3, message.notice?.durationMonths)
        assertEquals(38, message.notice?.cumulativeGiftTotal)
        assertEquals("community-wave-1", message.notice?.communityGiftId)
    }

    @Test
    fun subGiftKeepsLegacyRecipientNameFallback() {
        val message = parseMessage(
            """@display-name=Gifter;id=gift-legacy;login=gifter;msg-id=subgift;msg-param-months=1;msg-param-recipient-display-name=LegacyUser;msg-param-recipient-id=55;msg-param-recipient-name=legacyuser;msg-param-sub-plan=1000;room-id=42;tmi-sent-ts=1700000000000;user-id=7 :tmi.twitch.tv USERNOTICE #channel""",
        )

        assertEquals("legacyuser", message.notice?.recipientUserLogin)
        assertEquals(1, message.notice?.durationMonths)
    }

    @Test
    fun communityGiftMapsWaveTotalsAndCommunityId() {
        val message = parseMessage(
            """@display-name=Gifter;id=gift-wave;login=gifter;msg-id=submysterygift;msg-param-mass-gift-count=5;msg-param-origin-id=community-wave-2;msg-param-sender-count=13;msg-param-sub-plan=1000;room-id=42;system-msg=Gifter\sis\sgifting\s5\sTier\s1\sSubs!;tmi-sent-ts=1700000000000;user-id=7 :tmi.twitch.tv USERNOTICE #channel""",
        )

        assertEquals(ChatMessageType.GIFT_SUBSCRIPTION, message.type)
        assertEquals(true, message.notice?.isGift)
        assertEquals(5, message.notice?.giftTotal)
        assertEquals(13, message.notice?.cumulativeGiftTotal)
        assertEquals("community-wave-2", message.notice?.communityGiftId)
        assertNull(message.notice?.recipientUserId)
        assertNull(message.notice?.recipientUserLogin)
    }

    @Test
    fun raidMapsBroadcasterAndViewerCount() {
        val message = parseMessage(
            """@badges=turbo/1;color=#9ACD32;display-name=TestChannel;id=raid-1;login=testchannel;msg-id=raid;msg-param-displayName=TestChannel;msg-param-login=testchannel;msg-param-viewerCount=15;room-id=33332222;system-msg=15\sraiders\sfrom\sTestChannel\shave\sjoined!;tmi-sent-ts=1507246572675;user-id=123456 :tmi.twitch.tv USERNOTICE #othertestchannel""",
        )

        assertEquals(ChatMessageType.RAID, message.type)
        assertEquals("123456", message.notice?.raidUserId)
        assertEquals("testchannel", message.notice?.raidUserLogin)
        assertEquals("TestChannel", message.notice?.raidUserName)
        assertEquals(15, message.notice?.raidViewerCount)
        assertFalse(message.notice?.isAnonymous ?: true)
    }

    @Test
    fun newerUnknownUserNoticeTypesRemainVisibleAsSystemRows() {
        val message = parseMessage(
            """@badges=;color=#1E90FF;display-name=TwitchDev;id=milestone-1;login=twitchdev;msg-id=viewermilestone;msg-param-category=watch-streak;msg-param-id=milestone-event;msg-param-value=3;room-id=197886470;system-msg=TwitchDev\swatched\s3\sconsecutive\sstreams!;tmi-sent-ts=1681057151588;user-id=141981764 :tmi.twitch.tv USERNOTICE #twitchrivals""",
        )

        assertEquals(ChatMessageType.SYSTEM, message.type)
        assertTrue(message.isSystem)
        assertEquals("viewermilestone", message.notice?.type)
        assertEquals("TwitchDev watched 3 consecutive streams!", message.notice?.systemMessage)
    }

    @Test
    fun userNoticeStillResolvesCanonicalRoomBeforeDeliveringMessage() {
        val events = TwitchIrcParser.parse(
            """@id=sub-1;login=viewer;display-name=Viewer;msg-id=sub;msg-param-cumulative-months=1;msg-param-sub-plan=1000;room-id=42;system-msg=Viewer\ssubscribed!;tmi-sent-ts=1700000000000;user-id=7 :tmi.twitch.tv USERNOTICE #channel""",
        ) { login -> "anonymous:$login" }

        assertEquals(2, events.size)
        assertEquals(TwitchIrcEvent.RoomResolved("channel", "42"), events.first())
        val chat = assertIs<TwitchIrcEvent.Chat>(events.last())
        val event = assertIs<ChatEvent.Message>(chat.event)
        assertEquals(ChatMessageType.SUBSCRIPTION, event.message.type)
        assertEquals("42", event.message.channelId)
    }

    private fun parseMessage(raw: String): ChatMessage {
        val chat = TwitchIrcParser.parse(raw) { login -> "anonymous:$login" }
            .filterIsInstance<TwitchIrcEvent.Chat>()
            .single()
        return assertIs<ChatEvent.Message>(chat.event).message
    }
}
