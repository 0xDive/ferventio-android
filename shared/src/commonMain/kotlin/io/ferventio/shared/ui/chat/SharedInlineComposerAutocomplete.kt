package io.ferventio.shared.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ferventio.app.domain.ComposerSuggestion

@Composable
internal fun SharedInlineComposerAutocomplete(
    suggestions: List<ComposerSuggestion>,
    selectedIndex: Int,
    onSelect: (ComposerSuggestion) -> Unit,
) {
    if (suggestions.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = suggestions,
            key = { _, suggestion -> suggestion.key },
        ) { index, suggestion ->
            val selected = index == selectedIndex.coerceIn(0, suggestions.lastIndex)
            Surface(
                modifier = Modifier
                    .height(50.dp)
                    .width(132.dp)
                    .clickable { onSelect(suggestion) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                tonalElevation = if (selected) 4.dp else 1.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SharedSuggestionLeading(suggestion)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = sharedSuggestionTitle(suggestion),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = sharedSuggestionSubtitle(suggestion),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedSuggestionLeading(suggestion: ComposerSuggestion) {
    when (suggestion) {
        is ComposerSuggestion.Emote -> AsyncImage(
            model = suggestion.asset.imageUrl2x.ifBlank { suggestion.asset.imageUrl1x },
            contentDescription = suggestion.asset.code,
            modifier = Modifier.size(38.dp),
            contentScale = ContentScale.Fit,
        )

        is ComposerSuggestion.User -> if (!suggestion.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = suggestion.avatarUrl,
                contentDescription = suggestion.displayName,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.AlternateEmail, contentDescription = null)
            }
        }
    }
}

private fun sharedSuggestionTitle(suggestion: ComposerSuggestion): String = when (suggestion) {
    is ComposerSuggestion.Emote -> suggestion.asset.code
    is ComposerSuggestion.User -> "@" + suggestion.login
}

private fun sharedSuggestionSubtitle(suggestion: ComposerSuggestion): String = when (suggestion) {
    is ComposerSuggestion.Emote -> suggestion.asset.provider
    is ComposerSuggestion.User -> suggestion.displayName
}
