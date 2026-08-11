package io.ferventio.app.domain

object UserCardBanState {
    fun resolve(
        knownPermanentlyBanned: Boolean,
        localActions: List<LocalModerationAction>,
    ): Boolean {
        val latestBanStateAction = localActions
            .asSequence()
            .map { action -> action to action.action.uppercase() }
            .filter { (_, action) -> action == "BAN" || action == "UNBAN" }
            .maxByOrNull { (entry, _) -> entry.createdAtMillis }

        return when (latestBanStateAction?.second) {
            "BAN" -> true
            "UNBAN" -> false
            else -> knownPermanentlyBanned
        }
    }
}
