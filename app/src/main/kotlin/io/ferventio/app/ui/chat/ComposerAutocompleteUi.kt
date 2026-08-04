package io.ferventio.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.ferventio.app.domain.ComposerSuggestion

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InlineComposerAutocomplete(
    suggestions: List<ComposerSuggestion>,
    selectedIndex: Int,
    onSelect: (ComposerSuggestion) -> Unit,
    onOpenEmoteInfo: (ComposerSuggestion.Emote) -> Unit,
) {
    if (suggestions.isEmpty()) return
    val stopHorizontalOverscroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(x = available.x, y = 0f)

            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity,
            ): Velocity = Velocity(x = available.x, y = 0f)
        }
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(stopHorizontalOverscroll)
            .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(
            items = suggestions,
            key = { _, item -> item.key },
        ) { index, suggestion ->
            val selected = index == selectedIndex.coerceIn(0, suggestions.lastIndex)
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .width(if (index == 0) 178.dp else 132.dp)
                    .combinedClickable(
                        onClick = { onSelect(suggestion) },
                        onLongClick = {
                            if (suggestion is ComposerSuggestion.Emote) onOpenEmoteInfo(suggestion)
                        },
                    ),
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
                    SuggestionLeading(suggestion)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = suggestionTitle(suggestion),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = suggestionSubtitle(suggestion),
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
private fun SuggestionLeading(suggestion: ComposerSuggestion) {
    when (suggestion) {
        is ComposerSuggestion.Emote -> AsyncImage(
            model = suggestion.asset.imageUrl2x.ifBlank { suggestion.asset.imageUrl1x },
            contentDescription = suggestion.asset.code,
            modifier = Modifier.size(40.dp),
            contentScale = ContentScale.Fit,
        )

        is ComposerSuggestion.User -> if (!suggestion.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = suggestion.avatarUrl,
                contentDescription = suggestion.displayName,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.AlternateEmail, contentDescription = null)
            }
        }

    }
}

private fun suggestionTitle(suggestion: ComposerSuggestion): String = when (suggestion) {
    is ComposerSuggestion.Emote -> suggestion.asset.code
    is ComposerSuggestion.User -> "@${suggestion.login}"
}

private fun suggestionSubtitle(suggestion: ComposerSuggestion): String = when (suggestion) {
    is ComposerSuggestion.Emote -> suggestion.asset.provider
    is ComposerSuggestion.User -> suggestion.displayName
}
