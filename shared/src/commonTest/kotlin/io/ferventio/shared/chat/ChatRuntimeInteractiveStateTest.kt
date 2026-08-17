package io.ferventio.shared.chat

import io.ferventio.app.domain.InteractiveChatOverlayEvent
import io.ferventio.app.domain.PollChoice
import io.ferventio.app.domain.PollOverlay
import io.ferventio.app.domain.PollStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatRuntimeInteractiveStateTest {
    @Test
    fun interactiveSnapshotLivesWithChannelAndClearsOnRemoval() {
        val state = ChatRuntimeStateHolder()
        val poll = PollOverlay(
            id = "poll-1",
            channelId = "channel-1",
            title = "Question",
            choices = listOf(PollChoice("a", "A", votes = 2)),
            status = PollStatus.ACTIVE,
            startedAtMillis = 1_000L,
            updatedAtMillis = 2_000L,
        )

        state.applyInteractive(InteractiveChatOverlayEvent.PollSnapshot(poll))
        assertEquals(poll, state.interactiveState.pollsByChannel["channel-1"])

        state.removeChannel("channel-1")
        assertNull(state.interactiveState.pollsByChannel["channel-1"])
    }

    @Test
    fun clearResetsInteractiveState() {
        val state = ChatRuntimeStateHolder()
        state.applyInteractive(
            InteractiveChatOverlayEvent.PollSnapshot(
                PollOverlay(
                    id = "poll-1",
                    channelId = "channel-1",
                    title = "Question",
                    choices = emptyList(),
                    status = PollStatus.COMPLETED,
                    startedAtMillis = 1_000L,
                    endedAtMillis = 2_000L,
                    updatedAtMillis = 2_000L,
                ),
            ),
        )

        state.clear()

        assertEquals(emptyMap(), state.interactiveState.pollsByChannel)
        assertEquals(emptyMap(), state.interactiveState.predictionsByChannel)
    }
}
