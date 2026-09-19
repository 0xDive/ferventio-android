package io.ferventio.shared.moderation

import io.ferventio.app.domain.ModerationUser
import io.ferventio.app.domain.ModerationUserGroup

/**
 * Merges role hints from Twitch Community surfaces with canonical user identities.
 *
 * Community endpoints may expose only login-derived placeholder IDs/display names, while canonical
 * Helix/chat state owns stable identity. Role hints win when known; canonical IDs and display names
 * remain authoritative whenever available.
 */
fun mergeCategorizedChatters(
    canonical: List<ModerationUser>,
    categorized: List<ModerationUser>,
): List<ModerationUser> {
    val canonicalByLogin = canonical.associateBy { it.login.lowercase() }
    val categorizedLogins = categorized.mapTo(hashSetOf()) { it.login.lowercase() }
    val merged = categorized.map { categorizedUser ->
        val canonicalUser = canonicalByLogin[categorizedUser.login.lowercase()]
        if (canonicalUser == null) {
            categorizedUser.withViewerFallback()
        } else {
            canonicalUser.copy(
                group = categorizedUser.group.takeUnless { it == ModerationUserGroup.UNKNOWN }
                    ?: canonicalUser.group.takeUnless { it == ModerationUserGroup.UNKNOWN }
                    ?: ModerationUserGroup.VIEWER,
            )
        }
    }
    return merged + canonical
        .filterNot { it.login.lowercase() in categorizedLogins }
        .map(ModerationUser::withViewerFallback)
}

private fun ModerationUser.withViewerFallback(): ModerationUser =
    if (group == ModerationUserGroup.UNKNOWN) copy(group = ModerationUserGroup.VIEWER) else this
