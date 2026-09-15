package io.ferventio.shared.ui.chat

internal enum class BttvEmoteModifier {
    WIDE,
    FLIP_HORIZONTAL,
    FLIP_VERTICAL,
    ZERO_SPACE,
    CURSED,
    ROTATE_LEFT,
    ROTATE_RIGHT,
    PARTY,
    SHAKE,
}

internal data class BttvEmoteTransform(
    val wide: Boolean = false,
    val zeroSpace: Boolean = false,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val rotationDegrees: Float = 0f,
    val cursed: Boolean = false,
    val party: Boolean = false,
    val shake: Boolean = false,
)

internal fun BttvEmoteTransform(
    modifiers: Set<BttvEmoteModifier>,
): BttvEmoteTransform {
    // BetterTTV expresses flip/rotate as separate CSS classes that all assign `transform`.
    // Preserve the source stylesheet cascade: rotate-right > rotate-left > flip-vertical >
    // flip-horizontal. Wide/zero-space/filter/animation modifiers remain independently additive.
    val rotation = when {
        BttvEmoteModifier.ROTATE_RIGHT in modifiers -> 90f
        BttvEmoteModifier.ROTATE_LEFT in modifiers -> -90f
        else -> 0f
    }
    val hasRotation = rotation != 0f
    val flipVertical = !hasRotation && BttvEmoteModifier.FLIP_VERTICAL in modifiers
    val flipHorizontal = !hasRotation &&
        !flipVertical &&
        BttvEmoteModifier.FLIP_HORIZONTAL in modifiers
    return BttvEmoteTransform(
        wide = BttvEmoteModifier.WIDE in modifiers && !hasRotation,
        zeroSpace = BttvEmoteModifier.ZERO_SPACE in modifiers,
        flipHorizontal = flipHorizontal,
        flipVertical = flipVertical,
        rotationDegrees = rotation,
        cursed = BttvEmoteModifier.CURSED in modifiers,
        party = BttvEmoteModifier.PARTY in modifiers,
        shake = BttvEmoteModifier.SHAKE in modifiers,
    )
}

/**
 * Consumes BetterTTV modifier tokens immediately adjacent to an emote segment.
 *
 * BetterTTV parses these modifiers from normal chat text rather than from the emote catalog.
 * Prefix tokens (`w!`, `h!`, `v!`, `z!`, `c!`, `l!`, `r!`, `p!`, `s!`) apply to the following
 * emote. Legacy FFZ suffix tokens (`ffzW`, `ffzX`, `ffzY`, `ffzCursed`) apply to the previous
 * emote. Tokens without a valid adjacent emote remain visible text instead of being swallowed.
 */
internal fun applyBttvEmoteModifiers(
    segments: List<ChatMessageSegment>,
): List<ChatMessageSegment> {
    if (segments.none(ChatMessageSegment::canReceiveBttvModifier)) return segments
    val result = segments.toMutableList()

    result.indices.forEach { index ->
        val emote = result[index]
        if (!emote.canReceiveBttvModifier()) return@forEach

        val modifiers = linkedSetOf<BttvEmoteModifier>()
        val previousIndex = index - 1
        if (previousIndex >= 0 && result[previousIndex].kind == ChatMessageSegmentKind.TEXT) {
            val parsed = consumeTrailingPrefixModifiers(result[previousIndex].text)
            if (parsed.modifiers.isNotEmpty()) {
                modifiers += parsed.modifiers
                result[previousIndex] = result[previousIndex].copy(text = parsed.remainingText)
            }
        }

        val nextIndex = index + 1
        if (nextIndex < result.size && result[nextIndex].kind == ChatMessageSegmentKind.TEXT) {
            val parsed = consumeLeadingSuffixModifiers(result[nextIndex].text)
            if (parsed.modifiers.isNotEmpty()) {
                modifiers += parsed.modifiers
                result[nextIndex] = result[nextIndex].copy(text = parsed.remainingText)
            }
        }

        if (modifiers.isNotEmpty()) {
            result[index] = emote.copy(
                bttvModifiers = emote.bttvModifiers + modifiers,
            )
        }
    }

    return result.filterNot { segment ->
        segment.kind == ChatMessageSegmentKind.TEXT && segment.text.isEmpty()
    }
}

private data class ModifierConsumption(
    val remainingText: String,
    val modifiers: Set<BttvEmoteModifier>,
)

