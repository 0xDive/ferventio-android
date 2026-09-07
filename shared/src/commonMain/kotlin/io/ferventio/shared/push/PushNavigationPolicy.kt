package io.ferventio.shared.push

data class PushNavigationInput(
    val channelId: String? = null,
    val channelLogin: String? = null,
    val messageId: String? = null,
    val destination: String? = null,
)

data class PushChannelReference(
    val id: String? = null,
    val login: String? = null,
)

sealed interface PushNavigationTarget {
    data object PushSettings : PushNavigationTarget

    data class Mentions(
        val channel: PushChannelReference,
    ) : PushNavigationTarget

    data class Moderation(
        val channel: PushChannelReference,
    ) : PushNavigationTarget

    data class Message(
        val channel: PushChannelReference,
        val messageId: String,
    ) : PushNavigationTarget

    data class Channel(
        val channel: PushChannelReference,
    ) : PushNavigationTarget
}

/** Normalizes platform push payload fields into a single cross-platform navigation target. */
object PushNavigationPolicy {
    fun resolve(input: PushNavigationInput): PushNavigationTarget? {
        val destination = input.destination.normalized()?.lowercase()
        if (destination == "push_settings") {
            return PushNavigationTarget.PushSettings
        }

        val channel = PushChannelReference(
            id = input.channelId.normalized(),
            login = input.channelLogin.normalized()?.lowercase(),
        ).takeIf { it.id != null || it.login != null } ?: return null

        return when (destination) {
            "mentions" -> PushNavigationTarget.Mentions(channel)
            "moderation" -> PushNavigationTarget.Moderation(channel)
            else -> input.messageId.normalized()?.let { messageId ->
                PushNavigationTarget.Message(channel, messageId)
            } ?: PushNavigationTarget.Channel(channel)
        }
    }

    private fun String?.normalized(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)
}
