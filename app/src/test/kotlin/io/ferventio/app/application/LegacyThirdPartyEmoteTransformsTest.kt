package io.ferventio.app.application

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LegacyThirdPartyEmoteTransformsTest {
    @Test
    fun messageWithoutThirdPartyFragmentsIsReused() {
        val message = message(
            fragments = listOf(
                ChatFragment.Text("hello "),
                ChatFragment.TwitchEmote(
                    text = "Kappa",
                    emoteId = "1",
                ),
            ),
        )

        val result = stripLegacyThirdPartyEmoteFragments(message)

        assertTrue(result === message)
        assertTrue(result.fragments === message.fragments)
    }

    @Test
    fun thirdPartyFragmentsAreRestoredToSourceTextLazily() {
        val twitch = ChatFragment.TwitchEmote(
            text = "Kappa",
            emoteId = "1",
        )
        val message = message(
            fragments = listOf(
                twitch,
                ChatFragment.ThirdPartyEmote(
                    text = "Overlay",
                    emoteId = "overlay",
                    provider = "7tv",
                    zeroWidth = true,
                ),
                ChatFragment.Text(" tail"),
            ),
        )

        val result = stripLegacyThirdPartyEmoteFragments(message)

        assertTrue(result !== message)
        assertEquals(
            listOf(
                twitch,
                ChatFragment.Text(" Overlay"),
                ChatFragment.Text(" tail"),
            ),
            result.fragments,
        )
    }

    @Test
    fun thirdPartyTextMergesWithPreviousTextLikeLegacyImplementation() {
        val message = message(
            fragments = listOf(
                ChatFragment.Text("hello "),
                ChatFragment.ThirdPartyEmote(
                    text = "OMEGALUL",
                    emoteId = "emote",
                    provider = "bttv",
                ),
            ),
        )

        val result = stripLegacyThirdPartyEmoteFragments(message)

        assertEquals(
            listOf(ChatFragment.Text("hello OMEGALUL")),
            result.fragments,
        )
    }

    private fun message(
        fragments: List<ChatFragment>,
    ) = ChatMessage(
        id = "message",
        channelId = "channel",
        channelLogin = "channel",
        author = ChatAuthor(
            id = "user",
            login = "user",
            displayName = "User",
        ),
        text = fragments.joinToString("") { it.text },
        fragments = fragments,
        timestamp = "2026-09-21T00:00:00Z",
    )
}
