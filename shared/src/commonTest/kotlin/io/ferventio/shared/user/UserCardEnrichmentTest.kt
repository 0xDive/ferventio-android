package io.ferventio.shared.user

import io.ferventio.app.domain.ChannelFollowerInfo
import io.ferventio.app.domain.PublicChannelRelationship
import io.ferventio.app.domain.TwitchUser
import io.ferventio.app.domain.UserCardData
import kotlin.test.Test
import kotlin.test.assertEquals

class UserCardEnrichmentTest {
    @Test
    fun remoteProfileAndRelationshipOverlayLocalCard() {
        val local = UserCardData(
            channelId = "channel-id",
            user = TwitchUser(
                id = "user-id",
                login = "viewer",
                displayName = "viewer",
            ),
        )
        val remote = UserCardRemoteEnrichment(
            user = TwitchUser(
                id = "user-id",
                login = "viewer",
                displayName = "Viewer",
                profileImageUrl = "https://cdn.test/avatar.png",
                createdAt = "2020-01-01T00:00:00Z",
                description = "Profile",
            ),
            relationship = PublicChannelRelationship(
                followedAt = "2024-01-01T00:00:00Z",
                subscriptionStatusHidden = false,
                isCurrentlySubscribed = true,
                subscriberMonths = 18,
                subscriberTier = "2000",
            ),
        )

        val enriched = local.withRemoteEnrichment(remote)

        assertEquals("Viewer", enriched.user.displayName)
        assertEquals("https://cdn.test/avatar.png", enriched.user.profileImageUrl)
        assertEquals(ChannelFollowerInfo("2024-01-01T00:00:00Z"), enriched.followerInfo)
        assertEquals(true, enriched.isCurrentlySubscribed)
        assertEquals(18, enriched.subscriberMonths)
        assertEquals("2000", enriched.subscriberTier)
    }

    @Test
    fun missingRemoteRelationshipPreservesExistingLocalFacts() {
        val local = UserCardData(
            channelId = "channel-id",
            user = TwitchUser("user-id", "viewer", "Viewer"),
            followerInfo = ChannelFollowerInfo("2023-01-01T00:00:00Z"),
            subscriberMonths = 3,
            subscriberTier = "1000",
            isCurrentlySubscribed = true,
        )

        val enriched = local.withRemoteEnrichment(
            UserCardRemoteEnrichment(
                user = local.user.copy(profileImageUrl = "https://cdn.test/avatar.png"),
                relationship = null,
            ),
        )

        assertEquals("2023-01-01T00:00:00Z", enriched.followerInfo.followedAt)
        assertEquals(3, enriched.subscriberMonths)
        assertEquals("1000", enriched.subscriberTier)
        assertEquals(true, enriched.isCurrentlySubscribed)
    }
}
