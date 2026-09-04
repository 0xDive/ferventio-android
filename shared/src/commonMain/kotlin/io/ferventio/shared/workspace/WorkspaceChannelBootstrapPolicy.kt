package io.ferventio.shared.workspace

import io.ferventio.app.domain.ChatChannel

data class WorkspaceChannelBootstrapResult(
    val channels: List<ChatChannel>,
    val selectedChannelId: String?,
)

/**
 * Platform-neutral merge policy for restoring the channel workspace.
 *
 * Persisted login order remains the source of truth. Fresh Twitch metadata wins over cache,
 * while cache fills transient refresh gaps so an outage does not make saved channels disappear.
 */
object WorkspaceChannelBootstrapPolicy {
    fun resolve(
        persistedLogins: List<String>,
        cachedChannels: List<ChatChannel>,
        refreshedChannels: List<ChatChannel>,
        selectedLogin: String?,
    ): WorkspaceChannelBootstrapResult {
        val orderedLogins = normalizeLogins(persistedLogins)
        if (orderedLogins.isEmpty()) {
            return WorkspaceChannelBootstrapResult(
                channels = emptyList(),
                selectedChannelId = null,
            )
        }

        val cachedByLogin = channelsByLogin(cachedChannels)
        val refreshedByLogin = channelsByLogin(refreshedChannels)
        val seenIds = hashSetOf<String>()
        val channels = buildList(orderedLogins.size) {
            orderedLogins.forEach { login ->
                val channel = refreshedByLogin[login] ?: cachedByLogin[login]
                if (channel != null && seenIds.add(channel.id)) {
                    add(channel)
                }
            }
        }

        val normalizedSelectedLogin = selectedLogin
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotEmpty)
        val selectedChannelId = normalizedSelectedLogin
            ?.let { login -> channels.firstOrNull { it.login.lowercase() == login } }
            ?.id
            ?: channels.firstOrNull()?.id

        return WorkspaceChannelBootstrapResult(
            channels = channels,
            selectedChannelId = selectedChannelId,
        )
    }

    fun normalizeLogins(logins: Iterable<String>): List<String> {
        val seen = hashSetOf<String>()
        return buildList {
            logins.forEach { raw ->
                val login = raw.trim().lowercase()
                if (login.isNotEmpty() && seen.add(login)) {
                    add(login)
                }
            }
        }
    }

    private fun channelsByLogin(channels: List<ChatChannel>): Map<String, ChatChannel> =
        buildMap {
            channels.forEach { channel ->
                val login = channel.login.trim().lowercase()
                if (channel.id.isNotBlank() && login.isNotEmpty()) {
                    put(login, channel)
                }
            }
        }
}
