package io.ferventio.shared.chat

internal class TwitchEventSubAuthorizationRevokedException(
    val subscriptionType: String?,
) : IllegalStateException(
    buildString {
        append("Twitch EventSub authorization was revoked")
        subscriptionType?.takeIf(String::isNotBlank)?.let { type ->
            append(" for ")
            append(type)
        }
    },
)

internal fun Throwable.isTwitchAuthenticationFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is TwitchEventSubAuthorizationRevokedException) {
            return true
        }
        if (current is TwitchEventSubSubscriptionException && current.statusCode in AUTH_FAILURE_CODES) {
            return true
        }
        current = current.cause
    }
    return false
}

private val AUTH_FAILURE_CODES = setOf(401, 403)
