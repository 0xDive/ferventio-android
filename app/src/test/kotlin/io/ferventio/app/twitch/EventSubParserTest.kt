package io.ferventio.app.twitch

import io.ferventio.app.domain.ChatEvent
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSubParserTest {
    @Test
    fun parsesChatMessage() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_type": "notification",
                "message_timestamp": "2026-07-21T10:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "message-1",
                  "message_type": "text",
                  "color": "#00FF00",
                  "badges": [{"set_id": "moderator", "id": "1", "info": ""}],
                  "message": {"text": "Привет, Ferventio!"}
                }
              }
            }
            """.trimIndent(),
        )

        val event = envelope.event
        assertTrue(event is ChatEvent.Message)
        val message = (event as ChatEvent.Message).message
        assertEquals("message-1", message.id)
        assertEquals("Viewer", message.userDisplayName)
        assertEquals("Привет, Ferventio!", message.text)
        assertEquals("moderator", message.badges.single().setId)
    }

    @Test
    fun textFragmentStartingWithUrlRemainsPlainFragmentForRangeDetection() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_type": "notification",
                "message_timestamp": "2026-08-02T10:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "message-link-range",
                  "message_type": "text",
                  "color": "#00FF00",
                  "badges": [],
                  "message": {
                    "text": "https://example.com/path this stays plain",
                    "fragments": [
                      {"type": "text", "text": "https://example.com/path this stays plain"}
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        assertTrue(message.fragments.single() is ChatFragment.Text)
        assertEquals("https://example.com/path this stays plain", message.fragments.single().text)
    }

    @Test
    fun parsesWelcomeSessionAndKeepaliveTimeout() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "session_welcome"},
              "payload": {
                "session": {
                  "id": "session-1",
                  "keepalive_timeout_seconds": 30,
                  "reconnect_url": null
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("session_welcome", envelope.type)
        assertEquals("session-1", envelope.sessionId)
        assertEquals(30, envelope.keepaliveTimeoutSeconds)
    }

    @Test
    fun parsesDeletedMessage() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-delete-1",
                "message_type": "notification",
                "message_timestamp": "2026-08-02T08:15:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message_delete"},
                "event": {
                  "broadcaster_user_id": "100",
                  "message_id": "deleted-1"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            ChatEvent.MessageDeleted(
                channelId = "100",
                messageId = "deleted-1",
                eventId = "event-delete-1",
                createdAt = "2026-08-02T08:15:00Z",
            ),
            envelope.event,
        )
    }


    @Test
    fun parsesUserMessagesClearWithNeutralModerationKind() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-clear-user-1",
                "message_type": "notification",
                "message_timestamp": "2026-08-02T08:16:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.clear_user_messages"},
                "event": {
                  "broadcaster_user_id": "100",
                  "target_user_id": "300",
                  "target_user_login": "viewer"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(
            ChatEvent.UserMessagesCleared(
                channelId = "100",
                userId = "300",
                userLogin = "viewer",
                eventId = "event-clear-user-1",
                createdAt = "2026-08-02T08:16:00Z",
            ),
            envelope.event,
        )
    }

    @Test
    fun parsesChatMessageWithExplicitJsonNullFields() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_type": "notification",
                "message_timestamp": "2026-07-21T17:09:10Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "message_id": "message-null-fields",
                  "message_type": "text",
                  "color": null,
                  "reply": null,
                  "badges": null,
                  "message": {"text": "Обычное сообщение без reply"}
                }
              }
            }
            """.trimIndent(),
        )

        val event = envelope.event
        assertTrue(event is ChatEvent.Message)
        val message = (event as ChatEvent.Message).message
        assertEquals("message-null-fields", message.id)
        assertEquals("Обычное сообщение без reply", message.text)
        assertTrue(message.badges.isEmpty())
        assertEquals(null, message.replyParentMessageId)
        assertEquals(null, message.replyParentUserName)
    }

    @Test
    fun parsesKeepaliveMetadata() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "keepalive-1",
                "message_type": "session_keepalive",
                "message_timestamp": "2026-07-21T18:00:00Z"
              },
              "payload": {}
            }
            """.trimIndent(),
        )

        assertEquals("session_keepalive", envelope.type)
        assertEquals("keepalive-1", envelope.messageId)
        assertEquals("2026-07-21T18:00:00Z", envelope.messageTimestamp)
    }

    @Test
    fun parsesReconnectUrl() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "session_reconnect"},
              "payload": {
                "session": {
                  "id": "session-2",
                  "keepalive_timeout_seconds": null,
                  "reconnect_url": "wss://eventsub.wss.twitch.tv/ws?reconnect=test"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("session_reconnect", envelope.type)
        assertEquals("session-2", envelope.sessionId)
        assertEquals(
            "wss://eventsub.wss.twitch.tv/ws?reconnect=test",
            envelope.reconnectUrl,
        )
    }

    @Test
    fun parsesRevocationReason() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "revocation-1",
                "message_type": "revocation",
                "subscription_type": "channel.chat.message"
              },
              "payload": {
                "subscription": {
                  "type": "channel.chat.message",
                  "status": "authorization_revoked"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("revocation", envelope.type)
        assertEquals("channel.chat.message", envelope.subscriptionType)
        assertEquals("authorization_revoked", envelope.revocationStatus)
    }

    @Test
    fun ignoresMessageNotificationWithoutEventPayload() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "notification"},
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": null
              }
            }
            """.trimIndent(),
        )

        assertEquals(null, envelope.event)
        assertTrue(envelope.parseError?.contains("message_id") == true)
    }

    @Test
    fun ignoresUnknownNotificationType() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "notification"},
              "payload": {
                "subscription": {"type": "channel.unknown.event"},
                "event": {"value": "ignored"}
              }
            }
            """.trimIndent(),
        )

        assertEquals("channel.unknown.event", envelope.subscriptionType)
        assertEquals(null, envelope.event)
    }

    @Test
    fun parsesFragmentsReplyFlagsAndEventSubId() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "eventsub-message-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-21T18:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "message-fragments",
                  "message_type": "text",
                  "first_message": true,
                  "returning_chatter": false,
                  "badges": [],
                  "reply": {
                    "parent_message_id": "parent-1",
                    "parent_message_body": "Parent text",
                    "parent_user_id": "300",
                    "parent_user_login": "parent",
                    "parent_user_name": "Parent"
                  },
                  "message": {
                    "text": "Hello Kappa @friend Cheer100",
                    "fragments": [
                      {"type": "text", "text": "Hello "},
                      {"type": "emote", "text": "Kappa", "emote": {
                        "id": "25", "emote_set_id": "0", "owner_id": "twitch", "format": ["static"]
                      }},
                      {"type": "text", "text": " "},
                      {"type": "mention", "text": "@friend", "mention": {
                        "user_id": "400", "user_login": "friend", "user_name": "Friend"
                      }},
                      {"type": "text", "text": " "},
                      {"type": "cheermote", "text": "Cheer100", "cheermote": {
                        "prefix": "Cheer", "bits": 100, "tier": 100
                      }}
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val message = ((envelope.event as ChatEvent.Message).message)
        assertEquals("eventsub-message-1", message.eventSubMessageId)
        assertEquals("parent-1", message.reply?.parentMessageId)
        assertTrue(message.flags.isFirstMessage)
        assertEquals(ChatMessageType.CHEER, message.type)
        assertTrue(message.fragments.any { it is ChatFragment.TwitchEmote })
        assertTrue(message.fragments.any { it is ChatFragment.Mention })
        assertTrue(message.fragments.any { it is ChatFragment.Cheermote })
    }

    @Test
    fun parsesSubscriptionNotification() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-sub-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-21T19:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.notification"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "chatter_is_anonymous": false,
                  "color": "#AA00FF",
                  "badges": [{"set_id": "subscriber", "id": "12", "info": "12"}],
                  "system_message": "Viewer subscribed at Tier 1.",
                  "message_id": "notice-sub-1",
                  "message": {"text": "Glad to be here!", "fragments": [{"type": "text", "text": "Glad to be here!"}]},
                  "notice_type": "sub",
                  "sub": {"sub_plan": "1000", "is_prime": false, "duration_months": 1}
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        assertEquals(ChatMessageType.SUBSCRIPTION, message.type)
        assertEquals("1000", message.notice?.subTier)
        assertEquals(1, message.notice?.durationMonths)
        assertEquals("Glad to be here!", message.notice?.userMessage)
        assertTrue(message.flags.isSystem)
        assertTrue(message.text.contains("Glad to be here!"))
    }

    @Test
    fun parsesResubGiftRaidAndAnnouncementNotifications() {
        val cases = listOf(
            Triple(
                "resub",
                "\"resub\": {\"sub_tier\": \"2000\", \"cumulative_months\": 18, \"duration_months\": 1}",
                ChatMessageType.RESUBSCRIPTION,
            ),
            Triple(
                "community_sub_gift",
                "\"community_sub_gift\": {\"sub_tier\": \"1000\", \"total\": 5, \"cumulative_total\": 25}",
                ChatMessageType.GIFT_SUBSCRIPTION,
            ),
            Triple(
                "raid",
                "\"raid\": {\"user_id\": \"300\", \"user_login\": \"raider\", \"user_name\": \"Raider\", \"viewer_count\": 321}",
                ChatMessageType.RAID,
            ),
            Triple(
                "announcement",
                "\"announcement\": {\"color\": \"PURPLE\"}",
                ChatMessageType.ANNOUNCEMENT,
            ),
        )

        cases.forEachIndexed { index, (noticeType, detail, expectedType) ->
            val messageText = if (noticeType == "announcement") "Стрим начнётся через пять минут" else ""
            val envelope = EventSubParser.parseEnvelope(
                """
                {
                  "metadata": {"message_type": "notification", "message_timestamp": "2026-07-21T19:00:00Z"},
                  "payload": {
                    "subscription": {"type": "channel.chat.notification"},
                    "event": {
                      "broadcaster_user_id": "100",
                      "broadcaster_user_login": "channel",
                      "chatter_user_id": "200",
                      "chatter_user_login": "viewer",
                      "chatter_user_name": "Viewer",
                      "chatter_is_anonymous": false,
                      "badges": [],
                      "system_message": "System notice",
                      "message_id": "notice-$index",
                      "message": {"text": "$messageText", "fragments": []},
                      "notice_type": "$noticeType",
                      $detail
                    }
                  }
                }
                """.trimIndent(),
            )

            val message = (envelope.event as ChatEvent.Message).message
            assertEquals(expectedType, message.type)
            if (expectedType == ChatMessageType.ANNOUNCEMENT) {
                assertEquals("Стрим начнётся через пять минут", message.text)
                assertFalse(message.flags.isSystem)
                assertEquals("Viewer", message.userDisplayName)
            }
        }
    }

    @Test
    fun parsesGiftRecipientAndGifterDetails() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "notification", "message_timestamp": "2026-07-21T19:00:00Z"},
              "payload": {
                "subscription": {"type": "channel.chat.notification"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "gifter",
                  "chatter_user_name": "Gifter",
                  "chatter_is_anonymous": false,
                  "badges": [],
                  "system_message": "Gifter gifted a Tier 1 sub to Recipient!",
                  "message_id": "gift-1",
                  "message": {"text": "Enjoy!", "fragments": [{"type": "text", "text": "Enjoy!"}]},
                  "notice_type": "sub_gift",
                  "sub_gift": {
                    "duration_months": 1,
                    "cumulative_total": 12,
                    "recipient_user_id": "300",
                    "recipient_user_name": "Recipient",
                    "recipient_user_login": "recipient",
                    "sub_tier": "1000",
                    "community_gift_id": "community-1"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        assertEquals(ChatMessageType.GIFT_SUBSCRIPTION, message.type)
        assertEquals("Recipient", message.notice?.recipientUserName)
        assertEquals("community-1", message.notice?.communityGiftId)
        assertEquals(12, message.notice?.cumulativeGiftTotal)
        assertEquals("Enjoy!", message.notice?.userMessage)
    }

    @Test
    fun parsesAnonymousGiftNotificationWithoutChatterId() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "notification", "message_timestamp": "2026-07-21T19:00:00Z"},
              "payload": {
                "subscription": {"type": "channel.chat.notification"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": null,
                  "chatter_user_login": null,
                  "chatter_user_name": null,
                  "chatter_is_anonymous": true,
                  "badges": null,
                  "system_message": "An anonymous user gifted 5 subs.",
                  "message_id": "anonymous-gift",
                  "message": null,
                  "notice_type": "community_sub_gift",
                  "community_sub_gift": {"sub_tier": "1000", "total": 5}
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        assertEquals(ChatMessageType.GIFT_SUBSCRIPTION, message.type)
        assertEquals("Аноним", message.userDisplayName)
        assertEquals(5, message.notice?.giftTotal)
    }

    @Test
    fun stripsGeneratedParentMentionFromReplyButKeepsOtherMentions() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {"message_type": "notification", "message_timestamp": "2026-07-21T20:00:00Z"},
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "reply-message",
                  "message_type": "text",
                  "badges": [],
                  "reply": {
                    "parent_message_id": "parent-1",
                    "parent_user_id": "300",
                    "parent_user_login": "parent",
                    "parent_user_name": "Parent"
                  },
                  "message": {
                    "text": "@parent привет @friend",
                    "fragments": [
                      {"type": "mention", "text": "@parent", "mention": {
                        "user_id": "300", "user_login": "parent", "user_name": "Parent"
                      }},
                      {"type": "text", "text": " привет "},
                      {"type": "mention", "text": "@friend", "mention": {
                        "user_id": "400", "user_login": "friend", "user_name": "Friend"
                      }}
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        assertEquals("привет @friend", message.text)
        assertEquals("привет ", message.fragments.first().text)
        assertTrue(message.fragments.last() is ChatFragment.Mention)
        assertEquals("@friend", message.fragments.last().text)
    }

    @Test
    fun parsesTwitchGifFragment() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-gif",
                "message_type": "notification",
                "message_timestamp": "2026-07-22T10:00:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat.message"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "chatter_user_id": "200",
                  "chatter_user_login": "viewer",
                  "chatter_user_name": "Viewer",
                  "message_id": "message-gif",
                  "message_type": "text",
                  "badges": [],
                  "message": {
                    "text": "FunnyGif",
                    "fragments": [
                      {
                        "type": "gif",
                        "text": "FunnyGif",
                        "gif": {
                          "gif_id": "gif-1",
                          "url": "https://example.test/gif-1.gif"
                        }
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val message = (envelope.event as ChatEvent.Message).message
        val gif = message.fragments.single() as ChatFragment.Gif
        assertEquals("gif-1", gif.gifId)
        assertEquals("https://example.test/gif-1.gif", gif.url)
    }

    @Test
    fun parsesAutoModHoldV2WithBlockedBoundary() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-automod-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:00:00Z"
              },
              "payload": {
                "subscription": {"type": "automod.message.hold", "version": "2"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "broadcaster_user_name": "Channel",
                  "user_id": "200",
                  "user_login": "viewer",
                  "user_name": "Viewer",
                  "message_id": "held-1",
                  "message": {
                    "text": "hello badword world",
                    "fragments": [{"type": "text", "text": "hello badword world"}]
                  },
                  "reason": "automod",
                  "automod": {
                    "category": "aggression",
                    "level": 3,
                    "boundaries": [{"start_pos": 6, "end_pos": 12}]
                  },
                  "held_at": "2026-07-23T10:00:00Z"
                }
              }
            }
            """.trimIndent(),
        )

        val event = envelope.event as ChatEvent.AutoModHeld
        assertEquals("held-1", event.message.messageId)
        assertEquals("aggression", event.message.category)
        assertEquals(3, event.message.level)
        assertEquals(6, event.message.boundaries.single().start)
        assertEquals(12, event.message.boundaries.single().endInclusive)
    }

    @Test
    fun parsesAutoModUpdateV2() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "event-automod-2",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:01:00Z"
              },
              "payload": {
                "subscription": {"type": "automod.message.update", "version": "2"},
                "event": {
                  "broadcaster_user_id": "100",
                  "user_id": "200",
                  "user_login": "viewer",
                  "user_name": "Viewer",
                  "message_id": "held-update-1",
                  "message": {"text": "hello", "fragments": []},
                  "status": "approved",
                  "moderator_user_id": "300",
                  "moderator_user_login": "mod",
                  "moderator_user_name": "Mod"
                }
              }
            }
            """.trimIndent(),
        )

        val event = envelope.event as ChatEvent.AutoModUpdated
        assertEquals(io.ferventio.app.domain.AutoModMessageStatus.APPROVED, event.message.status)
        assertEquals("mod", event.message.decidedByUserLogin)
    }

    @Test
    fun parsesChannelModerateV2Ban() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "moderation-event-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:02:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.moderate", "version": "2"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "broadcaster_user_name": "Channel",
                  "moderator_user_id": "300",
                  "moderator_user_login": "mod",
                  "moderator_user_name": "Mod",
                  "action": "ban",
                  "ban": {
                    "user_id": "200",
                    "user_login": "viewer",
                    "user_name": "Viewer",
                    "reason": "spam"
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val event = envelope.event as ChatEvent.ModerationPerformed
        assertEquals("moderation-event-1", event.action.id)
        assertEquals("ban", event.action.action)
        assertEquals("viewer", event.action.targetUserLogin)
        assertEquals("spam", event.action.reason)
    }


    @Test
    fun parsesChannelModerateV2WarnAndTimeoutDuration() {
        val warnEnvelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "moderation-warn-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:03:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.moderate", "version": "2"},
                "event": {
                  "broadcaster_user_id": "100",
                  "broadcaster_user_login": "channel",
                  "moderator_user_id": "300",
                  "moderator_user_login": "mod",
                  "action": "warn",
                  "warn": {
                    "user_id": "200",
                    "user_login": "viewer",
                    "user_name": "Viewer",
                    "reason": "stop spam"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val warn = (warnEnvelope.event as ChatEvent.ModerationPerformed).action
        assertEquals("warn", warn.action)
        assertEquals("viewer", warn.targetUserLogin)
        assertEquals("stop spam", warn.reason)

        val timeoutEnvelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "moderation-timeout-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:04:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.moderate", "version": "2"},
                "event": {
                  "broadcaster_user_id": "100",
                  "moderator_user_id": "300",
                  "action": "timeout",
                  "timeout": {
                    "user_id": "200",
                    "user_login": "viewer",
                    "expires_at": "2026-07-23T10:14:00Z"
                  }
                }
              }
            }
            """.trimIndent(),
        )
        val timeout = (timeoutEnvelope.event as ChatEvent.ModerationPerformed).action
        assertEquals(600, timeout.durationSeconds)
    }

    @Test
    fun parsesChatSettingsUpdateIncludingUniqueMode() {
        val envelope = EventSubParser.parseEnvelope(
            """
            {
              "metadata": {
                "message_id": "chat-settings-1",
                "message_type": "notification",
                "message_timestamp": "2026-07-23T10:05:00Z"
              },
              "payload": {
                "subscription": {"type": "channel.chat_settings.update", "version": "1"},
                "event": {
                  "broadcaster_user_id": "100",
                  "slow_mode": true,
                  "slow_mode_wait_time_seconds": 15,
                  "follower_mode": true,
                  "follower_mode_duration_minutes": 30,
                  "subscriber_mode": false,
                  "emote_mode": true,
                  "unique_chat_mode": true
                }
              }
            }
            """.trimIndent(),
        )

        val settings = (envelope.event as ChatEvent.ChatSettingsUpdated).settings
        assertTrue(settings.slowMode)
        assertEquals(15, settings.slowModeWaitSeconds)
        assertEquals(30, settings.followerModeDurationMinutes)
        assertTrue(settings.emoteMode)
        assertTrue(settings.uniqueChatMode)
    }
}