private fun consumeTrailingPrefixModifiers(value: String): ModifierConsumption {
    if (value.isEmpty()) return ModifierConsumption(value, emptySet())
    val tokens = tokenizeWhitespaceSeparated(value)
    if (tokens.isEmpty()) return ModifierConsumption(value, emptySet())

    var index = tokens.lastIndex
    while (index >= 0 && tokens[index].isWhitespace) index -= 1
    if (index < 0) return ModifierConsumption(value, emptySet())

    val consumed = linkedSetOf<BttvEmoteModifier>()
    var firstConsumedStart = value.length
    var cursor = index
    while (cursor >= 0) {
        val token = tokens[cursor]
        if (token.isWhitespace) {
            cursor -= 1
            continue
        }
        val modifier = PREFIX_MODIFIERS[token.text] ?: break
        consumed += modifier
        firstConsumedStart = token.start
        cursor -= 1
    }
    if (consumed.isEmpty()) return ModifierConsumption(value, emptySet())

    // A prefix modifier must be separated from preceding normal text. Keep the separator before
    // the modifier sequence so `hello w! Kappa` still renders as `hello Kappa` rather than
    // concatenating the words.
    val remaining = value.substring(0, firstConsumedStart)
        .trimEndModifierWhitespace()
    return ModifierConsumption(remaining, consumed)
}

private fun consumeLeadingSuffixModifiers(value: String): ModifierConsumption {
    if (value.isEmpty()) return ModifierConsumption(value, emptySet())
    val tokens = tokenizeWhitespaceSeparated(value)
    if (tokens.isEmpty()) return ModifierConsumption(value, emptySet())

    var cursor = 0
    while (cursor < tokens.size && tokens[cursor].isWhitespace) cursor += 1
    if (cursor >= tokens.size) return ModifierConsumption(value, emptySet())

    val consumed = linkedSetOf<BttvEmoteModifier>()
    var lastConsumedEnd = 0
    while (cursor < tokens.size) {
        val token = tokens[cursor]
        if (token.isWhitespace) {
            cursor += 1
            continue
        }
        val modifier = SUFFIX_MODIFIERS[token.text] ?: break
        consumed += modifier
        lastConsumedEnd = token.endExclusive
        cursor += 1
    }
    if (consumed.isEmpty()) return ModifierConsumption(value, emptySet())

    val suffix = value.substring(lastConsumedEnd)
        .trimStartModifierWhitespace()
    return ModifierConsumption(suffix, consumed)
}

private fun String.trimEndModifierWhitespace(): String {
    if (isEmpty()) return this
    val trimmed = trimEnd()
    return if (trimmed.isEmpty()) "" else "$trimmed "
}

private fun String.trimStartModifierWhitespace(): String {
    if (isEmpty()) return this
    val trimmed = trimStart()
    return if (trimmed.isEmpty()) "" else " $trimmed"
}

private data class TextToken(
    val text: String,
    val start: Int,
    val endExclusive: Int,
    val isWhitespace: Boolean,
)

private fun tokenizeWhitespaceSeparated(value: String): List<TextToken> = buildList {
    var start = 0
    while (start < value.length) {
        val whitespace = value[start].isWhitespace()
        var end = start + 1
        while (end < value.length && value[end].isWhitespace() == whitespace) end += 1
        add(
            TextToken(
                text = value.substring(start, end),
                start = start,
                endExclusive = end,
                isWhitespace = whitespace,
            ),
        )
        start = end
    }
}

private fun ChatMessageSegment.canReceiveBttvModifier(): Boolean =
    !imageUrl.isNullOrBlank() &&
        when (kind) {
            ChatMessageSegmentKind.TWITCH_EMOTE,
            ChatMessageSegmentKind.THIRD_PARTY_EMOTE,
            ChatMessageSegmentKind.GIF,
            ChatMessageSegmentKind.CHEERMOTE -> true
            else -> false
        }

private val PREFIX_MODIFIERS = mapOf(
    "w!" to BttvEmoteModifier.WIDE,
    "h!" to BttvEmoteModifier.FLIP_HORIZONTAL,
    "v!" to BttvEmoteModifier.FLIP_VERTICAL,
    "z!" to BttvEmoteModifier.ZERO_SPACE,
    "c!" to BttvEmoteModifier.CURSED,
    "l!" to BttvEmoteModifier.ROTATE_LEFT,
    "r!" to BttvEmoteModifier.ROTATE_RIGHT,
    "p!" to BttvEmoteModifier.PARTY,
    "s!" to BttvEmoteModifier.SHAKE,
)

private val SUFFIX_MODIFIERS = mapOf(
    "ffzW" to BttvEmoteModifier.WIDE,
    "ffzX" to BttvEmoteModifier.FLIP_HORIZONTAL,
    "ffzY" to BttvEmoteModifier.FLIP_VERTICAL,
    "ffzCursed" to BttvEmoteModifier.CURSED,
)
