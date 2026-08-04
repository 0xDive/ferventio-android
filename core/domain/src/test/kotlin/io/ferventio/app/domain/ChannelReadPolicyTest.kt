package io.ferventio.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelReadPolicyTest {
    @Test
    fun `background or transiently composed channel cannot be marked read`() {
        assertFalse(ChannelReadPolicy.canMarkRead("other", setOf("active")))
        assertTrue(ChannelReadPolicy.canMarkRead("active", setOf("active")))
    }

    @Test
    fun `visible channel is live only while it is at the bottom`() {
        assertTrue(ChannelReadPolicy.isLiveVisible("active", setOf("active"), null))
        assertTrue(
            ChannelReadPolicy.isLiveVisible(
                "active",
                setOf("active"),
                ChatScrollPosition("active", firstVisibleItemIndex = 10, firstVisibleItemScrollOffset = 0),
            ),
        )
        assertFalse(
            ChannelReadPolicy.isLiveVisible(
                "active",
                setOf("active"),
                ChatScrollPosition(
                    "active",
                    firstVisibleItemIndex = 2,
                    firstVisibleItemScrollOffset = 0,
                    isAtBottom = false,
                ),
            ),
        )
        assertFalse(ChannelReadPolicy.isLiveVisible("other", setOf("active"), null))
    }
}
