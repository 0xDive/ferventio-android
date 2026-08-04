package io.ferventio.app.domain

object ChatAssetResolver {
    private const val TWITCH_EMOTE_TEMPLATE =
        "https://static-cdn.jtvnw.net/emoticons/v2/{id}/{format}/dark/{scale}"
    private const val BETTER_TTV_EMOTE_TEMPLATE =
        "https://cdn.betterttv.net/emote/{id}/{scale}"
    private const val SEVEN_TV_EMOTE_TEMPLATE =
        "https://cdn.7tv.app/emote/{id}/{scale}.webp"

    fun twitchEmoteUrl(
        fragment: ChatFragment.TwitchEmote,
        animate: Boolean,
        scale: String = "2.0",
    ): String? {
        val id = fragment.emoteId.trim()
        if (id.isEmpty()) return null
        val format = if (animate && "animated" in fragment.formats) "animated" else "static"
        return TWITCH_EMOTE_TEMPLATE
            .replace("{id}", id)
            .replace("{format}", format)
            .replace("{scale}", scale)
    }

    fun twitchEmoteUrl(
        emoteId: String,
        animate: Boolean,
        scale: String = "2.0",
        animatedAvailable: Boolean = false,
    ): String? {
        val id = emoteId.trim()
        if (id.isEmpty()) return null
        val format = if (animate && animatedAvailable) "animated" else "static"
        return TWITCH_EMOTE_TEMPLATE
            .replace("{id}", id)
            .replace("{format}", format)
            .replace("{scale}", scale)
    }

    fun betterTtvEmoteUrl(
        emoteId: String,
        scale: String = "2x",
    ): String? {
        val id = emoteId.trim()
        if (id.isEmpty()) return null
        return BETTER_TTV_EMOTE_TEMPLATE
            .replace("{id}", id)
            .replace("{scale}", scale)
    }

    fun sevenTvEmoteUrl(
        emoteId: String,
        scale: String = "2x",
    ): String? {
        val id = emoteId.trim()
        if (id.isEmpty()) return null
        return SEVEN_TV_EMOTE_TEMPLATE
            .replace("{id}", id)
            .replace("{scale}", scale)
    }

    fun absoluteImageUrl(value: String?): String? {
        val url = value?.trim().orEmpty()
        if (url.isEmpty()) return null
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") || url.startsWith("https://") -> url
            else -> null
        }
    }

    fun badgeAsset(
        badge: ChatBadge,
        assets: Map<String, ChatBadgeAsset>,
    ): ChatBadgeAsset? = assets[chatBadgeAssetKey(badge.setId, badge.id)]
}
