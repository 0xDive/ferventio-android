package io.ferventio.shared.settings

/** Android source-of-truth presets exposed by the shared History settings UI. */
internal object HistorySettingsPresets {
    val messageLimits: List<Int> = listOf(250, 500, 1_000)
    val retentionDays: List<Int> = listOf(1, 7, 30, 0)
    val maxSizeMb: List<Int> = listOf(50, 100, 250, 0)
}
