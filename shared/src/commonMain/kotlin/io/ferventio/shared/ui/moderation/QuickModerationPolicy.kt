package io.ferventio.shared.ui.moderation

import io.ferventio.app.domain.ChatMessage
import io.ferventio.shared.settings.SharedLocalUiPreferences

data class QuickModerationAvailability(
    val canBan: Boolean,
    val canDelete: Boolean,
)

/** Android 0.0.5 quick-moderation visibility and safety contract. */
fun quickModerationAvailability(
    message: ChatMessage,
    ownUserId: String?,
    canModerate: Boolean,
    preferences: SharedLocalUiPreferences,
): QuickModerationAvailability {
    val broadcaster = message.badges.any { badge -> badge.setId == "broadcaster" }
    return QuickModerationAvailability(
        canBan = preferences.showQuickBan &&
            canModerate &&
            !message.isDeleted &&
            !message.isSystem &&
            message.userId.isNotBlank() &&
            message.userId != ownUserId &&
            !broadcaster,
        canDelete = preferences.showQuickDelete &&
            canModerate &&
            !message.isDeleted &&
            !message.isSystem &&
            message.id.isNotBlank(),
    )
}
