package io.ferventio.shared.settings

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.HighlightRuleType
import io.ferventio.app.domain.IgnoreDisplayMode
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.IgnoreRuleType
import io.ferventio.app.domain.MessageRuleCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnonymousMessageRulesCoordinatorTest {
    @Test
    fun restoreReadsAndroidCompatibleRuleJson() {
        val highlight = highlight("h1", "hello")
        val ignore = ignore("i1", "spam")
        val store = RecordingStore(
            AnonymousMessageRulesRaw(
                highlights = MessageRuleCodec.encodeHighlights(listOf(highlight)),
                ignores = MessageRuleCodec.encodeIgnores(listOf(ignore)),
            ),
        )
        val state = SharedMessageRulesStateHolder()

        AnonymousMessageRulesCoordinator(store).restore(state)

        assertEquals(listOf(highlight), state.highlightRules)
        assertEquals(listOf(ignore), state.ignoreRules)
    }

    @Test
    fun mutationsPersistBothRuleSets() {
        val store = RecordingStore()
        val state = SharedMessageRulesStateHolder()
        val coordinator = AnonymousMessageRulesCoordinator(store)

        coordinator.upsertHighlight(highlight("h1", "hello"), state)
        coordinator.upsertIgnore(ignore("i1", "spam"), state)
        coordinator.deleteHighlight("h1", state)

        assertEquals(emptyList(), state.highlightRules)
        assertEquals(listOf("i1"), state.ignoreRules.map(IgnoreRule::id))
        assertEquals(
            emptyList(),
            MessageRuleCodec.decodeHighlights(store.raw.highlights),
        )
        assertEquals(
            listOf("i1"),
            MessageRuleCodec.decodeIgnores(store.raw.ignores).map(IgnoreRule::id),
        )
    }

    @Test
    fun failedDurableWriteLeavesPublishedRulesUnchanged() {
        val original = highlight("h1", "hello")
        val store = RecordingStore(
            AnonymousMessageRulesRaw(
                highlights = MessageRuleCodec.encodeHighlights(listOf(original)),
                ignores = MessageRuleCodec.encodeIgnores(emptyList()),
            ),
        )
        val state = SharedMessageRulesStateHolder(
            SharedMessageRulesSnapshot(highlightRules = listOf(original)),
        )
        val coordinator = AnonymousMessageRulesCoordinator(store)
        store.failOnSave = true

        assertFailsWith<IllegalStateException> {
            coordinator.upsertIgnore(ignore("i1", "spam"), state)
        }

        assertEquals(listOf(original), state.highlightRules)
        assertEquals(emptyList(), state.ignoreRules)
        assertEquals(SharedSettingsSaveStatus.FAILED, state.saveStatus)
    }

    private fun highlight(id: String, pattern: String) = HighlightRule(
        id = id,
        type = HighlightRuleType.WORD,
        pattern = pattern,
    )

    private fun ignore(id: String, pattern: String) = IgnoreRule(
        id = id,
        type = IgnoreRuleType.WORD,
        pattern = pattern,
        displayMode = IgnoreDisplayMode.COLLAPSE,
    )

    private class RecordingStore(
        var raw: AnonymousMessageRulesRaw = AnonymousMessageRulesRaw(),
    ) : AnonymousMessageRulesStore {
        var failOnSave = false

        override fun load(): AnonymousMessageRulesRaw = raw

        override fun save(raw: AnonymousMessageRulesRaw) {
            if (failOnSave) error("disk full")
            this.raw = raw
        }
    }
}
