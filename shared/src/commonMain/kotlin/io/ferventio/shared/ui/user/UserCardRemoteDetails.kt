package io.ferventio.shared.ui.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ferventio.app.domain.UserCardData
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.user_card_account_created
import io.ferventio.shared.generated.resources.user_card_following_since
import io.ferventio.shared.generated.resources.user_card_not_subscribed
import io.ferventio.shared.generated.resources.user_card_subscribed
import io.ferventio.shared.generated.resources.user_card_subscribed_months
import io.ferventio.shared.generated.resources.user_card_subscriber_tier
import io.ferventio.shared.generated.resources.user_card_subscription_hidden
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun UserCardRemoteDetails(
    data: UserCardData,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        data.user.description
            ?.takeIf(String::isNotBlank)
            ?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

        if (data.hasRelationshipFacts()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    data.user.createdAt
                        ?.takeIf(String::isNotBlank)
                        ?.let { createdAt ->
                            Fact(stringResource(Res.string.user_card_account_created, createdAt))
                        }
                    data.followerInfo.followedAt
                        ?.takeIf(String::isNotBlank)
                        ?.let { followedAt ->
                            Fact(stringResource(Res.string.user_card_following_since, followedAt))
                        }
                    when {
                        data.subscriptionStatusHidden -> Fact(
                            stringResource(Res.string.user_card_subscription_hidden),
                        )
                        data.isCurrentlySubscribed == true -> {
                            Fact(stringResource(Res.string.user_card_subscribed))
                            data.subscriberMonths?.takeIf { it > 0 }?.let { months ->
                                Fact(stringResource(Res.string.user_card_subscribed_months, months))
                            }
                            data.subscriberTier?.takeIf(String::isNotBlank)?.let { tier ->
                                Fact(stringResource(Res.string.user_card_subscriber_tier, tier))
                            }
                        }
                        data.isCurrentlySubscribed == false -> Fact(
                            stringResource(Res.string.user_card_not_subscribed),
                        )
                    }
                }
            }
        }
    }
}

internal fun hasUserCardRemoteDetails(data: UserCardData): Boolean =
    !data.user.description.isNullOrBlank() || data.hasRelationshipFacts()

@Composable
private fun Fact(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun UserCardData.hasRelationshipFacts(): Boolean =
    !user.createdAt.isNullOrBlank() ||
        !followerInfo.followedAt.isNullOrBlank() ||
        subscriptionStatusHidden ||
        isCurrentlySubscribed != null
