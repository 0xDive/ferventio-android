package io.ferventio.shared.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatHistoryPagingDecisionTest {
    @Test
    fun `paging is paused while exact message navigation is pending`() {
        assertFalse(
            shouldRequestOlderHistory(
                isFollowingTail = false,
                hasNavigationTarget = true,
                totalItemsCount = 50,
                firstVisibleItemIndex = 0,
            ),
        )
    }

    @Test
    fun `paging starts near top after navigation target is consumed`() {
        assertTrue(
            shouldRequestOlderHistory(
                isFollowingTail = false,
                hasNavigationTarget = false,
                totalItemsCount = 50,
                firstVisibleItemIndex = 4,
            ),
        )
    }

    @Test
    fun `paging stays idle at live tail or away from prefetch window`() {
        assertFalse(
            shouldRequestOlderHistory(
                isFollowingTail = true,
                hasNavigationTarget = false,
                totalItemsCount = 50,
                firstVisibleItemIndex = 0,
            ),
        )
        assertFalse(
            shouldRequestOlderHistory(
                isFollowingTail = false,
                hasNavigationTarget = false,
                totalItemsCount = 50,
                firstVisibleItemIndex = 5,
            ),
        )
        assertFalse(
            shouldRequestOlderHistory(
                isFollowingTail = false,
                hasNavigationTarget = false,
                totalItemsCount = 0,
                firstVisibleItemIndex = 0,
            ),
        )
    }
}
