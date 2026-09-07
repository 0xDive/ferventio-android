package io.ferventio.shared.ui.chat

import io.ferventio.app.domain.ChatAuthor
import io.ferventio.app.domain.ChatFragment
import io.ferventio.app.domain.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatMessageZeroWidthPresentationTest {
    @Test
    fun preservesThirdPartyZeroWidthMetadata() {
        val presentation = projectChatMessage(
            message = ChatMessage(
                id = "message",
                channelId = "channel",
                channelLogin = "channel-login",
                author = ChatAuthor(
                    id = "user",
                    login = "viewer",
                    displayName = "Viewer",
                ),
                text = "hat",
                fragments = listOf(
                    ChatFragment.ThirdPartyEmote(
                        text = "hat",
                        emoteId = "overlay",
                        provider = "betterttv",
                        imageUrl = "https://cdn.test/hat.png",
                        zeroWidth = true,
                    ),
                ),
                timestamp = "2026-08-17T00:00:00Z",
            ),
            deletedPlaceholder = "[deleted]",
        )

        val segment = presentation.segments.single()
        assertEquals(ChatMessageSegmentKind.THIRD_PARTY_EMOTE, segment.kind)
        assertEquals("https://cdn.test/hat.png", segment.imageUrl)
        assertTrue(segment.zeroWidth)
    }
}
