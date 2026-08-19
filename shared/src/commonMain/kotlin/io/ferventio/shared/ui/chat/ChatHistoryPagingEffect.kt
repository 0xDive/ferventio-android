package io.ferventio.shared.ui.chat

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ChatRepeatCollapseConfig
import io.ferventio.app.domain.ChatRepeatCollapser
import io.ferventio.shared.history.ChatHistoryPagingGate
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

private const val HISTORY_PREFETCH_ITEMS = 4
private const val HISTORY_PAGE_SIZE = 200

private data class HistoryTopSignal(
    val nearTop: Boolean,
    val oldestTimestampMillis: Long?,
    val oldestMessageId: String?,
)

/** Loads durable history as the user approaches the top while preserving the visible anchor. */
@Composable
internal fun ChatHistoryPagingEffect(
    channel: ChatChannel,
    listState: LazyListState,
    isFollowingTail: Boolean,
    onPagingStarted: () -> Unit,
) {
    val runtime = LocalFerventioRuntimeState.current
    val history = runtime.history
    val chat = runtime.chat
    val preferences = runtime.settings.preferences
    val gate = remember(channel.id, history) { ChatHistoryPagingGate() }

    LaunchedEffect(
        channel.id,
        history,
        preferences.localHistoryEnabled,
        preferences.showSystemMessages,
        preferences.repeatCollapseEnabled,
        listState,
    ) {
        val historyStore = history ?: return@LaunchedEffect
        if (!preferences.localHistoryEnabled) return@LaunchedEffect

        snapshotFlow {
            val oldest = chat.messages(channel.id).firstOrNull()
            HistoryTopSignal(
                nearTop = !isFollowingTail &&
                    listState.layoutInfo.totalItemsCount > 0 &&
                    listState.firstVisibleItemIndex <= HISTORY_PREFETCH_ITEMS,
                oldestTimestampMillis = oldest?.timestampMillis,
                oldestMessageId = oldest?.id,
            )
        }
            .distinctUntilChanged()
            .collect { signal ->
                if (!signal.nearTop) return@collect
                val oldestTimestampMillis = signal.oldestTimestampMillis ?: return@collect
                val oldestMessageId = signal.oldestMessageId ?: return@collect
                val boundary = gate.tryStart(oldestTimestampMillis, oldestMessageId) ?: return@collect
                val anchorId = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
                val anchorOffset = listState.firstVisibleItemScrollOffset
                onPagingStarted()

                try {
                    val existingIds = chat.messages(channel.id).mapTo(hashSetOf(), ChatMessage::id)
                    val older = historyStore.loadOlderMessages(
                        channelId = channel.id,
                        beforeTimestampMillis = boundary.timestampMillis,
                        beforeMessageId = boundary.messageId,
                        limit = HISTORY_PAGE_SIZE,
                    ).filterNot { it.id in existingIds }

                    gate.finish(boundary, older.size)
                    if (older.isEmpty()) return@collect

                    chat.prependHistory(channel.id, older)
                    val anchor = anchorId ?: return@collect

                    // Let LazyColumn consume the new keyed items before restoring the old viewport.
                    withFrameNanos { }
                    val sourceAfter = chat.messages(channel.id).let { canonical ->
                        if (preferences.showSystemMessages) canonical
                        else canonical.filterNot(ChatMessage::isSystem)
                    }
                    val collapsePlan = ChatRepeatCollapser.build(
                        messages = sourceAfter,
                        config = ChatRepeatCollapseConfig(enabled = preferences.repeatCollapseEnabled),
                    )
                    val visibleAfter = sourceAfter.filter { it.id in collapsePlan.visibleMessageIds }
                    val anchorIndex = resolveHistoryAnchorIndex(
                        visibleMessages = visibleAfter,
                        sourceMessages = sourceAfter,
                        anchorMessageId = anchor,
                    ) ?: return@collect
                    listState.scrollToItem(anchorIndex, anchorOffset)
                } catch (error: CancellationException) {
                    gate.cancel(boundary)
                    throw error
                } catch (_: Exception) {
                    // History paging is best-effort and must never interrupt the live timeline.
                    gate.cancel(boundary)
                }
            }
    }
}

private fun resolveHistoryAnchorIndex(
    visibleMessages: List<ChatMessage>,
    sourceMessages: List<ChatMessage>,
    anchorMessageId: String,
): Int? {
    val directIndex = visibleMessages.indexOfFirst { it.id == anchorMessageId }
    if (directIndex >= 0) return directIndex

    // Loading older messages can change a repeat-collapse group. Keep the viewport on the nearest
    // visible representative instead of jumping to the start of the newly inserted page.
    val sourceIndex = sourceMessages.indexOfFirst { it.id == anchorMessageId }
    if (sourceIndex < 0) return null
    val visibleIds = visibleMessages.mapTo(hashSetOf(), ChatMessage::id)
    val representative = sourceMessages
        .subList(0, sourceIndex + 1)
        .asReversed()
        .firstOrNull { it.id in visibleIds }
        ?: sourceMessages.drop(sourceIndex + 1).firstOrNull { it.id in visibleIds }
        ?: return null
    return visibleMessages.indexOfFirst { it.id == representative.id }.takeIf { it >= 0 }
}
