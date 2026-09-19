package io.ferventio.shared.settings

import io.ferventio.app.domain.HighlightRule
import io.ferventio.app.domain.IgnoreRule
import io.ferventio.app.domain.MessageRuleCodec
import kotlin.Throws

data class AnonymousMessageRulesRaw(
    val highlights: String? = null,
    val ignores: String? = null,
)

/** Device-local highlight/ignore storage used before a Twitch account is authorized. */
interface AnonymousMessageRulesStore {
    fun load(): AnonymousMessageRulesRaw

    fun save(raw: AnonymousMessageRulesRaw)
}

/** Keeps guest message rules compatible with Android's established preference JSON. */
class AnonymousMessageRulesCoordinator(
    private val store: AnonymousMessageRulesStore = InMemoryAnonymousMessageRulesStore(),
) {
    @Throws(Exception::class)
    fun restore(state: SharedMessageRulesStateHolder): SharedMessageRulesSnapshot {
        val raw = store.load()
        val snapshot = decode(raw)
        state.restore(snapshot)
        return snapshot
    }

    @Throws(Exception::class)
    fun upsertHighlight(
        rule: HighlightRule,
        state: SharedMessageRulesStateHolder,
    ): SharedMessageRulesSnapshot = mutate(state) { next ->
        next.upsertHighlight(rule)
    }

    @Throws(Exception::class)
    fun deleteHighlight(
        ruleId: String,
        state: SharedMessageRulesStateHolder,
    ): SharedMessageRulesSnapshot = mutate(state) { next ->
        next.deleteHighlight(ruleId)
    }

    @Throws(Exception::class)
    fun upsertIgnore(
        rule: IgnoreRule,
        state: SharedMessageRulesStateHolder,
    ): SharedMessageRulesSnapshot = mutate(state) { next ->
        next.upsertIgnore(rule)
    }

    @Throws(Exception::class)
    fun deleteIgnore(
        ruleId: String,
        state: SharedMessageRulesStateHolder,
    ): SharedMessageRulesSnapshot = mutate(state) { next ->
        next.deleteIgnore(ruleId)
    }

    private inline fun mutate(
        state: SharedMessageRulesStateHolder,
        block: (SharedMessageRulesStateHolder) -> Unit,
    ): SharedMessageRulesSnapshot {
        state.markSaveStarted()
        return try {
            val next = SharedMessageRulesStateHolder(state.snapshot)
            block(next)
            val snapshot = normalize(next.snapshot)
            store.save(encode(snapshot))
            state.markSaveSucceeded(snapshot)
            snapshot
        } catch (error: Exception) {
            state.markSaveFailed(error.message)
            throw error
        }
    }

    private fun normalize(snapshot: SharedMessageRulesSnapshot): SharedMessageRulesSnapshot = decode(
        encode(snapshot),
    )

    private fun encode(snapshot: SharedMessageRulesSnapshot): AnonymousMessageRulesRaw =
        AnonymousMessageRulesRaw(
            highlights = MessageRuleCodec.encodeHighlights(snapshot.highlightRules),
            ignores = MessageRuleCodec.encodeIgnores(snapshot.ignoreRules),
        )

    private fun decode(raw: AnonymousMessageRulesRaw): SharedMessageRulesSnapshot =
        SharedMessageRulesSnapshot(
            highlightRules = MessageRuleCodec.decodeHighlights(raw.highlights),
            ignoreRules = MessageRuleCodec.decodeIgnores(raw.ignores),
        )
}

internal const val ANONYMOUS_HIGHLIGHT_RULES_KEY = "highlight_rules_json"
internal const val ANONYMOUS_IGNORE_RULES_KEY = "ignore_rules_json"

private class InMemoryAnonymousMessageRulesStore : AnonymousMessageRulesStore {
    private var raw = AnonymousMessageRulesRaw()

    override fun load(): AnonymousMessageRulesRaw = raw

    override fun save(raw: AnonymousMessageRulesRaw) {
        this.raw = raw
    }
}
