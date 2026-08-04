package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ChannelOrderTest {
    private val channels = listOf(
        ChatChannel("1", "one", "One"),
        ChatChannel("2", "two", "Two"),
        ChatChannel("3", "three", "Three"),
    )

    @Test
    fun `moves a channel to the exact pager position`() {
        val moved = ChannelOrder.move(channels, channelId = "3", targetIndex = 0)
        assertEquals(listOf("3", "1", "2"), moved.map(ChatChannel::id))
    }

    @Test
    fun `clamps target and keeps unknown channel untouched`() {
        assertEquals(listOf("2", "3", "1"), ChannelOrder.move(channels, "1", 99).map(ChatChannel::id))
        assertSame(channels, ChannelOrder.move(channels, "missing", 0))
    }
}
