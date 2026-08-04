package io.ferventio.app.domain

/** Stable persisted order for the moderation shortcuts shown in the user card. */
object UserCardModerationLayout {
    fun defaultOrder(timeoutPresetsSeconds: List<Int>): List<String> =
        timeoutPresetsSeconds.distinct().map(::timeoutActionId) + listOf(WARN, BAN, UNBAN)

    fun normalize(
        storedOrder: List<String>,
        timeoutPresetsSeconds: List<Int>,
    ): List<String> {
        val defaults = defaultOrder(timeoutPresetsSeconds)
        val allowed = defaults.toSet()
        return buildList {
            storedOrder.filter { it in allowed }.distinct().forEach(::add)
            defaults.filter { it !in this }.forEach(::add)
        }
    }

    fun move(
        storedOrder: List<String>,
        timeoutPresetsSeconds: List<Int>,
        actionId: String,
        direction: Int,
        hiddenActionIds: Set<String> = emptySet(),
    ): List<String> {
        val normalized = normalize(storedOrder, timeoutPresetsSeconds)
        val visible = normalized.filterNot(hiddenActionIds::contains)
        if (direction == 0) return normalized
        val index = visible.indexOf(actionId)
        if (index < 0) return normalized
        val target = (index + direction).coerceIn(0, visible.lastIndex)
        if (target == index) return normalized
        val movedVisible = visible.toMutableList().apply {
            add(target, removeAt(index))
        }
        return movedVisible + normalized.filter(hiddenActionIds::contains)
    }

    fun timeoutActionId(seconds: Int): String = "timeout:$seconds"

    const val WARN = "warn"
    const val BAN = "ban"
    const val UNBAN = "unban"
}
