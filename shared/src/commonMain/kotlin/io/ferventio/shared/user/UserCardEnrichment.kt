package io.ferventio.shared.user

import io.ferventio.app.domain.ChannelFollowerInfo
import io.ferventio.app.domain.UserCardData

internal fun UserCardData.withRemoteEnrichment(
    remote: UserCardRemoteEnrichment?,
): UserCardData {
    val relationship = remote?.relationship
    return copy(
        user = remote?.user ?: user,
        followerInfo = relationship
            ?.let { ChannelFollowerInfo(followedAt = it.followedAt) }
            ?: followerInfo,
        subscriberMonths = relationship?.subscriberMonths ?: subscriberMonths,
        subscriberTier = relationship?.subscriberTier ?: subscriberTier,
        subscriptionStatusHidden = relationship?.subscriptionStatusHidden
            ?: subscriptionStatusHidden,
        isCurrentlySubscribed = relationship?.isCurrentlySubscribed
            ?: isCurrentlySubscribed,
    )
}
