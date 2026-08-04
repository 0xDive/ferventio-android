package io.ferventio.app.domain

enum class AppThemeMode {
    LIGHT,
    DARK,
    AMOLED,
}

enum class MessageDensity {
    COMPACT,
    NORMAL,
    RELAXED,
}

enum class ChatNameStyle {
    DISPLAY_NAME,
    LOGIN,
    DISPLAY_AND_LOGIN,
}

enum class SettingsSyncStatus {
    DISABLED,
    IDLE,
    SYNCING,
    CONFLICT,
    ERROR,
}

data class SettingsSyncHistoryEntry(
    val revision: Long,
    val updatedAt: String,
    val updatedByInstallationId: String,
    val appVersion: String?,
)

data class SettingsSyncConflict(
    val serverRevision: Long,
    val serverUpdatedAt: String,
    val serverUpdatedByInstallationId: String,
    val serverPayload: String,
)

object MentionColors {
    const val GOLD: Long = 0xFFFFC857
    const val PURPLE: Long = 0xFFC7A8FF
    const val BLUE: Long = 0xFF7DB7FF
    const val GREEN: Long = 0xFF72D9A2
    const val ORANGE: Long = 0xFFFF9F5A
    const val RED: Long = 0xFFFF7D7D

    val presets: List<Long> = listOf(GOLD, PURPLE, BLUE, GREEN, ORANGE, RED)
}
