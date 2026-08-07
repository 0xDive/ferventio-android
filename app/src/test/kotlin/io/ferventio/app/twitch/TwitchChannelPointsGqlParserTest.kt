package io.ferventio.app.twitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TwitchChannelPointsGqlParserTest {
    @Test
    fun `parses balance and custom rewards from nested context`() {
        val context = TwitchChannelPointsGqlParser.parseContext(
            """
            {
              "data": {
                "community": {
                  "channel": {
                    "self": {"communityPoints":{"balance":12345}},
                    "communityPointsSettings": {
                      "customRewards": [
                        {
                          "id":"reward-1",
                          "title":"Hydrate",
                          "prompt":"Drink water",
                          "cost":500,
                          "isEnabled":true,
                          "isUserInputRequired":false,
                          "image":{"url4x":"https://example.test/reward.png"}
                        },
                        {
                          "id":"reward-2",
                          "title":"Say something",
                          "prompt":"",
                          "cost":1000,
                          "isEnabled":false,
                          "isUserInputRequired":true,
                          "defaultImage":{"url":"https://example.test/default.png"}
                        }
                      ]
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals(12345, context.balance)
        assertEquals(2, context.rewards.size)
        assertEquals("Hydrate", context.rewards[0].title)
        assertEquals(500, context.rewards[0].cost)
        assertTrue(context.rewards[0].enabled)
        assertFalse(context.rewards[0].userInputRequired)
        assertEquals("https://example.test/reward.png", context.rewards[0].imageUrl)
        assertFalse(context.rewards[1].enabled)
        assertTrue(context.rewards[1].userInputRequired)
    }

    @Test
    fun `context tolerates missing balance and rewards`() {
        val context = TwitchChannelPointsGqlParser.parseContext("""{"data":{"community":null}}""")

        assertNull(context.balance)
        assertTrue(context.rewards.isEmpty())
    }

    @Test
    fun `successful redemption returns id`() {
        val redemption = TwitchChannelPointsGqlParser.parseRedemption(
            """{"data":{"redeemCommunityPointsCustomReward":{"error":null,"redemption":{"id":"redemption-1"}}}}""",
        )

        assertEquals("redemption-1", redemption.id)
    }

    @Test(expected = TwitchChannelPointsRedeemException::class)
    fun `redemption surfaces Twitch error code`() {
        TwitchChannelPointsGqlParser.parseRedemption(
            """{"data":{"redeemCommunityPointsCustomReward":{"error":{"code":"NOT_ENOUGH_POINTS"},"redemption":null}}}""",
        )
    }
}
