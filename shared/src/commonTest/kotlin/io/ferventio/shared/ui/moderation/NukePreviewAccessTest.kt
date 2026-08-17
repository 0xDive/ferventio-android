package io.ferventio.shared.ui.moderation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NukePreviewAccessTest {
    @Test
    fun moderatorChannelCanOpenPreview() {
        assertTrue(canPreviewNuke("channel-1", setOf("channel-1")))
    }

    @Test
    fun nonModeratorChannelCannotOpenPreview() {
        assertFalse(canPreviewNuke("channel-1", setOf("channel-2")))
        assertFalse(canPreviewNuke("", setOf("")))
    }
}
