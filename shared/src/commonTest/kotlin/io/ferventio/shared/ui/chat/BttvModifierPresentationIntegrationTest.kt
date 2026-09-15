package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BttvModifierPresentationIntegrationTest {
    @Test
    fun projectsPrefixModifiersOntoTwitchEmoteAndRemovesControlTokens() {
        val message = ChatMessage(
            id = "message",
            channelId = "channel",
            channelLogin = "channel",
            author = ChatAuthor(id = "user", login = "user", displayName = "User"),
            text = "hello p! s! Kappa world",
            fragments = listOf(
                ChatFragment.Text("hello p! s! "),
                ChatFragment.TwitchEmote(
                    text = "Kappa",
                    emoteId = "25",
                    formats = setOf("static"),
                ),
                ChatFragment.Text(" world"),
            ),
            timestamp = "2026-08-17T00:00:00Z",
            timestampMillis = 0L,
        )

        val presentation = projectChatMessage(
            message = message,
            deletedPlaceholder = "[deleted]",
            animatedMediaSupported = false,
        )

        assertEquals(listOf("hello ", "Kappa", " world"), presentation.segments.map { it.text })
        assertEquals(
            setOf(BttvEmoteModifier.PARTY, BttvEmoteModifier.SHAKE),
            presentation.segments[1].bttvModifiers,
        )
        assertTrue(presentation.segments.none { "p!" in it.text || "s!" in it.text })
    }

    @Test
    fun keepsModifierLikeTextWhenNoRenderableEmoteFollows() {
        val message = ChatMessage(
            id = "message",
            channelId = "channel",
            channelLogin = "channel",
            author = ChatAuthor(id = "user", login = "user", displayName = "User"),
            text = "w! this is text",
            fragments = listOf(ChatFragment.Text("w! this is text")),
            timestamp = "2026-08-17T00:00:00Z",
            timestampMillis = 0L,
        )

        val presentation = projectChatMessage(
            message = message,
            deletedPlaceholder = "[deleted]",
        )

        assertEquals("w! this is text", presentation.segments.single().text)
        assertTrue(presentation.segments.single().bttvModifiers.isEmpty())
    }
}
